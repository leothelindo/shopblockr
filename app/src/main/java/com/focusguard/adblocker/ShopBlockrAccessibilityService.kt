package com.focusguard.adblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class ShopBlockrAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ShopBlockrService"
        private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
        
        // TikTok UI element identifiers for shopping elements
        private val SHOPPING_ELEMENTS = listOf(
            "shopping_bag",
            "shop_now", 
            "buy_now",
            "add_to_cart",
            "purchase",
            "shop_tab",
            "shopping_cart",
            "product_link",
            "tiktok_shop",
            "shop_button",
            "learn_more",
            "learn more"
        )
        
        // Precise sponsored content indicators (excluding generic "shop")
        private val SPONSORED_INDICATORS = listOf(
            "sponsored",
            "paid partnership",
            "ad",
            "promoted",
            "brand partner",
            "#ad",
            "#sponsored",
            "partnership with",
            "paid promotion",
            "advertiser",
            "learn more"
        )
        
        // TikTok Shop navigation elements (these should trigger countdown, not sponsor warnings)
        private val SHOP_TAB_ELEMENTS = listOf(
            "shop",
            "shopping",
            "tiktok_shop",
            "shop_tab",
            "mall",
            "storefront"
        )
        
        // UI elements to ignore for sponsored detection (permanent navigation)
        private val NAVIGATION_ELEMENTS = listOf(
            "bottom_nav",
            "top_nav",
            "tab_bar",
            "navigation",
            "menu",
            "toolbar"
        )
    }

    private var overlayService: OverlayService? = null
    private lateinit var settingsManager: SettingsManager
    private lateinit var clickTracker: ClickTracker
    private val seenSponsoredContent = mutableSetOf<String>()
    
    // Debouncing for content changes to prevent multiple events per swipe
    private var lastContentChangeTime = 0L
    private val contentChangeDebounceMs = 500L // 0.5 seconds for better live content responsiveness
    private var lastPeriodicScanTime = 0L
    private val periodicScanIntervalMs = 3000L // Scan every 3 seconds for live content
    
    // Shop blocking tracking
    private var hasActiveShopTabs = false
    private var lastShopTabSeenTime = 0L
    private val shopTabPersistenceMs = 2000L // Keep overlays for 2 seconds after shop disappears
    
    // Session tracking
    private var isTikTokSessionActive = false
    private lateinit var adAnalytics: AdAnalytics
    
    // Auto-swipe tracking
    private var lastAutoSwipeTime = 0L
    private val autoSwipeDebounceMs = 3000L // Minimum 3 seconds between auto-swipes

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "ShopBlockr Accessibility Service Connected")
        
        settingsManager = SettingsManager(this)
        
        // Initialize analytics and click tracker
        adAnalytics = AdAnalytics.getInstance(this)
        clickTracker = ClickTracker(adAnalytics)
        
        // Start overlay service
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
    }
    

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Check if service is enabled before processing events
        if (!::settingsManager.isInitialized || !settingsManager.isServiceEnabled()) {
            return
        }
        
        // Free version - no subscription checks needed
        
        event?.let { accessibilityEvent ->
            if (accessibilityEvent.packageName == TIKTOK_PACKAGE) {
                Log.v(TAG, "TikTok event: ${AccessibilityEvent.eventTypeToString(accessibilityEvent.eventType)}")
                
                // Start TikTok session if not already active
                if (!isTikTokSessionActive) {
                    // Session started - no explicit tracking needed with UsageStatsManager
                    isTikTokSessionActive = true
                    Log.d(TAG, "TikTok session started")
                }
                
                // Handle different event types - optimized for live content detection
                when (accessibilityEvent.eventType) {
                    AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                        Log.v(TAG, "TikTok click event detected")
                        // Handle click events (for analytics only)
                        handleClickEvent(accessibilityEvent)
                        // Track clicks for analytics
                        clickTracker.trackClicks(accessibilityEvent, this)
                    }
                    AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastContentChangeTime > contentChangeDebounceMs) {
                            Log.v(TAG, "TikTok view focused - checking for shopping elements")
                            lastContentChangeTime = currentTime
                            handleTikTokEvent()
                        } else {
                            Log.v(TAG, "Debouncing view focused event")
                        }
                    }
                    AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastPeriodicScanTime > periodicScanIntervalMs / 2) { // More frequent on scroll
                            Log.v(TAG, "TikTok scroll detected - checking for live shopping")
                            lastPeriodicScanTime = currentTime
                            handleTikTokEvent()
                        } else {
                            Log.v(TAG, "Debouncing scroll event")
                        }
                    }
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                        val currentTime = System.currentTimeMillis()
                        
                        // Process text changes with reduced debounce for live content
                        if (accessibilityEvent.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT != 0) {
                            // Debounce rapid text changes from the same transition
                            if (currentTime - lastContentChangeTime < contentChangeDebounceMs) {
                                Log.v(TAG, "Debouncing rapid text change - ignoring")
                                return
                            }
                            
                            Log.d(TAG, "TikTok TEXT changed - clearing seen content and checking for sponsored content")
                            // Clear seen content on text changes (indicates new video content)
                            seenSponsoredContent.clear()
                            lastContentChangeTime = currentTime
                            handleTikTokEvent()
                        } else {
                            // For live videos, periodically scan even without text changes
                            // since shopping elements may appear without text changes
                            if (currentTime - lastPeriodicScanTime > periodicScanIntervalMs) {
                                Log.d(TAG, "Periodic scan for live content (no text change)")
                                lastPeriodicScanTime = currentTime
                                handleTikTokEvent()
                            } else {
                                Log.v(TAG, "Ignoring non-text content change (not time for periodic scan)")
                            }
                        }
                    }
                    else -> {
                        Log.v(TAG, "Unhandled TikTok event type: ${AccessibilityEvent.eventTypeToString(accessibilityEvent.eventType)}")
                    }
                }
            } else {
                // End TikTok session when switching to different app
                if (isTikTokSessionActive) {
                    // Session ended - no explicit tracking needed with UsageStatsManager
                    isTikTokSessionActive = false
                    Log.d(TAG, "TikTok session ended - switched to ${accessibilityEvent.packageName}")
                }
                
                // ALWAYS clear ALL overlays when leaving TikTok to prevent overlay persistence outside the app
                Log.d(TAG, "Left TikTok app - FORCE clearing ALL overlays immediately")
                clearAllOverlays()
                hasActiveShopTabs = false
                lastShopTabSeenTime = 0L // Reset persistence timer
                Log.v(TAG, "Non-TikTok app event: ${accessibilityEvent.packageName}")
            }
        }
    }

    private fun handleTikTokEvent() {
        val rootNode = rootInActiveWindow
        rootNode?.let { 
            // Detect shop tab elements if shop blocking is enabled
            if (settingsManager.isShopBlockingEnabled()) {
                detectShopTabs(it)
            }
            // Detect shopping elements in posts, not navigation
            detectShoppingElements(it)
            // Detect sponsored content and shopping content
            detectSponsoredContent(it)
        }
    }
    
    private fun handleClickEvent(event: AccessibilityEvent) {
        val clickedNode = event.source
        Log.v(TAG, "Click event detected")
        
        // Track clicks for analytics
        if (clickedNode != null) {
            clickTracker.trackClicks(event, this)
        }
    }
    
    private fun detectShopTabs(rootNode: AccessibilityNodeInfo) {
        val shopTabNodes = mutableListOf<AccessibilityNodeInfo>()
        findShopTabs(rootNode, shopTabNodes)
        
        if (shopTabNodes.isNotEmpty()) {
            Log.d(TAG, "Found ${shopTabNodes.size} shop tab elements")
            hasActiveShopTabs = true
            lastShopTabSeenTime = System.currentTimeMillis()
            
            // Record shop tab visit for analytics
            adAnalytics.recordShopTabVisit()
            
            when (settingsManager.getShopBlockingMode()) {
                SettingsManager.SHOP_BLOCKING_OVERLAY -> {
                    // Send shop tabs to overlay service for blocking
                    val intent = Intent(this, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_BLOCK_SHOP_TABS
                        putExtra(OverlayService.EXTRA_ELEMENT_COUNT, shopTabNodes.size)
                        
                        // Extract bounds of shop tab elements
                        val bounds = shopTabNodes.mapNotNull { node ->
                            val rect = android.graphics.Rect()
                            node.getBoundsInScreen(rect)
                            if (!rect.isEmpty) {
                                intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
                            } else null
                        }.toTypedArray()
                        
                        putExtra(OverlayService.EXTRA_ELEMENT_BOUNDS, bounds)
                    }
                    startService(intent)
                }
            }
        } else {
            // No shop tabs found - check if persistence period has expired
            if (hasActiveShopTabs) {
                val timeSinceLastSeen = System.currentTimeMillis() - lastShopTabSeenTime
                if (timeSinceLastSeen > shopTabPersistenceMs) {
                    Log.d(TAG, "Shop tab persistence expired (${timeSinceLastSeen}ms) - clearing overlays")
                    clearShopTabOverlays()
                    hasActiveShopTabs = false
                } else {
                    Log.v(TAG, "Shop tabs not found but within persistence period (${timeSinceLastSeen}ms)")
                }
            }
        }
    }
    
    private fun findShopTabs(node: AccessibilityNodeInfo, results: MutableList<AccessibilityNodeInfo>) {
        // Check current node for shop tab indicators
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""
        
        // Look for shop tab elements, especially at the top of the screen
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        
        // Focus on elements in the top navigation area (increased height to catch more tabs)
        val isInTopNavigation = rect.top < 400
        
        // Look specifically for "Shop" text (case-insensitive) or other shop indicators
        val isShopTab = text.equals("Shop", ignoreCase = true) ||
                        contentDesc.equals("Shop", ignoreCase = true) ||
                        text.contains("Shop", ignoreCase = true) ||
                        contentDesc.contains("Shop", ignoreCase = true) ||
                        SHOP_TAB_ELEMENTS.any { element ->
                            text.contains(element, ignoreCase = true) || 
                            contentDesc.contains(element, ignoreCase = true) || 
                            resourceId.contains(element, ignoreCase = true)
                        }
        
        if (isShopTab && isInTopNavigation) {
            results.add(node)
            Log.d(TAG, "Found shop tab: text='$text', desc='$contentDesc', resourceId='$resourceId', bounds=$rect, clickable=${node.isClickable}")
        }
        
        // Log all top navigation elements for debugging (only when shop blocking is enabled)
        if (isInTopNavigation && (text.isNotEmpty() || contentDesc.isNotEmpty()) && settingsManager.isShopBlockingEnabled()) {
            Log.v(TAG, "Top nav element: text='$text', desc='$contentDesc', resourceId='$resourceId', bounds=$rect")
        }
        
        // Recursively check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let { findShopTabs(it, results) }
        }
    }
    
    
    private fun detectShoppingElements(rootNode: AccessibilityNodeInfo) {
        val shoppingNodes = mutableListOf<AccessibilityNodeInfo>()
        findShoppingElements(rootNode, shoppingNodes)
        
        if (shoppingNodes.isNotEmpty()) {
            Log.d(TAG, "Found ${shoppingNodes.size} shopping elements")
            
            // Send shopping elements to overlay service
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_BLOCK_SHOPPING_ELEMENTS
                putExtra(OverlayService.EXTRA_ELEMENT_COUNT, shoppingNodes.size)
                
                // Extract bounds of shopping elements
                val bounds = shoppingNodes.mapNotNull { node ->
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        intArrayOf(rect.left, rect.top, rect.right, rect.bottom)
                    } else null
                }.toTypedArray()
                
                putExtra(OverlayService.EXTRA_ELEMENT_BOUNDS, bounds)
            }
            startService(intent)
        }
    }
    
    private fun clearShopTabOverlays() {
        // Send clear command to overlay service
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_CLEAR_SHOP_TAB_OVERLAYS
        }
        startService(intent)
    }
    
    private fun clearAllOverlays() {
        // Send clear all command to overlay service
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_CLEAR_ALL_OVERLAYS
        }
        startService(intent)
    }

    private fun findShoppingElements(node: AccessibilityNodeInfo, results: MutableList<AccessibilityNodeInfo>) {
        // Check current node
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val resourceId = node.viewIdResourceName?.lowercase() ?: ""
        
        val isShoppingElement = SHOPPING_ELEMENTS.any { element ->
            text.contains(element) || contentDesc.contains(element) || resourceId.contains(element)
        }
        
        if (isShoppingElement && node.isClickable) {
            results.add(node)
        }
        
        // Recursively check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let { findShoppingElements(it, results) }
        }
    }

    private fun detectSponsoredContent(rootNode: AccessibilityNodeInfo) {
        Log.d(TAG, "🔍 Starting sponsored content detection...")
        val sponsoredNodes = mutableListOf<AccessibilityNodeInfo>()
        findSponsoredContent(rootNode, sponsoredNodes)
        
        if (sponsoredNodes.isNotEmpty()) {
            // Create a unique identifier for this sponsored content
            val contentIdentifier = createContentIdentifier(sponsoredNodes)
            
            if (seenSponsoredContent.contains(contentIdentifier)) {
                Log.d(TAG, "🔄 Already seen this sponsored content, skipping notification")
                return
            }
            
            // Add to seen list
            seenSponsoredContent.add(contentIdentifier)
            
            // Clean up old entries (keep only last 20 to prevent memory issues)
            if (seenSponsoredContent.size > 20) {
                val iterator = seenSponsoredContent.iterator()
                iterator.next() // Remove the oldest entry
                iterator.remove()
            }
            
            Log.w(TAG, "🚨 Found NEW sponsored content - SENDING TO OVERLAY SERVICE")
            
            // Determine ad type based on detected content
            val adType = determineAdType(sponsoredNodes)
            
            // Track ad seen - only once per unique sponsored content
            val analytics = AdAnalytics.getInstance(this)
            analytics.recordAdSeen(adType)
            
            // Send sponsored content detection to overlay service
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_SPONSORED_WARNING
            }
            startService(intent)
            Log.d(TAG, "📤 Sent sponsored warning request to OverlayService")
            
            // Auto-swipe if enabled and not recently performed
            if (settingsManager.isAutoSwipeEnabled()) {
                Log.w(TAG, "🚀 AUTO-SWIPE IS ENABLED - Checking debounce...")
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAutoSwipeTime > autoSwipeDebounceMs) {
                    Log.w(TAG, "🚀 DEBOUNCE PASSED - Triggering auto-swipe!")
                    performAutoSwipe()
                    lastAutoSwipeTime = currentTime
                } else {
                    Log.w(TAG, "⏰ Auto-swipe debounced (last swipe ${currentTime - lastAutoSwipeTime}ms ago, need ${autoSwipeDebounceMs}ms)")
                }
            } else {
                Log.d(TAG, "⚙️ Auto-swipe is DISABLED in settings")
            }
        } else {
            Log.v(TAG, "✅ No sponsored content found in current view")
        }
    }
    
    private fun determineAdType(nodes: List<AccessibilityNodeInfo>): AdType {
        for (node in nodes) {
            val text = node.text?.toString()?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val allText = "$text $contentDesc"
            
            when {
                // Enhanced live shopping detection
                allText.contains("live") && (allText.contains("shopping") || allText.contains("selling")) ||
                allText.contains("tap to watch live") ||
                allText.contains("watch live") ||
                allText.contains("join live") -> {
                    return AdType.LIVE_SHOPPING
                }
                // Learn more button detection
                allText.contains("learn more") -> {
                    return AdType.LEARN_MORE
                }
                // Enhanced shop now button detection
                allText.contains("shop now") || 
                allText.contains("buy now") || 
                allText.contains("add to cart") ||
                allText.contains("check it out") ||
                (allText.contains("from $") || allText.contains("starting at $") || allText.contains("only $")) -> {
                    return AdType.SHOP_NOW
                }
                // Product/review-based shopping content
                allText.contains("free shipping") ||
                allText.contains("star reviews") ||
                allText.contains("4+ star") ||
                allText.contains("5 star") -> {
                    return AdType.SHOP_NOW
                }
            }
        }
        // Default to regular sponsored if no specific type detected
        return AdType.REGULAR_SPONSORED
    }
    
    private fun createContentIdentifier(sponsoredNodes: List<AccessibilityNodeInfo>): String {
        // Create a unique identifier based on the sponsored content found
        val textParts = sponsoredNodes.mapNotNull { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (text.isNotEmpty() || desc.isNotEmpty()) {
                "$text|$desc"
            } else null
        }
        
        return textParts.joinToString("##").hashCode().toString()
    }
    
    private fun findSponsoredContent(node: AccessibilityNodeInfo, results: MutableList<AccessibilityNodeInfo>) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""
        
        // Check if this node is actually visible on screen
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        
        // Skip nodes that are not visible (off-screen, cached, or have zero/negative dimensions)
        if (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0) {
            return
        }
        
        // Also skip nodes that are way off screen (TikTok pre-loads content)
        if (rect.top < -500 || rect.bottom > 3000 || rect.left < -200 || rect.right > 1500) {
            Log.v(TAG, "Skipping off-screen node: bounds=$rect, text='$text'")
            return
        }
        
        // Comprehensive detection - look for sponsored content, live shopping, and carousel ads
        val hasSponsoredContent = text.contains("sponsored", ignoreCase = true) ||
                                 contentDesc.contains("sponsored", ignoreCase = true) ||
                                 resourceId.contains("sponsored", ignoreCase = true) ||
                                 text.contains("paid partnership", ignoreCase = true) ||
                                 contentDesc.contains("paid partnership", ignoreCase = true) ||
                                 text.contains("creator earns commission", ignoreCase = true) ||
                                 contentDesc.contains("creator earns commission", ignoreCase = true) ||
        text.contains("find shopping from tiktok", ignoreCase = true) ||
        contentDesc.contains("find shopping from tiktok", ignoreCase = true)
        
        // Enhanced live shopping detection - more aggressive for live streams
        val hasLiveShoppingContent = 
            // Direct live indicators (any form of LIVE)
            (text.contains("live", ignoreCase = true) || text.contains("LIVE", ignoreCase = false) ||
             contentDesc.contains("live", ignoreCase = true) || contentDesc.contains("LIVE", ignoreCase = false)) &&
             (text.contains("shop", ignoreCase = true) ||
              text.contains("gold star seller", ignoreCase = true) ||
              text.contains("silver star seller", ignoreCase = true) ||
              text.contains("authorized seller", ignoreCase = true) ||
              text.contains("official shop", ignoreCase = true) ||
              text.contains("sold", ignoreCase = true) ||
              text.contains("buy", ignoreCase = true) ||
              text.contains("sale", ignoreCase = true) ||
              text.contains("deal", ignoreCase = true) ||
              text.contains("deals", ignoreCase = true) ||
              text.contains("discount", ignoreCase = true) ||
              text.contains("offer", ignoreCase = true) ||
              text.contains("selling", ignoreCase = true) ||
              text.contains("price", ignoreCase = true) ||
              text.contains("order", ignoreCase = true) ||
              text.contains("checkout", ignoreCase = true) ||
              text.contains("join the giveaway happening now", ignoreCase = true) ||
              text.contains("viewers", ignoreCase = true) ||
              text.contains("watching", ignoreCase = true)) ||

            
            // Content description live shopping
            (contentDesc.contains("live", ignoreCase = true) && 
             (contentDesc.contains("shopping", ignoreCase = true) ||
              contentDesc.contains("shop now", ignoreCase = true) ||
              contentDesc.contains("buy now", ignoreCase = true) ||
              contentDesc.contains("selling", ignoreCase = true))) ||
              
            // Live selling indicators without "live" text (for when live badge isn't captured)
            (text.contains("selling now", ignoreCase = true) ||
             text.contains("available now", ignoreCase = true) ||
             text.contains("limited time", ignoreCase = true) ||
             text.contains("while supplies last", ignoreCase = true) ||
             text.contains("get yours", ignoreCase = true) ||
             text.contains("link in bio", ignoreCase = true) ||
             contentDesc.contains("selling now", ignoreCase = true)) ||
             
            // Standalone seller badges that indicate live shopping
            (text.contains("gold star seller", ignoreCase = true) ||
             text.contains("authorized seller", ignoreCase = true) ||
             text.contains("official shop", ignoreCase = true) ||
             contentDesc.contains("gold star seller", ignoreCase = true) ||
             contentDesc.contains("authorized seller", ignoreCase = true) ||
             contentDesc.contains("official shop", ignoreCase = true)) ||
             
            // Price mentions that often indicate live selling
            (text.contains("$", ignoreCase = false) && 
             (text.contains("only", ignoreCase = true) ||
              text.contains("just", ignoreCase = true) ||
              text.contains("today", ignoreCase = true) ||
              text.contains("now", ignoreCase = true))) ||
              
            // Resource ID indicators for live shopping
            (resourceId.contains("live", ignoreCase = true) && 
             (resourceId.contains("shop", ignoreCase = true) ||
              resourceId.contains("product", ignoreCase = true) ||
              resourceId.contains("sell", ignoreCase = true))) ||
              
            // New: "Tap to watch LIVE" patterns and live streaming indicators
            text.contains("tap to watch live", ignoreCase = true) ||
            contentDesc.contains("tap to watch live", ignoreCase = true) ||
            text.contains("watch live", ignoreCase = true) ||
            contentDesc.contains("watch live", ignoreCase = true) ||
            text.contains("join live", ignoreCase = true) ||
            contentDesc.contains("join live", ignoreCase = true) ||
            
            // Shopping-specific keywords that indicate commercial content
            text.contains("free shipping", ignoreCase = true) ||
            contentDesc.contains("free shipping", ignoreCase = true) ||
            text.contains("4+ star", ignoreCase = true) ||
            text.contains("5 star", ignoreCase = true) ||
            text.contains("star reviews", ignoreCase = true) ||
            contentDesc.contains("star reviews", ignoreCase = true) ||
            
            // Pricing patterns that indicate shopping content
            (text.contains("from $", ignoreCase = true) ||
             text.contains("starting at $", ignoreCase = true) ||
             text.contains("only $", ignoreCase = true) ||
             text.contains("just $", ignoreCase = true) ||
             contentDesc.contains("from $", ignoreCase = true) ||
             contentDesc.contains("starting at $", ignoreCase = true)) ||
             
            // Additional shopping indicators
            text.contains("shop now", ignoreCase = true) ||
            text.contains("buy now", ignoreCase = true) ||
            text.contains("add to cart", ignoreCase = true) ||
            text.contains("check it out", ignoreCase = true) ||
            contentDesc.contains("shop now", ignoreCase = true) ||
            contentDesc.contains("buy now", ignoreCase = true) ||
            contentDesc.contains("add to cart", ignoreCase = true)
        
        // Carousel ad detection - look for carousel/multiple product indicators
        val hasCarouselAds = text.contains("carousel", ignoreCase = true) ||
                            contentDesc.contains("carousel", ignoreCase = true) ||
                            text.contains("swipe for more", ignoreCase = true) ||
                            contentDesc.contains("multiple products", ignoreCase = true) ||
                            (text.contains("products", ignoreCase = true) && text.contains("more", ignoreCase = true)) ||
                            resourceId.contains("carousel", ignoreCase = true)
        
        // Debug logging for live shopping detection
        if (text.contains("live", ignoreCase = true) || text.contains("LIVE", ignoreCase = false)) {
            Log.d(TAG, "🔍 LIVE detected in text: '$text', contentDesc: '$contentDesc', bounds=$rect")
            if (text.contains("gold star seller", ignoreCase = true) || 
                text.contains("authorized seller", ignoreCase = true) || 
                text.contains("official shop", ignoreCase = true) ||
                    text.contains("join the giveaway happening now", ignoreCase = true)){
                Log.w(TAG, "🏪 SELLER BADGE found with LIVE: '$text'")
            }
        }
        
        if (text.contains("gold star seller", ignoreCase = true) || 
            text.contains("authorized seller", ignoreCase = true) || 
            text.contains("official shop", ignoreCase = true)) {
            Log.w(TAG, "🏪 STANDALONE SELLER BADGE: '$text', bounds=$rect")
        }
        
        // Check if we found any type of shopping/sponsored content
        val hasAnyShoppingContent = hasSponsoredContent || hasLiveShoppingContent || hasCarouselAds
        
        if (hasAnyShoppingContent) {
            // Additional check: only add if it's likely part of the main video content
            // TikTok main feed videos are typically in the center area of the screen
            val screenCenterY = 1200 // Approximate middle of most phone screens
            val distanceFromCenter = kotlin.math.abs(rect.centerY() - screenCenterY)
            
            if (distanceFromCenter < 800) { // Within reasonable distance of screen center
                results.add(node)
                
                // Log different types of content found
                when {
                    hasSponsoredContent -> Log.w(TAG, "🚨 FOUND SPONSORED CONTENT: text='$text', desc='$contentDesc', bounds=$rect")
                    hasLiveShoppingContent -> Log.w(TAG, "🛒 FOUND LIVE SHOPPING: text='$text', desc='$contentDesc', bounds=$rect")
                    hasCarouselAds -> Log.w(TAG, "🎠 FOUND CAROUSEL AD: text='$text', desc='$contentDesc', bounds=$rect")
                }
            } else {
                Log.v(TAG, "Skipping shopping content too far from main video area: bounds=$rect, text='$text'")
            }
        }
        
        // Recursively check children (but only if current node is reasonably positioned)
        if (rect.top > -200 && rect.bottom < 2500) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                child?.let { findSponsoredContent(it, results) }
            }
        }
    }
    
    private fun performAutoSwipe() {
        Log.w(TAG, "🔄 AUTO-SWIPE TRIGGERED - Performing swipe for sponsored content")
        
        // Check if gestures are supported
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Log.e(TAG, "❌ Auto-swipe requires Android 7.0+ (API 24), current API: ${android.os.Build.VERSION.SDK_INT}")
            return
        }
        
        try {
            // Create a swipe up gesture from bottom to top (moves to next TikTok)
            val swipePath = Path()
            val startX = 500f // Middle of screen horizontally
            val startY = 2000f // Bottom of screen
            val endX = 500f // Same horizontal position
            val endY = 300f // Top of screen
            
            swipePath.moveTo(startX, startY)
            swipePath.lineTo(endX, endY)
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(swipePath, 0, 300) // 300ms duration
            gestureBuilder.addStroke(strokeDescription)
            
            val gesture = gestureBuilder.build()
            
            val result = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.w(TAG, "✅ AUTO-SWIPE COMPLETED SUCCESSFULLY!")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.e(TAG, "❌ AUTO-SWIPE WAS CANCELLED!")
                }
            }, Handler(Looper.getMainLooper()))
            
            if (result) {
                Log.w(TAG, "📱 AUTO-SWIPE GESTURE DISPATCHED SUCCESSFULLY")
            } else {
                Log.e(TAG, "❌ FAILED TO DISPATCH AUTO-SWIPE GESTURE - Check accessibility permissions!")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error performing auto-swipe: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "ShopBlockr Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // End any active TikTok session
        if (isTikTokSessionActive && ::adAnalytics.isInitialized) {
            // Session ended - no explicit tracking needed with UsageStatsManager
            isTikTokSessionActive = false
            Log.d(TAG, "TikTok session ended - service destroyed")
        }
        
        // Stop overlay service
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        
        Log.d(TAG, "ShopBlockr Accessibility Service Destroyed")
    }
}
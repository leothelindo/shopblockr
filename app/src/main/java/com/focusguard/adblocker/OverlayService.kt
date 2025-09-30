package com.focusguard.adblocker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_SHOW_SPONSORED_WARNING = "show_sponsored_warning"
        const val ACTION_BLOCK_SHOPPING_ELEMENTS = "block_shopping_elements"
        const val ACTION_BLOCK_SHOP_TABS = "block_shop_tabs"
        const val ACTION_SHOW_SHOP_COOLDOWN = "show_shop_cooldown"
        const val ACTION_CLEAR_ALL_OVERLAYS = "clear_all_overlays"
        const val ACTION_CLEAR_SHOP_TAB_OVERLAYS = "clear_shop_tab_overlays"
        const val EXTRA_ELEMENT_COUNT = "element_count"
        const val EXTRA_ELEMENT_BOUNDS = "element_bounds"
        const val EXTRA_COOLDOWN_SECONDS = "cooldown_seconds"
    }

    private var windowManager: WindowManager? = null
    private var sponsoredOverlay: View? = null
    private var blockingOverlays = mutableListOf<View>()
    private var shopTabOverlays = mutableListOf<View>()
    private var shopClickTrackingOverlays = mutableListOf<View>()
    private var cooldownOverlay: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var hideOverlayJob: Job? = null
    private var hideCooldownJob: Job? = null
    private var safetyTimeoutJob: Job? = null
    private lateinit var settingsManager: SettingsManager
    
    // Safety timeout to auto-clear overlays if they persist too long
    private val SAFETY_TIMEOUT_MS = 30000L // 30 seconds

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsManager = SettingsManager(this)
        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand: ${intent?.action}")
        
        intent?.let { serviceIntent ->
            when (serviceIntent.action) {
                ACTION_SHOW_SPONSORED_WARNING -> {
                    showSponsoredWarning()
                }
                ACTION_BLOCK_SHOPPING_ELEMENTS -> {
                    val elementCount = serviceIntent.getIntExtra(EXTRA_ELEMENT_COUNT, 0)
                    val elementBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("DEPRECATION")
                        serviceIntent.getSerializableExtra(EXTRA_ELEMENT_BOUNDS, Array<IntArray>::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        serviceIntent.getSerializableExtra(EXTRA_ELEMENT_BOUNDS) as? Array<IntArray>
                    }
                    blockShoppingElements(elementCount, elementBounds)
                }
                ACTION_BLOCK_SHOP_TABS -> {
                    val elementCount = serviceIntent.getIntExtra(EXTRA_ELEMENT_COUNT, 0)
                    val elementBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("DEPRECATION")
                        serviceIntent.getSerializableExtra(EXTRA_ELEMENT_BOUNDS, Array<IntArray>::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        serviceIntent.getSerializableExtra(EXTRA_ELEMENT_BOUNDS) as? Array<IntArray>
                    }
                    blockShopTabs(elementCount, elementBounds)
                }
                ACTION_SHOW_SHOP_COOLDOWN -> {
                    val cooldownSeconds = serviceIntent.getIntExtra(EXTRA_COOLDOWN_SECONDS, 3)
                    showShopCooldown(cooldownSeconds)
                }
                ACTION_CLEAR_ALL_OVERLAYS -> {
                    clearAllOverlays()
                }
                ACTION_CLEAR_SHOP_TAB_OVERLAYS -> {
                    clearShopTabOverlays()
                }
            }
        }
        
        return START_NOT_STICKY
    }

    private fun showSponsoredWarning() {
        if (!settingsManager.isServiceEnabled()) {
            Log.d(TAG, "Service disabled, not showing sponsored warning")
            return
        }
        
        if (!settingsManager.isOverlayWarningsEnabled()) {
            Log.d(TAG, "Overlay warnings disabled, not showing sponsored warning")
            return
        }

        Log.d(TAG, "🚨 SHOW SPONSORED WARNING CALLED")
        
        try {
            // Remove existing sponsored overlay if present
            sponsoredOverlay?.let { overlay ->
                windowManager?.removeView(overlay)
                sponsoredOverlay = null
                Log.d(TAG, "Removed existing sponsored overlay")
            }

            // Create new sponsored content warning
            val inflater = LayoutInflater.from(this)
            val overlayView = inflater.inflate(R.layout.sponsored_warning_overlay, null)

            // Set up the overlay
            val warningText = overlayView.findViewById<TextView>(R.id.warning_text)
            warningText.text = "⚠️ SPONSORED CONTENT DETECTED\nTap to dismiss"

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 200

            // Add click listener to dismiss
            overlayView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "Sponsored warning dismissed by user")
                    hideSponsoredWarning()
                    true
                } else {
                    false
                }
            }

            // Add the overlay
            windowManager?.addView(overlayView, params)
            sponsoredOverlay = overlayView

            Log.w(TAG, "✅ SPONSORED WARNING OVERLAY ADDED TO SCREEN")
            
            // Vibrate to get attention (if enabled)
            if (settingsManager.isVibrationEnabled()) {
                vibrateDevice()
            }

            // Auto-hide after user-configured delay
            hideOverlayJob?.cancel()
            hideOverlayJob = serviceScope.launch {
                delay(settingsManager.getWarningDuration())
                hideSponsoredWarning()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing sponsored warning: ${e.message}", e)
        }
    }

    private fun blockShoppingElements(elementCount: Int, elementBounds: Array<IntArray>?) {
        if (!settingsManager.isServiceEnabled()) {
            return
        }

        Log.d(TAG, "Blocking $elementCount shopping elements")

        // Clear existing blocking overlays
        clearBlockingOverlays()

        elementBounds?.forEach { bounds ->
            try {
                val inflater = LayoutInflater.from(this)
                val blockingView = inflater.inflate(R.layout.blocking_overlay, null)

                val params = WindowManager.LayoutParams(
                    bounds[2] - bounds[0], // width
                    bounds[3] - bounds[1], // height
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )

                params.x = bounds[0]
                params.y = bounds[1]
                params.gravity = Gravity.TOP or Gravity.START

                windowManager?.addView(blockingView, params)
                blockingOverlays.add(blockingView)

                Log.d(TAG, "Added blocking overlay at (${bounds[0]}, ${bounds[1]}) size ${bounds[2] - bounds[0]}x${bounds[3] - bounds[1]}")

            } catch (e: Exception) {
                Log.e(TAG, "Error creating blocking overlay: ${e.message}", e)
            }
        }
    }

    private fun hideSponsoredWarning() {
        sponsoredOverlay?.let { overlay ->
            try {
                windowManager?.removeView(overlay)
                sponsoredOverlay = null
                Log.d(TAG, "Sponsored warning hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding sponsored warning: ${e.message}", e)
            }
        }
    }

    private fun clearBlockingOverlays() {
        blockingOverlays.forEach { overlay ->
            try {
                windowManager?.removeView(overlay)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing blocking overlay: ${e.message}", e)
            }
        }
        blockingOverlays.clear()
        Log.d(TAG, "Cleared all blocking overlays")
    }

    private fun blockShopTabs(elementCount: Int, elementBounds: Array<IntArray>?) {
        if (!settingsManager.isServiceEnabled() || !settingsManager.isShopBlockingEnabled()) {
            return
        }

        Log.d(TAG, "Blocking $elementCount shop tab elements")

        // Clear existing shop tab overlays
        clearShopTabOverlays()
        
        // Start safety timeout to prevent overlay persistence
        startSafetyTimeout()

        elementBounds?.forEach { bounds ->
            try {
                val inflater = LayoutInflater.from(this)
                val blockingView = inflater.inflate(R.layout.blocking_overlay, null)

                // Adjust overlay positioning and size for better Shop tab blocking
                val originalWidth = bounds[2] - bounds[0]
                val originalHeight = bounds[3] - bounds[1]
                
                // Make overlay same width but half the height
                val adjustedHeight = (originalHeight * 0.5).toInt().coerceAtLeast(40) // Min 40dp height
                val yOffset = -10 // Move up by 10dp

                val params = WindowManager.LayoutParams(
                    originalWidth, // Keep original width
                    adjustedHeight, // Half height
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )

                params.x = bounds[0]
                params.y = bounds[1] + yOffset
                params.gravity = Gravity.TOP or Gravity.START

                windowManager?.addView(blockingView, params)
                shopTabOverlays.add(blockingView)

                // Create invisible click tracking overlay over the original shop button area
                createShopClickTrackingOverlay(bounds, originalWidth, originalHeight)

                Log.d(TAG, "Added shop tab blocking overlay at (${bounds[0]}, ${bounds[1] + yOffset}) size ${originalWidth}x${adjustedHeight}")

            } catch (e: Exception) {
                Log.e(TAG, "Error creating shop tab blocking overlay: ${e.message}", e)
            }
        }
    }

    private fun showShopCooldown(cooldownSeconds: Int) {
        if (!settingsManager.isServiceEnabled() || !settingsManager.isShopBlockingEnabled()) {
            return
        }

        Log.d(TAG, "Showing shop cooldown overlay for $cooldownSeconds seconds")
        
        try {
            // Remove existing cooldown overlay if present
            cooldownOverlay?.let { overlay ->
                windowManager?.removeView(overlay)
                cooldownOverlay = null
            }

            // Create new cooldown overlay
            val inflater = LayoutInflater.from(this)
            val overlayView = inflater.inflate(R.layout.sponsored_warning_overlay, null)

            val warningText = overlayView.findViewById<TextView>(R.id.warning_text)
            warningText.text = "🛒 SHOP ACCESS COOLDOWN\n$cooldownSeconds second${if (cooldownSeconds == 1) "" else "s"} remaining"

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 300 // Slightly lower than sponsored warning

            // Add click listener to dismiss
            overlayView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "Shop cooldown dismissed by user")
                    hideCooldownOverlay()
                    true
                } else {
                    false
                }
            }

            // Add the overlay
            windowManager?.addView(overlayView, params)
            cooldownOverlay = overlayView

            Log.d(TAG, "Shop cooldown overlay added to screen")
            
            // Vibrate if enabled
            if (settingsManager.isVibrationEnabled()) {
                vibrateDevice()
            }

            // Auto-hide after delay
            hideCooldownJob?.cancel()
            hideCooldownJob = serviceScope.launch {
                delay(2000) // Show for 2 seconds
                hideCooldownOverlay()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error showing shop cooldown: ${e.message}", e)
        }
    }

    private fun hideCooldownOverlay() {
        cooldownOverlay?.let { overlay ->
            try {
                windowManager?.removeView(overlay)
                cooldownOverlay = null
                Log.d(TAG, "Shop cooldown overlay hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding shop cooldown overlay: ${e.message}", e)
            }
        }
    }

    private fun createShopClickTrackingOverlay(bounds: IntArray, width: Int, height: Int) {
        try {
            // Create invisible view for click tracking
            val trackingView = View(this).apply {
                setBackgroundColor(0x00000000) // Fully transparent
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        // Track shop button click attempt
                        val analytics = AdAnalytics.getInstance(this@OverlayService)
                        analytics.recordShopTabVisit()
                        Log.d(TAG, "🎯 Shop button click detected and tracked")
                    }
                    false // Allow event to pass through
                }
            }

            val params = WindowManager.LayoutParams(
                width,
                height,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            params.x = bounds[0]
            params.y = bounds[1]
            params.gravity = Gravity.TOP or Gravity.START

            windowManager?.addView(trackingView, params)
            shopClickTrackingOverlays.add(trackingView)

            Log.d(TAG, "Added invisible shop click tracking overlay at (${bounds[0]}, ${bounds[1]}) size ${width}x${height}")

        } catch (e: Exception) {
            Log.e(TAG, "Error creating shop click tracking overlay: ${e.message}", e)
        }
    }

    private fun clearShopTabOverlays() {
        shopTabOverlays.forEach { overlay ->
            try {
                windowManager?.removeView(overlay)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing shop tab overlay: ${e.message}", e)
            }
        }
        shopTabOverlays.clear()
        
        // Also clear click tracking overlays
        shopClickTrackingOverlays.forEach { overlay ->
            try {
                windowManager?.removeView(overlay)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing shop click tracking overlay: ${e.message}", e)
            }
        }
        shopClickTrackingOverlays.clear()
        
        Log.d(TAG, "Cleared all shop tab overlays and click tracking overlays")
    }

    private fun clearAllOverlays() {
        Log.d(TAG, "Clearing all overlays")
        hideSponsoredWarning()
        clearBlockingOverlays()
        clearShopTabOverlays()
        hideCooldownOverlay()
        hideOverlayJob?.cancel()
        hideCooldownJob?.cancel()
        stopSafetyTimeout()
    }
    
    private fun startSafetyTimeout() {
        stopSafetyTimeout()
        safetyTimeoutJob = serviceScope.launch {
            Log.d(TAG, "Safety timeout started - will auto-clear overlays in ${SAFETY_TIMEOUT_MS}ms")
            delay(SAFETY_TIMEOUT_MS)
            Log.w(TAG, "SAFETY TIMEOUT - Force clearing all overlays after ${SAFETY_TIMEOUT_MS}ms")
            clearAllOverlays()
        }
    }
    
    private fun stopSafetyTimeout() {
        safetyTimeoutJob?.cancel()
        safetyTimeoutJob = null
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
            Log.d(TAG, "Device vibrated for sponsored content warning")
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating device: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService destroyed")
        clearAllOverlays()
        hideOverlayJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
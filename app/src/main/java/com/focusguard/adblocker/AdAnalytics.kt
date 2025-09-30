package com.focusguard.adblocker

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

enum class AdType {
    REGULAR_SPONSORED,
    LIVE_SHOPPING,
    LEARN_MORE,
    SHOP_NOW
}

class AdAnalytics private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("ad_analytics", Context.MODE_PRIVATE)
    private val usageStatsManager: UsageStatsManager? = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    
    companion object {
        private const val TAG = "AdAnalytics"
        
        // Core analytics constants - only tracking what we can actually measure
        private const val KEY_TOTAL_ADS = "total_ads_seen"
        private const val KEY_DAILY_ADS = "daily_ads_"
        private const val KEY_WEEKLY_ADS = "weekly_ads_"
        private const val KEY_LIVE_SHOPPING_ADS = "live_shopping_ads"
        private const val KEY_REGULAR_SPONSORED_ADS = "regular_sponsored_ads"
        private const val KEY_LEARN_MORE_ADS = "learn_more_ads"
        private const val KEY_SHOP_NOW_ADS = "shop_now_ads"
        private const val KEY_SHOP_CLICKS_BLOCKED = "shop_clicks_blocked"
        private const val KEY_OVERLAYS_SHOWN = "overlays_shown"
        private const val KEY_SHOP_TAB_VISITS = "shop_tab_visits"
        
        // TikTok package name for screen time tracking
        private const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
        
        @Volatile
        private var INSTANCE: AdAnalytics? = null
        
        fun getInstance(context: Context): AdAnalytics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdAnalytics(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    data class AnalyticsData(
        val totalAdsSeen: Int,
        val dailyAdsSeen: Int,
        val weeklyAdsSeen: Int,
        val liveShoppingAds: Int,
        val regularSponsoredAds: Int,
        val learnMoreAds: Int,
        val shopNowAds: Int,
        val shopClicksBlocked: Int,
        val overlaysShown: Int,
        val shopTabVisits: Int,
        val dailyScreenTimeMinutes: Long,
        val weeklyScreenTimeMinutes: Long,
        val blockingEffectiveness: Double
    )
    
    fun recordAdSeen(adType: AdType) {
        val today = getTodayString()
        val thisWeek = getThisWeekString()
        
        // Update totals
        prefs.edit().apply {
            putInt(KEY_TOTAL_ADS, prefs.getInt(KEY_TOTAL_ADS, 0) + 1)
            putInt("$KEY_DAILY_ADS$today", prefs.getInt("$KEY_DAILY_ADS$today", 0) + 1)
            putInt("$KEY_WEEKLY_ADS$thisWeek", prefs.getInt("$KEY_WEEKLY_ADS$thisWeek", 0) + 1)
            
            // Update ad type counters
            when (adType) {
                AdType.LIVE_SHOPPING -> putInt(KEY_LIVE_SHOPPING_ADS, prefs.getInt(KEY_LIVE_SHOPPING_ADS, 0) + 1)
                AdType.REGULAR_SPONSORED -> putInt(KEY_REGULAR_SPONSORED_ADS, prefs.getInt(KEY_REGULAR_SPONSORED_ADS, 0) + 1)
                AdType.LEARN_MORE -> putInt(KEY_LEARN_MORE_ADS, prefs.getInt(KEY_LEARN_MORE_ADS, 0) + 1)
                AdType.SHOP_NOW -> putInt(KEY_SHOP_NOW_ADS, prefs.getInt(KEY_SHOP_NOW_ADS, 0) + 1)
            }
            
            apply()
        }
        
        Log.d(TAG, "Recorded ad seen: $adType")
    }
    
    fun recordShopClickBlocked() {
        prefs.edit().putInt(KEY_SHOP_CLICKS_BLOCKED, prefs.getInt(KEY_SHOP_CLICKS_BLOCKED, 0) + 1).apply()
        Log.d(TAG, "Recorded shop click blocked")
    }
    
    fun recordOverlayShown() {
        prefs.edit().putInt(KEY_OVERLAYS_SHOWN, prefs.getInt(KEY_OVERLAYS_SHOWN, 0) + 1).apply()
        Log.d(TAG, "Recorded overlay shown")
    }
    
    fun recordShopTabVisit() {
        prefs.edit().putInt(KEY_SHOP_TAB_VISITS, prefs.getInt(KEY_SHOP_TAB_VISITS, 0) + 1).apply()
        Log.d(TAG, "Recorded shop tab visit")
    }
    
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        val hasPermission = mode == AppOpsManager.MODE_ALLOWED
        Log.d(TAG, "Usage stats permission check: $hasPermission")
        return hasPermission
    }
    
    fun requestUsageStatsPermission(): Intent {
        Log.d(TAG, "Creating intent to request usage stats permission")
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }
    
    fun getTikTokScreenTime(days: Int): Long {
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return 0L
        }
        
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startTime = calendar.timeInMillis
        
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        var totalTime = 0L
        usageStatsList.filter { it.packageName == TIKTOK_PACKAGE }
            .forEach { stats ->
                totalTime += stats.totalTimeInForeground
                Log.d(TAG, "TikTok usage for ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(stats.lastTimeUsed))}: ${stats.totalTimeInForeground / (1000 * 60)} minutes")
            }
        
        return totalTime / (1000 * 60) // Convert to minutes
    }
    
    fun getTikTokUsageToday(): Long {
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return 0L
        }
        
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Usage stats permission not granted")
            return 0L
        }
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        Log.d(TAG, "Querying usage stats from $startOfDay to $endTime")
        
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            endTime
        )
        
        Log.d(TAG, "Found ${usageStatsList.size} apps with usage data")
        
        // Debug: Log all apps to see what's available
        usageStatsList.forEach { stats ->
            if (stats.totalTimeInForeground > 0) {
                Log.d(TAG, "App: ${stats.packageName}, Time: ${stats.totalTimeInForeground / (1000 * 60)} minutes, LastUsed: ${Date(stats.lastTimeUsed)}")
            }
        }
        
        // Additional debug: Try different TikTok package names
        val possibleTikTokPackages = listOf(
            "com.zhiliaoapp.musically", // Main TikTok package
            "com.ss.android.ugc.trill", // Alternative package name  
            "musical.ly", // Old Musical.ly package
            "com.zhiliaoapp.musically.go" // TikTok Lite
        )
        
        Log.d(TAG, "Searching for TikTok with packages: $possibleTikTokPackages")
        
        var foundTikTok: UsageStats? = null
        for (packageName in possibleTikTokPackages) {
            val stats = usageStatsList.find { it.packageName == packageName }
            if (stats != null && stats.totalTimeInForeground > 0) {
                Log.d(TAG, "Found TikTok with package: $packageName")
                foundTikTok = stats
                break
            }
        }
        
        // Use the found TikTok stats or fall back to original search
        val tiktokStats = foundTikTok ?: usageStatsList.find { it.packageName == TIKTOK_PACKAGE }
        val usageMinutes = tiktokStats?.totalTimeInForeground?.div(1000 * 60) ?: 0L
        
        if (tiktokStats == null) {
            Log.w(TAG, "No TikTok usage data found for any known package names")
        } else {
            Log.d(TAG, "TikTok usage today: $usageMinutes minutes (raw: ${tiktokStats.totalTimeInForeground}ms) from package: ${tiktokStats.packageName}")
        }
        
        return usageMinutes
    }
    
    fun getTikTokUsageThisWeek(): Long {
        if (usageStatsManager == null) {
            Log.w(TAG, "UsageStatsManager not available")
            return 0L
        }
        
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "Usage stats permission not granted")
            return 0L
        }
        
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY,
            startOfWeek,
            endTime
        )
        
        var totalTime = 0L
        usageStatsList.filter { it.packageName == TIKTOK_PACKAGE }
            .forEach { totalTime += it.totalTimeInForeground }
        
        val usageMinutes = totalTime / (1000 * 60)
        Log.d(TAG, "TikTok usage this week: $usageMinutes minutes")
        return usageMinutes
    }
    
    fun getAllAnalyticsData(): AnalyticsData {
        val today = getTodayString()
        val thisWeek = getThisWeekString()
        
        val totalAds = prefs.getInt(KEY_TOTAL_ADS, 0)
        val dailyAds = prefs.getInt("$KEY_DAILY_ADS$today", 0)
        val weeklyAds = prefs.getInt("$KEY_WEEKLY_ADS$thisWeek", 0)
        val shopClicksBlocked = prefs.getInt(KEY_SHOP_CLICKS_BLOCKED, 0)
        val overlaysShown = prefs.getInt(KEY_OVERLAYS_SHOWN, 0)
        
        val totalProtectiveActions = shopClicksBlocked + overlaysShown
        val blockingEffectiveness = if (totalAds > 0) {
            (totalProtectiveActions.toDouble() / totalAds) * 100
        } else 0.0
        
        return AnalyticsData(
            totalAdsSeen = totalAds,
            dailyAdsSeen = dailyAds,
            weeklyAdsSeen = weeklyAds,
            liveShoppingAds = prefs.getInt(KEY_LIVE_SHOPPING_ADS, 0),
            regularSponsoredAds = prefs.getInt(KEY_REGULAR_SPONSORED_ADS, 0),
            learnMoreAds = prefs.getInt(KEY_LEARN_MORE_ADS, 0),
            shopNowAds = prefs.getInt(KEY_SHOP_NOW_ADS, 0),
            shopClicksBlocked = shopClicksBlocked,
            overlaysShown = overlaysShown,
            shopTabVisits = prefs.getInt(KEY_SHOP_TAB_VISITS, 0),
            dailyScreenTimeMinutes = getTikTokUsageToday(),
            weeklyScreenTimeMinutes = getTikTokUsageThisWeek(),
            blockingEffectiveness = blockingEffectiveness
        )
    }
    
    fun getFormattedTime(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "0m"
        }
    }
    
    private fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    
    private fun getThisWeekString(): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        return SimpleDateFormat("yyyy-'W'ww", Locale.getDefault()).format(calendar.time)
    }
    
    fun debugInstalledApps() {
        try {
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(0)
            
            Log.d(TAG, "=== INSTALLED APPS DEBUG ===")
            installedApps.forEach { appInfo ->
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                if (appName.lowercase().contains("tiktok") || 
                    appInfo.packageName.lowercase().contains("tiktok") ||
                    appInfo.packageName.lowercase().contains("musically") ||
                    appInfo.packageName.lowercase().contains("trill")) {
                    Log.d(TAG, "Potential TikTok app: $appName (${appInfo.packageName})")
                }
            }
            Log.d(TAG, "=== END INSTALLED APPS DEBUG ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error debugging installed apps: ${e.message}")
        }
    }
    
    fun resetAnalytics() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Analytics data reset")
    }
}
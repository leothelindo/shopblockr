package com.focusguard.adblocker

import android.content.Context
import android.content.SharedPreferences
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
    
    companion object {
        private const val TAG = "AdAnalytics"
        private const val PREFS_NAME = "ad_analytics"
        private const val KEY_TOTAL_ADS_SEEN = "total_ads_seen"
        private const val KEY_LAST_UPDATE_DATE = "last_update_date"
        private const val KEY_DAILY_ADS_SEEN = "daily_ads_seen"
        private const val KEY_WEEKLY_ADS_SEEN = "weekly_ads_seen"
        private const val KEY_WEEK_START_DATE = "week_start_date"
        
        // Time tracking
        private const val KEY_TOTAL_APP_TIME = "total_app_time_minutes"
        private const val KEY_DAILY_APP_TIME = "daily_app_time_minutes"
        private const val KEY_LAST_SESSION_START = "last_session_start"
        private const val KEY_SESSION_COUNT = "session_count"
        
        // Ad type tracking
        private const val KEY_LIVE_SHOPPING_ADS = "live_shopping_ads"
        private const val KEY_REGULAR_SPONSORED_ADS = "regular_sponsored_ads"
        private const val KEY_LEARN_MORE_ADS = "learn_more_ads"
        private const val KEY_SHOP_NOW_ADS = "shop_now_ads"
        
        // Blocking effectiveness
        private const val KEY_SHOP_CLICKS_BLOCKED = "shop_clicks_blocked"
        private const val KEY_SHOP_COOLDOWNS_TRIGGERED = "shop_cooldowns_triggered"
        private const val KEY_OVERLAYS_SHOWN = "overlays_shown"
        
        // Historical data (JSON strings for daily breakdown)
        private const val KEY_DAILY_HISTORY = "daily_history"
        private const val KEY_HOURLY_BREAKDOWN = "hourly_breakdown"
        
        @Volatile
        private var INSTANCE: AdAnalytics? = null
        
        fun getInstance(context: Context): AdAnalytics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdAnalytics(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    data class AnalyticsData(
        val dailyAdsSeen: Int,
        val weeklyAdsSeen: Int,
        val totalAdsSeen: Int,
        
        // Time-based metrics
        val dailyAppTimeMinutes: Int,
        val totalAppTimeMinutes: Int,
        val sessionCount: Int,
        val adsPerMinute: Double,
        val adsPerSession: Double,
        
        // Ad type breakdown
        val liveShoppingAds: Int,
        val regularSponsoredAds: Int,
        val learnMoreAds: Int,
        val shopNowAds: Int,
        
        // Blocking effectiveness
        val shopClicksBlocked: Int,
        val shopCooldownsTriggered: Int,
        val overlaysShown: Int,
        val blockingEffectiveness: Double // percentage of shopping attempts blocked
    )
    
    // Session tracking
    fun startSession() {
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SESSION_START, currentTime).apply()
        
        val sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0)
        prefs.edit().putInt(KEY_SESSION_COUNT, sessionCount + 1).apply()
        
        Log.d(TAG, "Session started - Total sessions: ${sessionCount + 1}")
    }
    
    fun endSession() {
        val sessionStart = prefs.getLong(KEY_LAST_SESSION_START, 0)
        if (sessionStart > 0) {
            val sessionDurationMillis = System.currentTimeMillis() - sessionStart
            val sessionDurationMinutes = (sessionDurationMillis / 60000).toInt()
            
            if (sessionDurationMinutes > 0) {
                updateAppTime(sessionDurationMinutes)
                Log.d(TAG, "Session ended - Duration: $sessionDurationMinutes minutes")
            }
            
            prefs.edit().remove(KEY_LAST_SESSION_START).apply()
        }
    }
    
    private fun updateAppTime(minutes: Int) {
        val today = dateFormat.format(Date())
        val lastUpdateDate = prefs.getString(KEY_LAST_UPDATE_DATE, "")
        
        // Reset daily time if new day
        if (lastUpdateDate != today) {
            prefs.edit().putInt(KEY_DAILY_APP_TIME, 0).apply()
        }
        
        val currentDaily = prefs.getInt(KEY_DAILY_APP_TIME, 0)
        val currentTotal = prefs.getInt(KEY_TOTAL_APP_TIME, 0)
        
        prefs.edit()
            .putInt(KEY_DAILY_APP_TIME, currentDaily + minutes)
            .putInt(KEY_TOTAL_APP_TIME, currentTotal + minutes)
            .apply()
    }
    
    // Ad recording with type classification
    fun recordAdSeen(adType: AdType = AdType.REGULAR_SPONSORED) {
        val today = dateFormat.format(Date())
        val lastUpdateDate = prefs.getString(KEY_LAST_UPDATE_DATE, "")
        
        // Check if we need to reset daily counter
        if (lastUpdateDate != today) {
            Log.d(TAG, "New day detected, resetting daily counter")
            resetDailyCounter()
            prefs.edit().putString(KEY_LAST_UPDATE_DATE, today).apply()
        }
        
        // Check if we need to reset weekly counter
        checkAndResetWeeklyCounter()
        
        // Increment all counters
        val currentDaily = prefs.getInt(KEY_DAILY_ADS_SEEN, 0)
        val currentWeekly = prefs.getInt(KEY_WEEKLY_ADS_SEEN, 0)
        val currentTotal = prefs.getInt(KEY_TOTAL_ADS_SEEN, 0)
        
        // Update ad type counters
        val typeKey = when (adType) {
            AdType.LIVE_SHOPPING -> KEY_LIVE_SHOPPING_ADS
            AdType.LEARN_MORE -> KEY_LEARN_MORE_ADS
            AdType.SHOP_NOW -> KEY_SHOP_NOW_ADS
            AdType.REGULAR_SPONSORED -> KEY_REGULAR_SPONSORED_ADS
        }
        val currentTypeCount = prefs.getInt(typeKey, 0)
        
        prefs.edit()
            .putInt(KEY_DAILY_ADS_SEEN, currentDaily + 1)
            .putInt(KEY_WEEKLY_ADS_SEEN, currentWeekly + 1)
            .putInt(KEY_TOTAL_ADS_SEEN, currentTotal + 1)
            .putInt(typeKey, currentTypeCount + 1)
            .apply()
        
        // Record hourly breakdown
        recordHourlyAd()
        
        Log.d(TAG, "Ad recorded (${adType.name}) - Daily: ${currentDaily + 1}, Weekly: ${currentWeekly + 1}, Total: ${currentTotal + 1}")
    }
    
    // Blocking effectiveness tracking
    fun recordShopClickBlocked() {
        val current = prefs.getInt(KEY_SHOP_CLICKS_BLOCKED, 0)
        prefs.edit().putInt(KEY_SHOP_CLICKS_BLOCKED, current + 1).apply()
        Log.d(TAG, "Shop click blocked - Total: ${current + 1}")
    }
    
    fun recordShopCooldownTriggered() {
        val current = prefs.getInt(KEY_SHOP_COOLDOWNS_TRIGGERED, 0)
        prefs.edit().putInt(KEY_SHOP_COOLDOWNS_TRIGGERED, current + 1).apply()
        Log.d(TAG, "Shop cooldown triggered - Total: ${current + 1}")
    }
    
    fun recordOverlayShown() {
        val current = prefs.getInt(KEY_OVERLAYS_SHOWN, 0)
        prefs.edit().putInt(KEY_OVERLAYS_SHOWN, current + 1).apply()
        Log.d(TAG, "Overlay shown - Total: ${current + 1}")
    }
    
    private fun recordHourlyAd() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hourlyData = getHourlyBreakdown()
        hourlyData[hour] = hourlyData.getOrDefault(hour, 0) + 1
        saveHourlyBreakdown(hourlyData)
    }
    
    private fun getHourlyBreakdown(): MutableMap<Int, Int> {
        // Simple implementation - in production might use JSON
        val breakdown = mutableMapOf<Int, Int>()
        for (hour in 0..23) {
            breakdown[hour] = prefs.getInt("hour_$hour", 0)
        }
        return breakdown
    }
    
    private fun saveHourlyBreakdown(hourlyData: Map<Int, Int>) {
        val editor = prefs.edit()
        hourlyData.forEach { (hour, count) ->
            editor.putInt("hour_$hour", count)
        }
        editor.apply()
    }
    
    fun getAllAnalyticsData(): AnalyticsData {
        val today = dateFormat.format(Date())
        val lastUpdateDate = prefs.getString(KEY_LAST_UPDATE_DATE, "")
        
        // Check if we need to reset daily counter
        if (lastUpdateDate != today) {
            resetDailyCounter()
            prefs.edit().putString(KEY_LAST_UPDATE_DATE, today).apply()
        }
        
        // Check if we need to reset weekly counter
        checkAndResetWeeklyCounter()
        
        // Calculate metrics
        val dailyAds = prefs.getInt(KEY_DAILY_ADS_SEEN, 0)
        val totalAds = prefs.getInt(KEY_TOTAL_ADS_SEEN, 0)
        val dailyTime = prefs.getInt(KEY_DAILY_APP_TIME, 0)
        val totalTime = prefs.getInt(KEY_TOTAL_APP_TIME, 0)
        val sessions = prefs.getInt(KEY_SESSION_COUNT, 0)
        
        val adsPerMinute = if (totalTime > 0) totalAds.toDouble() / totalTime else 0.0
        val adsPerSession = if (sessions > 0) totalAds.toDouble() / sessions else 0.0
        
        // Calculate blocking effectiveness
        val clicksBlocked = prefs.getInt(KEY_SHOP_CLICKS_BLOCKED, 0)
        val cooldownsTriggered = prefs.getInt(KEY_SHOP_COOLDOWNS_TRIGGERED, 0)
        val totalShopAttempts = clicksBlocked + cooldownsTriggered
        val blockingEffectiveness = if (totalShopAttempts > 0) {
            (clicksBlocked + cooldownsTriggered).toDouble() / totalShopAttempts * 100
        } else 0.0
        
        return AnalyticsData(
            dailyAdsSeen = dailyAds,
            weeklyAdsSeen = prefs.getInt(KEY_WEEKLY_ADS_SEEN, 0),
            totalAdsSeen = totalAds,
            
            dailyAppTimeMinutes = dailyTime,
            totalAppTimeMinutes = totalTime,
            sessionCount = sessions,
            adsPerMinute = adsPerMinute,
            adsPerSession = adsPerSession,
            
            liveShoppingAds = prefs.getInt(KEY_LIVE_SHOPPING_ADS, 0),
            regularSponsoredAds = prefs.getInt(KEY_REGULAR_SPONSORED_ADS, 0),
            learnMoreAds = prefs.getInt(KEY_LEARN_MORE_ADS, 0),
            shopNowAds = prefs.getInt(KEY_SHOP_NOW_ADS, 0),
            
            shopClicksBlocked = clicksBlocked,
            shopCooldownsTriggered = cooldownsTriggered,
            overlaysShown = prefs.getInt(KEY_OVERLAYS_SHOWN, 0),
            blockingEffectiveness = blockingEffectiveness
        )
    }
    
    private fun resetDailyCounter() {
        prefs.edit().putInt(KEY_DAILY_ADS_SEEN, 0).apply()
        Log.d(TAG, "Daily counter reset")
    }
    
    private fun checkAndResetWeeklyCounter() {
        val calendar = Calendar.getInstance()
        val currentWeekStart = getWeekStartDate(calendar)
        val storedWeekStart = prefs.getString(KEY_WEEK_START_DATE, "")
        
        if (storedWeekStart != currentWeekStart) {
            Log.d(TAG, "New week detected, resetting weekly counter")
            prefs.edit()
                .putInt(KEY_WEEKLY_ADS_SEEN, 0)
                .putString(KEY_WEEK_START_DATE, currentWeekStart)
                .apply()
        }
    }
    
    private fun getWeekStartDate(calendar: Calendar): String {
        // Set to start of week (Monday)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return dateFormat.format(calendar.time)
    }
    
    fun getHourlyAdBreakdown(): Map<Int, Int> {
        return getHourlyBreakdown().toMap()
    }
    
    fun getPeakAdHours(): List<Int> {
        val hourlyData = getHourlyBreakdown()
        val maxAds = hourlyData.values.maxOrNull() ?: 0
        return hourlyData.filter { it.value == maxAds && maxAds > 0 }.keys.toList()
    }
    
    fun getFormattedAppTime(minutes: Int): String {
        return when {
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
            else -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
        }
    }
    
    fun resetAllData() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All analytics data cleared")
    }
}
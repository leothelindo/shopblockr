package com.focusguard.adblocker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class AdAnalytics private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "AdAnalytics"
        private const val PREFS_NAME = "ad_analytics"
        private const val KEY_TOTAL_ADS_SEEN = "total_ads_seen"
        private const val KEY_LAST_UPDATE_DATE = "last_update_date"
        private const val KEY_DAILY_ADS_SEEN = "daily_ads_seen"
        private const val KEY_WEEKLY_ADS_SEEN = "weekly_ads_seen"
        private const val KEY_WEEK_START_DATE = "week_start_date"
        
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
        val totalAdsSeen: Int
    )
    
    fun recordAdSeen() {
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
        
        prefs.edit()
            .putInt(KEY_DAILY_ADS_SEEN, currentDaily + 1)
            .putInt(KEY_WEEKLY_ADS_SEEN, currentWeekly + 1)
            .putInt(KEY_TOTAL_ADS_SEEN, currentTotal + 1)
            .apply()
        
        Log.d(TAG, "Ad recorded - Daily: ${currentDaily + 1}, Weekly: ${currentWeekly + 1}, Total: ${currentTotal + 1}")
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
        
        return AnalyticsData(
            dailyAdsSeen = prefs.getInt(KEY_DAILY_ADS_SEEN, 0),
            weeklyAdsSeen = prefs.getInt(KEY_WEEKLY_ADS_SEEN, 0),
            totalAdsSeen = prefs.getInt(KEY_TOTAL_ADS_SEEN, 0)
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
    
    fun resetAllData() {
        prefs.edit().clear().apply()
        Log.d(TAG, "All analytics data cleared")
    }
}
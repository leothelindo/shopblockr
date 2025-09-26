package com.focusguard.adblocker

import android.content.Context
import android.content.SharedPreferences

class SavingsTracker private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "savings_tracker"
        
        @Volatile
        private var INSTANCE: SavingsTracker? = null
        
        fun getInstance(context: Context): SavingsTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SavingsTracker(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // This class is kept for compatibility but functionality moved to AdAnalytics
    fun getTotalSavings(): Double {
        return 0.0 // Deprecated - use AdAnalytics instead
    }
    
    fun getDailySavings(): Double {
        return 0.0 // Deprecated - use AdAnalytics instead
    }
    
    fun getWeeklySavings(): Double {
        return 0.0 // Deprecated - use AdAnalytics instead
    }
}
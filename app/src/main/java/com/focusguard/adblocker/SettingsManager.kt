package com.focusguard.adblocker

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "shopblockr_settings"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_OVERLAY_WARNINGS_ENABLED = "overlay_warnings_enabled"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, true)
    }
    
    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }
    
    fun isVibrationEnabled(): Boolean {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
    }
    
    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }
    
    fun isOverlayWarningsEnabled(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_WARNINGS_ENABLED, true)
    }
    
    fun setOverlayWarningsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_WARNINGS_ENABLED, enabled).apply()
    }
}
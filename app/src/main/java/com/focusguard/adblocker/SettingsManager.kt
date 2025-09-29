package com.focusguard.adblocker

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "shopblockr_settings"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_OVERLAY_WARNINGS_ENABLED = "overlay_warnings_enabled"
        private const val KEY_WARNING_DURATION_SECONDS = "warning_duration_seconds"
        private const val KEY_SHOP_BLOCKING_MODE = "shop_blocking_mode"
        private const val KEY_SHOP_COOLDOWN_SECONDS = "shop_cooldown_seconds"
        
        // Warning duration range
        const val MIN_DURATION_SECONDS = 1
        const val MAX_DURATION_SECONDS = 15
        const val DEFAULT_DURATION_SECONDS = 5
        
        // Shop blocking modes
        const val SHOP_BLOCKING_OFF = 0
        const val SHOP_BLOCKING_OVERLAY = 1
        const val SHOP_BLOCKING_COOLDOWN = 2
        
        // Shop cooldown range
        const val MIN_COOLDOWN_SECONDS = 1
        const val MAX_COOLDOWN_SECONDS = 10
        const val DEFAULT_COOLDOWN_SECONDS = 3
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
    
    fun getWarningDurationSeconds(): Int {
        return prefs.getInt(KEY_WARNING_DURATION_SECONDS, DEFAULT_DURATION_SECONDS)
    }
    
    fun setWarningDurationSeconds(seconds: Int) {
        val clampedSeconds = seconds.coerceIn(MIN_DURATION_SECONDS, MAX_DURATION_SECONDS)
        prefs.edit().putInt(KEY_WARNING_DURATION_SECONDS, clampedSeconds).apply()
    }
    
    fun getWarningDuration(): Long {
        return getWarningDurationSeconds() * 1000L // Convert to milliseconds
    }
    
    fun getWarningDurationText(): String {
        val seconds = getWarningDurationSeconds()
        return "$seconds second${if (seconds == 1) "" else "s"}"
    }
    
    // Shop blocking settings
    fun getShopBlockingMode(): Int {
        return prefs.getInt(KEY_SHOP_BLOCKING_MODE, SHOP_BLOCKING_OFF)
    }
    
    fun setShopBlockingMode(mode: Int) {
        val clampedMode = mode.coerceIn(SHOP_BLOCKING_OFF, SHOP_BLOCKING_COOLDOWN)
        prefs.edit().putInt(KEY_SHOP_BLOCKING_MODE, clampedMode).apply()
    }
    
    fun getShopBlockingModeText(): String {
        return when (getShopBlockingMode()) {
            SHOP_BLOCKING_OFF -> "Off"
            SHOP_BLOCKING_OVERLAY -> "Block with overlay"
            SHOP_BLOCKING_COOLDOWN -> "Cooldown delay"
            else -> "Off"
        }
    }
    
    fun getShopCooldownSeconds(): Int {
        return prefs.getInt(KEY_SHOP_COOLDOWN_SECONDS, DEFAULT_COOLDOWN_SECONDS)
    }
    
    fun setShopCooldownSeconds(seconds: Int) {
        val clampedSeconds = seconds.coerceIn(MIN_COOLDOWN_SECONDS, MAX_COOLDOWN_SECONDS)
        prefs.edit().putInt(KEY_SHOP_COOLDOWN_SECONDS, clampedSeconds).apply()
    }
    
    fun getShopCooldownText(): String {
        val seconds = getShopCooldownSeconds()
        return "$seconds second${if (seconds == 1) "" else "s"}"
    }
    
    fun isShopBlockingEnabled(): Boolean {
        return getShopBlockingMode() != SHOP_BLOCKING_OFF
    }
}
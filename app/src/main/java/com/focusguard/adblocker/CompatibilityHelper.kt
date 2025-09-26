package com.focusguard.adblocker

import android.os.Build

object CompatibilityHelper {
    
    fun requiresOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
    
    fun supportsModernVibration(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
    
    fun supportsVibrationManager(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    
    fun supportsApplicationOverlay(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
    
    fun supportsAccessibilityService(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
    }
    
    fun supportsToastOverlay(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
    }
    
    fun getRecommendedOverlayType(): Int {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_TOAST
            }
            else -> {
                @Suppress("DEPRECATION")
                android.view.WindowManager.LayoutParams.TYPE_PHONE
            }
        }
    }
    
    fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} - Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
    
    fun getCompatibilityLevel(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> "Fully Compatible"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> "Excellent Compatibility"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> "Good Compatibility"
            else -> "Not Supported"
        }
    }
}
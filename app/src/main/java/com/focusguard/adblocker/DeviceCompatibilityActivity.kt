package com.focusguard.adblocker

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DeviceCompatibilityActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_compatibility)
        
        setupViews()
        checkCompatibility()
    }
    
    private fun setupViews() {
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }
    }
    
    private fun checkCompatibility() {
        // Device info
        findViewById<TextView>(R.id.device_model).text = CompatibilityHelper.getDeviceInfo()
        findViewById<TextView>(R.id.android_version).text = "Compatibility Level: ${CompatibilityHelper.getCompatibilityLevel()}"
        
        // Compatibility checks
        val compatibilityText = StringBuilder()
        
        // Accessibility service compatibility
        if (CompatibilityHelper.supportsAccessibilityService()) {
            compatibilityText.append("✅ Accessibility services supported\n")
        } else {
            compatibilityText.append("❌ Accessibility services not supported (requires Android 4.1+)\n")
        }
        
        // Overlay support
        when {
            CompatibilityHelper.supportsApplicationOverlay() -> {
                compatibilityText.append("✅ Modern overlay system supported\n")
            }
            CompatibilityHelper.supportsToastOverlay() -> {
                compatibilityText.append("⚠️ Basic overlay support (using toast overlay)\n")
            }
            else -> {
                compatibilityText.append("⚠️ Limited overlay support (using phone overlay)\n")
            }
        }
        
        // Overlay permission requirement
        if (CompatibilityHelper.requiresOverlayPermission()) {
            compatibilityText.append("ℹ️ Overlay permission required (Android 6.0+)\n")
        } else {
            compatibilityText.append("✅ No overlay permission required\n")
        }
        
        // Vibration support
        when {
            CompatibilityHelper.supportsVibrationManager() -> {
                compatibilityText.append("✅ Advanced vibration effects supported\n")
            }
            CompatibilityHelper.supportsModernVibration() -> {
                compatibilityText.append("✅ Modern vibration effects supported\n")
            }
            else -> {
                compatibilityText.append("ℹ️ Basic vibration support only\n")
            }
        }
        
        // TikTok compatibility
        compatibilityText.append("✅ TikTok content detection supported\n")
        
        // Overall compatibility with detailed explanation
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                compatibilityText.append("\n🟢 Fully Compatible!\nAll features work optimally on your device.")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                compatibilityText.append("\n🟢 Excellent Compatibility!\nAll features work great with modern permissions system.")
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                compatibilityText.append("\n🟡 Good Compatibility\nCore features work well. Some newer features may be limited.")
            }
            else -> {
                compatibilityText.append("\n❌ Not Supported\nThis app requires Android 5.0 (Lollipop) or higher.")
            }
        }
        
        findViewById<TextView>(R.id.compatibility_details).text = compatibilityText.toString()
    }
}
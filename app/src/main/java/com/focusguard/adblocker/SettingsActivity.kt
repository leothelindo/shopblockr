package com.focusguard.adblocker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var adAnalytics: AdAnalytics
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        settingsManager = SettingsManager(this)
        adAnalytics = AdAnalytics.getInstance(this)
        
        setupViews()
        updateUI()
    }
    
    private fun setupViews() {
        // Service toggle
        val serviceToggle = findViewById<Switch>(R.id.service_enabled_toggle)
        serviceToggle.isChecked = settingsManager.isServiceEnabled()
        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setServiceEnabled(isChecked)
            updateUI()
            if (isChecked) {
                Toast.makeText(this, "ShopBlockr enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ShopBlockr disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Accessibility settings button
        findViewById<Button>(R.id.accessibility_settings_button).setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'ShopBlockr' and enable it", Toast.LENGTH_LONG).show()
        }
        
        // Overlay permission button
        findViewById<Button>(R.id.overlay_permission_button).setOnClickListener {
            requestOverlayPermission()
        }
        
        // Warning settings toggles
        val overlayWarningsToggle = findViewById<Switch>(R.id.overlay_warnings_toggle)
        overlayWarningsToggle.isChecked = settingsManager.isOverlayWarningsEnabled()
        overlayWarningsToggle.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setOverlayWarningsEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Warning overlays enabled" else "Warning overlays disabled", Toast.LENGTH_SHORT).show()
        }
        
        val vibrationToggle = findViewById<Switch>(R.id.vibration_toggle)
        vibrationToggle.isChecked = settingsManager.isVibrationEnabled()
        vibrationToggle.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setVibrationEnabled(isChecked)
            Toast.makeText(this, if (isChecked) "Vibration enabled" else "Vibration disabled", Toast.LENGTH_SHORT).show()
        }
        
        // Warning duration slider
        val durationSlider = findViewById<SeekBar>(R.id.warning_duration_slider)
        durationSlider.progress = settingsManager.getWarningDurationSeconds() - 1 // Slider is 0-based
        durationSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val seconds = progress + 1 // Convert from 0-based to 1-based
                    settingsManager.setWarningDurationSeconds(seconds)
                    updateUI()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val seconds = (seekBar?.progress ?: 0) + 1
                Toast.makeText(this@SettingsActivity, "Warning duration set to $seconds second${if (seconds == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
            }
        })
        
        // Shop blocking mode button
        findViewById<Button>(R.id.shop_blocking_mode_button).setOnClickListener {
            showShopBlockingModeDialog()
        }
        
        // Shop cooldown slider
        val cooldownSlider = findViewById<SeekBar>(R.id.shop_cooldown_slider)
        cooldownSlider.progress = settingsManager.getShopCooldownSeconds() - 1 // Slider is 0-based
        cooldownSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val seconds = progress + 1 // Convert from 0-based to 1-based
                    settingsManager.setShopCooldownSeconds(seconds)
                    updateUI()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val seconds = (seekBar?.progress ?: 0) + 1
                Toast.makeText(this@SettingsActivity, "Shop cooldown set to $seconds second${if (seconds == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
            }
        })
        
        // Clear data button
        findViewById<Button>(R.id.clear_data_button).setOnClickListener {
            adAnalytics.resetAllData()
            Toast.makeText(this, "All analytics data cleared", Toast.LENGTH_SHORT).show()
            updateUI()
        }
        
        // Back button
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }
    }
    
    private fun updateUI() {
        val isAccessibilityServiceEnabled = isAccessibilityServiceEnabled()
        val hasOverlayPermission = hasOverlayPermission()
        val isServiceEnabled = settingsManager.isServiceEnabled()
        
        // Update status text
        val statusText = findViewById<TextView>(R.id.settings_status)
        when {
            !isServiceEnabled -> {
                statusText.text = "🔴 ShopBlockr is disabled"
                statusText.setTextColor(getColor(R.color.error))
            }
            isAccessibilityServiceEnabled && hasOverlayPermission -> {
                statusText.text = "🟢 ShopBlockr is fully active"
                statusText.setTextColor(getColor(R.color.success))
            }
            !hasOverlayPermission -> {
                statusText.text = "🟡 Overlay permission needed"
                statusText.setTextColor(getColor(R.color.warning))
            }
            !isAccessibilityServiceEnabled -> {
                statusText.text = "🟡 Accessibility service needed"
                statusText.setTextColor(getColor(R.color.warning))
            }
            else -> {
                statusText.text = "🟡 Setup incomplete"
                statusText.setTextColor(getColor(R.color.warning))
            }
        }
        
        // Update permission status
        findViewById<TextView>(R.id.accessibility_status).text = 
            if (isAccessibilityServiceEnabled) "✅ Enabled" else "❌ Disabled"
        
        findViewById<TextView>(R.id.overlay_status).text = 
            if (hasOverlayPermission) "✅ Granted" else "❌ Not granted"
        
        // Update analytics summary
        val analyticsData = adAnalytics.getAllAnalyticsData()
        findViewById<TextView>(R.id.analytics_summary).text = 
            "Total ads detected: ${analyticsData.totalAdsSeen}"
        
        // Update warning duration text
        findViewById<TextView>(R.id.warning_duration_text).text = settingsManager.getWarningDurationText()
        
        // Update shop blocking settings
        findViewById<TextView>(R.id.shop_blocking_mode_text).text = settingsManager.getShopBlockingModeText()
        findViewById<TextView>(R.id.shop_cooldown_text).text = settingsManager.getShopCooldownText()
        
        // Show/hide cooldown slider based on mode
        val cooldownContainer = findViewById<LinearLayout>(R.id.shop_cooldown_container)
        cooldownContainer.visibility = if (settingsManager.getShopBlockingMode() == SettingsManager.SHOP_BLOCKING_COOLDOWN) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
    
    private fun showShopBlockingModeDialog() {
        val options = arrayOf("Off", "Block with overlay", "Cooldown delay")
        val currentMode = settingsManager.getShopBlockingMode()
        
        AlertDialog.Builder(this)
            .setTitle("Select Shop Blocking Mode")
            .setSingleChoiceItems(options, currentMode) { dialog, which ->
                settingsManager.setShopBlockingMode(which)
                updateUI()
                val modeText = when (which) {
                    SettingsManager.SHOP_BLOCKING_OFF -> "Shop blocking disabled"
                    SettingsManager.SHOP_BLOCKING_OVERLAY -> "Shop tabs will be blocked with overlays"
                    SettingsManager.SHOP_BLOCKING_COOLDOWN -> "Shop tabs will have a cooldown delay"
                    else -> "Shop blocking disabled"
                }
                Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    
    private fun requestOverlayPermission() {
        if (!hasOverlayPermission()) {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hasOverlayPermission(): Boolean {
        return if (CompatibilityHelper.requiresOverlayPermission()) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return accessibilityServices?.contains("${packageName}/${ShopBlockrAccessibilityService::class.java.name}") == true
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
package com.focusguard.adblocker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var savingsTracker: SavingsTracker
    private lateinit var settingsManager: SettingsManager
    private lateinit var adAnalytics: AdAnalytics
    private lateinit var statusText: TextView
    private lateinit var dailySavingsAmount: TextView
    private lateinit var weeklySavingsAmount: TextView
    private lateinit var totalSavingsAmount: TextView
    private lateinit var impulsesPreventedCount: TextView
    private lateinit var mainToggleSwitch: Switch
    private lateinit var setupSection: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_modern)
        
        savingsTracker = SavingsTracker.getInstance(this)
        settingsManager = SettingsManager(this)
        adAnalytics = AdAnalytics.getInstance(this)
        
        initializeViews()
        updateUI()
        
        // Start notification service
        startNotificationService()
        
        // Refresh UI every few seconds
        startUIRefreshLoop()
    }
    
    private fun initializeViews() {
        statusText = findViewById(R.id.status_text_modern)
        dailySavingsAmount = findViewById(R.id.daily_savings_amount)
        weeklySavingsAmount = findViewById(R.id.weekly_savings_amount)
        totalSavingsAmount = findViewById(R.id.total_savings_amount)
        impulsesPreventedCount = findViewById(R.id.impulses_prevented_count)
        mainToggleSwitch = findViewById(R.id.main_toggle_switch)
        setupSection = findViewById(R.id.setup_section)
        
        // Main toggle switch - set initial state
        mainToggleSwitch.isChecked = settingsManager.isServiceEnabled()
        mainToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setServiceEnabled(isChecked)
            updateUI()
            if (isChecked) {
                Toast.makeText(this, "✅ ShopBlockr enabled - You're now protected!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⏸️ ShopBlockr disabled - No protection active", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Analytics button
        findViewById<Button>(R.id.analytics_button).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        
        // Settings button
        findViewById<Button>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // Compatibility button
        findViewById<Button>(R.id.compatibility_button_modern).setOnClickListener {
            startActivity(Intent(this, DeviceCompatibilityActivity::class.java))
        }
        
        // Add a temporary clear overlays button (for debugging stuck overlays)
        findViewById<Button>(R.id.compatibility_button_modern).setOnLongClickListener {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_CLEAR_ALL_OVERLAYS
            }
            startService(intent)
            Toast.makeText(this, "Cleared all overlays", Toast.LENGTH_SHORT).show()
            true
        }
        
        // Setup buttons
        findViewById<Button>(R.id.setup_accessibility_button).setOnClickListener {
            openAccessibilitySettings()
        }
        
        findViewById<Button>(R.id.setup_overlay_button).setOnClickListener {
            requestOverlayPermission()
        }
    }
    
    private fun updateUI() {
        val analyticsData = adAnalytics.getAllAnalyticsData()
        val isAccessibilityServiceEnabled = isAccessibilityServiceEnabled()
        val hasOverlayPermission = hasOverlayPermission()
        val isAppEnabled = settingsManager.isServiceEnabled()
        
        // Update toggle switch state without triggering listener
        mainToggleSwitch.setOnCheckedChangeListener(null) // Temporarily remove listener
        mainToggleSwitch.isChecked = isAppEnabled
        
        // Re-add the listener
        mainToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setServiceEnabled(isChecked)
            if (isChecked) {
                Toast.makeText(this, "✅ ShopBlockr enabled - You're now protected!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⏸️ ShopBlockr disabled - No protection active", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Status with color coding
        when {
            !isAppEnabled -> {
                statusText.text = "🔴 ShopBlockr is Disabled"
                statusText.setTextColor(getColor(R.color.error))
            }
            isAccessibilityServiceEnabled && hasOverlayPermission -> {
                statusText.text = "🟢 ShopBlockr is Active"
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
                statusText.text = "🟡 Setup required"
                statusText.setTextColor(getColor(R.color.warning))
            }
        }
        
        // Real analytics data - now focused on ads seen
        dailySavingsAmount.text = "${analyticsData.dailyAdsSeen}"
        weeklySavingsAmount.text = "${analyticsData.weeklyAdsSeen}"
        totalSavingsAmount.text = "${analyticsData.totalAdsSeen}"
        
        // Show ads seen summary
        impulsesPreventedCount.text = "${analyticsData.totalAdsSeen} total shopping content detected"
        
        // Show/hide setup section
        val needsSetup = !isAccessibilityServiceEnabled || !hasOverlayPermission
        setupSection.visibility = if (needsSetup) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        
        // Update analytics info (for debugging)
        if (analyticsData.totalAdsSeen > 0) {
            android.util.Log.d("ShopBlockr", "Total ads seen: ${analyticsData.totalAdsSeen}, Daily: ${analyticsData.dailyAdsSeen}, Weekly: ${analyticsData.weeklyAdsSeen}")
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Find 'ShopBlockr' and enable it", Toast.LENGTH_LONG).show()
    }
    
    private fun requestOverlayPermission() {
        if (!hasOverlayPermission()) {
            val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            } else {
                // For older versions, just show app settings
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            startActivity(intent)
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
    
    private fun startNotificationService() {
        val intent = Intent(this, NotificationService::class.java)
        startService(intent)
    }
    
    private fun startUIRefreshLoop() {
        lifecycleScope.launch {
            while (true) {
                delay(3000) // Update every 3 seconds
                updateUI()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
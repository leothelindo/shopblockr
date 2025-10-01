package com.focusguard.adblocker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat

class AnalyticsActivity : AppCompatActivity() {
    
    private lateinit var adAnalytics: AdAnalytics
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        
        adAnalytics = AdAnalytics.getInstance(this)
        settingsManager = SettingsManager(this)
        
        // Debug TikTok package names
        adAnalytics.debugInstalledApps()
        
        setupViews()
        updateAnalytics()
    }
    
    private fun setupViews() {
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }
        
        findViewById<Button>(R.id.refresh_button).setOnClickListener {
            updateAnalytics()
            Toast.makeText(this, "Analytics refreshed", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.export_data_button).setOnClickListener {
            exportAnalyticsData()
        }
        
        findViewById<Button>(R.id.grant_usage_access_button).setOnClickListener {
            requestUsageStatsPermission()
        }
    }
    
    private fun updateAnalytics() {
        // Check if usage permission is granted
        if (!adAnalytics.hasUsageStatsPermission()) {
            // Only show dialog if user hasn't been asked before
            if (!settingsManager.hasBeenAskedForUsageAccess()) {
                showUsagePermissionDialog()
                return
            } else {
                // User has been asked before but doesn't have permission - show limited view
                showLimitedAnalytics()
                return
            }
        }
        
        val analyticsData = adAnalytics.getAllAnalyticsData()
        val decimalFormat = DecimalFormat("#.##")
        
        // Hide the grant usage access button since permission is granted
        findViewById<Button>(R.id.grant_usage_access_button).visibility = android.view.View.GONE
        
        // Daily/Weekly screen time
        findViewById<TextView>(R.id.daily_screen_time).text = adAnalytics.getFormattedTime(analyticsData.dailyScreenTimeMinutes)
        findViewById<TextView>(R.id.weekly_screen_time).text = adAnalytics.getFormattedTime(analyticsData.weeklyScreenTimeMinutes)
        
        // Quick Stats
        val avgAdsPerHour = if (analyticsData.dailyScreenTimeMinutes > 0) {
            (analyticsData.dailyAdsSeen.toDouble() / (analyticsData.dailyScreenTimeMinutes / 60.0))
        } else 0.0
        findViewById<TextView>(R.id.ads_per_hour).text = decimalFormat.format(avgAdsPerHour)
        
        // Ad counts
        findViewById<TextView>(R.id.today_ads_count).text = "${analyticsData.dailyAdsSeen}"
        findViewById<TextView>(R.id.week_ads_count).text = "${analyticsData.weeklyAdsSeen}"
        findViewById<TextView>(R.id.total_ads_count).text = "${analyticsData.totalAdsSeen}"
        
        // Ad Type Breakdown
        findViewById<TextView>(R.id.live_shopping_count).text = "${analyticsData.liveShoppingAds}"
        findViewById<TextView>(R.id.regular_sponsored_count).text = "${analyticsData.regularSponsoredAds}"
        findViewById<TextView>(R.id.learn_more_count).text = "${analyticsData.learnMoreAds}"
        findViewById<TextView>(R.id.shop_now_count).text = "${analyticsData.shopNowAds}"
        
        // Protection Effectiveness
        findViewById<TextView>(R.id.shop_clicks_blocked).text = "${analyticsData.shopClicksBlocked}"
        findViewById<TextView>(R.id.overlays_shown).text = "${analyticsData.overlaysShown}"
        findViewById<TextView>(R.id.shop_tab_visits).text = "${analyticsData.shopTabVisits}"
        
        // Enhanced Summary
        val totalProtectionActions = analyticsData.shopClicksBlocked + analyticsData.overlaysShown
        val summaryText = if (analyticsData.dailyScreenTimeMinutes > 0) {
            buildString {
                append("During your ${adAnalytics.getFormattedTime(analyticsData.dailyScreenTimeMinutes)} of TikTok usage today, ")
                append("ShopBlockr detected ${analyticsData.dailyAdsSeen} instances of sponsored content. ")
                
                if (totalProtectionActions > 0) {
                    append("Our protection system intervened $totalProtectionActions times, ")
                    append("achieving ${decimalFormat.format(analyticsData.blockingEffectiveness)}% effectiveness in blocking unwanted shopping interactions. ")
                } else {
                    append("No interventions were needed today. ")
                }
                
                if (analyticsData.dailyAdsSeen > 0) {
                    val adsPerHour = if (analyticsData.dailyScreenTimeMinutes > 0) {
                        (analyticsData.dailyAdsSeen.toDouble() / (analyticsData.dailyScreenTimeMinutes / 60.0))
                    } else 0.0
                    append("This represents an exposure rate of ${decimalFormat.format(adsPerHour)} sponsored content instances per hour of usage.")
                }
            }
        } else {
            "ShopBlockr is actively monitoring for sponsored content. Enable usage access permission in Settings to see detailed screen time analysis and get insights into your TikTok browsing patterns."
        }
        
        findViewById<TextView>(R.id.summary_text).text = summaryText
    }
    
    private fun exportAnalyticsData() {
        val analyticsData = adAnalytics.getAllAnalyticsData()
        val exportText = buildString {
            appendLine("SHOPBLOCKR ANALYTICS REPORT")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("=".repeat(50))
            appendLine()
            
            appendLine("USAGE ANALYSIS")
            appendLine("-".repeat(20))
            appendLine("Daily TikTok usage: ${adAnalytics.getFormattedTime(analyticsData.dailyScreenTimeMinutes)}")
            appendLine("Weekly TikTok usage: ${adAnalytics.getFormattedTime(analyticsData.weeklyScreenTimeMinutes)}")
            appendLine()
            
            appendLine("SPONSORED CONTENT DETECTION")
            appendLine("-".repeat(30))
            appendLine("Today: ${analyticsData.dailyAdsSeen} instances")
            appendLine("This week: ${analyticsData.weeklyAdsSeen} instances")
            appendLine("Total detected: ${analyticsData.totalAdsSeen} instances")
            appendLine()
            
            appendLine("CONTENT CLASSIFICATION")
            appendLine("-".repeat(25))
            appendLine("Live shopping content: ${analyticsData.liveShoppingAds}")
            appendLine("Sponsored posts: ${analyticsData.regularSponsoredAds}")
            appendLine("Promotional prompts: ${analyticsData.learnMoreAds}")
            appendLine("Direct shopping calls: ${analyticsData.shopNowAds}")
            appendLine()
            
            appendLine("PROTECTION PERFORMANCE")
            appendLine("-".repeat(25))
            appendLine("Shopping interactions blocked: ${analyticsData.shopClicksBlocked}")
            appendLine("Alert notifications shown: ${analyticsData.overlaysShown}")
            appendLine("Shopping section access attempts: ${analyticsData.shopTabVisits}")
            appendLine("Overall effectiveness: ${String.format("%.1f", analyticsData.blockingEffectiveness)}%")
            appendLine()
            
            val avgAdsPerHour = if (analyticsData.dailyScreenTimeMinutes > 0) {
                (analyticsData.dailyAdsSeen.toDouble() / (analyticsData.dailyScreenTimeMinutes / 60.0))
            } else 0.0
            appendLine("EXPOSURE METRICS")
            appendLine("-".repeat(20))
            appendLine("Average sponsored content per hour: ${String.format("%.1f", avgAdsPerHour)}")
            appendLine("Total protection interventions: ${analyticsData.shopClicksBlocked + analyticsData.overlaysShown}")
            appendLine()
            
            appendLine("-".repeat(50))
            appendLine("Report generated by ShopBlockr")
            appendLine("TikTok Shopping Protection System")
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, exportText)
            putExtra(Intent.EXTRA_SUBJECT, "ShopBlockr Analytics Report")
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Export Analytics Data"))
        } catch (e: Exception) {
            Toast.makeText(this, "No apps available to share analytics", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showUsagePermissionDialog() {
        // Mark that we've asked the user
        settingsManager.setUsageAccessAsked(true)
        
        AlertDialog.Builder(this, R.style.ShopBlockr_Dialog)
            .setTitle("Usage Access Required")
            .setMessage("ShopBlockr needs access to usage statistics to track your TikTok screen time. This helps provide accurate analytics about your app usage.\n\nPlease grant 'Usage Access' permission to ShopBlockr in the next screen.")
            .setPositiveButton("Grant Permission") { _, _ ->
                try {
                    val intent = adAnalytics.requestUsageStatsPermission()
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open settings. Please manually grant usage access permission.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Skip") { dialog, _ ->
                Toast.makeText(this, "You can enable usage access later in Settings if you change your mind.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                showLimitedAnalytics()
            }
            .setCancelable(true)
            .show()
    }
    
    private fun showLimitedAnalytics() {
        val analyticsData = adAnalytics.getAllAnalyticsData()
        val decimalFormat = DecimalFormat("#.##")
        
        // Show the grant usage access button
        findViewById<Button>(R.id.grant_usage_access_button).visibility = android.view.View.VISIBLE
        
        // Show N/A for screen time data
        findViewById<TextView>(R.id.daily_screen_time).text = "N/A - Grant Usage Access"
        findViewById<TextView>(R.id.weekly_screen_time).text = "N/A - Grant Usage Access"
        
        // Show available data (ads seen, etc.)
        findViewById<TextView>(R.id.ads_per_hour).text = "N/A"
        
        // Ad counts (these work without usage permission)
        findViewById<TextView>(R.id.today_ads_count).text = "${analyticsData.dailyAdsSeen}"
        findViewById<TextView>(R.id.week_ads_count).text = "${analyticsData.weeklyAdsSeen}"
        findViewById<TextView>(R.id.total_ads_count).text = "${analyticsData.totalAdsSeen}"
        
        // Ad Type Breakdown
        findViewById<TextView>(R.id.live_shopping_count).text = "${analyticsData.liveShoppingAds}"
        findViewById<TextView>(R.id.regular_sponsored_count).text = "${analyticsData.regularSponsoredAds}"
        findViewById<TextView>(R.id.learn_more_count).text = "${analyticsData.learnMoreAds}"
        findViewById<TextView>(R.id.shop_now_count).text = "${analyticsData.shopNowAds}"
        
        // Protection Effectiveness
        findViewById<TextView>(R.id.shop_clicks_blocked).text = "${analyticsData.shopClicksBlocked}"
        findViewById<TextView>(R.id.overlays_shown).text = "${analyticsData.overlaysShown}"
        findViewById<TextView>(R.id.shop_tab_visits).text = "${analyticsData.shopTabVisits}"
        
        // Enhanced Summary for limited view
        val totalProtectionActions = analyticsData.shopClicksBlocked + analyticsData.overlaysShown
        val summaryText = buildString {
            append("ShopBlockr has identified ${analyticsData.totalAdsSeen} instances of sponsored content since installation. ")
            
            if (totalProtectionActions > 0) {
                append("Our protection systems have successfully intervened $totalProtectionActions times, ")
                append("achieving ${decimalFormat.format(analyticsData.blockingEffectiveness)}% effectiveness in preventing unwanted shopping interactions. ")
            }
            
            if (analyticsData.totalAdsSeen > 0) {
                val breakdown = mutableListOf<String>()
                if (analyticsData.liveShoppingAds > 0) breakdown.add("${analyticsData.liveShoppingAds} live shopping instances")
                if (analyticsData.regularSponsoredAds > 0) breakdown.add("${analyticsData.regularSponsoredAds} sponsored posts")
                if (analyticsData.learnMoreAds > 0) breakdown.add("${analyticsData.learnMoreAds} promotional prompts")
                if (analyticsData.shopNowAds > 0) breakdown.add("${analyticsData.shopNowAds} direct shopping calls")
                
                if (breakdown.isNotEmpty()) {
                    append("This includes ${breakdown.joinToString(", ")}. ")
                }
            }
            
            append("Enable usage access permission in Settings to unlock detailed screen time analytics and gain deeper insights into your TikTok browsing patterns.")
        }
        
        findViewById<TextView>(R.id.summary_text).text = summaryText
    }
    
    private fun requestUsageStatsPermission() {
        try {
            val intent = adAnalytics.requestUsageStatsPermission()
            startActivity(intent)
            Toast.makeText(this, "Please find 'ShopBlockr' and enable usage access", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open settings. Please manually grant usage access permission.", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh analytics when returning from settings
        updateAnalytics()
    }
}
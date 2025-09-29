package com.focusguard.adblocker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat

class AnalyticsActivity : AppCompatActivity() {
    
    private lateinit var adAnalytics: AdAnalytics
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)
        
        adAnalytics = AdAnalytics.getInstance(this)
        
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
    }
    
    private fun updateAnalytics() {
        val analyticsData = adAnalytics.getAllAnalyticsData()
        val decimalFormat = DecimalFormat("#.##")
        
        // Quick Stats
        findViewById<TextView>(R.id.ads_per_minute).text = decimalFormat.format(analyticsData.adsPerMinute)
        findViewById<TextView>(R.id.ads_per_session).text = decimalFormat.format(analyticsData.adsPerSession)
        findViewById<TextView>(R.id.blocking_effectiveness).text = "${decimalFormat.format(analyticsData.blockingEffectiveness)}%"
        
        // Update today's stats
        findViewById<TextView>(R.id.today_ads_count).text = "${analyticsData.dailyAdsSeen}"
        
        // Update this week's stats
        findViewById<TextView>(R.id.week_ads_count).text = "${analyticsData.weeklyAdsSeen}"
        
        // Update all time stats
        findViewById<TextView>(R.id.total_ads_count).text = "${analyticsData.totalAdsSeen}"
        
        // Time Analysis
        findViewById<TextView>(R.id.daily_app_time).text = adAnalytics.getFormattedAppTime(analyticsData.dailyAppTimeMinutes)
        findViewById<TextView>(R.id.total_sessions).text = "${analyticsData.sessionCount}"
        
        val peakHours = adAnalytics.getPeakAdHours()
        findViewById<TextView>(R.id.peak_ad_hours).text = if (peakHours.isNotEmpty()) {
            peakHours.joinToString(", ") { "${it}:00" }
        } else {
            "No data"
        }
        
        // Ad Type Breakdown
        findViewById<TextView>(R.id.live_shopping_count).text = "${analyticsData.liveShoppingAds}"
        findViewById<TextView>(R.id.regular_sponsored_count).text = "${analyticsData.regularSponsoredAds}"
        findViewById<TextView>(R.id.learn_more_count).text = "${analyticsData.learnMoreAds}"
        findViewById<TextView>(R.id.shop_now_count).text = "${analyticsData.shopNowAds}"
        
        // Protection Effectiveness
        findViewById<TextView>(R.id.shop_clicks_blocked).text = "${analyticsData.shopClicksBlocked}"
        findViewById<TextView>(R.id.cooldowns_triggered).text = "${analyticsData.shopCooldownsTriggered}"
        findViewById<TextView>(R.id.overlays_shown).text = "${analyticsData.overlaysShown}"
        
        // Summary text
        val totalProtectionActions = analyticsData.shopClicksBlocked + analyticsData.shopCooldownsTriggered + analyticsData.overlaysShown
        findViewById<TextView>(R.id.summary_text).text = 
            "ShopBlockr has detected ${analyticsData.totalAdsSeen} shopping content items and taken " +
            "$totalProtectionActions protective actions, helping you stay focused on content you actually want to see."
    }
    
    private fun exportAnalyticsData() {
        val analyticsData = adAnalytics.getAllAnalyticsData()
        val exportText = buildString {
            appendLine("📊 ShopBlockr Analytics Export")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine()
            
            appendLine("📈 QUICK STATS")
            appendLine("Ads per minute: ${String.format("%.2f", analyticsData.adsPerMinute)}")
            appendLine("Ads per session: ${String.format("%.2f", analyticsData.adsPerSession)}")
            appendLine("Blocking effectiveness: ${String.format("%.1f", analyticsData.blockingEffectiveness)}%")
            appendLine()
            
            appendLine("📅 TIME BREAKDOWN")
            appendLine("Daily ads: ${analyticsData.dailyAdsSeen}")
            appendLine("Weekly ads: ${analyticsData.weeklyAdsSeen}")
            appendLine("Total ads: ${analyticsData.totalAdsSeen}")
            appendLine("Daily app time: ${adAnalytics.getFormattedAppTime(analyticsData.dailyAppTimeMinutes)}")
            appendLine("Total sessions: ${analyticsData.sessionCount}")
            
            val peakHours = adAnalytics.getPeakAdHours()
            appendLine("Peak ad hours: ${if (peakHours.isNotEmpty()) peakHours.joinToString(", ") { "${it}:00" } else "No data"}")
            appendLine()
            
            appendLine("🛒 AD TYPE BREAKDOWN")
            appendLine("Live shopping: ${analyticsData.liveShoppingAds}")
            appendLine("Regular sponsored: ${analyticsData.regularSponsoredAds}")
            appendLine("Learn more ads: ${analyticsData.learnMoreAds}")
            appendLine("Shop now ads: ${analyticsData.shopNowAds}")
            appendLine()
            
            appendLine("🛡️ PROTECTION EFFECTIVENESS")
            appendLine("Shop clicks blocked: ${analyticsData.shopClicksBlocked}")
            appendLine("Cooldowns triggered: ${analyticsData.shopCooldownsTriggered}")
            appendLine("Warning overlays: ${analyticsData.overlaysShown}")
            appendLine()
            
            appendLine("Generated by ShopBlockr - Your TikTok Shopping Protector")
        }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, exportText)
            putExtra(Intent.EXTRA_SUBJECT, "ShopBlockr Analytics Export")
        }
        
        try {
            startActivity(Intent.createChooser(intent, "Export Analytics Data"))
        } catch (e: Exception) {
            Toast.makeText(this, "No apps available to share analytics", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateAnalytics()
    }
}
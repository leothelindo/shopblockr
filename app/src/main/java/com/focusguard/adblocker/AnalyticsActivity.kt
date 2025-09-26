package com.focusguard.adblocker

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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
        }
    }
    
    private fun updateAnalytics() {
        val analyticsData = adAnalytics.getAllAnalyticsData()
        
        // Update today's stats
        findViewById<TextView>(R.id.today_ads_count).text = "${analyticsData.dailyAdsSeen}"
        
        // Update this week's stats
        findViewById<TextView>(R.id.week_ads_count).text = "${analyticsData.weeklyAdsSeen}"
        
        // Update all time stats
        findViewById<TextView>(R.id.total_ads_count).text = "${analyticsData.totalAdsSeen}"
        
        // Summary text
        findViewById<TextView>(R.id.summary_text).text = 
            "ShopBlockr has detected ${analyticsData.totalAdsSeen} shopping content items, " +
            "helping you stay focused on content you actually want to see."
    }
    
    override fun onResume() {
        super.onResume()
        updateAnalytics()
    }
}
package com.focusguard.adblocker

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class TipsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tips)
        
        setupViews()
    }
    
    private fun setupViews() {
        findViewById<Button>(R.id.back_button).setOnClickListener {
            finish()
        }
    }
}
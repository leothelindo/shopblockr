package com.focusguard.adblocker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_SHOW_SPONSORED_WARNING = "show_sponsored_warning"
        const val ACTION_BLOCK_SHOPPING_ELEMENTS = "block_shopping_elements"
        const val ACTION_CLEAR_ALL_OVERLAYS = "clear_all_overlays"
        const val EXTRA_ELEMENT_COUNT = "element_count"
        const val EXTRA_ELEMENT_BOUNDS = "element_bounds"
    }

    private var windowManager: WindowManager? = null
    private var sponsoredOverlay: View? = null
    private var blockingOverlays = mutableListOf<View>()
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var hideOverlayJob: Job? = null
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsManager = SettingsManager(this)
        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand: ${intent?.action}")
        
        intent?.let { serviceIntent ->
            when (serviceIntent.action) {
                ACTION_SHOW_SPONSORED_WARNING -> {
                    showSponsoredWarning()
                }
                ACTION_BLOCK_SHOPPING_ELEMENTS -> {
                    val elementCount = serviceIntent.getIntExtra(EXTRA_ELEMENT_COUNT, 0)
                    val elementBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        @Suppress("DEPRECATION")
                        serviceIntent.getSerializableExtra(EXTRA_ELEMENT_BOUNDS, Array<IntArray>::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        serviceIntent.getSerializableExtra(EXTRA_ELEMENT_BOUNDS) as? Array<IntArray>
                    }
                    blockShoppingElements(elementCount, elementBounds)
                }
                ACTION_CLEAR_ALL_OVERLAYS -> {
                    clearAllOverlays()
                }
            }
        }
        
        return START_NOT_STICKY
    }

    private fun showSponsoredWarning() {
        if (!settingsManager.isServiceEnabled()) {
            Log.d(TAG, "Service disabled, not showing sponsored warning")
            return
        }

        Log.d(TAG, "🚨 SHOW SPONSORED WARNING CALLED")
        
        try {
            // Remove existing sponsored overlay if present
            sponsoredOverlay?.let { overlay ->
                windowManager?.removeView(overlay)
                sponsoredOverlay = null
                Log.d(TAG, "Removed existing sponsored overlay")
            }

            // Create new sponsored content warning
            val inflater = LayoutInflater.from(this)
            val overlayView = inflater.inflate(R.layout.sponsored_warning_overlay, null)

            // Set up the overlay
            val warningText = overlayView.findViewById<TextView>(R.id.warning_text)
            warningText.text = "⚠️ SPONSORED CONTENT DETECTED\nTap to dismiss"

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 200

            // Add click listener to dismiss
            overlayView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "Sponsored warning dismissed by user")
                    hideSponsoredWarning()
                    true
                } else {
                    false
                }
            }

            // Add the overlay
            windowManager?.addView(overlayView, params)
            sponsoredOverlay = overlayView

            Log.w(TAG, "✅ SPONSORED WARNING OVERLAY ADDED TO SCREEN")
            
            // Vibrate to get attention
            vibrateDevice()

            // Auto-hide after delay
            hideOverlayJob?.cancel()
            hideOverlayJob = serviceScope.launch {
                delay(4000)
                hideSponsoredWarning()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing sponsored warning: ${e.message}", e)
        }
    }

    private fun blockShoppingElements(elementCount: Int, elementBounds: Array<IntArray>?) {
        if (!settingsManager.isServiceEnabled()) {
            return
        }

        Log.d(TAG, "Blocking $elementCount shopping elements")

        // Clear existing blocking overlays
        clearBlockingOverlays()

        elementBounds?.forEach { bounds ->
            try {
                val inflater = LayoutInflater.from(this)
                val blockingView = inflater.inflate(R.layout.blocking_overlay, null)

                val params = WindowManager.LayoutParams(
                    bounds[2] - bounds[0], // width
                    bounds[3] - bounds[1], // height
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )

                params.x = bounds[0]
                params.y = bounds[1]
                params.gravity = Gravity.TOP or Gravity.START

                windowManager?.addView(blockingView, params)
                blockingOverlays.add(blockingView)

                Log.d(TAG, "Added blocking overlay at (${bounds[0]}, ${bounds[1]}) size ${bounds[2] - bounds[0]}x${bounds[3] - bounds[1]}")

            } catch (e: Exception) {
                Log.e(TAG, "Error creating blocking overlay: ${e.message}", e)
            }
        }
    }

    private fun hideSponsoredWarning() {
        sponsoredOverlay?.let { overlay ->
            try {
                windowManager?.removeView(overlay)
                sponsoredOverlay = null
                Log.d(TAG, "Sponsored warning hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error hiding sponsored warning: ${e.message}", e)
            }
        }
    }

    private fun clearBlockingOverlays() {
        blockingOverlays.forEach { overlay ->
            try {
                windowManager?.removeView(overlay)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing blocking overlay: ${e.message}", e)
            }
        }
        blockingOverlays.clear()
        Log.d(TAG, "Cleared all blocking overlays")
    }

    private fun clearAllOverlays() {
        Log.d(TAG, "Clearing all overlays")
        hideSponsoredWarning()
        clearBlockingOverlays()
        hideOverlayJob?.cancel()
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
            Log.d(TAG, "Device vibrated for sponsored content warning")
        } catch (e: Exception) {
            Log.e(TAG, "Error vibrating device: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService destroyed")
        clearAllOverlays()
        hideOverlayJob?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
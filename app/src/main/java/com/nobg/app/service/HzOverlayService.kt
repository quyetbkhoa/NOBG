package com.nobg.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class HzOverlayService : Service(), Choreographer.FrameCallback {

    private var windowManager: WindowManager? = null
    private var tvHz: TextView? = null

    private var frameCount: Int = 0
    private var lastFpsCalcTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun createOverlayView() {
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(Color.parseColor("#10B981")) // Vivid Green Badge
        }

        val tv = TextView(this).apply {
            text = "120 Hz"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = bgDrawable
            setPadding(24, 12, 24, 12)
            elevation = 12f
        }
        tvHz = tv

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 50
        }

        try {
            windowManager?.addView(tvHz, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (lastFpsCalcTime == 0L) {
            lastFpsCalcTime = frameTimeNanos
        }
        frameCount++
        val elapsedNanos = frameTimeNanos - lastFpsCalcTime
        if (elapsedNanos >= 1000_000_000L) { // 1 second
            val currentHz = frameCount
            tvHz?.text = "$currentHz Hz"
            frameCount = 0
            lastFpsCalcTime = frameTimeNanos
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Choreographer.getInstance().removeFrameCallback(this)
        if (tvHz != null) {
            try {
                windowManager?.removeView(tvHz)
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}

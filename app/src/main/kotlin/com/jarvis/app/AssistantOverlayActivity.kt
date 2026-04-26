package com.jarvis.app

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager

/**
 * Assistant entry activity shown as an overlay when Jarvis is launched via
 * the system digital assistant trigger (for example long-press power button).
 */
class AssistantOverlayActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOverlayWindow()
    }

    private fun configureOverlayWindow() {
        val width = (resources.displayMetrics.widthPixels * 0.94f).toInt()
        window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        window?.attributes = window?.attributes?.apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (resources.displayMetrics.density * 24f).toInt()
            dimAmount = 0.36f
        }
    }
}

package com.jarvis.app

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jarvis.app.ui.OverlayScreen
import com.jarvis.app.ui.theme.JarvisTheme
import com.jarvis.core.JarvisState

/**
 * Assistant entry activity shown as an overlay when Jarvis is launched via
 * the system digital assistant trigger.
 */
class AssistantOverlayActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOverlayWindow()

        // Play launch sound when overlay appears
        soundPlayer?.playLaunchSound()

        setContent {
            JarvisTheme(isOverlay = true) {
                // Transparent background for overlay
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    val state = jarvisStateFlow.collectAsState().value
                    val inputText = jarvisInputTextFlow.collectAsState().value
                    
                    OverlayScreen(
                        state = state,
                        inputText = inputText
                    )
                }
            }
        }
    }

    private fun configureOverlayWindow() {
        val width = WindowManager.LayoutParams.MATCH_PARENT
        val height = WindowManager.LayoutParams.WRAP_CONTENT
        window?.setLayout(width, height)
        window?.attributes = window?.attributes?.apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            dimAmount = 0.36f
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}

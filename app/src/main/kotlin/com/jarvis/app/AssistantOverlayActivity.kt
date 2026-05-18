package com.jarvis.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.ui.FolderSetupDialog
import com.jarvis.app.ui.MalformedFilesDialog
import com.jarvis.app.ui.FolderSetupProgressDialog
import com.jarvis.app.ui.FolderSetupResultDialog
import com.jarvis.app.ui.OverlayScreen
import com.jarvis.app.ui.theme.JarvisTheme
import com.jarvis.core.JarvisState
import com.jarvis.app.GeminiCloudLLMProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Assistant entry activity shown as an overlay when Jarvis is launched via
 * the system digital assistant trigger.
 */
class AssistantOverlayActivity : MainActivity() {

    private var hasBeenActive = false

    override fun onJarvisStateChanged(state: JarvisState) {
        // Track if we've moved out of the initial BARN_DOOR state
        if (state != JarvisState.BARN_DOOR) {
            hasBeenActive = true
        }

        // The overlay only exists to serve a session. If the state returns to BARN_DOOR
        // after being active, we dismiss it.
        // We add a check for SPEAKING/THINKING to avoid closing while processing.
        if (hasBeenActive && state == JarvisState.BARN_DOOR && isActivityResumed) {
            // Check if we are actually done or just transitioned briefly
            lifecycleScope.launch {
                delay(800) // Slightly longer delay to be safe
                val currentState = JarvisEngine.stateFlow.value
                if (currentState == JarvisState.BARN_DOOR) {
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Reset voice auto-start flag to allow re-triggering listening mode
        voiceAutoStarted = false
        hasBeenActive = false
        maybeAutoStartVoiceMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOverlayWindow()

        // Play launch sound when overlay appears
        soundPlayer?.playLaunchSound()

        setContent {
            JarvisTheme(isOverlay = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    val state by JarvisEngine.stateFlow.collectAsState()
                    val inputText by inputTextFlow.collectAsState()
                    val folderSetupUiState by folderSetupViewModel.uiState.collectAsState()
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        OverlayScreen(
                            state = state,
                            inputText = inputText,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        // Also include setup dialogs if they trigger while overlay is active
                        FolderSetupDialog(
                            uiState = folderSetupUiState,
                            onFormatFolder = {
                                folderSetupViewModel.setupFolder(JarvisEngine.getNotesBasePath(this@AssistantOverlayActivity)) {
                                    JarvisEngine.folderSetupHandled()
                                    refreshOrchestrator()
                                }
                            },
                            onDismiss = { folderSetupViewModel.dismissDialogs() }
                        )

                        MalformedFilesDialog(
                            uiState = folderSetupUiState,
                            onOrganizeWithAI = {
                                val apiKey = settingsViewModel.uiState.value.geminiApiKey
                                if (apiKey.isNotBlank()) {
                                    val provider = GeminiCloudLLMProvider(apiKey = apiKey, logger = logger)
                                    folderSetupViewModel.classifyAndOrganizeFiles(
                                        JarvisEngine.getNotesBasePath(this@AssistantOverlayActivity),
                                        provider
                                    ) {
                                        JarvisEngine.folderSetupHandled()
                                        refreshOrchestrator()
                                    }
                                } else {
                                    folderSetupViewModel.skipOrganizingFiles()
                                }
                            },
                            onSkip = { folderSetupViewModel.skipOrganizingFiles() }
                        )

                        FolderSetupProgressDialog(
                            uiState = folderSetupUiState,
                            onDismiss = { }
                        )

                        FolderSetupResultDialog(
                            uiState = folderSetupUiState,
                            onDismiss = { folderSetupViewModel.clearMessages() }
                        )
                    }
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

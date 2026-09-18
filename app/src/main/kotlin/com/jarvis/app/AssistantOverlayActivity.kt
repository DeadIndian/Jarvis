package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jarvis.app.BuildConfig
import com.jarvis.app.core.audio.LocalAudioFeedback
import com.jarvis.app.core.audio.LocalTtsEngine
import com.jarvis.app.core.config.AgentConfigRepository
import com.jarvis.app.core.contracts.ActionEvent
import com.jarvis.app.core.llm.AgentBackend
import com.jarvis.app.core.llm.LocalBackend
import com.jarvis.app.core.mcp.McpClient
import com.jarvis.app.core.model.ModelDownloader
import com.jarvis.app.core.session.VoiceSessionListener
import com.jarvis.app.core.session.VoiceSessionManager
import com.jarvis.app.core.stt.AndroidSpeechToText
import com.jarvis.app.core.stt.SpeechListener
import com.jarvis.app.core.tools.InMemoryPhoneToolExecutor
import com.jarvis.app.InstalledAppLauncher
import com.jarvis.app.core.router.RoutingResult
import com.jarvis.app.core.trigger.DevTrigger
import com.jarvis.app.core.trigger.TriggerType
import com.jarvis.app.ui.GlowingOrb
import com.jarvis.app.ui.VoiceOrbState

/**
 * Floating assistant overlay launched via power/side button or ASSIST intent.
 * Shows a compact bubble with a glowing orb, live transcript, and JARVIS reply.
 * Tap outside or anywhere on the backdrop to dismiss.
 */
class AssistantOverlayActivity : ComponentActivity() {

    private lateinit var audioFeedback: LocalAudioFeedback
    private lateinit var ttsEngine: LocalTtsEngine
    private lateinit var speechToText: AndroidSpeechToText
    private lateinit var phoneToolExecutor: InMemoryPhoneToolExecutor
    private lateinit var voiceSession: VoiceSessionManager

    private var _orbState by mutableStateOf(VoiceOrbState.LISTENING)
    private var _statusText by mutableStateOf("Listening...")
    private val _exchangeLines = mutableStateListOf<Pair<Boolean, String>>() // isUser, text

    // Runtime permission launcher
    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                _orbState = VoiceOrbState.LISTENING
                _statusText = "Listening..."
                speechToText.startListening()
            } else {
                _orbState = VoiceOrbState.IDLE
                _statusText = "Microphone permission denied"
                deliverReply("Microphone permission denied. Please grant it in Settings.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure floating window at bottom of screen, no dim
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        audioFeedback = LocalAudioFeedback()
        ttsEngine = LocalTtsEngine(this)
        speechToText = AndroidSpeechToText(this)
        phoneToolExecutor = InMemoryPhoneToolExecutor(this, InstalledAppLauncher(this))
        voiceSession = buildVoiceSession()

        speechToText.addListener(object : SpeechListener {
            override fun onResults(text: String) {
                _exchangeLines.add(Pair(true, text))
                _orbState = VoiceOrbState.THINKING
                _statusText = "Thinking..."
                voiceSession.processTranscript(text)
            }
            override fun onError(message: String) {
                _statusText = "STT error: $message"
                _orbState = VoiceOrbState.IDLE
            }
            override fun onEndOfSpeech() {}
        })

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { finish() } // tap backdrop to dismiss
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = Color(0xFF161B22).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f)
                    ),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .clickable(onClick = {}) // absorb clicks on bubble itself
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        GlowingOrb(state = _orbState, onMicClicked = { retriggerListening() })

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = _statusText,
                            color = Color(0xFF8B949E),
                            fontSize = 13.sp
                        )

                        // Show recent exchange (last 4 lines)
                        if (_exchangeLines.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                            ) {
                                items(_exchangeLines.takeLast(4)) { (isUser, text) ->
                                    val bg = if (isUser) Color(0xFF1F6FEB) else Color(0xFF21262D)
                                    val align = if (isUser) Alignment.End else Alignment.Start
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalAlignment = align
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(bg, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                .widthIn(max = 280.dp)
                                        ) {
                                            Text(text = text, color = Color(0xFFF0F6FC), fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto-start: open a session (plays ding), request mic if needed, begin listening.
        voiceSession.startSession(DevTrigger())
        if (hasMicPermission()) {
            _orbState = VoiceOrbState.LISTENING
            _statusText = "Listening..."
            speechToText.startListening()
        } else {
            _statusText = "Microphone permission needed"
            _orbState = VoiceOrbState.IDLE
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun retriggerListening() {
        ttsEngine.stopSpeaking()
        if (!hasMicPermission()) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        _orbState = VoiceOrbState.LISTENING
        _statusText = "Listening..."
        speechToText.startListening()
    }

    /**
     * Builds the same VoiceSessionManager the main app uses, so the overlay (side-button / ASSIST)
     * and the in-app mic share one brain: deterministic router, on-device model, cloud agent.
     * The onSpeak callback mirrors every reply into the overlay bubble and drives orb state.
     */
    private fun buildVoiceSession(): VoiceSessionManager {
        val agentConfig = AgentConfigRepository(this)
        val modelDownloader = ModelDownloader(this)
        val activeModel = agentConfig.activeToolModel()
        val toolModelPath = activeModel?.let {
            java.io.File(filesDir, it.fileName).takeIf { f -> f.exists() && f.length() > 0 }?.absolutePath
        } ?: if (modelDownloader.isDownloaded) modelDownloader.modelFile.absolutePath else null

        val llmBackend = LocalBackend(
            context = this,
            modelPath = toolModelPath,
            geminiKey = BuildConfig.JARVIS_GEMINI_API_KEY.ifEmpty { null },
            openaiKey = BuildConfig.JARVIS_OPENAI_API_KEY.ifEmpty { null },
            anthropicKey = BuildConfig.JARVIS_ANTHROPIC_API_KEY.ifEmpty { null }
        )
        val mcpClients = agentConfig.getMcpServers().map { McpClient(it) }
        val agentBackend = if (agentConfig.hasAgent) {
            AgentBackend(agentConfig, phoneToolExecutor, mcpClients)
        } else null

        val listener = object : VoiceSessionListener {
            override fun onSessionStarted(sessionId: String, triggerType: TriggerType) {}
            override fun onTranscriptReceived(sessionId: String, transcript: String) {}
            override fun onRoutingDecision(sessionId: String, result: RoutingResult) {}
            override fun onActionExecuted(sessionId: String, action: ActionEvent) {}
            override fun onError(sessionId: String, error: String) {
                _orbState = VoiceOrbState.IDLE
                _statusText = "Error"
            }
        }

        return VoiceSessionManager(
            context = this,
            audioFeedback = audioFeedback,
            ttsEngine = ttsEngine,
            phoneToolExecutor = phoneToolExecutor,
            llmBackend = llmBackend,
            agentBackend = agentBackend,
            sessionListener = listener,
            onSpeak = { text -> deliverReply(text) }
        )
    }

    private fun deliverReply(text: String) {
        _exchangeLines.add(Pair(false, text))
        _orbState = VoiceOrbState.SPEAKING
        _statusText = "Speaking..."
    }

    override fun onDestroy() {
        super.onDestroy()
        speechToText.destroy()
        audioFeedback.release()
        ttsEngine.shutdown()
        voiceSession.stopSession()
    }
}

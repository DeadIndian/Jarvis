package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jarvis.app.BuildConfig
import com.jarvis.app.core.audio.LocalAudioFeedback
import com.jarvis.app.core.audio.LocalTtsEngine
import com.jarvis.app.core.config.AgentConfigRepository
import com.jarvis.app.core.llm.AgentBackend
import com.jarvis.app.core.llm.LLMProviderManager
import com.jarvis.app.core.llm.LocalBackend
import com.jarvis.app.core.model.ModelDownloader
import com.jarvis.app.core.contracts.ActionEvent
import com.jarvis.app.core.router.DeterministicRouter
import com.jarvis.app.core.router.RoutingResult
import com.jarvis.app.core.session.VoiceSessionListener
import com.jarvis.app.core.session.VoiceSessionManager
import com.jarvis.app.core.stt.AndroidSpeechToText
import com.jarvis.app.core.stt.SpeechListener
import com.jarvis.app.core.tools.InMemoryPhoneToolExecutor
import com.jarvis.app.core.trigger.DevTrigger
import com.jarvis.app.core.trigger.TriggerSource
import com.jarvis.app.core.trigger.TriggerType
import com.jarvis.app.ui.ChatMessage
import com.jarvis.app.ui.ChatScreen
import com.jarvis.app.ui.MessageSender
import com.jarvis.app.ui.VoiceOrbState
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var audioFeedback: LocalAudioFeedback
    private lateinit var ttsEngine: LocalTtsEngine
    private lateinit var phoneToolExecutor: InMemoryPhoneToolExecutor
    private lateinit var llmBackend: LocalBackend
    private lateinit var voiceSession: VoiceSessionManager
    private lateinit var speechToText: AndroidSpeechToText
    private lateinit var agentConfig: AgentConfigRepository

    private val _messages = mutableStateListOf<ChatMessage>()
    private var _orbState by mutableStateOf(VoiceOrbState.IDLE)
    private var _connectionStatus by mutableStateOf("● Ready")

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                _orbState = VoiceOrbState.LISTENING
                _connectionStatus = "● Listening..."
                speechToText.startListening()
            } else {
                _orbState = VoiceOrbState.IDLE
                reply("Microphone permission denied. Grant it in Settings.")
            }
        }

    private val voiceSessionListener = object : VoiceSessionListener {
        override fun onSessionStarted(sessionId: String, triggerType: TriggerType) {
            _orbState = VoiceOrbState.LISTENING
            _connectionStatus = "● Listening (sid: ${sessionId.take(4)})"
        }

        override fun onTranscriptReceived(sessionId: String, transcript: String) {
            _orbState = VoiceOrbState.THINKING
            _connectionStatus = "● Processing..."
        }

        override fun onRoutingDecision(sessionId: String, result: RoutingResult) {
            // no-op; routing happens inside session manager
        }

        override fun onActionExecuted(sessionId: String, action: ActionEvent) {
            _connectionStatus = "● Ready"
        }

        override fun onError(sessionId: String, error: String) {
            _orbState = VoiceOrbState.IDLE
            _connectionStatus = "● Error"
            reply(error)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioFeedback = LocalAudioFeedback()
        ttsEngine = LocalTtsEngine(this)
        phoneToolExecutor = InMemoryPhoneToolExecutor(this, InstalledAppLauncher(this))
        speechToText = AndroidSpeechToText(this)
        agentConfig = AgentConfigRepository(this)

        val modelDownloader = ModelDownloader(this)
        val modelPath = if (modelDownloader.isDownloaded) modelDownloader.modelFile.absolutePath else null

        // On-device tool model (B): prefer an active configured model, else the bundled default.
        val activeModel = agentConfig.activeToolModel()
        val toolModelPath = activeModel?.let {
            java.io.File(filesDir, it.fileName).takeIf { f -> f.exists() && f.length() > 0 }?.absolutePath
        } ?: modelPath

        llmBackend = LocalBackend(
            context = this,
            modelPath = toolModelPath,
            geminiKey = BuildConfig.JARVIS_GEMINI_API_KEY.ifEmpty { null },
            openaiKey = BuildConfig.JARVIS_OPENAI_API_KEY.ifEmpty { null },
            anthropicKey = BuildConfig.JARVIS_ANTHROPIC_API_KEY.ifEmpty { null }
        )

        // Cloud agent (A): tool loop + MCP memory. Only if a key is configured.
        val mcpClients = agentConfig.getMcpServers().map { com.jarvis.app.core.mcp.McpClient(it) }
        val agentBackend = if (agentConfig.hasAgent) {
            AgentBackend(agentConfig, phoneToolExecutor, mcpClients)
        } else null

        voiceSession = VoiceSessionManager(
            context = this,
            audioFeedback = audioFeedback,
            ttsEngine = ttsEngine,
            phoneToolExecutor = phoneToolExecutor,
            llmBackend = llmBackend,
            agentBackend = agentBackend,
            sessionListener = voiceSessionListener
        )

        speechToText.addListener(object : SpeechListener {
            override fun onResults(text: String) {
                voiceSession.processTranscript(text)
            }
            override fun onError(message: String) {
                _orbState = VoiceOrbState.IDLE
                _connectionStatus = "● STT error"
                reply("Speech error: $message")
            }
            override fun onEndOfSpeech() {}
        })

        _messages.add(ChatMessage(
            sender = MessageSender.JARVIS,
            text = "JARVIS online. Tap the mic to talk. Works offline for phone tools."
        ))

        setContent {
            var showSettings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            if (showSettings) {
                com.jarvis.app.ui.SettingsScreen(
                    config = agentConfig,
                    toolModelActions = buildToolModelActions(),
                    onBack = { showSettings = false }
                )
            } else {
                ChatScreen(
                    messages = _messages,
                    orbState = _orbState,
                    connectionStatus = _connectionStatus,
                    onSendMessage = { text -> handleTextInput(text) },
                    onMicClicked = { triggerVoiceTurn() },
                    onSettingsClicked = { showSettings = true }
                )
            }
        }
    }

    // Download progress per tool-model name, observed by the settings screen.
    private val _downloadProgress = mutableStateMapOf<String, Int>()

    private fun buildToolModelActions(): com.jarvis.app.ui.ToolModelActions {
        val downloader = ModelDownloader(this)
        return com.jarvis.app.ui.ToolModelActions(
            onDownload = { m ->
                _downloadProgress[m.name] = 0
                downloader.downloadTo(m.url, m.fileName, object : com.jarvis.app.core.model.ModelDownloadCallback {
                    override fun onProgress(state: com.jarvis.app.core.model.DownloadState) {
                        _downloadProgress[m.name] = state.progress
                    }
                    override fun onComplete(file: java.io.File?) {
                        _downloadProgress.remove(m.name)
                    }
                })
            },
            onDelete = { m -> java.io.File(filesDir, m.fileName).delete() },
            isDownloaded = { m -> java.io.File(filesDir, m.fileName).let { it.exists() && it.length() > 0 } },
            downloadProgress = { name -> _downloadProgress[name] }
        )
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun triggerVoiceTurn() {
        ttsEngine.stopSpeaking()

        if (!hasMicPermission()) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // Real voice path: start session (plays ding), then begin STT.
        val devTrigger = DevTrigger()
        devTrigger.type // DEV_INJECTED, but session uses this trigger type

        voiceSession.startSession(devTrigger)
        speechToText.startListening()
    }

    private fun handleTextInput(text: String) {
        _orbState = VoiceOrbState.THINKING

        val localAnswer = resolveLocally(text)
        if (localAnswer != null) {
            reply(localAnswer)
            return
        }

        // Feed typed text through the same voice pipeline
        _orbState = VoiceOrbState.THINKING
        voiceSession.processTranscript(text)
    }

    private fun resolveLocally(text: String): String? {
        val t = text.trim().lowercase()

        if (t.contains("time") && (t.contains("what") || t.contains("tell") ||
            t.contains("current") || t == "time")) {
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "It's ${fmt.format(Date())}."
        }

        if (t.contains("date") || t.contains("today") || t.contains("what day")) {
            val fmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
            return "Today is ${fmt.format(Date())}."
        }

        if (t.contains("battery")) {
            val capacity = try {
                val bm = getSystemService(BATTERY_SERVICE) as android.os.BatteryManager
                bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } catch (e: Exception) {
                -1
            }
            if (capacity >= 0) return "Battery is at $capacity%."
        }

        if (t == "hello" || t == "hi" || t == "hey") return "Hey! How can I help?"
        if (t.startsWith("hey jarvis") || t.startsWith("hi jarvis") || t.startsWith("hello jarvis")) {
            return "Hey! I'm listening."
        }

        return null
    }

    private fun reply(text: String) {
        _orbState = VoiceOrbState.SPEAKING
        _messages.add(ChatMessage(sender = MessageSender.JARVIS, text = text))
        ttsEngine.speak(text, onDone = {
            _orbState = VoiceOrbState.IDLE
            _connectionStatus = "● Ready"
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        speechToText.destroy()
        audioFeedback.release()
        ttsEngine.shutdown()
        voiceSession.stopSession()
    }
}

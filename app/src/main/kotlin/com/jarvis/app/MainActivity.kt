package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.ui.MainScreen
import com.jarvis.app.ui.theme.JarvisTheme
import com.jarvis.app.utils.SoundPlayer
import com.jarvis.core.Event
import com.jarvis.core.InMemoryEventBus
import com.jarvis.core.JarvisState
import com.jarvis.execution.PipelineOrchestrator
import com.jarvis.execution.SkillExecutionEngine
import com.jarvis.execution.HttpRemoteAgentClient
import com.jarvis.execution.RemoteAgentClient
import com.jarvis.intent.LLMIntentRouter
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.llm.LLMProvider
import com.jarvis.llm.LocalFirstLLMRouter
import com.jarvis.llm.providers.MediaPipeLLMProvider
import com.jarvis.memory.MarkdownFileMemoryStore
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.CurrentTimeSkill
import com.jarvis.skills.InMemorySkillRegistry
import com.jarvis.skills.SystemControlSkill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

open class MainActivity : AppCompatActivity() {
    companion object {
        private const val GOOGLE_TTS_ENGINE_PACKAGE = "com.google.android.tts"
        private const val EXTRA_AUTO_START_VOICE = "com.jarvis.app.extra.AUTO_START_VOICE"
        private const val VOICE_SESSION_EXIT_PHRASE = "thank you"
        const val ACTION_LAUNCH_OVERLAY = "com.jarvis.app.action.LAUNCH_OVERLAY"

        fun createOverlayIntent(context: Context, autoStartVoice: Boolean = true): Intent {
            return Intent(context, AssistantOverlayActivity::class.java).apply {
                action = ACTION_LAUNCH_OVERLAY
                putExtra(EXTRA_AUTO_START_VOICE, autoStartVoice)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private var orchestrator: PipelineOrchestrator? = null
    private val eventBus = InMemoryEventBus()
    private lateinit var speechToText: AndroidSpeechToText
    private lateinit var wakeWordEngine: OpenWakeWordEngine
    private lateinit var modelManager: OnDeviceModelManager
    private lateinit var modelViewModel: ModelStatusViewModel
    private lateinit var logger: AndroidLogJarvisLogger
    private lateinit var textToSpeech: TextToSpeech
    private var isTextToSpeechReady: Boolean = false
    private var lastRuntimeServiceState: JarvisState? = null
    private var lastWakeWordState: JarvisState? = null
    private var activeLocalLlmProvider: MediaPipeLLMProvider? = null
    private lateinit var classifierTraceStore: ClassifierTraceStore
    private var voiceAutoStarted = false
    private var continuousVoiceModeEnabled = false
    private var sttCaptureActive = false
    private var pendingWakeWordStart = false
    private var wakeWordBlockedByMedia = false
    private var lastWakeWordGateReason: String? = null
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private val wakeWordMediaPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val wakeWordMediaPollRunnable = object : Runnable {
        override fun run() {
            syncWakeWordEngine(_jarvisStateFlow.value)
            if (wakeWordBlockedByMedia) {
                wakeWordMediaPollHandler.postDelayed(this, 1500L)
            }
        }
    }
    protected var soundPlayer: SoundPlayer? = null

    protected open fun onJarvisStateChanged(state: JarvisState) = Unit

    // Compose States
    private val _jarvisStateFlow = MutableStateFlow(JarvisState.IDLE)
    val jarvisStateFlow: StateFlow<JarvisState> = _jarvisStateFlow.asStateFlow()

    private val _jarvisInputTextFlow = MutableStateFlow("")
    val jarvisInputTextFlow: StateFlow<String> = _jarvisInputTextFlow.asStateFlow()

    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            appendLog("Microphone permission granted")
            if (pendingWakeWordStart) {
                pendingWakeWordStart = false
                syncWakeWordEngine(_jarvisStateFlow.value)
            } else if (continuousVoiceModeEnabled) {
                syncContinuousVoiceCapture(_jarvisStateFlow.value)
            } else {
                startVoiceCapture()
            }
        } else {
            pendingWakeWordStart = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        soundPlayer = SoundPlayer(this)
        logger = AndroidLogJarvisLogger { line -> runOnUiThread { appendLog(line) } }
        classifierTraceStore = ClassifierTraceStore(this)

        modelManager = LiteRtOnDeviceModelManager(
            context = applicationContext,
            logger = logger,
            modelDirectoryPath = BuildConfig.JARVIS_LITERT_MODEL_DIR,
            huggingFaceToken = BuildConfig.JARVIS_HF_TOKEN
        )
        initializeTextToSpeech()
        
        modelViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ModelStatusViewModel(modelManager, logger) as T
            }
        }).get(ModelStatusViewModel::class.java)

        eventBus.subscribe { event ->
            if (event is Event.StateChanged) {
                _jarvisStateFlow.value = event.state
                
                runOnUiThread {
                    syncRuntimeService(event.state)
                    syncWakeWordEngine(event.state)
                    syncContinuousVoiceCapture(event.state)
                    onJarvisStateChanged(event.state)
                }
            }
        }

        lifecycleScope.launch {
            orchestrator = buildOrchestrator(warmup = false)
            orchestrator?.let {
                _jarvisStateFlow.value = it.currentState()
                syncRuntimeService(it.currentState())
                syncWakeWordEngine(it.currentState())
            }
        }
        
        speechToText = AndroidSpeechToText(
            context = this,
            logger = logger,
            onPartialResult = { partial -> _jarvisInputTextFlow.value = partial },
            onFinalResult = { finalText -> 
                sttCaptureActive = false
                _jarvisInputTextFlow.value = "" // clear
                if (isVoiceSessionExitPhrase(finalText)) {
                    disableContinuousVoiceMode()
                } else {
                    dispatch(Event.VoiceInput(finalText))
                }
            },
            onError = { message ->
                sttCaptureActive = false
                appendLog("STT error: $message")
                scheduleContinuousVoiceRetry()
                syncWakeWordEngine(_jarvisStateFlow.value)
            }
        )
        wakeWordEngine = OpenWakeWordEngine(
            context = this,
            logger = logger,
            onWakeWordDetected = { keyword ->
                launchOverlayFromWakeWord()
                dispatch(Event.WakeWordDetected(keyword))
            },
            onError = { message -> appendLog("Wake-word error: $message") }
        )
        // Ensure wake-word state is reconciled after engine creation.
        syncWakeWordEngine(_jarvisStateFlow.value)
        registerMediaPlaybackCallback()

        maybeAutoStartVoiceMode()
        
        modelViewModel.refreshModelList()

        if (this !is AssistantOverlayActivity) {
            setContent {
                JarvisTheme {
                    val state = jarvisStateFlow.collectAsState().value
                    val logs = logsFlow.collectAsState().value
                    val modelUiState = modelViewModel.uiState.collectAsState().value
                    
                    MainScreen(
                        jarvisState = state.name,
                        logs = logs,
                        modelStatusUiState = modelUiState,
                        onUseModelClicked = { initializeModelBackground() },
                        onModelSelected = { modelViewModel.selectModel(it) },
                        onRefreshModels = { modelViewModel.refreshModelList() },
                        onDownloadModel = { modelViewModel.downloadModel(it) },
                        onDeleteModel = { modelViewModel.deleteModel(it) }
                    )
                }
            }
        }
        
        appendLog("Jarvis MVP ready")
    }

    override fun onDestroy() {
        activeLocalLlmProvider?.close()
        wakeWordEngine.release()
        wakeWordMediaPollHandler.removeCallbacks(wakeWordMediaPollRunnable)
        unregisterMediaPlaybackCallback()
        speechToText.release()
        soundPlayer?.release()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Reconcile wake-word lifecycle whenever activity comes to foreground.
        syncWakeWordEngine(_jarvisStateFlow.value)
    }

    private fun initializeModelBackground() {
        requestBackgroundPermissions()
        lifecycleScope.launch {
            val orch = buildOrchestrator(warmup = true)
            orchestrator = orch
            val isReady = activeLocalLlmProvider != null
            if (isReady) {
                appendLog("Model warmed up and ready")
                syncRuntimeService(orch.currentState())
            } else {
                appendLog("Model Initialization Failed")
            }
        }
    }

    private fun dispatch(event: Event) {
        val orch = orchestrator ?: return
        lifecycleScope.launch(Dispatchers.Default) {
            orch.dispatch(event)
            appendLog("Event: ${event::class.simpleName}")
        }
    }

    private suspend fun buildOrchestrator(warmup: Boolean): PipelineOrchestrator = withContext(Dispatchers.IO) {
        val installedAppLauncher = InstalledAppLauncher(this@MainActivity)
        val systemControlManager = SystemControlManager(this@MainActivity)
        val memoryStore = MarkdownFileMemoryStore(writesEnabled = false)

        activeLocalLlmProvider?.close()
        activeLocalLlmProvider = null

        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(installedAppLauncher::launch))
            register(CurrentTimeSkill())
            register(SystemControlSkill(systemControlManager::execute))
        }

        val modelId = modelManager.getActiveModelId()
        val modelFile = resolveGpuSafeModelFile(modelId)
        
        val localProvider: LLMProvider? = if (modelFile?.exists() == true) {
            val provider = MediaPipeLLMProvider(
                context = this@MainActivity,
                modelPath = modelFile.absolutePath,
                logger = logger
            )
            if (warmup) {
                val success = provider.warmup()
                if (success) provider.also { activeLocalLlmProvider = it } else null
            } else {
                provider.also { activeLocalLlmProvider = it }
            }
        } else {
            null
        }

        val llmRouter = LocalFirstLLMRouter(
            localProvider = localProvider,
            cloudProvider = null,
            logger = logger
        )

        val remoteAgentClient: RemoteAgentClient? = BuildConfig.JARVIS_REMOTE_AGENT_BASE_URL
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { baseUrl ->
                HttpRemoteAgentClient(
                    baseUrl = baseUrl,
                    logger = logger
                )
            }

        val outputChannel = UiOutputChannel(
            onState = { state -> appendLog("Output state: $state") },
            onSpeak = { text -> speakAfterActionSound(text) }
        )

        val intentRouter = LLMIntentRouter(
            fallbackRouter = RuleBasedIntentRouter(),
            logger = logger,
            onClassified = { trace -> classifierTraceStore.append(trace) }
        )

        PipelineOrchestrator(
            eventBus = eventBus,
            intentRouter = intentRouter,
            planner = SimplePlanner(),
            executionEngine = SkillExecutionEngine(skillRegistry),
            outputChannel = outputChannel,
            logger = logger,
            memoryStore = memoryStore,
            llmRouter = llmRouter,
            remoteAgentClient = remoteAgentClient,
            allowCloudFallback = false,
            localMemoryWritesEnabled = false
        )
    }

    private fun resolveModelFileName(modelId: String): String {
        return when (modelId) {
            "gemma-3-1b-it-q4" -> "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"
            "gemma-3-1b-it-q4-sm8650" -> "gemma-4-E2B-it.litertlm"
            "gemma-4-e2b-it" -> "gemma-4-E2B-it.litertlm"
            "gemma-4-e4b-it" -> "gemma-4-E4B-it.litertlm"
            "qwen-2.5-1.5b-instruct" -> "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
            else -> "$modelId.litertlm"
        }
    }

    private fun resolveGpuSafeModelFile(activeModelId: String): File? {
        val llmDir = File(applicationContext.filesDir, "llm")
        val activeFileName = resolveModelFileName(activeModelId)

        val candidates = buildList {
            if (!isKnownGpuUnstableModel(activeFileName)) {
                add(activeFileName)
            }
            // Prefer known lighter local artifacts for GPU stability.
            add("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm")
            add("Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm")
            add(activeFileName)
        }

        val resolved = candidates
            .distinct()
            .map { File(llmDir, it) }
            .firstOrNull { it.exists() }

        if (resolved != null && resolved.name != activeFileName && isKnownGpuUnstableModel(activeFileName)) {
            logger.warn(
                "llm",
                "Selected model is unstable on GPU; using fallback artifact",
                mapOf("activeModel" to activeFileName, "fallbackModel" to resolved.name)
            )
        }

        return resolved
    }

    private fun isKnownGpuUnstableModel(fileName: String): Boolean {
        val normalized = fileName.lowercase(Locale.US)
        return normalized.contains("gemma-4-e2b") || normalized.contains("gemma-4-e4b")
    }

    private fun appendLog(line: String) {
        val currentLogs = _logsFlow.value.toMutableList()
        currentLogs.add(line)
        if (currentLogs.size > 100) currentLogs.removeAt(0)
        _logsFlow.value = currentLogs
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTextToSpeechReady = true
                textToSpeech.language = Locale.US
            }
        }, GOOGLE_TTS_ENGINE_PACKAGE)
    }

    private fun speakWithAndroidTextToSpeech(text: String) {
        if (isTextToSpeechReady) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
        }
    }

    private fun speakAfterActionSound(text: String) {
        val player = soundPlayer
        if (player == null) {
            runOnUiThread { speakWithAndroidTextToSpeech(text) }
            return
        }

        player.playActionSoundThen {
            runOnUiThread { speakWithAndroidTextToSpeech(text) }
        }
    }

    private fun requestBackgroundPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionRequest.launch(perms.toTypedArray())
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun startVoiceCapture() {
        if (hasMicPermission()) {
            sttCaptureActive = true
            // Ensure wake-word recognizer is not holding the mic when regular STT starts.
            wakeWordEngine.stop()
            appendLog("Listening...")
            speechToText.startListening()
        } else {
            requestBackgroundPermissions()
        }
    }

    private fun enableContinuousVoiceMode() {
        if (continuousVoiceModeEnabled) {
            return
        }
        continuousVoiceModeEnabled = true
        appendLog("Voice mode enabled")

        lifecycleScope.launch(Dispatchers.Default) {
            orchestrator?.transitionState(JarvisState.ACTIVE)
        }
        syncContinuousVoiceCapture(JarvisState.ACTIVE)
    }

    private fun disableContinuousVoiceMode() {
        if (!continuousVoiceModeEnabled) {
            return
        }
        continuousVoiceModeEnabled = false
        sttCaptureActive = false
        appendLog("Voice mode disabled")
        speechToText.stopListening()

        lifecycleScope.launch(Dispatchers.Default) {
            orchestrator?.transitionState(JarvisState.IDLE)
        }
    }

    private fun syncContinuousVoiceCapture(state: JarvisState) {
        if (!continuousVoiceModeEnabled) {
            return
        }
        when (state) {
            JarvisState.ACTIVE -> startVoiceCapture()
            JarvisState.THINKING,
            JarvisState.SPEAKING,
            JarvisState.IDLE,
            JarvisState.BARN_DOOR,
            JarvisState.HOUSE_PARTY -> speechToText.stopListening()
        }
    }

    private fun scheduleContinuousVoiceRetry() {
        if (!continuousVoiceModeEnabled || _jarvisStateFlow.value != JarvisState.ACTIVE) {
            return
        }
        lifecycleScope.launch {
            delay(500)
            if (continuousVoiceModeEnabled && _jarvisStateFlow.value == JarvisState.ACTIVE) {
                startVoiceCapture()
            }
        }
    }

    private fun isVoiceSessionExitPhrase(text: String): Boolean {
        val normalized = text
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
        return normalized == VOICE_SESSION_EXIT_PHRASE
    }

    private fun maybeAutoStartVoiceMode() {
        val action = intent?.action
        val requestedFromIntent = intent?.getBooleanExtra(EXTRA_AUTO_START_VOICE, false) == true
        val shouldAutoStart = requestedFromIntent ||
            action == ACTION_LAUNCH_OVERLAY ||
            action == Intent.ACTION_ASSIST ||
            action == Intent.ACTION_VOICE_COMMAND
        if (!shouldAutoStart || voiceAutoStarted) {
            return
        }
        voiceAutoStarted = true
        enableContinuousVoiceMode()
    }

    private fun launchOverlayFromWakeWord() {
        runOnUiThread {
            startActivity(createOverlayIntent(this, autoStartVoice = true))
        }
    }

    private fun startWakeWordEngine() {
        if (isMediaPlaybackActive()) {
            return
        }
        if (hasMicPermission()) {
            pendingWakeWordStart = false
            wakeWordEngine.start(keyword = "jarvis")
        } else {
            pendingWakeWordStart = true
            requestBackgroundPermissions()
        }
    }

    private fun recordWakeWordGate(reason: String) {
        if (lastWakeWordGateReason == reason) {
            return
        }
        lastWakeWordGateReason = reason
        appendLog("Wake-word gate: $reason")
    }

    private fun syncWakeWordEngine(state: JarvisState) {
        if (this is AssistantOverlayActivity) {
            wakeWordEngine.stop()
            recordWakeWordGate("overlay-activity")
            lastWakeWordState = state
            return
        }

        if (continuousVoiceModeEnabled) {
            wakeWordEngine.stop()
            recordWakeWordGate("continuous-voice")
            lastWakeWordState = state
            return
        }

        if (sttCaptureActive) {
            wakeWordEngine.stop()
            recordWakeWordGate("stt-active")
            lastWakeWordState = state
            return
        }

        if (isMediaPlaybackActive()) {
            wakeWordEngine.stop()
            if (!wakeWordBlockedByMedia) {
                wakeWordBlockedByMedia = true
                appendLog("Wake-word paused while media is playing")
                wakeWordMediaPollHandler.removeCallbacks(wakeWordMediaPollRunnable)
                wakeWordMediaPollHandler.postDelayed(wakeWordMediaPollRunnable, 1500L)
            }
            recordWakeWordGate("media-playing")
            lastWakeWordState = state
            return
        }

        if (wakeWordBlockedByMedia) {
            wakeWordBlockedByMedia = false
            wakeWordMediaPollHandler.removeCallbacks(wakeWordMediaPollRunnable)
            appendLog("Wake-word resumed after media playback stopped")
            if (state == JarvisState.IDLE) {
                startWakeWordEngine()
                lastWakeWordState = state
                return
            }
        }

        val shouldRunWakeWord = state == JarvisState.IDLE || state == JarvisState.ACTIVE
        if (shouldRunWakeWord) {
            recordWakeWordGate("enabled-$state")
            startWakeWordEngine()
        } else {
            wakeWordEngine.stop()
            recordWakeWordGate("state-$state")
        }
        lastWakeWordState = state
    }

    private fun registerMediaPlaybackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || playbackCallback != null) {
            return
        }
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                runOnUiThread {
                    syncWakeWordEngine(_jarvisStateFlow.value)
                }
            }
        }
        playbackCallback = callback
        audioManager.registerAudioPlaybackCallback(callback, null)
    }

    private fun unregisterMediaPlaybackCallback() {
        val callback = playbackCallback ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.unregisterAudioPlaybackCallback(callback)
        }
        playbackCallback = null
    }

    private fun isMediaPlaybackActive(): Boolean {
        // `isMusicActive` is less noisy here than playback configuration snapshots,
        // which can remain populated for paused/inactive sessions on some devices.
        return audioManager.isMusicActive
    }

    private fun syncRuntimeService(state: JarvisState) {
        if (activeLocalLlmProvider != null) {
            JarvisRuntimeService.start(this, state.name)
        }
        lastRuntimeServiceState = state
    }
}

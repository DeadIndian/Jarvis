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
import android.speech.tts.UtteranceProgressListener
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
import com.jarvis.app.ui.MainTab
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
import com.jarvis.skills.HelpCenterSkill
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
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class MainActivity : AppCompatActivity() {
    companion object {
        private const val GOOGLE_TTS_ENGINE_PACKAGE = "com.google.android.tts"
        private const val EXTRA_AUTO_START_VOICE = "com.jarvis.app.extra.AUTO_START_VOICE"
        private const val EXTRA_INITIAL_TAB = "com.jarvis.app.extra.INITIAL_TAB"
        private const val VOICE_SESSION_EXIT_PHRASE = "thank you"
        private val MALE_TTS_VOICE_NAMES = listOf(
            "en-us-x-iom-local",
            "en-us-x-iol-local",
            "en-us-x-iog-local",
            "en-us-x-iob-local"
        )
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

        fun createMainIntent(context: Context, initialTab: MainTab = MainTab.HOME): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, initialTab.route)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
    private val pendingEvents = mutableListOf<Event>()
    private val pendingSpeechCompletions = ConcurrentHashMap<String, CountDownLatch>()
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
    private val _selectedMainTabFlow = MutableStateFlow(MainTab.HOME)
    val selectedMainTabFlow: StateFlow<MainTab> = _selectedMainTabFlow.asStateFlow()

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
        supportActionBar?.hide()
        handleNavigationIntent(intent)
        
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
            val orch = buildOrchestrator(warmup = false)
            attachOrchestrator(orch)
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
                    val selectedTab = selectedMainTabFlow.collectAsState().value
                    val modelUiState = modelViewModel.uiState.collectAsState().value
                    
                    MainScreen(
                        jarvisState = state.name,
                        logs = logs,
                        selectedTab = selectedTab,
                        helpOverview = JarvisHelpCatalog.overviewBlocks,
                        helpCommandSections = JarvisHelpCatalog.commandSections,
                        modelStatusUiState = modelUiState,
                        onTabSelected = { _selectedMainTabFlow.value = it },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onDestroy() {
        activeLocalLlmProvider?.close()
        wakeWordEngine.release()
        wakeWordMediaPollHandler.removeCallbacks(wakeWordMediaPollRunnable)
        unregisterMediaPlaybackCallback()
        speechToText.release()
        soundPlayer?.release()
        pendingSpeechCompletions.values.forEach { it.countDown() }
        pendingSpeechCompletions.clear()
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
            attachOrchestrator(orch)
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
        val orch = orchestrator
        if (orch == null) {
            synchronized(pendingEvents) {
                pendingEvents += event
            }
            appendLog("Queued event: ${event::class.simpleName}")
            return
        }
        dispatchToOrchestrator(orch, event)
    }

    private fun dispatchToOrchestrator(orch: PipelineOrchestrator, event: Event) {
        lifecycleScope.launch(Dispatchers.Default) {
            orch.dispatch(event)
            appendLog("Event: ${event::class.simpleName}")
        }
    }

    private fun attachOrchestrator(orch: PipelineOrchestrator) {
        orchestrator = orch
        val queuedEvents = synchronized(pendingEvents) {
            pendingEvents.toList().also { pendingEvents.clear() }
        }
        queuedEvents.forEach { event -> dispatchToOrchestrator(orch, event) }
    }

    private suspend fun buildOrchestrator(warmup: Boolean): PipelineOrchestrator = withContext(Dispatchers.IO) {
        val installedAppLauncher = InstalledAppLauncher(this@MainActivity)
        val systemControlManager = SystemControlManager(this@MainActivity)
        val memoryBaseDir: Path = File(applicationContext.filesDir, "memory").toPath()
        val memoryStore = MarkdownFileMemoryStore(
            baseDirectory = memoryBaseDir,
            writesEnabled = false
        )

        activeLocalLlmProvider?.close()
        activeLocalLlmProvider = null

        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(installedAppLauncher::launch))
            register(CurrentTimeSkill())
            register(SystemControlSkill(systemControlManager::execute))
            register(HelpCenterSkill(::openHelpCenter))
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

    private fun handleNavigationIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_INITIAL_TAB) ?: return
        _selectedMainTabFlow.value = MainTab.fromRoute(route)
    }

    private suspend fun openHelpCenter(): String {
        return withContext(Dispatchers.Main) {
            if (this@MainActivity is AssistantOverlayActivity) {
                startActivity(createMainIntent(this@MainActivity, MainTab.HELP))
            } else {
                _selectedMainTabFlow.value = MainTab.HELP
            }
            "Opened the help center."
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTextToSpeechReady = true
                applyPreferredMaleTtsVoice()
                appendLog("TTS initialized")
            } else {
                appendLog("TTS initialization failed: status=$status")
            }
        }, GOOGLE_TTS_ENGINE_PACKAGE)
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                signalSpeechCompletion(utteranceId)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                signalSpeechCompletion(utteranceId)
            }
        })
    }

    private fun applyPreferredMaleTtsVoice() {
        if (!isTextToSpeechReady) {
            return
        }

        val voice = selectPreferredMaleVoice()
        if (voice != null) {
            textToSpeech.voice = voice
            appendLog("TTS voice: ${voice.name}")
        } else {
            textToSpeech.language = Locale.US
            appendLog("TTS voice: Default US English")
        }
    }

    private fun selectPreferredMaleVoice(): Voice? {
        val voices = try {
            textToSpeech.voices
        } catch (_: Exception) {
            null
        } ?: return null

        val englishVoices = voices.filter { it.locale.language == "en" }

        val genderMatchedVoice = englishVoices
            .filter { voice -> readVoiceGender(voice) == 2 }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }
                .thenBy { it.locale.country != "US" }
            )
            .firstOrNull()
        if (genderMatchedVoice != null) {
            return genderMatchedVoice
        }

        for (name in MALE_TTS_VOICE_NAMES) {
            englishVoices.find { it.name.equals(name, ignoreCase = true) }?.let { return it }
        }

        return englishVoices.firstOrNull { it.name.contains("male", ignoreCase = true) }
    }

    private fun readVoiceGender(voice: Voice): Int? {
        return try {
            val field = voice.javaClass.getDeclaredField("mGender")
            field.isAccessible = true
            field.get(voice) as? Int
        } catch (_: Exception) {
            null
        }
    }

    private fun signalSpeechCompletion(utteranceId: String?) {
        if (utteranceId.isNullOrBlank()) {
            return
        }
        pendingSpeechCompletions.remove(utteranceId)?.countDown()
    }

    private fun speakWithAndroidTextToSpeech(text: String, utteranceId: String): Boolean {
        if (!isTextToSpeechReady) {
            appendLog("Skipped speaking because TTS is not ready yet")
            return false
        }
        val result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            appendLog("TTS speak request failed")
            return false
        }
        appendLog("Speaking: $text")
        return true
    }

    private fun speakAfterActionSound(text: String) {
        val utteranceId = "jarvis-${System.currentTimeMillis()}"
        val completion = CountDownLatch(1)
        pendingSpeechCompletions[utteranceId] = completion

        val startSpeech = {
            runOnUiThread {
                val accepted = speakWithAndroidTextToSpeech(text, utteranceId)
                if (!accepted) {
                    signalSpeechCompletion(utteranceId)
                }
            }
        }

        val player = soundPlayer
        if (player == null) {
            startSpeech()
        } else {
            player.playActionSoundThen(startSpeech)
        }

        val finished = completion.await(15, TimeUnit.SECONDS)
        if (!finished) {
            pendingSpeechCompletions.remove(utteranceId)
            appendLog("Timed out waiting for speech completion")
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
        if (playbackCallback != null) {
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
        audioManager.unregisterAudioPlaybackCallback(callback)
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

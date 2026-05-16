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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.ui.MainScreen
import com.jarvis.app.ui.MainTab
import com.jarvis.app.ui.theme.JarvisTheme
import com.jarvis.app.utils.SoundPlayer
import com.jarvis.core.Event
import com.jarvis.core.JarvisState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private val MALE_TTS_VOICE_NAMES = listOf("en-us-x-iom-local", "en-us-x-iol-local", "en-us-x-iog-local", "en-us-x-iob-local")
        const val ACTION_LAUNCH_OVERLAY = "com.jarvis.app.action.LAUNCH_OVERLAY"

        fun createOverlayIntent(context: Context, autoStartVoice: Boolean = true): Intent {
            return Intent(context, AssistantOverlayActivity::class.java).apply {
                action = ACTION_LAUNCH_OVERLAY
                putExtra(EXTRA_AUTO_START_VOICE, autoStartVoice)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }

        fun createMainIntent(context: Context, initialTab: MainTab = MainTab.HOME): Intent {
            return Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_TAB, initialTab.route)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    protected var soundPlayer: SoundPlayer? = null
    protected var isActivityResumed = false
    protected var voiceAutoStarted = false
    protected var continuousVoiceModeEnabled = false
    private var vadEngine: com.jarvis.app.vad.VadEngine? = null

    private lateinit var logger: AndroidLogJarvisLogger
    private lateinit var textToSpeech: TextToSpeech
    private var isTextToSpeechReady: Boolean = false
    
    private var sttCaptureActive = false
    private var wakeWordBlockedByMedia = false
    private var lastWakeWordGateReason: String? = null
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null
    private val pendingSpeechCompletions = ConcurrentHashMap<String, CountDownLatch>()
    private val wakeWordMediaPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val wakeWordMediaPollRunnable: Runnable = object : Runnable {
        override fun run() {
            syncVoiceLifecycle(JarvisEngine.stateFlow.value)
            if (wakeWordBlockedByMedia) wakeWordMediaPollHandler.postDelayed(this, 2000L)
        }
    }
    
    private lateinit var modelViewModel: ModelStatusViewModel
    private lateinit var settingsViewModel: LlmSettingsViewModel
    private lateinit var memorySettingsViewModel: MemorySettingsViewModel

    protected open fun onJarvisStateChanged(state: JarvisState) = Unit

    protected val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()
    private val _selectedMainTabFlow = MutableStateFlow(MainTab.HOME)
    val selectedMainTabFlow: StateFlow<MainTab> = _selectedMainTabFlow.asStateFlow()

    private val _inputTextFlow = MutableStateFlow("")
    val inputTextFlow: StateFlow<String> = _inputTextFlow.asStateFlow()

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            memorySettingsViewModel.updateNotesFolderPath(it.toString())
        }
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            appendLog("Microphone permission granted")
            syncVoiceLifecycle(JarvisEngine.stateFlow.value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        handleNavigationIntent(intent)
        
        soundPlayer = SoundPlayer(this)
        logger = AndroidLogJarvisLogger { line -> runOnUiThread { appendLog(line) } }

        JarvisEngine.init(this, logger)
        
        // Re-init engines to ensure they use current activity context and callbacks
        val wakeWordEngine = OpenWakeWordEngine(this, logger, 
            onWakeWordDetected = { keyword ->
                launchOverlayFromWakeWord()
                dispatch(Event.WakeWordDetected(keyword))
            },
            onError = { appendLog("Wake-word error: $it") }
        )
        JarvisEngine.setWakeWordEngine(wakeWordEngine)

        // initialize VAD (WebRTC-native-backed placeholder)
        vadEngine = com.jarvis.app.vad.WebRtcVadEngine(this, logger, onVoiceDetected = {
            // When VAD detects voice in HOUSE_PARTY, start wake-word engine so it can spot the keyword.
            runOnUiThread {
                appendLog("VAD triggered: starting wake-word listener")
                try {
                    JarvisEngine.wakeWordEngine.start(keyword = "jarvis")
                } catch (_: Exception) { /* ignore */ }
            }
        })

        val stt = AndroidSpeechToText(this, logger,
            onPartialResult = { _inputTextFlow.value = it },
            onFinalResult = { finalText ->
                sttCaptureActive = false
                _inputTextFlow.value = ""
                if (isVoiceSessionExitPhrase(finalText)) disableContinuousVoiceMode()
                else dispatch(Event.VoiceInput(finalText))
            },
            onError = { message ->
                sttCaptureActive = false
                appendLog("STT error: $message")
                scheduleContinuousVoiceRetry()
            }
        )
        JarvisEngine.setSpeechToText(stt)

        settingsViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LlmSettingsViewModel(JarvisEngine.llmSettingsRepository, logger) as T
            }
        }).get(LlmSettingsViewModel::class.java)
        
        memorySettingsViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MemorySettingsViewModel(JarvisEngine.memorySettingsRepository, logger) as T
            }
        }).get(MemorySettingsViewModel::class.java)

        initializeTextToSpeech()
        
        modelViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ModelStatusViewModel(JarvisEngine.modelManager, logger) as T
            }
        }).get(ModelStatusViewModel::class.java)

        JarvisEngine.eventBus.subscribe { event ->
            if (event is Event.StateChanged) {
                runOnUiThread {
                    syncRuntimeService(event.state)
                    syncVoiceLifecycle(event.state)
                    onJarvisStateChanged(event.state)
                }
            }
        }

        lifecycleScope.launch {
            val outputChannel = UiOutputChannel(
                onState = { state -> appendLog("Output state: $state") },
                onSpeak = { text -> speakAfterActionSound(text) }
            )
            JarvisEngine.buildOrchestrator(this@MainActivity, outputChannel)
            val state = JarvisEngine.orchestrator.currentState()
            syncRuntimeService(state)
            syncVoiceLifecycle(state)
            onJarvisStateChanged(state)
            maybeAutoStartVoiceMode()
        }
        
        registerMediaPlaybackCallback()
        modelViewModel.refreshModelList()

        if (this !is AssistantOverlayActivity) {
            setContent {
                JarvisTheme {
                    val state = JarvisEngine.stateFlow.collectAsState().value
                    val logs = logsFlow.collectAsState().value
                    val selectedTab = selectedMainTabFlow.collectAsState().value
                    MainScreen(
                        jarvisState = state.name,
                        logs = logs,
                        selectedTab = selectedTab,
                        helpOverview = JarvisHelpCatalog.overviewBlocks,
                        helpCommandSections = JarvisHelpCatalog.commandSections,
                        modelStatusUiState = modelViewModel.uiState.collectAsState().value,
                        settingsUiState = settingsViewModel.uiState.collectAsState().value,
                        memorySettingsUiState = memorySettingsViewModel.uiState.collectAsState().value,
                        onTabSelected = { _selectedMainTabFlow.value = it },
                        onUseModelClicked = { initializeModelBackground() },
                        onModelSelected = { modelViewModel.selectModel(it) },
                        onRefreshModels = { modelViewModel.refreshModelList() },
                        onDownloadModel = { modelViewModel.downloadModel(it) },
                        onDeleteModel = { modelViewModel.deleteModel(it) },
                        onBackendModeSelected = { mode -> settingsViewModel.selectBackendMode(mode); refreshOrchestrator() },
                        onGeminiApiKeyChanged = { settingsViewModel.updateGeminiApiKey(it) },
                        onSaveGeminiApiKey = { settingsViewModel.saveGeminiApiKey(); refreshOrchestrator() },
                        onClearGeminiApiKey = { settingsViewModel.clearGeminiApiKey(); refreshOrchestrator() },
                        onMemoryFolderChanged = { memorySettingsViewModel.updateNotesFolderPath(it) },
                        onSelectMemoryFolder = { folderPicker.launch(null) },
                        onSaveMemoryFolder = { memorySettingsViewModel.saveNotesFolderPath() },
                        onClearMemoryFolder = { memorySettingsViewModel.clearNotesFolderPath() }
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
        maybeAutoStartVoiceMode()
    }

    override fun onDestroy() {
        wakeWordMediaPollHandler.removeCallbacks(wakeWordMediaPollRunnable)
        unregisterMediaPlaybackCallback()
        soundPlayer?.release()
        pendingSpeechCompletions.values.forEach { it.countDown() }
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        syncVoiceLifecycle(JarvisEngine.stateFlow.value)
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing || this is AssistantOverlayActivity) {
            disableContinuousVoiceMode()
        }
    }

    private fun initializeModelBackground() {
        requestBackgroundPermissions()
        refreshOrchestrator()
    }

    protected fun dispatch(event: Event) {
        lifecycleScope.launch(Dispatchers.Default) {
            JarvisEngine.orchestrator.dispatch(event)
            appendLog("Event: ${event::class.simpleName}")
        }
    }

    private fun refreshOrchestrator() {
        lifecycleScope.launch {
            val outputChannel = UiOutputChannel(
                onState = { state -> appendLog("Output state: $state") },
                onSpeak = { text -> speakAfterActionSound(text) }
            )
            JarvisEngine.buildOrchestrator(this@MainActivity, outputChannel)
            appendLog("LLM backend refreshed")
        }
    }

    protected fun appendLog(line: String) {
        val currentLogs = _logsFlow.value.toMutableList()
        currentLogs.add(line)
        if (currentLogs.size > 100) currentLogs.removeAt(0)
        _logsFlow.value = currentLogs
    }

    private fun handleNavigationIntent(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_INITIAL_TAB) ?: return
        _selectedMainTabFlow.value = MainTab.fromRoute(route)
    }

    protected suspend fun openHelpCenter(): String {
        return withContext(Dispatchers.Main) {
            if (this@MainActivity is AssistantOverlayActivity) startActivity(createMainIntent(this@MainActivity, MainTab.HELP))
            else _selectedMainTabFlow.value = MainTab.HELP
            "Opened the help center."
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTextToSpeechReady = true
                applyPreferredMaleTtsVoice()
                appendLog("TTS initialized")
            } else appendLog("TTS initialization failed: status=$status")
        }, GOOGLE_TTS_ENGINE_PACKAGE)
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { signalSpeechCompletion(utteranceId) }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) { signalSpeechCompletion(utteranceId) }
        })
    }

    private fun applyPreferredMaleTtsVoice() {
        if (!isTextToSpeechReady) return
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
        val voices = try { textToSpeech.voices } catch (_: Exception) { null } ?: return null
        val englishVoices = voices.filter { it.locale.language == "en" }
        val genderMatchedVoice = englishVoices
            .filter { voice -> readVoiceGender(voice) == 2 }
            .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.locale.country != "US" })
            .firstOrNull()
        if (genderMatchedVoice != null) return genderMatchedVoice
        for (name in MALE_TTS_VOICE_NAMES) englishVoices.find { it.name.equals(name, ignoreCase = true) }?.let { return it }
        return englishVoices.firstOrNull { it.name.contains("male", ignoreCase = true) }
    }

    private fun readVoiceGender(voice: Voice): Int? {
        return try {
            val field = voice.javaClass.getDeclaredField("mGender")
            field.isAccessible = true
            field.get(voice) as? Int
        } catch (_: Exception) { null }
    }

    private fun signalSpeechCompletion(utteranceId: String?) {
        if (utteranceId.isNullOrBlank()) return
        pendingSpeechCompletions.remove(utteranceId)?.countDown()
    }

    private fun speakWithAndroidTextToSpeech(text: String, utteranceId: String): Boolean {
        if (!isTextToSpeechReady) return false
        val result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) return false
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
                if (!accepted) signalSpeechCompletion(utteranceId)
            }
        }
        val player = soundPlayer
        if (player == null) startSpeech() else player.playActionSoundThen(startSpeech)
        val finished = completion.await(15, TimeUnit.SECONDS)
        if (!finished) {
            pendingSpeechCompletions.remove(utteranceId)
            appendLog("Timed out waiting for speech completion")
        }
    }

    private fun requestBackgroundPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionRequest.launch(perms.toTypedArray())
    }

    private fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    protected fun syncVoiceLifecycle(state: JarvisState) {
        val isOverlay = this is AssistantOverlayActivity
        // In the new model: HOUSE_PARTY provides continuous streaming via VAD.
        val shouldStreamVad = state == JarvisState.HOUSE_PARTY
        val shouldBeListening = state == JarvisState.ACTIVE

        // Stop STT if it shouldn't be running
        if (!shouldBeListening) {
            JarvisEngine.speechToText.stopListening()
            sttCaptureActive = false
        }

        // Stop VAD/wake-word when not in HOUSE_PARTY
        if (!shouldStreamVad || isMediaPlaybackActive()) {
            vadEngine?.stop()
            JarvisEngine.wakeWordEngine.stop()
        }

        // Handle media playback gating for HOUSE_PARTY
        if (shouldStreamVad && isMediaPlaybackActive()) {
            if (!wakeWordBlockedByMedia) {
                wakeWordBlockedByMedia = true
                appendLog("Wake-word/VAD paused while media is playing")
                wakeWordMediaPollHandler.removeCallbacks(wakeWordMediaPollRunnable)
                wakeWordMediaPollHandler.postDelayed(wakeWordMediaPollRunnable, 2000L)
            }
            recordWakeWordGate("media-playing")
            return
        }

        if (wakeWordBlockedByMedia && !isMediaPlaybackActive()) {
            wakeWordBlockedByMedia = false
            wakeWordMediaPollHandler.removeCallbacksAndMessages(null)
            appendLog("Wake-word/VAD resumed after media playback stopped")
        }

        // Start VAD or STT as needed. Stagger startup to avoid recognizer busy loops.
        lifecycleScope.launch {
            if (shouldBeListening && !sttCaptureActive) {
                JarvisEngine.wakeWordEngine.stop()
                delay(300)
                startVoiceCapture()
            } else if (shouldStreamVad && !isMediaPlaybackActive()) {
                JarvisEngine.speechToText.stopListening()
                delay(300)
                vadEngine?.start()
            }
        }
    }

    protected fun startVoiceCapture() {
        if (hasMicPermission()) {
            sttCaptureActive = true
            appendLog("Listening...")
            JarvisEngine.speechToText.startListening()
        } else requestBackgroundPermissions()
    }

    protected fun startWakeWordEngine() {
        if (isMediaPlaybackActive()) return
        if (hasMicPermission()) {
            JarvisEngine.wakeWordEngine.start(keyword = "jarvis")
        } else requestBackgroundPermissions()
    }

    protected fun enableContinuousVoiceMode() {
        if (continuousVoiceModeEnabled) return
        continuousVoiceModeEnabled = true
        appendLog("Voice mode enabled")
        lifecycleScope.launch(Dispatchers.Default) { JarvisEngine.orchestrator.transitionState(JarvisState.ACTIVE) }
        syncVoiceLifecycle(JarvisState.ACTIVE)
    }

    protected fun disableContinuousVoiceMode() {
        if (!continuousVoiceModeEnabled) return
        continuousVoiceModeEnabled = false
        sttCaptureActive = false
        appendLog("Voice mode disabled")
        JarvisEngine.speechToText.stopListening()
        lifecycleScope.launch(Dispatchers.Default) { JarvisEngine.orchestrator.transitionState(JarvisState.BARN_DOOR) }
    }

    protected fun scheduleContinuousVoiceRetry() {
        if (!continuousVoiceModeEnabled || JarvisEngine.stateFlow.value != JarvisState.ACTIVE) return
        lifecycleScope.launch {
            delay(1500) // Sturdier delay to prevent loop
            if (continuousVoiceModeEnabled && JarvisEngine.stateFlow.value == JarvisState.ACTIVE) startVoiceCapture()
        }
    }

    protected fun isVoiceSessionExitPhrase(text: String): Boolean {
        val normalized = text.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9\\s]"), "").replace(Regex("\\s+"), " ")
        return normalized == VOICE_SESSION_EXIT_PHRASE
    }

    protected fun maybeAutoStartVoiceMode() {
        val action = intent?.action
        val requestedFromIntent = intent?.getBooleanExtra(EXTRA_AUTO_START_VOICE, false) == true
        val shouldAutoStart = requestedFromIntent || action == ACTION_LAUNCH_OVERLAY || action == Intent.ACTION_ASSIST || action == Intent.ACTION_VOICE_COMMAND
        if (!shouldAutoStart || voiceAutoStarted) return
        voiceAutoStarted = true
        enableContinuousVoiceMode()
    }

    private fun launchOverlayFromWakeWord() {
        if (isActivityResumed && this !is AssistantOverlayActivity) {
            runOnUiThread { enableContinuousVoiceMode() }
            return
        }
        val context = applicationContext
        val intent = createOverlayIntent(context, autoStartVoice = true)
        runOnUiThread {
            try { context.startActivity(intent) } catch (e: Exception) { logger.error("MainActivity", "Background activity start failed", e) }
        }
    }

    private fun recordWakeWordGate(reason: String) {
        if (lastWakeWordGateReason == reason) return
        lastWakeWordGateReason = reason
        appendLog("Wake-word gate: $reason")
    }

    private fun registerMediaPlaybackCallback() {
        if (playbackCallback != null) return
        val callback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                runOnUiThread { syncVoiceLifecycle(JarvisEngine.stateFlow.value) }
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

    private fun isMediaPlaybackActive(): Boolean = audioManager.isMusicActive

    private fun syncRuntimeService(state: JarvisState) {
        JarvisRuntimeService.start(this, state.name)
    }
}

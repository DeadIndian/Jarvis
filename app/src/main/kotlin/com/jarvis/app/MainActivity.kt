package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
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
    protected var soundPlayer: SoundPlayer? = null

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
            startVoiceCapture()
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
                }
            } else if (event is Event.SkillResult) {
                soundPlayer?.playActionSound()
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
                _jarvisInputTextFlow.value = "" // clear
                dispatch(Event.VoiceInput(finalText)) 
            },
            onError = { message -> appendLog("STT error: $message") }
        )
        wakeWordEngine = OpenWakeWordEngine(
            context = this,
            logger = logger,
            onWakeWordDetected = { keyword -> dispatch(Event.WakeWordDetected(keyword)) },
            onError = { message -> appendLog("Wake-word error: $message") }
        )

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
        speechToText.release()
        soundPlayer?.release()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
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
        val memoryStore = MarkdownFileMemoryStore()

        activeLocalLlmProvider?.close()
        activeLocalLlmProvider = null

        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(installedAppLauncher::launch))
            register(CurrentTimeSkill())
        }

        val modelId = modelManager.getActiveModelId()
        val modelFile = File(applicationContext.filesDir, "llm/" + resolveModelFileName(modelId))
        
        val localProvider: LLMProvider? = if (modelFile.exists()) {
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

        val outputChannel = UiOutputChannel(
            onState = { state -> appendLog("Output state: $state") },
            onSpeak = { text -> speakWithAndroidTextToSpeech(text) }
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
            allowCloudFallback = false
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
            appendLog("Listening...")
            speechToText.startListening()
        } else {
            requestBackgroundPermissions()
        }
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
        startVoiceCapture()
    }

    private fun startWakeWordEngine() {
        if (hasMicPermission()) wakeWordEngine.start(keyword = "jarvis") else requestBackgroundPermissions()
    }

    private fun syncWakeWordEngine(state: JarvisState) {
        when (WakeWordPolicy.decide(lastWakeWordState, state)) {
            WakeWordAction.START -> startWakeWordEngine()
            WakeWordAction.STOP -> wakeWordEngine.stop()
            else -> Unit
        }
        lastWakeWordState = state
    }

    private fun syncRuntimeService(state: JarvisState) {
        if (activeLocalLlmProvider != null) {
            JarvisRuntimeService.start(this, state.name)
        }
        lastRuntimeServiceState = state
    }
}

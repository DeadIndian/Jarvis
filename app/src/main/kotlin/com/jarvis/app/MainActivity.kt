package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.jarvis.core.Event
import com.jarvis.core.InMemoryEventBus
import com.jarvis.core.JarvisState
import com.jarvis.execution.PipelineOrchestrator
import com.jarvis.execution.SkillExecutionEngine
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.llm.LocalFirstLLMRouter
import com.jarvis.llm.LLMProvider
import com.jarvis.memory.MarkdownFileMemoryStore
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.InMemorySkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private enum class MicPermissionReason {
        VOICE_CAPTURE,
        WAKE_WORD
    }

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var inputText: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var modelStatusText: TextView
    private lateinit var modelStatusDetailText: TextView
    private lateinit var installedModelsText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadProgressText: TextView
    private lateinit var modelErrorText: TextView
    private lateinit var orchestrator: PipelineOrchestrator
    private lateinit var speechToText: AndroidSpeechToText
    private lateinit var wakeWordEngine: OpenWakeWordEngine
    private lateinit var modelManager: OnDeviceModelManager
    private lateinit var modelViewModel: ModelStatusViewModel
    private lateinit var modelAdapter: ArrayAdapter<String>
    private val logger = AndroidLogJarvisLogger()
    private lateinit var textToSpeech: TextToSpeech
    private var isTextToSpeechReady: Boolean = false
    private var lastRuntimeServiceState: JarvisState? = null
    private var lastWakeWordState: JarvisState? = null
    private var pendingMicPermissionReason: MicPermissionReason? = null
    private var knownModels: List<OnDeviceModel> = emptyList()

    private val microphonePermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val reason = pendingMicPermissionReason
        pendingMicPermissionReason = null

        if (granted) {
            appendLog("Microphone permission granted")
            when (reason) {
                MicPermissionReason.WAKE_WORD -> startWakeWordEngine()
                MicPermissionReason.VOICE_CAPTURE, null -> startVoiceCapture()
            }
        } else {
            when (reason) {
                MicPermissionReason.WAKE_WORD -> {
                    appendLog("Microphone permission denied. Wake-word unavailable in HOUSE_PARTY.")
                }

                MicPermissionReason.VOICE_CAPTURE, null -> {
                    appendLog("Microphone permission denied. Voice input unavailable.")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        inputText = findViewById(R.id.inputText)
        modelSpinner = findViewById(R.id.modelSpinner)
        modelStatusText = findViewById(R.id.modelStatusText)
        modelStatusDetailText = findViewById(R.id.modelStatusDetailText)
        installedModelsText = findViewById(R.id.installedModelsText)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        downloadProgressText = findViewById(R.id.downloadProgressText)
        modelErrorText = findViewById(R.id.modelErrorText)

        modelManager = LiteRtOnDeviceModelManager(
            context = applicationContext,
            logger = logger,
            modelDirectoryPath = BuildConfig.JARVIS_LITERT_MODEL_DIR,
            huggingFaceToken = BuildConfig.JARVIS_HF_TOKEN
        )
        initializeTextToSpeech()
        modelViewModel = ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ModelStatusViewModel(modelManager, logger) as T
            }
        }).get(ModelStatusViewModel::class.java)
        
        setupModelControls()
        observeModelState()

        orchestrator = buildOrchestrator()
        speechToText = AndroidSpeechToText(
            context = this,
            logger = logger,
            onPartialResult = { partial -> runOnUiThread { inputText.setText(partial) } },
            onFinalResult = { finalText -> dispatch(Event.VoiceInput(finalText)) },
            onError = { message -> runOnUiThread { appendLog("STT error: $message") } }
        )
        wakeWordEngine = OpenWakeWordEngine(
            context = this,
            logger = logger,
            onWakeWordDetected = { keyword -> dispatch(Event.WakeWordDetected(keyword)) },
            onError = { message -> runOnUiThread { appendLog("Wake-word error: $message") } }
        )

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val input = inputText.text.toString().trim()
            if (input.isNotEmpty()) {
                dispatch(Event.TextInput(input))
            }
        }

        findViewById<Button>(R.id.powerButton).setOnClickListener {
            dispatch(Event.PowerButtonHeld)
        }

        findViewById<Button>(R.id.housePartyOnButton).setOnClickListener {
            dispatch(Event.HousePartyToggle(enabled = true))
        }

        findViewById<Button>(R.id.wakeWordButton).setOnClickListener {
            dispatch(Event.WakeWordDetected("jarvis"))
        }

        findViewById<Button>(R.id.timeoutButton).setOnClickListener {
            dispatch(Event.TimeoutElapsed)
        }

        findViewById<Button>(R.id.voiceButton).setOnClickListener {
            requestMicAndStartListening()
        }

        updateStatus("State: ${orchestrator.currentState().name}")
        syncRuntimeService(orchestrator.currentState())
        syncWakeWordEngine(orchestrator.currentState())
        refreshModelList()
        appendLog("LLM backend: ${BuildConfig.JARVIS_LLM_BACKEND}")
        appendLog("Jarvis MVP initialized")
    }

    override fun onDestroy() {
        wakeWordEngine.release()
        speechToText.release()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    private fun dispatch(event: Event) {
        lifecycleScope.launch(Dispatchers.Default) {
            orchestrator.dispatch(event)
            val state = orchestrator.currentState()
            runOnUiThread {
                updateStatus("State: ${state.name}")
                syncRuntimeService(state)
                syncWakeWordEngine(state)
                appendLog("Event: ${event::class.simpleName}")
            }
        }
    }

    private fun buildOrchestrator(): PipelineOrchestrator {
        val eventBus = InMemoryEventBus()
        val installedAppLauncher = InstalledAppLauncher(this)
        val memoryStore = MarkdownFileMemoryStore()
        val backendMode = BuildConfig.JARVIS_LLM_BACKEND.lowercase()

        val localProvider: LLMProvider? = when (backendMode) {
            "cloud-only", "gemini" -> null
            else -> LiteRtNativeLLMProvider(modelManager = modelManager, logger = logger)
        }

        val cloudProvider: LLMProvider? = when (backendMode) {
            "local-only", "litert", "mlkit" -> null
            else -> {
                if (BuildConfig.JARVIS_GEMINI_API_KEY.isBlank()) {
                    logger.warn("llm", "Gemini fallback is disabled because API key is missing")
                    null
                } else {
                    GeminiCloudLLMProvider(apiKey = BuildConfig.JARVIS_GEMINI_API_KEY)
                }
            }
        }
        val llmRouter = LocalFirstLLMRouter(
            localProvider = localProvider,
            cloudProvider = cloudProvider,
            logger = logger
        )
        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(installedAppLauncher::launch))
        }

        val outputChannel = UiOutputChannel(
            onState = { state -> runOnUiThread { appendLog("Output state: $state") } },
            onSpeak = { text -> runOnUiThread { speakWithAndroidTextToSpeech(text) } }
        )

        return PipelineOrchestrator(
            eventBus = eventBus,
            intentRouter = RuleBasedIntentRouter(),
            planner = SimplePlanner(),
            executionEngine = SkillExecutionEngine(skillRegistry),
            outputChannel = outputChannel,
            logger = logger,
            memoryStore = memoryStore,
            llmRouter = llmRouter,
            allowCloudFallback = cloudProvider != null
        )
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    private fun appendLog(line: String) {
        logText.text = logText.text.toString() + "\n" + line
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                isTextToSpeechReady = false
                appendLog("TTS initialization failed")
                logger.warn("tts", "Initialization failed", mapOf("status" to status))
                return@TextToSpeech
            }

            val languageResult = textToSpeech.setLanguage(Locale.getDefault())
            isTextToSpeechReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                languageResult != TextToSpeech.LANG_NOT_SUPPORTED

            if (isTextToSpeechReady) {
                logger.info("tts", "Initialized", mapOf("locale" to Locale.getDefault().toLanguageTag()))
            } else {
                appendLog("TTS language not supported on this device")
                logger.warn("tts", "Language not supported", mapOf("locale" to Locale.getDefault().toLanguageTag()))
            }
        }
    }

    private fun speakWithAndroidTextToSpeech(text: String) {
        appendLog("Jarvis: $text")

        if (!::textToSpeech.isInitialized || !isTextToSpeechReady) {
            appendLog("TTS unavailable")
            return
        }

        textToSpeech.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "jarvis-${System.currentTimeMillis()}"
        )
    }

    private fun setupModelControls() {
        modelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf<String>()
        )
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = modelAdapter

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = knownModels.getOrNull(position) ?: return
                modelViewModel.selectModel(selected.id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.refreshModelsButton).setOnClickListener {
            modelViewModel.refreshModelList()
        }

        findViewById<Button>(R.id.downloadModelButton).setOnClickListener {
            val selected = knownModels.getOrNull(modelSpinner.selectedItemPosition)
            if (selected == null) {
                appendLog("No model selected")
                return@setOnClickListener
            }
            modelViewModel.downloadModel(selected.id)
        }

        findViewById<Button>(R.id.deleteModelButton).setOnClickListener {
            val selected = knownModels.getOrNull(modelSpinner.selectedItemPosition)
            if (selected == null) {
                appendLog("No model selected")
                return@setOnClickListener
            }
            modelViewModel.deleteModel(selected.id)
        }
    }

    private fun observeModelState() {
        modelViewModel.uiState
            .onEach { state ->
                updateModelUI(state)
            }
            .launchIn(lifecycleScope)
    }

    private fun updateModelUI(state: ModelStatusUiState) {
        // Update model list
        knownModels = state.models
        val labels = state.models.map { model ->
            val status = when (model.status) {
                OnDeviceModelStatus.DOWNLOADING -> "↓ Downloading"
                OnDeviceModelStatus.AVAILABLE -> "✓ Available"
                OnDeviceModelStatus.DOWNLOADABLE -> "⬇ Ready to download"
                OnDeviceModelStatus.UNAVAILABLE -> "✗ Unavailable"
                OnDeviceModelStatus.UNKNOWN -> "? Unknown - tap Download"
            }
            "${model.title} [$status]"
        }
        modelAdapter.clear()
        modelAdapter.addAll(labels)
        modelAdapter.notifyDataSetChanged()

        // Update installed models count
        val installed = state.models.filter { it.status == OnDeviceModelStatus.AVAILABLE }
        installedModelsText.text = if (installed.isEmpty()) {
            "Installed: none"
        } else {
            "Installed: " + installed.joinToString { it.title }
        }

        // Update active model selection and status
        if (state.activeModelId != null) {
            val activeIndex = state.models.indexOfFirst { it.id == state.activeModelId }
            if (activeIndex >= 0) {
                modelSpinner.setSelection(activeIndex)
                val activeModel = state.models[activeIndex]
                renderModelStatus(activeModel)
            }
        }

        // Update download progress
        if (state.downloadingModelId != null) {
            downloadProgressBar.visibility = View.VISIBLE
            downloadProgressText.visibility = View.VISIBLE
            downloadProgressBar.progress = state.downloadProgress
            downloadProgressText.text = "Downloading... ${state.downloadProgress}%"
            appendLog("Downloading model: ${state.downloadProgress}%")
        } else {
            downloadProgressBar.visibility = View.GONE
            downloadProgressText.visibility = View.GONE
        }

        // Update error message
        if (state.errorMessage != null) {
            modelErrorText.visibility = View.VISIBLE
            modelErrorText.text = state.errorMessage
            appendLog("Model error: ${state.errorMessage}")
        } else {
            modelErrorText.visibility = View.GONE
        }

        // Show loading state
        if (state.isLoading) {
            appendLog("Loading models...")
        }
    }

    private fun refreshModelList() {
        modelViewModel.refreshModelList()
    }

    private fun renderModelStatus(model: OnDeviceModel) {
        modelStatusText.text = "Selected: ${model.title}"
        
        val statusLabel = when (model.status) {
            OnDeviceModelStatus.AVAILABLE -> "✓ Available (ready to use)"
            OnDeviceModelStatus.DOWNLOADING -> "↓ Downloading..."
            OnDeviceModelStatus.DOWNLOADABLE -> "⬇ Ready to download"
            OnDeviceModelStatus.UNAVAILABLE -> "✗ Unavailable (not supported)"
            OnDeviceModelStatus.UNKNOWN -> "? Status probe failed; tap Download to attempt install"
        }
        
        modelStatusDetailText.text = "Status: $statusLabel | Source: ${model.source.name.lowercase()}"
    }

    private fun requestMicAndStartListening() {
        when (VoiceInputPolicy.onVoiceButtonTapped(hasMicPermission())) {
            VoiceInputAction.START_LISTENING -> startVoiceCapture()
            VoiceInputAction.REQUEST_PERMISSION -> requestMicrophonePermission(MicPermissionReason.VOICE_CAPTURE)
            VoiceInputAction.PERMISSION_DENIED -> Unit
        }
    }

    private fun requestMicrophonePermission(reason: MicPermissionReason) {
        pendingMicPermissionReason = reason
        microphonePermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startVoiceCapture() {
        appendLog("Listening for voice input...")
        speechToText.startListening()
    }

    private fun startWakeWordEngine() {
        if (!hasMicPermission()) {
            requestMicrophonePermission(MicPermissionReason.WAKE_WORD)
            return
        }
        wakeWordEngine.start(keyword = "jarvis")
        appendLog("Wake-word engine active (keyword: jarvis)")
    }

    private fun syncWakeWordEngine(state: JarvisState) {
        when (WakeWordPolicy.decide(lastWakeWordState, state)) {
            WakeWordAction.NONE -> Unit
            WakeWordAction.STOP -> {
                lastWakeWordState = state
                wakeWordEngine.stop()
                logger.info("wakeword", "Wake-word lifecycle stopped", mapOf("state" to state.name))
            }

            WakeWordAction.START -> {
                lastWakeWordState = state
                startWakeWordEngine()
                logger.info("wakeword", "Wake-word lifecycle started", mapOf("state" to state.name))
            }
        }
    }

    private fun syncRuntimeService(state: JarvisState) {
        when (RuntimeServicePolicy.decide(lastRuntimeServiceState, state)) {
            RuntimeServiceAction.NONE -> Unit
            RuntimeServiceAction.STOP -> {
                lastRuntimeServiceState = state
                JarvisRuntimeService.stop(this)
                appendLog("Runtime service stopped")
                logger.info("runtime", "Foreground runtime stopped", mapOf("state" to state.name))
            }

            RuntimeServiceAction.START -> {
                lastRuntimeServiceState = state
                JarvisRuntimeService.start(this, state.name)
                appendLog("Runtime service running in ${state.name}")
                logger.info("runtime", "Foreground runtime started", mapOf("state" to state.name))
            }
        }
    }
}

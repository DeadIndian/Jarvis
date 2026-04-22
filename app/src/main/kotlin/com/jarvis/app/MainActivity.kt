package com.jarvis.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import com.jarvis.intent.LLMIntentRouter
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.llm.LocalFirstLLMRouter
import com.jarvis.llm.LLMProvider
import com.jarvis.llm.ToolCallingMediaPipeProvider
import com.jarvis.memory.MarkdownFileMemoryStore
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.InMemorySkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private enum class MicPermissionReason {
        VOICE_CAPTURE,
        WAKE_WORD
    }

    private lateinit var statusText: TextView
    private lateinit var modelReadyText: TextView
    private lateinit var logText: TextView
    private lateinit var inputText: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var modelStatusText: TextView
    private lateinit var modelStatusDetailText: TextView
    private lateinit var installedModelsText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadProgressText: TextView
    private lateinit var modelErrorText: TextView
    private lateinit var modelManagementSection: View
    private var orchestrator: PipelineOrchestrator? = null
    private val eventBus = InMemoryEventBus()
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
    private var activeToolCallingProvider: ToolCallingMediaPipeProvider? = null
    private var knownModels: List<OnDeviceModel> = emptyList()

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (micGranted) {
            appendLog("Microphone permission granted")
            when (pendingMicPermissionReason) {
                MicPermissionReason.WAKE_WORD -> startWakeWordEngine()
                MicPermissionReason.VOICE_CAPTURE -> startVoiceCapture()
                null -> Unit
            }
        }
        pendingMicPermissionReason = null
        
        if (notificationGranted) {
            appendLog("Notification permission granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        modelReadyText = findViewById(R.id.modelReadyText)
        logText = findViewById(R.id.logText)
        inputText = findViewById(R.id.inputText)
        modelSpinner = findViewById(R.id.modelSpinner)
        modelStatusText = findViewById(R.id.modelStatusText)
        modelStatusDetailText = findViewById(R.id.modelStatusDetailText)
        installedModelsText = findViewById(R.id.installedModelsText)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        downloadProgressText = findViewById(R.id.downloadProgressText)
        modelErrorText = findViewById(R.id.modelErrorText)
        modelManagementSection = findViewById(R.id.modelManagementSection)

        modelManager = LiteRtOnDeviceModelManager(
            context = applicationContext,
            logger = logger,
            modelDirectoryPath = BuildConfig.JARVIS_LITERT_MODEL_DIR,
            huggingFaceToken = BuildConfig.JARVIS_HF_TOKEN
        )
        initializeTextToSpeech()
        modelViewModel = ViewModelProvider(this, object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ModelStatusViewModel(modelManager, logger) as T
            }
        }).get(ModelStatusViewModel::class.java)

        setupModelControls()
        observeModelState()

        eventBus.subscribe { event ->
            if (event is Event.StateChanged) {
                runOnUiThread {
                    updateStatus("State: ${event.state.name}")
                    syncRuntimeService(event.state)
                    syncWakeWordEngine(event.state)
                }
            }
        }

        lifecycleScope.launch {
            orchestrator = buildOrchestrator(warmup = false)
            orchestrator?.let {
                updateStatus("State: ${it.currentState().name}")
                syncRuntimeService(it.currentState())
                syncWakeWordEngine(it.currentState())
            }
        }
        
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

        findViewById<Button>(R.id.voiceButton).setOnClickListener {
            requestMicAndStartListening()
        }

        findViewById<Button>(R.id.powerButton).setOnClickListener {
            dispatch(Event.PowerButtonHeld)
        }

        findViewById<Button>(R.id.wakeWordButton).setOnClickListener {
            dispatch(Event.WakeWordDetected("jarvis"))
        }

        findViewById<Button>(R.id.timeoutButton).setOnClickListener {
            dispatch(Event.TimeoutElapsed)
        }

        findViewById<Button>(R.id.useModelButton).setOnClickListener {
            requestBackgroundPermissions()
            lifecycleScope.launch {
                updateModelReadyStatus("Initializing...", isReady = false)
                val orch = buildOrchestrator(warmup = true)
                orchestrator = orch
                val isReady = activeToolCallingProvider != null
                val message = if (isReady) "Model Ready (LiteRT)" else "Initialization Failed"
                updateModelReadyStatus(message, isReady)
                if (isReady) {
                    appendLog("Model warmed up and ready in background service")
                    syncRuntimeService(orch.currentState())
                }
            }
        }

        findViewById<Button>(R.id.manageModelsButton).setOnClickListener {
            modelManagementSection.visibility = if (modelManagementSection.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        refreshModelList()
        appendLog("Jarvis MVP ready")
    }

    override fun onDestroy() {
        activeToolCallingProvider?.close()
        wakeWordEngine.release()
        speechToText.release()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    private fun dispatch(event: Event) {
        val orch = orchestrator ?: run {
            appendLog("Wait: Jarvis is initializing...")
            return
        }
        lifecycleScope.launch(Dispatchers.Default) {
            orch.dispatch(event)
            runOnUiThread {
                appendLog("Event: ${event::class.simpleName}")
            }
        }
    }

    private suspend fun buildOrchestrator(warmup: Boolean): PipelineOrchestrator = withContext(Dispatchers.IO) {
        val installedAppLauncher = InstalledAppLauncher(this@MainActivity)
        val memoryStore = MarkdownFileMemoryStore()

        activeToolCallingProvider?.close()
        activeToolCallingProvider = null

        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(installedAppLauncher::launch))
        }

        val modelId = modelManager.getActiveModelId()
        val modelFile = File(applicationContext.filesDir, "llm/" + resolveModelFileName(modelId))
        
        val localProvider: LLMProvider? = if (modelFile.exists()) {
            val provider = ToolCallingMediaPipeProvider(
                context = this@MainActivity,
                modelPath = modelFile.absolutePath,
                logger = logger
            )
            if (warmup) {
                val success = provider.warmup()
                if (success) provider.also { activeToolCallingProvider = it } else null
            } else {
                provider.also { activeToolCallingProvider = it }
            }
        } else {
            logger.warn("llm", "Active model file missing", mapOf("path" to modelFile.absolutePath))
            null
        }

        val llmRouter = LocalFirstLLMRouter(
            localProvider = localProvider,
            cloudProvider = null,
            logger = logger
        )

        val outputChannel = UiOutputChannel(
            onState = { state -> runOnUiThread { appendLog("Output state: $state") } },
            onSpeak = { text -> runOnUiThread { speakWithAndroidTextToSpeech(text) } }
        )

        val intentRouter = if (activeToolCallingProvider != null) {
            LLMIntentRouter(
                toolCallingProvider = activeToolCallingProvider!!,
                skillRegistry = skillRegistry,
                fallbackRouter = RuleBasedIntentRouter(),
                logger = logger
            )
        } else {
            RuleBasedIntentRouter()
        }

        PipelineOrchestrator(
            eventBus = eventBus, // Use class-level eventBus
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
            "gemma-4-e2b-it" -> "gemma-4-E2B-it.litertlm"
            "gemma-4-e4b-it" -> "gemma-4-E4B-it.litertlm"
            else -> "$modelId.litertlm"
        }
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    private fun updateModelReadyStatus(text: String, isReady: Boolean) {
        runOnUiThread {
            modelReadyText.text = "Model: $text"
            modelReadyText.setTextColor(if (isReady) 0xFF4CAF50.toInt() else 0xFFf44336.toInt())
        }
    }

    private fun appendLog(line: String) {
        logText.text = logText.text.toString() + "\n" + line
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault())
                isTextToSpeechReady = true
            }
        }
    }

    private fun speakWithAndroidTextToSpeech(text: String) {
        appendLog("Jarvis: $text")
        if (isTextToSpeechReady) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-${System.currentTimeMillis()}")
        }
    }

    private fun setupModelControls() {
        modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = modelAdapter

        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = knownModels.getOrNull(position) ?: return
                modelViewModel.selectModel(selected.id)
                updateModelReadyStatus("Model changed - click Use to initialize", isReady = false)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.refreshModelsButton).setOnClickListener { modelViewModel.refreshModelList() }
        findViewById<Button>(R.id.downloadModelButton).setOnClickListener {
            knownModels.getOrNull(modelSpinner.selectedItemPosition)?.id?.let { modelViewModel.downloadModel(it) }
        }
        findViewById<Button>(R.id.deleteModelButton).setOnClickListener {
            knownModels.getOrNull(modelSpinner.selectedItemPosition)?.id?.let { modelViewModel.deleteModel(it) }
        }
    }

    private fun observeModelState() {
        modelViewModel.uiState.onEach { updateModelUI(it) }.launchIn(lifecycleScope)
    }

    private fun updateModelUI(state: ModelStatusUiState) {
        knownModels = state.models
        val labels = state.models.map { model ->
            val status = when (model.status) {
                OnDeviceModelStatus.DOWNLOADING -> "↓ Downloading"
                OnDeviceModelStatus.AVAILABLE -> "✓ Available"
                OnDeviceModelStatus.DOWNLOADABLE -> "⬇ Ready"
                else -> "✗ Unsupported"
            }
            "${model.title} [$status]"
        }
        modelAdapter.clear()
        modelAdapter.addAll(labels)
        
        if (state.activeModelId != null) {
            val idx = state.models.indexOfFirst { it.id == state.activeModelId }
            if (idx >= 0) modelSpinner.setSelection(idx)
        }

        if (state.downloadingModelId != null) {
            downloadProgressBar.visibility = View.VISIBLE
            downloadProgressText.visibility = View.VISIBLE
            downloadProgressBar.progress = state.downloadProgress
            downloadProgressText.text = "${state.downloadProgress}%"
        } else {
            downloadProgressBar.visibility = View.GONE
            downloadProgressText.visibility = View.GONE
        }
    }

    private fun refreshModelList() { modelViewModel.refreshModelList() }

    private fun requestMicAndStartListening() {
        if (hasMicPermission()) startVoiceCapture() else requestBackgroundPermissions(MicPermissionReason.VOICE_CAPTURE)
    }

    private fun requestBackgroundPermissions(reason: MicPermissionReason? = null) {
        pendingMicPermissionReason = reason
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
        appendLog("Listening...")
        speechToText.startListening()
    }

    private fun startWakeWordEngine() {
        if (hasMicPermission()) wakeWordEngine.start(keyword = "jarvis") else requestBackgroundPermissions(MicPermissionReason.WAKE_WORD)
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
        // Service is only started when "Use Model" is clicked to ensure background persistence
        if (activeToolCallingProvider != null) {
            JarvisRuntimeService.start(this, state.name)
        }
        lastRuntimeServiceState = state
    }
}

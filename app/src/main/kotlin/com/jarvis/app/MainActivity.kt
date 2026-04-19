package com.jarvis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jarvis.core.Event
import com.jarvis.core.InMemoryEventBus
import com.jarvis.core.JarvisState
import com.jarvis.execution.PipelineOrchestrator
import com.jarvis.execution.SkillExecutionEngine
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.llm.LocalFirstLLMRouter
import com.jarvis.memory.MarkdownFileMemoryStore
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.InMemorySkillRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var inputText: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var modelStatusText: TextView
    private lateinit var installedModelsText: TextView
    private lateinit var orchestrator: PipelineOrchestrator
    private lateinit var speechToText: AndroidSpeechToText
    private lateinit var modelManager: OnDeviceModelManager
    private lateinit var modelAdapter: ArrayAdapter<String>
    private val logger = AndroidLogJarvisLogger()
    private var lastRuntimeServiceState: JarvisState? = null
    private var knownModels: List<OnDeviceModel> = emptyList()

    private val microphonePermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        when (VoiceInputPolicy.onPermissionResult(granted)) {
            VoiceInputAction.START_LISTENING -> {
                appendLog("Microphone permission granted")
                startVoiceCapture()
            }

            VoiceInputAction.PERMISSION_DENIED -> {
                appendLog("Microphone permission denied. Voice input unavailable.")
            }

            VoiceInputAction.REQUEST_PERMISSION -> Unit
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
        installedModelsText = findViewById(R.id.installedModelsText)

        modelManager = MlKitNativeModelManager(applicationContext, logger)
        setupModelControls()

        orchestrator = buildOrchestrator()
        speechToText = AndroidSpeechToText(
            context = this,
            logger = logger,
            onPartialResult = { partial -> runOnUiThread { inputText.setText(partial) } },
            onFinalResult = { finalText -> dispatch(Event.VoiceInput(finalText)) },
            onError = { message -> runOnUiThread { appendLog("STT error: $message") } }
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
        refreshModelList()
        appendLog("Jarvis MVP initialized")
    }

    override fun onDestroy() {
        speechToText.release()
        super.onDestroy()
    }

    private fun dispatch(event: Event) {
        lifecycleScope.launch(Dispatchers.Default) {
            orchestrator.dispatch(event)
            val state = orchestrator.currentState()
            runOnUiThread {
                updateStatus("State: ${state.name}")
                syncRuntimeService(state)
                appendLog("Event: ${event::class.simpleName}")
            }
        }
    }

    private fun buildOrchestrator(): PipelineOrchestrator {
        val eventBus = InMemoryEventBus()
        val installedAppLauncher = InstalledAppLauncher(this)
        val memoryStore = MarkdownFileMemoryStore()
        val llmRouter = LocalFirstLLMRouter(
            localProvider = MlKitNativeLLMProvider(modelManager = modelManager, logger = logger),
            cloudProvider = null,
            logger = logger
        )
        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(installedAppLauncher::launch))
        }

        val outputChannel = UiOutputChannel(
            onState = { state -> runOnUiThread { appendLog("Output state: $state") } },
            onSpeak = { text -> runOnUiThread { appendLog("Jarvis: $text") } }
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
            allowCloudFallback = false
        )
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    private fun appendLog(line: String) {
        logText.text = logText.text.toString() + "\n" + line
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
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { modelManager.setActiveModel(selected.id) }
                        .onSuccess {
                            runOnUiThread {
                                renderModelStatus(selected)
                                appendLog("Active model: ${selected.title}")
                            }
                        }
                        .onFailure { error ->
                            runOnUiThread {
                                appendLog("Model select failed: ${error.message}")
                            }
                        }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.refreshModelsButton).setOnClickListener {
            refreshModelList()
        }

        findViewById<Button>(R.id.downloadModelButton).setOnClickListener {
            val selected = knownModels.getOrNull(modelSpinner.selectedItemPosition)
            if (selected == null) {
                appendLog("No model selected")
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { modelManager.downloadModel(selected.id) }
                    .onSuccess {
                        runOnUiThread {
                            appendLog("Downloaded model: ${selected.title}")
                            refreshModelList()
                        }
                    }
                    .onFailure { error ->
                        runOnUiThread {
                            appendLog("Model download failed: ${error.message}")
                        }
                    }
            }
        }

        findViewById<Button>(R.id.deleteModelButton).setOnClickListener {
            val selected = knownModels.getOrNull(modelSpinner.selectedItemPosition)
            if (selected == null) {
                appendLog("No model selected")
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { modelManager.deleteModel(selected.id) }
                    .onSuccess {
                        runOnUiThread {
                            appendLog("Cleared model cache: ${selected.title}")
                            refreshModelList()
                        }
                    }
                    .onFailure { error ->
                        runOnUiThread {
                            appendLog("Model delete failed: ${error.message}")
                        }
                    }
            }
        }
    }

    private fun refreshModelList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val models = runCatching { modelManager.listModels() }
                .getOrElse {
                    runOnUiThread { appendLog("Model list failed: ${it.message}") }
                    return@launch
                }
            val activeModelId = runCatching { modelManager.getActiveModelId() }
                .getOrElse {
                    runOnUiThread { appendLog("Active model resolve failed: ${it.message}") }
                    return@launch
                }

            runOnUiThread {
                knownModels = models
                val labels = models.map { model ->
                    "${model.title} [${model.source.name.lowercase()} | ${model.status.name.lowercase()}]"
                }
                modelAdapter.clear()
                modelAdapter.addAll(labels)
                modelAdapter.notifyDataSetChanged()

                val installed = models.filter { it.status == OnDeviceModelStatus.AVAILABLE }
                installedModelsText.text = if (installed.isEmpty()) {
                    "Installed: none"
                } else {
                    "Installed: " + installed.joinToString { it.title }
                }

                val activeIndex = models.indexOfFirst { it.id == activeModelId }
                if (activeIndex >= 0) {
                    modelSpinner.setSelection(activeIndex)
                    renderModelStatus(models[activeIndex])
                } else {
                    modelStatusText.text = "No model selected"
                }
            }
        }
    }

    private fun renderModelStatus(model: OnDeviceModel) {
        modelStatusText.text =
            "Selected: ${model.title} | Source: ${model.source.name.lowercase()} | Status: ${model.status.name.lowercase()}"
    }

    private fun requestMicAndStartListening() {
        when (VoiceInputPolicy.onVoiceButtonTapped(hasMicPermission())) {
            VoiceInputAction.START_LISTENING -> startVoiceCapture()
            VoiceInputAction.REQUEST_PERMISSION -> microphonePermissionRequest.launch(Manifest.permission.RECORD_AUDIO)
            VoiceInputAction.PERMISSION_DENIED -> Unit
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startVoiceCapture() {
        appendLog("Listening for voice input...")
        speechToText.startListening()
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

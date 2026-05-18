package com.jarvis.app

import android.content.Context
import com.jarvis.core.Event
import com.jarvis.core.EventBus
import com.jarvis.core.InMemoryEventBus
import com.jarvis.core.JarvisState
import com.jarvis.execution.PipelineOrchestrator
import com.jarvis.execution.SkillExecutionEngine
import com.jarvis.execution.HttpRemoteAgentClient
import com.jarvis.execution.RemoteAgentClient
import com.jarvis.intent.LLMIntentRouter
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.llm.LLMProvider
import com.jarvis.logging.JarvisLogger
import com.jarvis.memory.BrainMemoryStore
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.CurrentTimeSkill
import com.jarvis.skills.InMemorySkillRegistry
import com.jarvis.skills.RememberSkill
import com.jarvis.skills.SystemControlSkill
import com.jarvis.app.GeminiCloudLLMProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object JarvisEngine {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var initialized = false
    private val folderSetupChecked = AtomicBoolean(false)

    lateinit var eventBus: EventBus
        private set
    lateinit var orchestrator: PipelineOrchestrator
        private set
    lateinit var llmProviderManager: LLMProviderManager
        private set
    lateinit var modelManager: OnDeviceModelManager
        private set
    lateinit var llmSettingsRepository: LlmSettingsRepository
        private set
    lateinit var memorySettingsRepository: MemorySettingsRepository
        private set
    lateinit var logger: JarvisLogger
        private set
    lateinit var folderSetupManager: FolderSetupManager
        private set
    
    lateinit var wakeWordEngine: OpenWakeWordEngine
        private set
    lateinit var speechToText: AndroidSpeechToText
        private set

    private val _stateFlow = MutableStateFlow(JarvisState.BARN_DOOR)
    val stateFlow: StateFlow<JarvisState> = _stateFlow.asStateFlow()

    private val _inputTextFlow = MutableStateFlow("")
    val inputTextFlow: StateFlow<String> = _inputTextFlow.asStateFlow()

    private val _folderSetupResult = MutableStateFlow<FolderSetupResult?>(null)
    val folderSetupResult: StateFlow<FolderSetupResult?> = _folderSetupResult.asStateFlow()

    fun init(context: Context, jarvisLogger: JarvisLogger) {
        if (initialized) return
        val appContext = context.applicationContext
        logger = jarvisLogger
        eventBus = InMemoryEventBus()
        
        llmSettingsRepository = EncryptedLlmSettingsRepository(appContext, logger)
        memorySettingsRepository = EncryptedMemorySettingsRepository(appContext)
        llmProviderManager = LLMProviderManager(logger)
        modelManager = LiteRtOnDeviceModelManager(
            context = appContext,
            logger = logger,
            modelDirectoryPath = BuildConfig.JARVIS_LITERT_MODEL_DIR,
            huggingFaceToken = BuildConfig.JARVIS_HF_TOKEN
        )
        folderSetupManager = FolderSetupManager(appContext, logger)

        eventBus.subscribe { event ->
            if (event is Event.StateChanged) {
                _stateFlow.value = event.state
            }
        }
        
        initialized = true
    }

    suspend fun checkFolderSetup(context: Context): FolderSetupResult {
        val notesBasePath = getNotesBasePath(context)
        return folderSetupManager.checkFolderSetup(notesBasePath)
    }

    suspend fun ensureFolderSetup(context: Context): Boolean {
        val notesBasePath = getNotesBasePath(context)
        val result = folderSetupManager.checkFolderSetup(notesBasePath)
        
        if (result.needsSetup || result.hasMalformedFiles) {
            _folderSetupResult.value = result
            return false
        }
        
        return true
    }

    fun getNotesBasePath(context: Context): String {
        val savedPath = memorySettingsRepository.getNotesFolderPath()
        val defaultPath = File(context.filesDir, EncryptedMemorySettingsRepository.DEFAULT_NOTES_FOLDER).absolutePath
        
        if (savedPath == null || savedPath.isBlank()) {
            return defaultPath
        }

        if (savedPath.startsWith("content://")) {
            logger.warn("JarvisEngine", "Unsupported content:// URI in memory settings: $savedPath. Falling back to default internal storage.")
            return defaultPath
        }

        val file = File(savedPath)
        return if (file.isAbsolute) {
            savedPath
        } else {
            // Resolve relative paths against filesDir
            File(context.filesDir, savedPath).absolutePath
        }
    }

    fun folderSetupHandled() {
        folderSetupChecked.set(true)
        _folderSetupResult.value = null
    }

    fun initSttCallbacks(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (this::speechToText.isInitialized) {
            speechToText.release()
        }
        // Need context from somewhere or re-init in MainActivity
        // For now, let's keep speechToText creation in init if we can pass a context
    }

    fun setSpeechToText(stt: AndroidSpeechToText) {
        this.speechToText = stt
    }

    fun setWakeWordEngine(engine: OpenWakeWordEngine) {
        this.wakeWordEngine = engine
    }

    fun buildOrchestrator(context: Context, outputChannel: com.jarvis.output.OutputChannel) {
        val appContext = context.applicationContext
        val installedAppLauncher = InstalledAppLauncher(appContext)
        val systemControlManager = SystemControlManager(appContext)
        
        val notesBasePath = getNotesBasePath(appContext)

        val memoryStore = BrainMemoryStore.withCustomPath(appContext, notesBasePath)

        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(installedAppLauncher::launch))
            register(CurrentTimeSkill())
            register(SystemControlSkill(systemControlManager::execute))
            register(RememberSkill(memoryStore))
        }

        val backendMode = llmSettingsRepository.getBackendMode()
        val geminiApiKey = llmSettingsRepository.getGeminiApiKey()
        
        val availableProviders = mutableListOf<LLMProvider>()
        geminiApiKey?.let { availableProviders.add(GeminiCloudLLMProvider(apiKey = it, logger = logger)) }
        
        llmProviderManager.registerProviders(*availableProviders.toTypedArray())

        val intentRouter = LLMIntentRouter(
            fallbackRouter = RuleBasedIntentRouter(),
            logger = logger,
            onClassified = { } 
        )

        orchestrator = PipelineOrchestrator(
            eventBus = eventBus,
            intentRouter = intentRouter,
            planner = SimplePlanner(),
            executionEngine = SkillExecutionEngine(skillRegistry),
            outputChannel = outputChannel,
            logger = logger,
            memoryStore = memoryStore,
            llmRouter = llmProviderManager,
            remoteAgentClient = null,
            allowCloudFallback = backendMode == LlmBackendMode.HYBRID || backendMode == LlmBackendMode.GEMINI_CLOUD,
            localMemoryWritesEnabled = true
        )
    }
}

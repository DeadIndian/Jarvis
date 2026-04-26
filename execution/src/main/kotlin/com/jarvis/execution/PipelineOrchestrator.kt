package com.jarvis.execution

import com.jarvis.core.Event
import com.jarvis.core.EventBus
import com.jarvis.core.JarvisState
import com.jarvis.core.Orchestrator
import com.jarvis.intent.IntentRouter
import com.jarvis.intent.IntentResult
import com.jarvis.llm.LLMRouter
import com.jarvis.llm.LLMRequest
import com.jarvis.logging.JarvisLogger
import com.jarvis.memory.MemoryStore
import com.jarvis.memory.models.MemoryChunk
import com.jarvis.output.OutputChannel
import com.jarvis.planner.Planner
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PipelineOrchestrator(
    private val eventBus: EventBus,
    private val intentRouter: IntentRouter,
    private val planner: Planner,
    private val executionEngine: ExecutionEngine,
    private val outputChannel: OutputChannel,
    private val logger: JarvisLogger,
    private val memoryStore: MemoryStore? = null,
    private val llmRouter: LLMRouter? = null,
    private val remoteAgentClient: RemoteAgentClient? = null,
    private val allowCloudFallback: Boolean = false,
    private val retrievalLimit: Int = 3,
    private val localMemoryWritesEnabled: Boolean = true
) : Orchestrator {
    private var state: JarvisState = JarvisState.IDLE
    private val registeredSkills = mutableSetOf<String>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val allowedTransitions = mapOf(
        JarvisState.IDLE to setOf(JarvisState.BARN_DOOR, JarvisState.HOUSE_PARTY, JarvisState.ACTIVE, JarvisState.THINKING),
        JarvisState.BARN_DOOR to setOf(JarvisState.ACTIVE, JarvisState.IDLE, JarvisState.HOUSE_PARTY, JarvisState.THINKING),
        JarvisState.ACTIVE to setOf(JarvisState.IDLE, JarvisState.HOUSE_PARTY, JarvisState.THINKING),
        JarvisState.THINKING to setOf(JarvisState.SPEAKING, JarvisState.IDLE, JarvisState.ACTIVE),
        JarvisState.SPEAKING to setOf(JarvisState.IDLE, JarvisState.ACTIVE, JarvisState.THINKING),
        JarvisState.HOUSE_PARTY to setOf(JarvisState.ACTIVE, JarvisState.IDLE, JarvisState.THINKING)
    )

    fun currentState(): JarvisState = state

    override fun dispatch(event: Event) {
        when (event) {
            is Event.VoiceInput -> scope.launch { processInput(event.text, isVoiceInput = true) }
            is Event.TextInput -> scope.launch { processInput(event.text, isVoiceInput = false) }
            is Event.WakeWordDetected -> {
                if (state == JarvisState.HOUSE_PARTY || state == JarvisState.IDLE) {
                    transitionState(JarvisState.ACTIVE)
                } else {
                    logger.warn(
                        "state",
                        "Wake word ignored",
                        mapOf("currentState" to state.name)
                    )
                }
            }

            Event.PowerButtonHeld -> transitionState(JarvisState.BARN_DOOR)
            Event.TimeoutElapsed -> transitionState(JarvisState.IDLE)
            is Event.HousePartyToggle -> {
                if (event.enabled) {
                    transitionState(JarvisState.HOUSE_PARTY)
                } else {
                    transitionState(JarvisState.IDLE)
                }
            }

            is Event.SkillResult -> {
                eventBus.publish(event)
                logger.info("execution", "Skill result event received", mapOf("skill" to event.skill))
            }

            is Event.Error -> {
                eventBus.publish(event)
                logger.error("orchestrator", event.message, event.cause)
            }
            is Event.StateChanged -> Unit
        }
    }

    override fun transitionState(state: JarvisState) {
        val current = this.state
        if (current == state) {
            return
        }
        if (!isTransitionAllowed(current, state)) {
            logger.warn(
                "state",
                "Ignored invalid state transition",
                mapOf("from" to current.name, "to" to state.name)
            )
            return
        }
        this.state = state
        logger.info("state", "State changed", mapOf("state" to state.name))
        eventBus.publish(Event.StateChanged(state))
    }

    override fun registerSkill(skillName: String) {
        registeredSkills += skillName
        logger.info("skills", "Skill registered", mapOf("skill" to skillName))
    }

    private suspend fun processInput(text: String, isVoiceInput: Boolean) {
        val requestId = UUID.randomUUID().toString()
        var shouldKeepVoiceSessionLive = isVoiceInput
        var shouldSpeakResponse = true
        
        transitionState(JarvisState.THINKING)

        logger.info(
            "pipeline",
            "Input processing started",
            mapOf("requestId" to requestId, "textLength" to text.length, "state" to state.name)
        )
        
        try {
            outputChannel.showThinking()
            val memoryContext = retrieveMemoryContext(text, requestId)

            val intentResult = intentRouter.parse(text)
            val executionMode = decideExecutionMode(intentResult, text)
            logger.info(
                "pipeline",
                "Execution mode chosen",
                mapOf("requestId" to requestId, "mode" to executionMode.javaClass.simpleName)
            )

            val spokenText = when (executionMode) {
                ExecutionMode.LOCAL_FAST -> processLocalFast(text, intentResult, memoryContext, requestId)
                ExecutionMode.SERVER_AGENT -> processServerAgent(
                    text = text,
                    intentResult = intentResult,
                    memoryContext = memoryContext,
                    requestId = requestId
                )
            }

            if (isSilentActionIntent(intentResult.intent)) {
                shouldSpeakResponse = false
                shouldKeepVoiceSessionLive = false
            }

            if (shouldSpeakResponse) {
                transitionState(JarvisState.SPEAKING)
                outputChannel.showSpeaking()
                outputChannel.speak(spokenText)
            } else {
                transitionState(JarvisState.IDLE)
            }

            if (executionMode == ExecutionMode.LOCAL_FAST && localMemoryWritesEnabled) {
                persistInteraction(
                    requestId = requestId,
                    input = text,
                    response = spokenText,
                    usedLlm = intentResult.intent == "UNKNOWN" || intentResult.intent == "SPEAK",
                    memoryMatches = memoryContext.size
                )
            }

            eventBus.publish(Event.SkillResult(skill = "pipeline", result = spokenText))
            logger.info(
                "pipeline",
                "Input processing completed",
                mapOf("requestId" to requestId)
            )
        } catch (e: Exception) {
            logger.error("pipeline", "Input processing failed", e)
            transitionState(JarvisState.SPEAKING)
            outputChannel.showSpeaking()
            outputChannel.speak("I encountered an error while processing your request.")
            eventBus.publish(Event.Error("Pipeline failure", e))
        } finally {
            if (isVoiceInput && shouldKeepVoiceSessionLive) {
                // Voice sessions should stay live for follow-up commands.
                scope.launch {
                    delay(300)
                    if (state == JarvisState.SPEAKING || state == JarvisState.THINKING) {
                        transitionState(JarvisState.ACTIVE)
                        outputChannel.showListening()
                    }
                }
            } else {
                // Text interactions can return to idle after a delay to clear the UI/notification.
                scope.launch {
                    delay(10000)
                    if (state == JarvisState.SPEAKING || state == JarvisState.THINKING) {
                        transitionState(JarvisState.IDLE)
                    }
                }
            }
        }
    }

    private fun isSilentActionIntent(intent: String): Boolean {
        return intent == "OPEN_APP" || intent == "SYSTEM_CONTROL"
    }

    fun decideExecutionMode(intent: IntentResult, input: String): ExecutionMode {
        val normalized = input.lowercase().trim()
        val wordCount = normalized.split(Regex("\\s+")).count { it.isNotBlank() }

        val longFreeForm = input.length > 180 || wordCount > 30
        val conversational = CONVERSATIONAL_PREFIXES.any { normalized.startsWith(it) } ||
            CONVERSATIONAL_KEYWORDS.any { normalized.contains(it) }
        val multiStep = MULTI_STEP_MARKERS.any { normalized.contains(it) }
        val singleSkillIntent = intent.intent in SINGLE_SKILL_INTENTS

        return when {
            intent.intent == "UNKNOWN" -> ExecutionMode.SERVER_AGENT
            longFreeForm -> ExecutionMode.SERVER_AGENT
            conversational -> ExecutionMode.SERVER_AGENT
            multiStep -> ExecutionMode.SERVER_AGENT
            singleSkillIntent -> ExecutionMode.LOCAL_FAST
            else -> ExecutionMode.SERVER_AGENT
        }
    }

    private suspend fun processServerAgent(
        text: String,
        intentResult: IntentResult,
        memoryContext: List<MemoryChunk>,
        requestId: String
    ): String {
        val client = remoteAgentClient
        if (client == null) {
            logger.warn("remote", "Remote client missing, using local fallback", mapOf("requestId" to requestId))
            return processLocalFast(text, intentResult, memoryContext, requestId)
        }

        val startedAt = System.nanoTime()
        val response = runCatching {
            client.process(
                RemoteRequest(
                    input = text,
                    memoryContext = memoryContext,
                    metadata = mapOf(
                        "requestId" to requestId,
                        "intent" to intentResult.intent,
                        "mode" to "SERVER_AGENT"
                    )
                )
            )
        }.getOrElse { throwable ->
            logger.warn(
                "remote",
                "Remote processing failed; triggering fallback",
                mapOf("requestId" to requestId, "error" to (throwable.message ?: "unknown"))
            )
            null
        }

        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
        logger.info(
            "remote",
            "Remote processing completed",
            mapOf("requestId" to requestId, "latencyMs" to latencyMs)
        )

        if (response != null && response.text.isNotBlank()) {
            return response.text
        }

        logger.warn(
            "pipeline",
            "Remote response empty; triggering fallback",
            mapOf("requestId" to requestId)
        )
        return processLocalFast(text, intentResult, memoryContext, requestId)
    }

    private suspend fun processLocalFast(
        text: String,
        intentResult: IntentResult,
        memoryContext: List<MemoryChunk>,
        requestId: String
    ): String {
        if (intentResult.intent == "UNKNOWN") {
            logger.info("pipeline", "No intent matched, falling back to LLM", mapOf("requestId" to requestId))
            val llmRequest = LLMRequest(
                prompt = buildPromptWithContext(text, memoryContext),
                allowCloudFallback = allowCloudFallback
            )
            val llmResponse = llmRouter?.complete(llmRequest)
            return llmResponse?.text ?: "I'm sorry, I don't know how to help with that."
        }

        val plan = planner.createPlan(intentResult.intent, intentResult.entities)
        val results = executionEngine.execute(plan)
        val successful = results.filter { it.success }
        val failed = results.filterNot { it.success }

        return when {
            intentResult.intent == "SPEAK" -> {
                intentResult.entities["text"].orEmpty().ifBlank { "I am here." }
            }
            successful.isNotEmpty() -> successful.first().output ?: "Done"
            failed.isNotEmpty() -> "I could not complete that request"
            else -> "No action was needed"
        }
    }

    private fun buildPromptWithContext(input: String, context: List<MemoryChunk>): String {
        if (context.isEmpty()) return input
        val lines = context.map { "(${it.filePath} - ${it.section})\n${it.text}" }
        return "Context:\n${lines.joinToString("\n") }\n\nUser: $input\nJarvis:"
    }

    private suspend fun retrieveMemoryContext(text: String, requestId: String): List<MemoryChunk> {
        val store = memoryStore ?: return emptyList()
        val startedAt = System.nanoTime()
        val results = runCatching {
            store.search(query = text, limit = retrievalLimit.coerceAtLeast(1))
        }.getOrElse {
            logger.warn("memory", "Memory retrieval failed", mapOf("requestId" to requestId))
            emptyList()
        }.mapIndexed { index, value ->
            parseMemoryChunk(value = value, requestId = requestId, index = index)
        }
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
        logger.info(
            "memory",
            "Memory retrieval completed",
            mapOf("requestId" to requestId, "matches" to results.size, "latencyMs" to latencyMs)
        )
        return results
    }

    private fun parseMemoryChunk(value: String, requestId: String, index: Int): MemoryChunk {
        val lines = value.lines()
        val header = lines.firstOrNull().orEmpty()
        val body = lines.drop(1).joinToString("\n").trim().ifBlank { value.trim() }

        val headerPattern = Regex("^\\((.+) - (.+)\\)$")
        val match = headerPattern.find(header.trim())
        val filePath = match?.groupValues?.getOrNull(1) ?: "memory/local"
        val section = match?.groupValues?.getOrNull(2) ?: "Context"

        return MemoryChunk(
            id = "$requestId-$index",
            text = body.take(MAX_MEMORY_CHARS),
            filePath = filePath,
            section = section,
            timestamp = Instant.now().toEpochMilli(),
            tokenCount = body.split(Regex("\\s+")).count { it.isNotBlank() },
            embedding = floatArrayOf()
        )
    }

    private suspend fun persistInteraction(
        requestId: String,
        input: String,
        response: String,
        usedLlm: Boolean,
        memoryMatches: Int
    ) {
        val store = memoryStore ?: return
        val key = "interaction-${Instant.now().toEpochMilli()}"
        val value = buildString {
            appendLine("# Interaction")
            appendLine("requestId: $requestId")
            appendLine("usedLlm: $usedLlm")
            appendLine("memoryMatches: $memoryMatches")
            appendLine("input: $input")
            appendLine("response: $response")
        }

        runCatching {
            store.put(key, value)
        }.onFailure {
            logger.warn("memory", "Failed to persist interaction", mapOf("requestId" to requestId))
        }
    }

    private fun isTransitionAllowed(from: JarvisState, to: JarvisState): Boolean {
        return allowedTransitions[from]?.contains(to) == true
    }

    companion object {
        private const val MAX_MEMORY_CHARS = 400
        private val SINGLE_SKILL_INTENTS = setOf("OPEN_APP", "SYSTEM_CONTROL", "CurrentTime")
        private val CONVERSATIONAL_PREFIXES = listOf(
            "what",
            "why",
            "how",
            "can you",
            "could you",
            "tell me",
            "summarize",
            "explain"
        )
        private val CONVERSATIONAL_KEYWORDS = listOf(
            "conversation",
            "chat",
            "my week",
            "overview",
            "insight"
        )
        private val MULTI_STEP_MARKERS = listOf(
            " and then ",
            " then ",
            " after that ",
            " step by step ",
            " compare ",
            " summarize "
        )
    }
}

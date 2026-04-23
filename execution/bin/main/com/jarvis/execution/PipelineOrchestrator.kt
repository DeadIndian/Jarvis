package com.jarvis.execution

import com.jarvis.core.Event
import com.jarvis.core.EventBus
import com.jarvis.core.JarvisState
import com.jarvis.core.Orchestrator
import com.jarvis.intent.IntentRouter
import com.jarvis.llm.LLMRouter
import com.jarvis.llm.LLMRequest
import com.jarvis.logging.JarvisLogger
import com.jarvis.memory.MemoryStore
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
    private val allowCloudFallback: Boolean = false,
    private val retrievalLimit: Int = 3
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
            is Event.VoiceInput -> scope.launch { processInput(event.text) }
            is Event.TextInput -> scope.launch { processInput(event.text) }
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

    private suspend fun processInput(text: String) {
        val requestId = UUID.randomUUID().toString()
        
        transitionState(JarvisState.THINKING)

        logger.info(
            "pipeline",
            "Input processing started",
            mapOf("requestId" to requestId, "textLength" to text.length, "state" to state.name)
        )
        
        try {
            outputChannel.showThinking()
            val memoryContext = retrieveMemoryContext(text, requestId)
            
            // Step 1: Intent Parsing
            val intentResult = intentRouter.parse(text)
            
            val spokenText = if (intentResult.intent == "UNKNOWN") {
                // Fallback to AI model (LLM) when no intent is matched
                logger.info("pipeline", "No intent matched, falling back to LLM", mapOf("requestId" to requestId))
                val llmRequest = LLMRequest(
                    prompt = buildPromptWithContext(text, memoryContext),
                    allowCloudFallback = allowCloudFallback
                )
                val llmResponse = llmRouter?.complete(llmRequest)
                llmResponse?.text ?: "I'm sorry, I don't know how to help with that."
            } else {
                // Step 2: Planning
                val plan = planner.createPlan(intentResult.intent, intentResult.entities)

                // Step 3: Execution
                val results = executionEngine.execute(plan)
                val successful = results.filter { it.success }
                val failed = results.filterNot { it.success }

                // Step 4: Response Generation
                when {
                    intentResult.intent == "SPEAK" -> {
                        intentResult.entities["text"].orEmpty().ifBlank { "I am here." }
                    }
                    successful.isNotEmpty() -> successful.first().output ?: "Done"
                    failed.isNotEmpty() -> "I could not complete that request"
                    else -> "No action was needed"
                }
            }

            transitionState(JarvisState.SPEAKING)
            outputChannel.showSpeaking()
            outputChannel.speak(spokenText)

            persistInteraction(
                requestId = requestId,
                input = text,
                response = spokenText,
                usedLlm = intentResult.intent == "UNKNOWN" || intentResult.intent == "SPEAK",
                memoryMatches = memoryContext.size
            )

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
            // Auto-idle after a delay to clear the UI/notification
            scope.launch {
                delay(10000) // 10 seconds of visibility for the final state
                if (state == JarvisState.SPEAKING || state == JarvisState.THINKING) {
                    transitionState(JarvisState.IDLE)
                }
            }
        }
    }

    private fun buildPromptWithContext(input: String, context: List<String>): String {
        if (context.isEmpty()) return input
        return "Context:\n${context.joinToString("\n")}\n\nUser: $input\nJarvis:"
    }

    private suspend fun retrieveMemoryContext(text: String, requestId: String): List<String> {
        val store = memoryStore ?: return emptyList()
        val startedAt = System.nanoTime()
        val results = runCatching {
            store.search(query = text, limit = retrievalLimit.coerceAtLeast(1))
        }.getOrElse {
            logger.warn("memory", "Memory retrieval failed", mapOf("requestId" to requestId))
            emptyList()
        }
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
        logger.info(
            "memory",
            "Memory retrieval completed",
            mapOf("requestId" to requestId, "matches" to results.size, "latencyMs" to latencyMs)
        )
        return results
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
    }
}

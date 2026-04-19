package com.jarvis.execution

import com.jarvis.core.Event
import com.jarvis.core.EventBus
import com.jarvis.core.JarvisState
import com.jarvis.core.Orchestrator
import com.jarvis.intent.IntentRouter
import com.jarvis.llm.LLMRequest
import com.jarvis.llm.LLMRouter
import com.jarvis.logging.JarvisLogger
import com.jarvis.memory.MemoryStore
import com.jarvis.output.OutputChannel
import com.jarvis.planner.Planner
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking

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
    private val allowedTransitions = mapOf(
        JarvisState.IDLE to setOf(JarvisState.BARN_DOOR, JarvisState.HOUSE_PARTY, JarvisState.ACTIVE),
        JarvisState.BARN_DOOR to setOf(JarvisState.ACTIVE, JarvisState.IDLE, JarvisState.HOUSE_PARTY),
        JarvisState.ACTIVE to setOf(JarvisState.IDLE, JarvisState.HOUSE_PARTY),
        JarvisState.HOUSE_PARTY to setOf(JarvisState.ACTIVE, JarvisState.IDLE)
    )

    fun currentState(): JarvisState = state

    override fun dispatch(event: Event) {
        when (event) {
            is Event.VoiceInput -> processInput(event.text)
            is Event.TextInput -> processInput(event.text)
            is Event.WakeWordDetected -> {
                if (state == JarvisState.HOUSE_PARTY) {
                    transitionState(JarvisState.ACTIVE)
                } else {
                    logger.warn(
                        "state",
                        "Wake word ignored outside HOUSE_PARTY",
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
    }

    override fun registerSkill(skillName: String) {
        registeredSkills += skillName
        logger.info("skills", "Skill registered", mapOf("skill" to skillName))
    }

    private fun processInput(text: String) {
        val requestId = UUID.randomUUID().toString()
        if (state == JarvisState.IDLE) {
            transitionState(JarvisState.ACTIVE)
        }

        logger.info(
            "pipeline",
            "Input processing started",
            mapOf("requestId" to requestId, "textLength" to text.length, "state" to state.name)
        )
        outputChannel.showThinking()
        val memoryContext = runBlocking { retrieveMemoryContext(text, requestId) }
        val intentResult = runBlocking { intentRouter.parse(text) }
        val plan = runBlocking { planner.createPlan(intentResult.intent, intentResult.entities) }

        val results = runBlocking { executionEngine.execute(plan) }
        val successful = results.filter { it.success }
        val failed = results.filterNot { it.success }

        val spokenText = when {
            successful.isNotEmpty() -> successful.first().output ?: "Done"
            else -> runBlocking {
                generateConversationalFallback(
                    text = text,
                    failedCount = failed.size,
                    memoryContext = memoryContext,
                    requestId = requestId
                )
            }
        }

        outputChannel.showSpeaking()
        outputChannel.speak(spokenText)

        runBlocking {
            persistInteraction(
                requestId = requestId,
                input = text,
                response = spokenText,
                usedLlm = successful.isEmpty(),
                memoryMatches = memoryContext.size
            )
        }

        eventBus.publish(Event.SkillResult(skill = "pipeline", result = spokenText))
        logger.info(
            "pipeline",
            "Input processing completed",
            mapOf("requestId" to requestId, "successfulSteps" to successful.size, "failedSteps" to failed.size)
        )
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

    private suspend fun generateConversationalFallback(
        text: String,
        failedCount: Int,
        memoryContext: List<String>,
        requestId: String
    ): String {
        val router = llmRouter ?: return if (failedCount > 0) {
            "I could not complete that request"
        } else {
            "No action was needed"
        }

        val prompt = buildPrompt(text = text, failedCount = failedCount, memoryContext = memoryContext)
        val response = runCatching {
            router.complete(LLMRequest(prompt = prompt, allowCloudFallback = allowCloudFallback))
        }.getOrElse {
            logger.warn("llm", "LLM fallback failed", mapOf("requestId" to requestId))
            return if (failedCount > 0) "I could not complete that request" else "No action was needed"
        }

        logger.info(
            "llm",
            "Generated conversational fallback",
            mapOf("requestId" to requestId, "provider" to response.provider)
        )
        return response.text.ifBlank {
            if (failedCount > 0) "I could not complete that request" else "No action was needed"
        }
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

    private fun buildPrompt(text: String, failedCount: Int, memoryContext: List<String>): String {
        val memorySection = if (memoryContext.isEmpty()) {
            "No relevant memory context."
        } else {
            memoryContext.joinToString(separator = "\n---\n") { it.take(MAX_MEMORY_CHARS) }
        }

        val status = if (failedCount > 0) {
            "The action plan failed for $failedCount step(s)."
        } else {
            "No direct action matched this request."
        }

        return buildString {
            appendLine("You are Jarvis, a concise assistant.")
            appendLine(status)
            appendLine("User request: $text")
            appendLine("Relevant memory context:")
            appendLine(memorySection)
            appendLine("Respond with one short helpful sentence.")
        }
    }

    private fun isTransitionAllowed(from: JarvisState, to: JarvisState): Boolean {
        return allowedTransitions[from]?.contains(to) == true
    }

    companion object {
        private const val MAX_MEMORY_CHARS = 400
    }
}

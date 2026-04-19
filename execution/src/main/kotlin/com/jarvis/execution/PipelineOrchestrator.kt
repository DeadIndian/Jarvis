package com.jarvis.execution

import com.jarvis.core.Event
import com.jarvis.core.EventBus
import com.jarvis.core.JarvisState
import com.jarvis.core.Orchestrator
import com.jarvis.intent.IntentRouter
import com.jarvis.logging.JarvisLogger
import com.jarvis.output.OutputChannel
import com.jarvis.planner.Planner
import kotlinx.coroutines.runBlocking

class PipelineOrchestrator(
    private val eventBus: EventBus,
    private val intentRouter: IntentRouter,
    private val planner: Planner,
    private val executionEngine: ExecutionEngine,
    private val outputChannel: OutputChannel,
    private val logger: JarvisLogger
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
        if (state == JarvisState.IDLE) {
            transitionState(JarvisState.ACTIVE)
        }

        outputChannel.showThinking()
        val intentResult = runBlocking { intentRouter.parse(text) }
        val plan = runBlocking { planner.createPlan(intentResult.intent, intentResult.entities) }

        val results = runBlocking { executionEngine.execute(plan) }
        val successful = results.filter { it.success }
        val failed = results.filterNot { it.success }

        val spokenText = when {
            successful.isNotEmpty() -> successful.first().output ?: "Done"
            failed.isNotEmpty() -> "I could not complete that request"
            else -> "No action was needed"
        }

        outputChannel.showSpeaking()
        outputChannel.speak(spokenText)

        eventBus.publish(Event.SkillResult(skill = "pipeline", result = spokenText))
    }

    private fun isTransitionAllowed(from: JarvisState, to: JarvisState): Boolean {
        return allowedTransitions[from]?.contains(to) == true
    }
}

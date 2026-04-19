package com.jarvis.execution

import com.jarvis.core.Event
import com.jarvis.core.EventBus
import com.jarvis.core.EventListener
import com.jarvis.core.InMemoryEventBus
import com.jarvis.core.JarvisState
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.logging.NoOpJarvisLogger
import com.jarvis.output.OutputChannel
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.InMemorySkillRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineOrchestratorTest {
    @Test
    fun wakeWordTransitionsToActiveInHouseParty() {
        val orchestrator = buildOrchestrator()
        orchestrator.dispatch(Event.HousePartyToggle(enabled = true))

        orchestrator.dispatch(Event.WakeWordDetected("jarvis"))

        assertEquals(JarvisState.ACTIVE, orchestrator.currentState())
    }

    @Test
    fun wakeWordIgnoredOutsideHouseParty() {
        val orchestrator = buildOrchestrator()

        orchestrator.dispatch(Event.WakeWordDetected("jarvis"))

        assertEquals(JarvisState.IDLE, orchestrator.currentState())
    }

    @Test
    fun voiceInputRunsAppLauncherPlanAndPublishesResult() {
        val output = FakeOutputChannel()
        val eventBus = InMemoryEventBus()
        val captured = mutableListOf<Event>()
        eventBus.subscribe(EventListener { captured += it })

        val orchestrator = buildOrchestrator(eventBus = eventBus, output = output)

        orchestrator.dispatch(Event.VoiceInput("open WhatsApp"))

        assertTrue(output.spoken.any { it.contains("Launching WhatsApp") })
        assertTrue(captured.any { it is Event.SkillResult && it.skill == "pipeline" })
        assertEquals(JarvisState.ACTIVE, orchestrator.currentState())
    }

    @Test
    fun powerButtonTransitionsToBarnDoorAndTimeoutReturnsIdle() {
        val orchestrator = buildOrchestrator()

        orchestrator.dispatch(Event.PowerButtonHeld)
        assertEquals(JarvisState.BARN_DOOR, orchestrator.currentState())

        orchestrator.dispatch(Event.TimeoutElapsed)
        assertEquals(JarvisState.IDLE, orchestrator.currentState())
    }

    @Test
    fun housePartyToggleTransitionsBetweenHousePartyAndIdle() {
        val orchestrator = buildOrchestrator()

        orchestrator.dispatch(Event.HousePartyToggle(enabled = true))
        assertEquals(JarvisState.HOUSE_PARTY, orchestrator.currentState())

        orchestrator.dispatch(Event.HousePartyToggle(enabled = false))
        assertEquals(JarvisState.IDLE, orchestrator.currentState())
    }

    private fun buildOrchestrator(
        eventBus: EventBus = InMemoryEventBus(),
        output: FakeOutputChannel = FakeOutputChannel()
    ): PipelineOrchestrator {
        val skillRegistry = InMemorySkillRegistry().apply { register(AppLauncherSkill()) }

        return PipelineOrchestrator(
            eventBus = eventBus,
            intentRouter = RuleBasedIntentRouter(),
            planner = SimplePlanner(),
            executionEngine = SkillExecutionEngine(skillRegistry),
            outputChannel = output,
            logger = NoOpJarvisLogger
        )
    }

    private class FakeOutputChannel : OutputChannel {
        val spoken = mutableListOf<String>()

        override fun showListening() = Unit

        override fun showThinking() = Unit

        override fun showSpeaking() = Unit

        override fun speak(text: String) {
            spoken += text
        }
    }
}

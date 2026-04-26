package com.jarvis.execution

import com.jarvis.core.Event
import com.jarvis.core.EventBus
import com.jarvis.core.EventListener
import com.jarvis.core.InMemoryEventBus
import com.jarvis.core.JarvisState
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.llm.LLMRequest
import com.jarvis.llm.LLMResponse
import com.jarvis.llm.LLMRouter
import com.jarvis.logging.NoOpJarvisLogger
import com.jarvis.memory.MemoryStore
import com.jarvis.memory.models.MemoryChunk
import com.jarvis.output.OutputChannel
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.CurrentTimeSkill
import com.jarvis.skills.InMemorySkillRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
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
    fun wakeWordTransitionsToActiveFromIdle() {
        val orchestrator = buildOrchestrator()

        orchestrator.dispatch(Event.WakeWordDetected("jarvis"))

        assertEquals(JarvisState.ACTIVE, orchestrator.currentState())
    }

    @Test
    fun voiceInputRunsAppLauncherPlanAndPublishesResult() {
        val output = FakeOutputChannel()
        val eventBus = InMemoryEventBus()
        val captured = mutableListOf<Event>()
        eventBus.subscribe(EventListener { captured += it })

        val orchestrator = buildOrchestrator(eventBus = eventBus, output = output)

        orchestrator.dispatch(Event.VoiceInput("open WhatsApp"))

        waitForCondition { captured.any { it is Event.SkillResult && it.skill == "pipeline" } }
        waitForCondition { orchestrator.currentState() == JarvisState.IDLE }

        assertTrue(output.spoken.isEmpty())
        assertEquals(0, output.listeningShownCount)
        assertTrue(captured.any { it is Event.SkillResult && it.skill == "pipeline" })
        assertEquals(JarvisState.IDLE, orchestrator.currentState())
    }

    @Test
    fun unknownInputFallsBackLocallyWhenServerUnavailableWithoutPersisting() {
        val output = FakeOutputChannel()
        val memory = FakeMemoryStore(searchResults = listOf("user prefers concise answers"))
        val llm = FakeLLMRouter(responseText = "I can help with that. Try asking me to open an app by name.")

        val orchestrator = buildOrchestrator(output = output, memoryStore = memory, llmRouter = llm)

        orchestrator.dispatch(Event.TextInput("what can you do"))

        waitForCondition { output.spoken.any { it.contains("Try asking me to open an app") } }
        waitForCondition { llm.requests.isNotEmpty() }

        assertTrue(output.spoken.any { it.contains("Try asking me to open an app") })
        assertEquals(1, llm.requests.size)
        assertTrue(llm.requests.first().prompt.contains("user prefers concise answers"))
        assertEquals(0, memory.persisted.size)
    }

    @Test
    fun unknownInputUsesRemoteAgentWhenAvailable() {
        val output = FakeOutputChannel()
        val memory = FakeMemoryStore(searchResults = listOf("weekly notes"))
        val remote = FakeRemoteAgentClient(response = RemoteResponse(text = "Your week was productive."))
        val llm = FakeLLMRouter(responseText = "local fallback")

        val orchestrator = buildOrchestrator(
            output = output,
            memoryStore = memory,
            llmRouter = llm,
            remoteAgentClient = remote
        )

        orchestrator.dispatch(Event.TextInput("summarize my week"))

        waitForCondition { output.spoken.any { it.contains("productive") } }
        assertEquals(1, remote.requests.size)
        assertEquals(0, llm.requests.size)
    }

    @Test
    fun decideExecutionModeUsesLocalFastForSingleSkillIntent() {
        val orchestrator = buildOrchestrator()

        val mode = orchestrator.decideExecutionMode(
            intent = com.jarvis.intent.IntentResult(
                intent = "SYSTEM_CONTROL",
                confidence = 0.95,
                entities = mapOf("target" to "wifi", "action" to "ON")
            ),
            input = "turn on wifi"
        )

        assertEquals(ExecutionMode.LOCAL_FAST, mode)
    }

    @Test
    fun decideExecutionModeUsesServerAgentForUnknownIntent() {
        val orchestrator = buildOrchestrator()

        val mode = orchestrator.decideExecutionMode(
            intent = com.jarvis.intent.IntentResult(
                intent = "UNKNOWN",
                confidence = 0.1,
                entities = emptyMap()
            ),
            input = "can you summarize my entire week in detail"
        )

        assertEquals(ExecutionMode.SERVER_AGENT, mode)
    }

    @Test
    fun decideExecutionModeUsesLocalFastForCurrentTimeConversationalQuery() {
        val orchestrator = buildOrchestrator()

        val mode = orchestrator.decideExecutionMode(
            intent = com.jarvis.intent.IntentResult(
                intent = "CurrentTime",
                confidence = 0.95,
                entities = emptyMap()
            ),
            input = "what is the time"
        )

        assertEquals(ExecutionMode.LOCAL_FAST, mode)
    }

    @Test
    fun voiceInputForCurrentTimeSpeaksResultAndReturnsToListening() {
        val output = FakeOutputChannel()
        val orchestrator = buildOrchestrator(output = output)

        orchestrator.dispatch(Event.VoiceInput("what is the time"))

        waitForCondition { output.spoken.any { it == "It is 1:05 PM UTC." } }
        waitForCondition { output.listeningShownCount > 0 }

        assertTrue(output.spoken.contains("It is 1:05 PM UTC."))
        assertTrue(output.listeningShownCount > 0)
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
        output: FakeOutputChannel = FakeOutputChannel(),
        memoryStore: MemoryStore? = null,
        llmRouter: LLMRouter? = null,
        remoteAgentClient: RemoteAgentClient? = null
    ): PipelineOrchestrator {
        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill())
            register(
                CurrentTimeSkill(
                    clock = Clock.fixed(Instant.parse("2026-04-22T13:05:00Z"), ZoneId.of("UTC")),
                    locale = Locale.US
                )
            )
        }

        return PipelineOrchestrator(
            eventBus = eventBus,
            intentRouter = RuleBasedIntentRouter(),
            planner = SimplePlanner(),
            executionEngine = SkillExecutionEngine(skillRegistry),
            outputChannel = output,
            logger = NoOpJarvisLogger,
            memoryStore = memoryStore,
            llmRouter = llmRouter,
            remoteAgentClient = remoteAgentClient
        )
    }

    private fun waitForCondition(
        timeoutMs: Long = 2_000,
        intervalMs: Long = 25,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(intervalMs)
        }
        assertTrue(condition(), "Condition was not met within ${timeoutMs}ms")
    }

    private class FakeOutputChannel : OutputChannel {
        val spoken = mutableListOf<String>()
        var listeningShownCount: Int = 0

        override fun showListening() {
            listeningShownCount += 1
        }

        override fun showThinking() = Unit

        override fun showSpeaking() = Unit

        override fun speak(text: String) {
            spoken += text
        }
    }

    private class FakeLLMRouter(
        private val responseText: String
    ) : LLMRouter {
        val requests = mutableListOf<LLMRequest>()

        override suspend fun complete(request: LLMRequest): LLMResponse {
            requests += request
            return LLMResponse(text = responseText, provider = "fake-local")
        }
    }

    private class FakeMemoryStore(
        private val searchResults: List<String>
    ) : MemoryStore {
        val persisted = mutableListOf<Pair<String, String>>()

        override suspend fun put(key: String, value: String) {
            persisted += key to value
        }

        override suspend fun get(key: String): String? = null

        override suspend fun search(query: String, limit: Int): List<String> = searchResults.take(limit)
    }

    private class FakeRemoteAgentClient(
        private val response: RemoteResponse
    ) : RemoteAgentClient {
        val requests = mutableListOf<RemoteRequest>()

        override suspend fun process(request: RemoteRequest): RemoteResponse {
            requests += request
            return response
        }
    }
}

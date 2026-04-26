package com.jarvis.app

import com.jarvis.core.Event
import com.jarvis.core.InMemoryEventBus
import com.jarvis.execution.PipelineOrchestrator
import com.jarvis.execution.SkillExecutionEngine
import com.jarvis.intent.RuleBasedIntentRouter
import com.jarvis.llm.LLMRequest
import com.jarvis.llm.LLMResponse
import com.jarvis.llm.LLMRouter
import com.jarvis.logging.NoOpJarvisLogger
import com.jarvis.memory.MemoryStore
import com.jarvis.planner.SimplePlanner
import com.jarvis.skills.AppLauncherSkill
import com.jarvis.skills.CurrentTimeSkill
import com.jarvis.skills.HelpCenterSkill
import com.jarvis.skills.InMemorySkillRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JarvisPipelineIntegrationTest {
    @Test
    fun textInputOpenAppRunsEndToEndLaunchFlow() {
        val outputStates = mutableListOf<String>()
        val spoken = mutableListOf<String>()

        val outputChannel = UiOutputChannel(
            onState = { outputStates += it },
            onSpeak = { spoken += it }
        )

        val orchestrator = buildOrchestrator(
            outputChannel = outputChannel,
            appLauncher = { app -> "Launching $app" }
        )

        orchestrator.dispatch(Event.TextInput("open WhatsApp"))

        waitForCondition { outputStates.contains("Thinking") }
        waitForCondition { orchestrator.currentState().name == "IDLE" }

        assertTrue(outputStates.contains("Thinking"))
        assertTrue(outputStates.contains("Speaking").not())
        assertTrue(spoken.isEmpty())
        assertEquals("IDLE", orchestrator.currentState().name)
    }

    @Test
    fun unknownTextInputFallsBackToLlmWithMemoryContext() {
        val spoken = mutableListOf<String>()
        val outputChannel = UiOutputChannel(
            onState = {},
            onSpeak = { spoken += it }
        )

        val memory = FakeMemoryStore(searchResults = listOf("user likes concise answers"))
        val llm = FakeLlmRouter(responseText = "Try asking me to open an app like WhatsApp.")

        val orchestrator = buildOrchestrator(
            outputChannel = outputChannel,
            appLauncher = { app -> "Launching $app" },
            memoryStore = memory,
            llmRouter = llm
        )

        orchestrator.dispatch(Event.TextInput("summarize what I asked you to do today"))

        waitForCondition { spoken.any { it.contains("open an app") } }
        waitForCondition { llm.requests.isNotEmpty() }

        assertTrue(spoken.any { it.contains("open an app") })
        assertEquals(1, llm.requests.size)
        assertTrue(llm.requests.first().prompt.contains("user likes concise answers"))
    }

    @Test
    fun timeQueryRunsEndToEndAndProducesSpokenAnswer() {
        val spoken = mutableListOf<String>()
        val outputChannel = UiOutputChannel(
            onState = {},
            onSpeak = { spoken += it }
        )

        val orchestrator = buildOrchestrator(
            outputChannel = outputChannel,
            appLauncher = { app -> "Launching $app" }
        )

        orchestrator.dispatch(Event.TextInput("what is the time"))

        waitForCondition { spoken.any { it == "It is 1:05 PM UTC." } }

        assertTrue(spoken.contains("It is 1:05 PM UTC."))
    }

    @Test
    fun helpCommandRunsLocallyAndOpensHelpCenter() {
        val spoken = mutableListOf<String>()
        var helpOpened = false
        val outputChannel = UiOutputChannel(
            onState = {},
            onSpeak = { spoken += it }
        )

        val orchestrator = buildOrchestrator(
            outputChannel = outputChannel,
            appLauncher = { app -> "Launching $app" },
            onOpenHelp = {
                helpOpened = true
                "Opened the help center."
            }
        )

        orchestrator.dispatch(Event.TextInput("tell me what you can do"))

        waitForCondition { helpOpened }
        waitForCondition { spoken.contains("Opened the help center.") }

        assertTrue(helpOpened)
        assertTrue(spoken.contains("Opened the help center."))
    }

    private fun waitForCondition(
        timeoutMs: Long = 2_000,
        pollIntervalMs: Long = 25,
        condition: () -> Boolean
    ) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Condition was not met within ${timeoutMs}ms")
            }
            Thread.sleep(pollIntervalMs)
        }
    }

    private fun buildOrchestrator(
        outputChannel: UiOutputChannel,
        appLauncher: suspend (String) -> String,
        onOpenHelp: suspend () -> String = { "Opened the help center." },
        memoryStore: MemoryStore = FakeMemoryStore(emptyList()),
        llmRouter: LLMRouter = FakeLlmRouter("No action was needed")
    ): PipelineOrchestrator {
        val skillRegistry = InMemorySkillRegistry().apply {
            register(AppLauncherSkill(appLauncher))
            register(
                CurrentTimeSkill(
                    clock = Clock.fixed(Instant.parse("2026-04-22T13:05:00Z"), ZoneId.of("UTC")),
                    locale = Locale.US
                )
            )
            register(HelpCenterSkill(onOpenHelp))
        }

        return PipelineOrchestrator(
            eventBus = InMemoryEventBus(),
            intentRouter = RuleBasedIntentRouter(),
            planner = SimplePlanner(),
            executionEngine = SkillExecutionEngine(skillRegistry, logger = NoOpJarvisLogger),
            outputChannel = outputChannel,
            logger = NoOpJarvisLogger,
            memoryStore = memoryStore,
            llmRouter = llmRouter,
            allowCloudFallback = false
        )
    }

    private class FakeMemoryStore(
        private val searchResults: List<String>
    ) : MemoryStore {
        override suspend fun put(key: String, value: String) = Unit

        override suspend fun get(key: String): String? = null

        override suspend fun search(query: String, limit: Int): List<String> = searchResults.take(limit)
    }

    private class FakeLlmRouter(
        private val responseText: String
    ) : LLMRouter {
        val requests = mutableListOf<LLMRequest>()

        override suspend fun complete(request: LLMRequest): LLMResponse {
            requests += request
            return LLMResponse(text = responseText, provider = "fake-local")
        }
    }
}

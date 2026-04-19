package com.jarvis.execution

import com.jarvis.logging.NoOpJarvisLogger
import com.jarvis.planner.Plan
import com.jarvis.planner.PlanStep
import com.jarvis.skills.InMemorySkillRegistry
import com.jarvis.skills.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class SkillExecutionEngineTest {
    @Test
    fun retriesFailedStepAndEventuallySucceeds() = runTest {
        val registry = InMemorySkillRegistry().apply {
            register(object : Skill {
                override val name: String = "Flaky"
                override val description: String = "Fails once then succeeds"
                private var calls = 0

                override suspend fun execute(input: Map<String, String>): String {
                    calls += 1
                    if (calls == 1) {
                        error("transient failure")
                    }
                    return "ok"
                }
            })
        }

        val engine = SkillExecutionEngine(skillRegistry = registry, logger = NoOpJarvisLogger)
        val plan = Plan(
            steps = listOf(
                PlanStep(skill = "Flaky", input = emptyMap(), maxRetries = 1)
            )
        )

        val results = engine.execute(plan)

        assertEquals(1, results.size)
        assertTrue(results.first().success)
        assertEquals("ok", results.first().output)
    }

    @Test
    fun timesOutStepWhenExecutionExceedsTimeout() = runTest {
        val registry = InMemorySkillRegistry().apply {
            register(object : Skill {
                override val name: String = "Slow"
                override val description: String = "Sleeps longer than timeout"

                override suspend fun execute(input: Map<String, String>): String {
                    delay(100)
                    return "finished"
                }
            })
        }

        val engine = SkillExecutionEngine(skillRegistry = registry, logger = NoOpJarvisLogger)
        val plan = Plan(
            steps = listOf(
                PlanStep(skill = "Slow", input = emptyMap(), timeoutMs = 20, maxRetries = 0)
            )
        )

        val results = engine.execute(plan)

        assertEquals(1, results.size)
        assertFalse(results.first().success)
        assertTrue(results.first().error?.contains("timed out") == true)
    }

    @Test
    fun propagatesCancellationDuringExecution() = runTest {
        val registry = InMemorySkillRegistry().apply {
            register(object : Skill {
                override val name: String = "Blocking"
                override val description: String = "Runs until cancelled"

                override suspend fun execute(input: Map<String, String>): String {
                    delay(1_000)
                    return "done"
                }
            })
        }

        val engine = SkillExecutionEngine(skillRegistry = registry, logger = NoOpJarvisLogger)
        val plan = Plan(steps = listOf(PlanStep(skill = "Blocking", input = emptyMap(), timeoutMs = 5_000)))

        val job = launch {
            engine.execute(plan)
        }

        job.cancel()

        try {
            job.join()
        } catch (ignored: CancellationException) {
            // Expected by test assertion below.
        }

        assertTrue(job.isCancelled)
    }
}

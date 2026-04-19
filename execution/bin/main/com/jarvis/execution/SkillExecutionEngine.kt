package com.jarvis.execution

import com.jarvis.logging.JarvisLogger
import com.jarvis.logging.NoOpJarvisLogger
import com.jarvis.planner.Plan
import com.jarvis.planner.PlanStep
import com.jarvis.skills.SkillRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

class SkillExecutionEngine(
    private val skillRegistry: SkillRegistry,
    private val logger: JarvisLogger = NoOpJarvisLogger,
    private val defaultTimeoutMs: Long = 2_000L,
    private val maxRetriesCap: Int = 3
) : ExecutionEngine {
    override suspend fun execute(plan: Plan): List<StepResult> {
        return plan.steps.map { step -> executeStep(step) }
    }

    private suspend fun executeStep(step: PlanStep): StepResult {
        currentCoroutineContext().ensureActive()

        val skill = skillRegistry.get(step.skill)
            ?: return StepResult(skill = step.skill, success = false, error = "Skill not found")

        val timeoutMs = step.timeoutMs.takeIf { it > 0 } ?: defaultTimeoutMs
        val retries = step.maxRetries.coerceIn(0, maxRetriesCap)

        repeat(retries + 1) { attemptIndex ->
            val attempt = attemptIndex + 1
            currentCoroutineContext().ensureActive()

            val startedAt = System.nanoTime()
            val outcome = withTimeoutOrNull(timeoutMs) {
                runCatching { skill.execute(step.input) }
            }
            val latencyMs = (System.nanoTime() - startedAt) / 1_000_000

            if (outcome == null) {
                logger.warn(
                    "execution",
                    "Step attempt timed out",
                    mapOf("skill" to step.skill, "attempt" to attempt, "timeoutMs" to timeoutMs, "latencyMs" to latencyMs)
                )
                if (attempt > retries) {
                    return StepResult(
                        skill = step.skill,
                        success = false,
                        error = "Step timed out after ${timeoutMs}ms"
                    )
                }
                return@repeat
            }

            val value = outcome.getOrElse { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }

                logger.warn(
                    "execution",
                    "Step attempt failed",
                    mapOf("skill" to step.skill, "attempt" to attempt, "latencyMs" to latencyMs)
                )
                if (attempt > retries) {
                    return StepResult(
                        skill = step.skill,
                        success = false,
                        error = throwable.message ?: "Skill execution failed"
                    )
                }
                return@repeat
            }

            logger.info(
                "execution",
                "Step attempt succeeded",
                mapOf("skill" to step.skill, "attempt" to attempt, "latencyMs" to latencyMs)
            )
            return StepResult(skill = step.skill, success = true, output = value)
        }

        return StepResult(skill = step.skill, success = false, error = "Skill execution failed")
    }
}

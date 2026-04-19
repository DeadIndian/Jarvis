package com.jarvis.execution

import com.jarvis.planner.Plan
import com.jarvis.skills.SkillRegistry

class SkillExecutionEngine(
    private val skillRegistry: SkillRegistry
) : ExecutionEngine {
    override suspend fun execute(plan: Plan): List<StepResult> {
        return plan.steps.map { step ->
            val skill = skillRegistry.get(step.skill)
            if (skill == null) {
                StepResult(
                    skill = step.skill,
                    success = false,
                    error = "Skill not found"
                )
            } else {
                runCatching { skill.execute(step.input) }
                    .fold(
                        onSuccess = { output ->
                            StepResult(
                                skill = step.skill,
                                success = true,
                                output = output
                            )
                        },
                        onFailure = { throwable ->
                            StepResult(
                                skill = step.skill,
                                success = false,
                                error = throwable.message ?: "Skill execution failed"
                            )
                        }
                    )
            }
        }
    }
}

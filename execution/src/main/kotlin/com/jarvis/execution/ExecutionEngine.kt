package com.jarvis.execution

import com.jarvis.planner.Plan

data class StepResult(
    val skill: String,
    val success: Boolean,
    val output: String? = null,
    val error: String? = null
)

interface ExecutionEngine {
    suspend fun execute(plan: Plan): List<StepResult>
}

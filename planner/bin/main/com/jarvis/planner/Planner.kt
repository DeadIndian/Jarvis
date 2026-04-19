package com.jarvis.planner

data class PlanStep(
    val skill: String,
    val input: Map<String, String>,
    val parallelGroup: String? = null,
    val timeoutMs: Long = 2_000L,
    val maxRetries: Int = 0
)

data class Plan(val steps: List<PlanStep>)

interface Planner {
    suspend fun createPlan(intent: String, entities: Map<String, String>): Plan
}

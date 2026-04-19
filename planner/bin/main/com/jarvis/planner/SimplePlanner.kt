package com.jarvis.planner

class SimplePlanner : Planner {
    override suspend fun createPlan(intent: String, entities: Map<String, String>): Plan {
        return when (intent) {
            "OPEN_APP" -> {
                val app = entities["app"].orEmpty()
                Plan(
                    steps = listOf(
                        PlanStep(
                            skill = "AppLauncher",
                            input = mapOf("app" to app)
                        )
                    )
                )
            }

            else -> Plan(emptyList())
        }
    }
}

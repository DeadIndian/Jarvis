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
            
            "CONVERSATIONAL_RESPONSE" -> {
                // This "intent" means the LLM already generated a text response.
                // We can wrap it in a pseudo-skill or handle it specially in orchestrator.
                // For simplicity, we'll return an empty plan and let orchestrator use the entity.
                Plan(emptyList())
            }

            else -> Plan(emptyList())
        }
    }
}

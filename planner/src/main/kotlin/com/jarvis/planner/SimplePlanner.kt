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

            "CurrentTime" -> {
                Plan(
                    steps = listOf(
                        PlanStep(
                            skill = "CurrentTime",
                            input = emptyMap()
                        )
                    )
                )
            }

            "SYSTEM_CONTROL" -> {
                Plan(
                    steps = listOf(
                        PlanStep(
                            skill = "SystemControl",
                            input = entities
                        )
                    )
                )
            }

            "SHOW_HELP" -> {
                Plan(
                    steps = listOf(
                        PlanStep(
                            skill = "HelpCenter",
                            input = emptyMap()
                        )
                    )
                )
            }
            
            "CONVERSATIONAL_RESPONSE", "SPEAK" -> {
                // No skill execution required; orchestrator will speak the provided text directly.
                Plan(emptyList())
            }

            else -> Plan(emptyList())
        }
    }
}

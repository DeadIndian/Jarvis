package com.jarvis.skills

class AppLauncherSkill : Skill {
    override val name: String = "AppLauncher"
    override val description: String = "Launches an app by name (mock implementation)."

    override suspend fun execute(input: Map<String, String>): String {
        val app = input["app"].orEmpty().ifBlank { "unknown app" }
        return "Launching $app"
    }
}

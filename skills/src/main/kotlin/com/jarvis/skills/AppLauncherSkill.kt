package com.jarvis.skills

class AppLauncherSkill(
    private val launcher: suspend (String) -> String = { app -> "Launching $app" }
) : Skill {
    override val name: String = "AppLauncher"
    override val description: String = "Launches an app by name using an injected launcher."

    override suspend fun execute(input: Map<String, String>): String {
        val app = input["app"].orEmpty().ifBlank { "unknown app" }
        return launcher(app)
    }
}

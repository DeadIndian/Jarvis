package com.jarvis.skills

class AppLauncherSkill(
    private val launcher: suspend (String) -> String = { app -> "Launching $app" }
) : Skill {
    override val name: String = "AppLauncher"
    override val description: String = "Launches an installed Android application by its name."
    
    override val parameters: List<SkillParameter> = listOf(
        SkillParameter(
            name = "app",
            type = "string",
            description = "The name of the application to launch (e.g., 'WhatsApp', 'Camera')."
        )
    )

    override suspend fun execute(input: Map<String, String>): String {
        val app = input["app"].orEmpty().ifBlank { "unknown app" }
        return launcher(app)
    }
}

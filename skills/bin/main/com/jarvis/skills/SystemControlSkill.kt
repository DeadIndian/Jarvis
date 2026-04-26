package com.jarvis.skills

class SystemControlSkill(
    private val controller: suspend (input: Map<String, String>) -> String = { input ->
        val target = input["target"].orEmpty().ifBlank { "unknown" }
        val action = input["action"].orEmpty().ifBlank { "TOGGLE" }
        "Requested $action for $target"
    }
) : Skill {
    override val name: String = "SystemControl"
    override val description: String = "Controls system features like flashlight, Bluetooth, hotspot, and Wi-Fi."

    override val parameters: List<SkillParameter> = listOf(
        SkillParameter(
            name = "target",
            type = "string",
            description = "Feature to control (flashlight, bluetooth, hotspot, wifi)."
        ),
        SkillParameter(
            name = "action",
            type = "string",
            description = "Action to apply (ON, OFF, TOGGLE, OPEN_SETTINGS)."
        )
    )

    override suspend fun execute(input: Map<String, String>): String {
        return controller(input)
    }
}
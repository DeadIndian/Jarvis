package com.jarvis.skills

class HelpCenterSkill(
    private val opener: suspend () -> String = { "Opened the help center." }
) : Skill {
    override val name: String = "HelpCenter"
    override val description: String = "Opens the in-app help center with the supported commands."

    override suspend fun execute(input: Map<String, String>): String {
        return opener()
    }
}

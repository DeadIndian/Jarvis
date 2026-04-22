package com.jarvis.skills

data class SkillParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

interface Skill {
    val name: String
    val description: String
    val parameters: List<SkillParameter> get() = emptyList()
    suspend fun execute(input: Map<String, String>): String
}

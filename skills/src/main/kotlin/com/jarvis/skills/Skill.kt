package com.jarvis.skills

interface Skill {
    val name: String
    val description: String
    suspend fun execute(input: Map<String, String>): String
}

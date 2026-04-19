package com.jarvis.skills

interface SkillRegistry {
    fun register(skill: Skill)
    fun get(name: String): Skill?
    fun all(): List<Skill>
}

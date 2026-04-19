package com.jarvis.skills

class InMemorySkillRegistry : SkillRegistry {
    private val skills = LinkedHashMap<String, Skill>()

    override fun register(skill: Skill) {
        require(!skills.containsKey(skill.name)) {
            "Skill '${skill.name}' is already registered"
        }
        skills[skill.name] = skill
    }

    override fun get(name: String): Skill? = skills[name]

    override fun all(): List<Skill> = skills.values.toList()
}

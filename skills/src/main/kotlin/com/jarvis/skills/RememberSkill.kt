package com.jarvis.skills

import com.jarvis.memory.MemoryStore

/**
 * A skill that allows Jarvis to explicitly remember information by storing it
 * in the MemoryStore.
 */
class RememberSkill(private val memoryStore: MemoryStore?) : Skill {
    override val name: String = "RememberInfo"
    override val description: String = "Saves specific information or facts to memory for later retrieval."

    override suspend fun execute(input: Map<String, String>): String {
        val info = input["info"] ?: return "I don't have anything specific to remember."
        val key = "user-note-${System.currentTimeMillis()}"
        
        return if (memoryStore != null) {
            try {
                memoryStore.put(key, info)
                "I've committed that to memory."
            } catch (e: Exception) {
                "I had trouble saving that to my memory: ${e.message}"
            }
        } else {
            "My memory storage is not currently available."
        }
    }
}

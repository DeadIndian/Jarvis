package com.jarvis.skills

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemControlSkillTest {
    @Test
    fun executePassesTargetAndActionToController() {
        var capturedInput: Map<String, String> = emptyMap()
        val skill = SystemControlSkill { input ->
            capturedInput = input
            "ok"
        }

        val result = kotlinx.coroutines.runBlocking {
            skill.execute(mapOf("target" to "bluetooth", "action" to "OFF"))
        }

        assertEquals("ok", result)
        assertEquals("bluetooth", capturedInput["target"])
        assertEquals("OFF", capturedInput["action"])
    }
}
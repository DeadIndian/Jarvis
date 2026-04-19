package com.jarvis.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class AppLauncherSkillTest {
    @Test
    fun defaultLauncherReturnsMockMessage() {
        val skill = AppLauncherSkill()

        runTest {
            assertEquals("Launching Notes", skill.execute(mapOf("app" to "Notes")))
        }
    }

    @Test
    fun injectedLauncherReceivesResolvedAppName() {
        val skill = AppLauncherSkill { app -> "Opened $app" }

        runTest {
            assertEquals("Opened WhatsApp", skill.execute(mapOf("app" to "WhatsApp")))
        }
    }
}
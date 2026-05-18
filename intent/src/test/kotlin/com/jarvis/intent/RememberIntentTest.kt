package com.jarvis.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class RememberIntentTest {
    private val router = RuleBasedIntentRouter()

    @Test
    fun parseSimpleRemember() = runBlocking {
        val result = router.parse("remember my keys are on the table")
        assertEquals("REMEMBER", result.intent)
        assertEquals("my keys are on the table", result.entities["info"])
    }

    @Test
    fun parseRememberWithFolderIn() = runBlocking {
        val result = router.parse("remember in travel my passport is in the drawer")
        assertEquals("REMEMBER", result.intent)
        assertEquals("travel", result.entities["folder"])
        assertEquals("my passport is in the drawer", result.entities["info"])
    }

    @Test
    fun parseRememberWithFolderTo() = runBlocking {
        val result = router.parse("remember to projects buy some milk")
        assertEquals("REMEMBER", result.intent)
        assertEquals("projects", result.entities["folder"])
        assertEquals("buy some milk", result.entities["info"])
    }
}

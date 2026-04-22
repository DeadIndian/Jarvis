package com.jarvis.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TinyIntentClassifierTest {
    private val classifier = TinyIntentClassifier()

    @Test
    fun classifyRoutesTimeQuestionsToCurrentTimeIntent() {
        val result = classifier.classify("what time is it right now")

        assertNotNull(result)
        assertEquals("CurrentTime", result.intent)
    }

    @Test
    fun classifyKeepsOpenAppPriorityForLaunchCommands() {
        val result = classifier.classify("open clock")

        assertNotNull(result)
        assertEquals("OPEN_APP", result.intent)
        assertEquals("clock", result.entities["app"])
    }
}

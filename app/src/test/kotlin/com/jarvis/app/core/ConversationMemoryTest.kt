package com.jarvis.app.core

import com.jarvis.app.core.memory.ConversationMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationMemoryTest {

    @Test fun recordsToolExecutionsInWindow() {
        val m = ConversationMemory()
        m.addUser("call John")
        m.addToolResult("make_call", "Calling John.")
        m.addUser("what did I just do")

        val w = m.window()
        assertEquals(3, w.size)
        assertEquals("assistant", w[1].role)
        assertTrue(w[1].content.contains("make_call"))
        assertTrue(w[1].content.contains("Calling John."))
    }

    @Test fun budgetDropsOldestKeepsNewest() {
        // ~40 tokens per turn (160 chars / 4); budget 100 fits ~2 recent turns.
        val m = ConversationMemory(tokenBudget = 100)
        val line = "x".repeat(160)
        m.addUser("$line 1")
        m.addUser("$line 2")
        m.addUser("$line 3")

        val w = m.window()
        assertTrue(w.size < 3, "expected oldest turns trimmed, got ${w.size}")
        assertTrue(w.last().content.endsWith("3"), "newest turn must be kept")
    }

    @Test fun singleOverBudgetTurnStillReturned() {
        val m = ConversationMemory(tokenBudget = 10)
        m.addUser("x".repeat(1000)) // far over budget on its own

        val w = m.window()
        assertEquals(1, w.size)
    }

    @Test fun windowIsChronological() {
        val m = ConversationMemory()
        m.addUser("first")
        m.addAssistant("second")
        m.addUser("third")

        val w = m.window()
        assertEquals(listOf("first", "second", "third"), w.map { it.content })
    }
}

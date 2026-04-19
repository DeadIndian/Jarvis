package com.jarvis.memory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MarkdownFileMemoryStoreTest {
    @Test
    fun putAndGetRoundTripsPersistedValue() = runTest {
        val tempDir = Files.createTempDirectory("jarvis-memory-test")
        val store = MarkdownFileMemoryStore(baseDirectory = tempDir)

        store.put("User Preference", "likes concise output")

        assertEquals("likes concise output", store.get("user preference"))
    }

    @Test
    fun getReturnsNullForMissingKey() = runTest {
        val tempDir = Files.createTempDirectory("jarvis-memory-test")
        val store = MarkdownFileMemoryStore(baseDirectory = tempDir)

        assertNull(store.get("missing"))
    }

    @Test
    fun searchReturnsTopRankedMatches() = runTest {
        val tempDir = Files.createTempDirectory("jarvis-memory-test")
        val store = MarkdownFileMemoryStore(baseDirectory = tempDir)

        store.put("alpha", "jarvis supports offline memory retrieval")
        store.put("beta", "this note mentions retrieval and memory")
        store.put("gamma", "unrelated content")

        val results = store.search(query = "memory retrieval", limit = 2)

        assertEquals(2, results.size)
        assertTrue(results.contains("jarvis supports offline memory retrieval"))
        assertTrue(results.contains("this note mentions retrieval and memory"))
    }
}
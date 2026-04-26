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

        val value = store.get("User Preference")
        assertTrue(value!!.contains("# User Preference"))
        assertTrue(value.contains("likes concise output"))
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

        store.put(
            "notes/alpha",
            """
            # Alpha

            ## Decisions

            - Jarvis supports offline memory retrieval and semantic lookup.
            """.trimIndent()
        )
        store.put(
            "notes/beta",
            """
            # Beta

            ## Details

            - This note mentions retrieval and memory systems.
            """.trimIndent()
        )
        store.put(
            "notes/gamma",
            """
            # Gamma

            ## Details

            - Unrelated content about weather.
            """.trimIndent()
        )

        val results = store.search(query = "memory retrieval", limit = 2)

        assertEquals(2, results.size)
        assertTrue(results.any { it.contains("memory") || it.contains("retrieval") })
        assertTrue(results.none { it.contains("weather") })
    }

    @Test
    fun updateSectionReplacesOnlyTargetSection() = runTest {
        val tempDir = Files.createTempDirectory("jarvis-memory-test")
        val store = MarkdownFileMemoryStore(baseDirectory = tempDir)

        store.put(
            "notes/projects",
            """
            # Projects

            ## Decisions

            - Keep old decision

            ## Ideas

            - Keep this section unchanged
            """.trimIndent()
        )

        store.updateSection(
            file = "notes/projects",
            section = "Decisions",
            content = "- Use section chunking for retrieval"
        )

        val value = store.get("notes/projects")
        assertTrue(value!!.contains("Use section chunking for retrieval"))
        assertTrue(value.contains("Keep this section unchanged"))
    }

    @Test
    fun appendAddsContentToExistingFile() = runTest {
        val tempDir = Files.createTempDirectory("jarvis-memory-test")
        val store = MarkdownFileMemoryStore(baseDirectory = tempDir)

        store.put(
            "notes/todo",
            """
            # Todo

            ## Notes

            - first item
            """.trimIndent()
        )

        store.append("notes/todo", "- second item")

        val value = store.get("notes/todo")
        assertTrue(value!!.contains("first item"))
        assertTrue(value.contains("second item"))
    }
}
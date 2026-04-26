package com.jarvis.memory

import com.jarvis.memory.index.JdbcSqliteVectorStore
import com.jarvis.memory.models.MemoryChunk
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JdbcSqliteVectorStoreTest {
    @Test
    fun upsertReplacesPreviousChunksForFile() {
        val tempDir = Files.createTempDirectory("jarvis-memory-sqlite-test")
        val dbPath = tempDir.resolve("memory.db")
        val store = JdbcSqliteVectorStore(dbPath)

        store.upsertFileChunks(
            filePath = "notes/alpha.md",
            chunks = listOf(
                chunk(id = "1", text = "old chunk", filePath = "notes/alpha.md"),
                chunk(id = "2", text = "old chunk 2", filePath = "notes/alpha.md")
            )
        )

        store.upsertFileChunks(
            filePath = "notes/alpha.md",
            chunks = listOf(chunk(id = "3", text = "new chunk", filePath = "notes/alpha.md"))
        )

        val fileChunks = store.getFileChunks("notes/alpha.md")
        assertEquals(1, fileChunks.size)
        assertEquals("new chunk", fileChunks.first().text)
    }

    @Test
    fun chunksPersistAcrossStoreInstances() {
        val tempDir = Files.createTempDirectory("jarvis-memory-sqlite-test")
        val dbPath = tempDir.resolve("memory.db")

        val writer = JdbcSqliteVectorStore(dbPath)
        writer.upsertFileChunks(
            filePath = "preferences/user.md",
            chunks = listOf(chunk(id = "pref-1", text = "prefers concise answers", filePath = "preferences/user.md"))
        )

        val reader = JdbcSqliteVectorStore(dbPath)
        val all = reader.getAllChunks()

        assertEquals(1, all.size)
        assertEquals("prefers concise answers", all.first().text)
        assertTrue(all.first().embedding.isNotEmpty())
    }

    private fun chunk(id: String, text: String, filePath: String): MemoryChunk {
        return MemoryChunk(
            id = id,
            text = text,
            filePath = filePath,
            section = "Notes",
            timestamp = 1_713_500_000_000,
            tokenCount = text.split(' ').size,
            embedding = floatArrayOf(0.1f, -0.3f, 0.5f)
        )
    }
}
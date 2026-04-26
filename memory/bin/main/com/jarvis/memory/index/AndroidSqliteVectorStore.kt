package com.jarvis.memory.index

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.jarvis.memory.models.MemoryChunk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

class AndroidSqliteVectorStore(
    private val databasePath: Path
) : VectorStore {
    init {
        Files.createDirectories(databasePath.parent)
        withDatabase { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_chunks (
                    id TEXT PRIMARY KEY,
                    text TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    section TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    token_count INTEGER NOT NULL,
                    embedding BLOB NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_chunks_file_path ON memory_chunks(file_path)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_chunks_timestamp ON memory_chunks(timestamp)")
        }
    }

    @Synchronized
    override fun upsertFileChunks(filePath: String, chunks: List<MemoryChunk>) {
        withDatabase { db ->
            db.beginTransaction()
            try {
                db.delete("memory_chunks", "file_path = ?", arrayOf(filePath))

                for (chunk in chunks) {
                    val values = ContentValues().apply {
                        put("id", chunk.id)
                        put("text", chunk.text)
                        put("file_path", filePath)
                        put("section", chunk.section)
                        put("timestamp", chunk.timestamp)
                        put("token_count", chunk.tokenCount)
                        put("embedding", chunk.embedding.toBlob())
                    }
                    db.insertOrThrow("memory_chunks", null, values)
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    @Synchronized
    override fun getAllChunks(): List<MemoryChunk> {
        return withDatabase { db ->
            db.query(
                "memory_chunks",
                arrayOf("id", "text", "file_path", "section", "timestamp", "token_count", "embedding"),
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toChunk())
                    }
                }
            }
        }
    }

    @Synchronized
    override fun getFileChunks(filePath: String): List<MemoryChunk> {
        return withDatabase { db ->
            db.query(
                "memory_chunks",
                arrayOf("id", "text", "file_path", "section", "timestamp", "token_count", "embedding"),
                "file_path = ?",
                arrayOf(filePath),
                null,
                null,
                null
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toChunk())
                    }
                }
            }
        }
    }

    private fun <T> withDatabase(block: (SQLiteDatabase) -> T): T {
        val db = SQLiteDatabase.openOrCreateDatabase(databasePath.toAbsolutePath().toString(), null)
        return db.use(block)
    }

    private fun android.database.Cursor.toChunk(): MemoryChunk {
        return MemoryChunk(
            id = getString(getColumnIndexOrThrow("id")),
            text = getString(getColumnIndexOrThrow("text")),
            filePath = getString(getColumnIndexOrThrow("file_path")),
            section = getString(getColumnIndexOrThrow("section")),
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            tokenCount = getInt(getColumnIndexOrThrow("token_count")),
            embedding = getBlob(getColumnIndexOrThrow("embedding")).toFloatArray()
        )
    }

    private fun FloatArray.toBlob(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (value in this) {
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    private fun ByteArray.toFloatArray(): FloatArray {
        if (isEmpty()) {
            return FloatArray(0)
        }
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(size / 4)
        var index = 0
        while (buffer.remaining() >= 4) {
            result[index++] = buffer.float
        }
        return if (index == result.size) result else result.copyOf(index)
    }
}
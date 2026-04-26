package com.jarvis.memory.index

import com.jarvis.memory.models.MemoryChunk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class JdbcSqliteVectorStore(
    private val databasePath: Path
) : VectorStore {
    init {
        Files.createDirectories(databasePath.parent)
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
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
                statement.execute("CREATE INDEX IF NOT EXISTS idx_memory_chunks_file_path ON memory_chunks(file_path)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_memory_chunks_timestamp ON memory_chunks(timestamp)")
            }
        }
    }

    @Synchronized
    override fun upsertFileChunks(filePath: String, chunks: List<MemoryChunk>) {
        withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement("DELETE FROM memory_chunks WHERE file_path = ?").use { deleteStmt ->
                    deleteStmt.setString(1, filePath)
                    deleteStmt.executeUpdate()
                }

                if (chunks.isNotEmpty()) {
                    connection.prepareStatement(
                        """
                        INSERT INTO memory_chunks (id, text, file_path, section, timestamp, token_count, embedding)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { insertStmt ->
                        for (chunk in chunks) {
                            insertStmt.setString(1, chunk.id)
                            insertStmt.setString(2, chunk.text)
                            insertStmt.setString(3, filePath)
                            insertStmt.setString(4, chunk.section)
                            insertStmt.setLong(5, chunk.timestamp)
                            insertStmt.setInt(6, chunk.tokenCount)
                            insertStmt.setBytes(7, chunk.embedding.toBlob())
                            insertStmt.addBatch()
                        }
                        insertStmt.executeBatch()
                    }
                }

                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    @Synchronized
    override fun getAllChunks(): List<MemoryChunk> {
        return withConnection { connection ->
            connection.prepareStatement(
                "SELECT id, text, file_path, section, timestamp, token_count, embedding FROM memory_chunks"
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<MemoryChunk>()
                    while (rs.next()) {
                        results += MemoryChunk(
                            id = rs.getString("id"),
                            text = rs.getString("text"),
                            filePath = rs.getString("file_path"),
                            section = rs.getString("section"),
                            timestamp = rs.getLong("timestamp"),
                            tokenCount = rs.getInt("token_count"),
                            embedding = rs.getBytes("embedding").toFloatArray()
                        )
                    }
                    results
                }
            }
        }
    }

    @Synchronized
    override fun getFileChunks(filePath: String): List<MemoryChunk> {
        return withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT id, text, file_path, section, timestamp, token_count, embedding
                FROM memory_chunks
                WHERE file_path = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, filePath)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<MemoryChunk>()
                    while (rs.next()) {
                        results += MemoryChunk(
                            id = rs.getString("id"),
                            text = rs.getString("text"),
                            filePath = rs.getString("file_path"),
                            section = rs.getString("section"),
                            timestamp = rs.getLong("timestamp"),
                            tokenCount = rs.getInt("token_count"),
                            embedding = rs.getBytes("embedding").toFloatArray()
                        )
                    }
                    results
                }
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        val jdbcUrl = "jdbc:sqlite:${databasePath.toAbsolutePath()}"
        return DriverManager.getConnection(jdbcUrl).use(block)
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
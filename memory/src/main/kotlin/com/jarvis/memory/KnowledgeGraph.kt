package com.jarvis.memory

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.file.Path

class KnowledgeGraph private constructor(
    context: Context,
    dbName: String
) : SQLiteOpenHelper(context, dbName, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS nodes (
                id TEXT PRIMARY KEY,
                file TEXT NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                path TEXT,
                content TEXT,
                summary TEXT,
                tags TEXT,
                aliases TEXT,
                links TEXT,
                importance REAL DEFAULT 0.5,
                confidence REAL DEFAULT 1.0,
                level INTEGER DEFAULT 1,
                created_at INTEGER,
                updated_at INTEGER,
                last_accessed INTEGER
            )
        """)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS edges (
                id TEXT PRIMARY KEY,
                from_node_id TEXT NOT NULL,
                to_node_id TEXT NOT NULL,
                type TEXT NOT NULL,
                weight REAL DEFAULT 1.0,
                created_at INTEGER
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_from ON edges(from_node_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_to ON edges(to_node_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_title ON nodes(title)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nodes_file ON nodes(file)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun upsertNode(node: KnowledgeNode) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", node.id)
            put("file", node.file)
            put("type", node.type.name)
            put("title", node.title)
            put("path", node.path.joinToString(" > "))
            put("content", node.content)
            put("summary", node.summary)
            put("tags", node.tags.joinToString(","))
            put("aliases", node.aliases.joinToString(","))
            put("links", node.links.joinToString(","))
            put("importance", node.importance)
            put("confidence", node.confidence)
            put("level", node.level)
            put("created_at", node.createdAt)
            put("updated_at", node.updatedAt)
            put("last_accessed", node.lastAccessed)
        }
        db.insertWithOnConflict("nodes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun addEdge(edge: GraphEdge) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", edge.id)
            put("from_node_id", edge.fromNodeId)
            put("to_node_id", edge.toNodeId)
            put("type", edge.type.name)
            put("weight", edge.weight)
            put("created_at", edge.createdAt)
        }
        db.insertWithOnConflict("edges", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getNode(id: String): KnowledgeNode? {
        return try {
            val db = readableDatabase
            val cursor = db.query("nodes", null, "id = ?", arrayOf(id), null, null, null)
            cursor.use {
                if (it.moveToFirst()) rowToNode(it) else null
            }
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeGraph", "Get node failed: ${e.message}")
            null
        }
    }

    fun getNodesByFile(file: String): List<KnowledgeNode> {
        val db = readableDatabase
        val cursor = db.query("nodes", null, "file = ?", arrayOf(file), null, null, null)
        return cursor.use { generateSequence { if (it.moveToNext()) rowToNode(it) else null }.toList() }
    }

    fun searchByTitle(query: String): List<KnowledgeNode> {
        return try {
            val db = readableDatabase
            val cursor = db.query("nodes", null, "title LIKE ?", arrayOf("%$query%"), null, null, null)
            cursor.use { generateSequence { if (it.moveToNext()) rowToNode(it) else null }.toList() }
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeGraph", "Search by title failed: ${e.message}")
            emptyList()
        }
    }

    fun getEdgesFrom(nodeId: String): List<GraphEdge> {
        return try {
            val db = readableDatabase
            val cursor = db.query("edges", null, "from_node_id = ?", arrayOf(nodeId), null, null, null)
            cursor.use { generateSequence { if (it.moveToNext()) rowToEdge(it) else null }.toList() }
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeGraph", "Get edges from failed: ${e.message}")
            emptyList()
        }
    }

    fun getEdgesTo(nodeId: String): List<GraphEdge> {
        return try {
            val db = readableDatabase
            val cursor = db.query("edges", null, "to_node_id = ?", arrayOf(nodeId), null, null, null)
            cursor.use { generateSequence { if (it.moveToNext()) rowToEdge(it) else null }.toList() }
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeGraph", "Get edges to failed: ${e.message}")
            emptyList()
        }
    }

    fun getRelatedNodes(nodeId: String, depth: Int = 1): List<KnowledgeNode> {
        if (depth <= 0) return emptyList()
        
        return try {
            val related = mutableSetOf<String>()
            val queue = ArrayDeque<String>()
            queue.add(nodeId)
            
            repeat(depth) {
                val currentLevel = queue.toList()
                queue.clear()
                currentLevel.forEach { id ->
                    getEdgesFrom(id).forEach { edge ->
                        if (related.add(edge.toNodeId)) queue.add(edge.toNodeId)
                    }
                    getEdgesTo(id).forEach { edge ->
                        if (related.add(edge.fromNodeId)) queue.add(edge.fromNodeId)
                    }
                }
            }
            
            related.mapNotNull { getNode(it) }
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeGraph", "Get related nodes failed: ${e.message}")
            emptyList()
        }
    }

    fun getAllNodes(): List<KnowledgeNode> {
        return try {
            val db = readableDatabase
            val cursor = db.query("nodes", null, null, null, null, null, null)
            cursor.use { generateSequence { if (it.moveToNext()) rowToNode(it) else null }.toList() }
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeGraph", "Get all nodes failed: ${e.message}")
            emptyList()
        }
    }

    fun deleteNode(id: String) {
        val db = writableDatabase
        db.delete("edges", "from_node_id = ? OR to_node_id = ?", arrayOf(id, id))
        db.delete("nodes", "id = ?", arrayOf(id))
    }

    private fun rowToNode(cursor: Cursor): KnowledgeNode {
        return KnowledgeNode(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            file = cursor.getString(cursor.getColumnIndexOrThrow("file")),
            type = runCatching { NoteType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))) }.getOrElse { NoteType.INBOX },
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            path = cursor.getString(cursor.getColumnIndexOrThrow("path"))?.split(" > ")?.filter { it.isNotBlank() } ?: emptyList(),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: "",
            summary = cursor.getString(cursor.getColumnIndexOrThrow("summary")) ?: "",
            tags = cursor.getString(cursor.getColumnIndexOrThrow("tags"))?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            aliases = cursor.getString(cursor.getColumnIndexOrThrow("aliases"))?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            links = cursor.getString(cursor.getColumnIndexOrThrow("links"))?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            importance = cursor.getFloat(cursor.getColumnIndexOrThrow("importance")),
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence")),
            level = cursor.getInt(cursor.getColumnIndexOrThrow("level")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            lastAccessed = cursor.getLong(cursor.getColumnIndexOrThrow("last_accessed"))
        )
    }

    private fun rowToEdge(cursor: Cursor): GraphEdge {
        return GraphEdge(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            fromNodeId = cursor.getString(cursor.getColumnIndexOrThrow("from_node_id")),
            toNodeId = cursor.getString(cursor.getColumnIndexOrThrow("to_node_id")),
            type = runCatching { EdgeType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))) }.getOrElse { EdgeType.REFERENCE },
            weight = cursor.getFloat(cursor.getColumnIndexOrThrow("weight")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }

    companion object {
        private var instance: KnowledgeGraph? = null
        private var currentDbPath: String? = null
        
        fun getInstance(context: Context, dbPath: Path): KnowledgeGraph {
            val pathString = dbPath.toAbsolutePath().toString()
            return synchronized(this) {
                if (instance != null && currentDbPath == pathString) {
                    instance!!
                } else {
                    instance?.close()
                    KnowledgeGraph(
                        context.applicationContext,
                        pathString
                    ).also { 
                        instance = it 
                        currentDbPath = pathString
                    }
                }
            }
        }
        
        fun resetInstance() {
            instance?.close()
            instance = null
        }
    }
}
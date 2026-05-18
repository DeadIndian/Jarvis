package com.jarvis.memory

import java.nio.file.Path
import java.nio.file.Paths

class BrainDirectory(
    basePath: Path,
    internalDataDir: Path? = null
) {
    val base: Path = basePath
    val notes: Path = base.resolve("notes")
    
    // Use internal storage for databases and cache if provided, otherwise stay in base
    private val persistenceBase: Path = internalDataDir ?: base
    
    val metadata: Path = persistenceBase.resolve("metadata")
    val embeddings: Path = persistenceBase.resolve("embeddings")
    val graph: Path = persistenceBase.resolve("graph")
    val cache: Path = persistenceBase.resolve("cache")
    val derived: Path = persistenceBase.resolve("derived")
    val logs: Path = persistenceBase.resolve("logs")

    val notesConcepts: Path = notes.resolve("concepts")
    val notesProjects: Path = notes.resolve("projects")
    val notesProcedures: Path = notes.resolve("procedures")
    val notesMemories: Path = notes.resolve("memories")
    val notesJournal: Path = notes.resolve("journal")
    val notesPeople: Path = notes.resolve("people")
    val notesInbox: Path = notes.resolve("inbox")

    val derivedSummaries: Path = derived.resolve("summaries")
    val derivedClusters: Path = derived.resolve("clusters")
    val derivedReflections: Path = derived.resolve("reflections")

    val metadataNodes: Path = metadata.resolve("nodes.db")
    val metadataAliases: Path = metadata.resolve("aliases.db")
    val metadataTags: Path = metadata.resolve("tags.db")

    val embeddingsVectors: Path = embeddings.resolve("vectors.db")
    val embeddingsModelInfo: Path = embeddings.resolve("model_info.json")

    val graphEdges: Path = graph.resolve("edges.db")
    val graphRelations: Path = graph.resolve("relations.db")

    fun ensureLayout() {
        val dirs = mutableListOf(
            base, notes, metadata, embeddings, graph, cache, derived, logs,
            notesConcepts, notesProjects, notesProcedures, notesMemories,
            notesJournal, notesPeople, notesInbox,
            derivedSummaries, derivedClusters, derivedReflections
        )
        if (persistenceBase != base) {
            dirs.add(0, persistenceBase)
        }
        
        dirs.forEach { dir ->
            try {
                dir.toFile().mkdirs()
            } catch (_: Exception) {
                // Ignore failures here, specific component access will log errors
            }
        }
    }

    fun notePath(type: NoteType, title: String): Path {
        val dir = when (type) {
            NoteType.CONCEPT -> notesConcepts
            NoteType.PROJECT -> notesProjects
            NoteType.PROCEDURE -> notesProcedures
            NoteType.MEMORY -> notesMemories
            NoteType.JOURNAL -> notesJournal
            NoteType.PERSON -> notesPeople
            NoteType.TASK -> notesInbox
            NoteType.RESEARCH -> notesInbox
            NoteType.SYSTEM -> notes.resolve("system")
            NoteType.IDEA -> notesInbox
            NoteType.REFERENCE -> notes.resolve("references")
            NoteType.INBOX -> notesInbox
        }
        val safeTitle = title.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(50)
        return dir.resolve("$safeTitle.md")
    }

    fun notePathFromFolder(folderPath: String, title: String): Path {
        val sanitizedFolder = folderPath.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9/_-]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        
        val safeTitle = title.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .take(50)
            .ifBlank { "note" }
        
        val fullPath = notes.resolve(sanitizedFolder)
        return fullPath.resolve("$safeTitle.md")
    }

    companion object {
        val default: BrainDirectory
            get() = throw IllegalStateException("BrainDirectory.default is not available on Android. Use withCustomPath.")
    }
}

enum class NoteType {
    CONCEPT,
    PROJECT,
    PROCEDURE,
    MEMORY,
    JOURNAL,
    PERSON,
    TASK,
    RESEARCH,
    SYSTEM,
    IDEA,
    REFERENCE,
    INBOX
}
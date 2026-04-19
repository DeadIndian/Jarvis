# JARVIS MEMORY SYSTEM — TECHNICAL SPECIFICATION

**Subsystem:** Long-Term Memory (Markdown + Embeddings Hybrid)
**Target Platform:** Android (Kotlin)
**Design Goal:** Human-readable persistence + efficient semantic retrieval

---

# 1. SYSTEM OBJECTIVE

Design a memory subsystem that:

- Stores knowledge in **Markdown files (human-readable, editable)**
- Enables **semantic retrieval via embeddings**
- Supports **LLM-assisted read/write**
- Maintains **performance and scalability on-device**

---

# 2. ARCHITECTURE OVERVIEW

## 2.1 Dual-Layer Model

```text
[ Markdown Files ]  ← Source of Truth (Readable, Editable)
         ↓
[ Chunking Layer ]
         ↓
[ Embedding Layer ]
         ↓
[ Vector Index (SQLite) ]
         ↓
[ Retrieval Engine ]
         ↓
[ LLM Context Injection ]
```

---

# 3. FILE STORAGE LAYER

## 3.1 Directory Structure

```text
/memory/
  /daily_logs/
  /notes/
  /preferences/
  /system/
```

---

## 3.2 File Naming Rules

- Daily logs: `YYYY-MM-DD.md`
- Notes: descriptive (`projects.md`, `ideas.md`)
- Preferences: `user.md`
- System memory: reserved namespace

---

## 3.3 Markdown Structure (MANDATORY FORMAT)

Each file must follow structured sections:

```markdown
# <Title>

## <Section Name>

- Entry 1
- Entry 2

## <Section Name>

- Entry 3
```

### Rules:

- Only `#` and `##` headings allowed
- Content must be list-based or short paragraphs
- No arbitrary formatting (to simplify parsing)

---

# 4. CHUNKING LAYER

## 4.1 Chunking Strategy

Primary method: **Section-based chunking**

Each `## section` = 1 chunk

Fallback:

- If section > 500 tokens → split into sub-chunks

---

## 4.2 Chunk Schema

```json
{
	"id": "uuid",
	"text": "string",
	"file_path": "memory/notes/projects.md",
	"section": "Decisions",
	"timestamp": 1713500000,
	"token_count": 120
}
```

---

## 4.3 Chunking Rules

- Preserve semantic meaning
- Do not split mid-sentence
- Maintain reference to original file + section

---

# 5. EMBEDDING LAYER

## 5.1 Interface

```kotlin
interface EmbeddingModel {
    fun embed(text: String): FloatArray
}
```

---

## 5.2 Requirements

- Must support **on-device inference**
- Quantized model preferred (4-bit / 8-bit)
- Max embedding size: 384–768 dimensions

---

## 5.3 Execution Strategy

- Synchronous for small batches
- Batch processing for indexing

---

# 6. VECTOR STORAGE (SQLITE)

## 6.1 Database Schema

```sql
CREATE TABLE memory_chunks (
    id TEXT PRIMARY KEY,
    text TEXT,
    file_path TEXT,
    section TEXT,
    timestamp INTEGER,
    embedding BLOB
);
```

---

## 6.2 Indexing

- No native vector DB required
- Use:
  - Linear scan (initial)
  - Optional: approximate indexing (later)

---

## 6.3 Embedding Storage

- Store as float array (serialized)
- Format: ByteBuffer / FloatArray → BLOB

---

# 7. RETRIEVAL ENGINE

## 7.1 Interface

```kotlin
interface MemoryRetriever {
    fun retrieve(query: String, k: Int = 5): List<MemoryChunk>
}
```

---

## 7.2 Retrieval Pipeline

```text
Query
 → Embed query
 → Compute cosine similarity
 → Rank results
 → Return top-K chunks
```

---

## 7.3 Ranking Enhancements

Final score =

```text
score = (similarity * 0.7)
      + (recency_weight * 0.2)
      + (source_weight * 0.1)
```

---

## 7.4 Source Weights

| Source      | Weight |
| ----------- | ------ |
| preferences | 1.0    |
| notes       | 0.8    |
| daily_logs  | 0.5    |

---

# 8. WRITE / UPDATE PIPELINE

## 8.1 Write Interface

```kotlin
interface MemoryWriter {
    fun append(file: String, content: String)
    fun updateSection(file: String, section: String, content: String)
}
```

---

## 8.2 Write Flow

```text
LLM Output
 → Validation Layer
 → Markdown Write
 → Re-chunk affected section
 → Re-embed updated chunks
 → Update DB
```

---

## 8.3 Validation Rules

- No file overwrite allowed
- Only:
  - Append
  - Section replace

- Reject malformed markdown

---

# 9. READ PIPELINE (LLM CONTEXT)

## 9.1 Context Injection Format

```text
Relevant Memory:

[1] (projects.md - Decisions)
Using Porcupine for wake word...

[2] (user.md - Preferences)
User prefers offline-first systems...
```

---

## 9.2 Constraints

- Max chunks: 3–8
- Max tokens: configurable (~1000–2000)

---

# 10. CHANGE DETECTION

## 10.1 File Monitoring

- Track file hash or last modified timestamp

---

## 10.2 Re-index Trigger

- On write → immediate re-index of affected section
- On app startup → scan for drift

---

# 11. PERFORMANCE REQUIREMENTS

| Operation       | Target Time |
| --------------- | ----------- |
| Embed chunk     | < 50ms      |
| Query retrieval | < 100ms     |
| Full re-index   | async only  |

---

# 12. MEMORY MANAGEMENT

## 12.1 Growth Control

- Max chunk count threshold
- Archive old logs

---

## 12.2 Archival Strategy

```text
daily_logs older than N days
 → summarize
 → move to /archive/
 → re-embed summary only
```

---

# 13. ERROR HANDLING

## 13.1 Failure Cases

- Embedding failure
- DB write failure
- Corrupt markdown

## 13.2 Recovery

- Retry embedding
- Fallback to raw text retrieval
- Log all failures

---

# 14. SECURITY CONSIDERATIONS

- No arbitrary file access
- Restrict writes to `/memory/`
- Validate all LLM-generated edits
- Prevent prompt injection via memory

---

# 15. LOGGING

## 15.1 Log Events

- Chunk creation
- Embedding generation
- Retrieval queries
- Write operations

---

## 15.2 Format

```json
{
	"event": "memory_retrieval",
	"query": "...",
	"results": ["chunk_id_1", "chunk_id_2"]
}
```

---

# 16. EXTENSIBILITY

Future support for:

- Hybrid search (keyword + semantic)
- Graph-based memory
- Cross-device sync
- Cloud embedding fallback

---

# 17. MODULE STRUCTURE

```text
memory/
 ├── storage/
 │    ├── MarkdownStore.kt
 │
 ├── chunking/
 │    ├── Chunker.kt
 │
 ├── embedding/
 │    ├── EmbeddingModel.kt
 │
 ├── index/
 │    ├── VectorStore.kt
 │
 ├── retrieval/
 │    ├── Retriever.kt
 │
 ├── writer/
 │    ├── MemoryWriter.kt
 │
 ├── models/
 │    ├── MemoryChunk.kt
```

---

# 18. ACCEPTANCE CRITERIA

The system is considered complete when:

- Markdown files are readable and editable manually
- Queries return semantically relevant results
- Writes update embeddings correctly
- Retrieval latency < 100ms (for ≤10k chunks)
- No data corruption occurs

---

# FINAL NOTE

This design deliberately:

- Avoids heavy dependencies
- Keeps system transparent and debuggable
- Balances **developer control + LLM capability**

If implemented as specified, this will scale cleanly from prototype → production without redesign.

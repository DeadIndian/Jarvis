# Jarvis Brain System — Full Specification (Mobile-First)

This specification defines the **knowledge/memory subsystem** for Jarvis.
Goal:

> A portable, structured, AI-native cognitive storage system built on markdown as canonical truth.

This is NOT just “notes”.
This is:

- semantic memory
- episodic memory
- procedural memory
- retrieval substrate
- reasoning context system

---

# 1. Core Principles

## 1.1 Canonical Truth = Markdown

All primary knowledge lives in `.md` files.

Reasons:

- human editable
- git compatible
- portable
- future proof
- model agnostic

No AI-generated data should overwrite canonical notes automatically.

---

## 1.2 Machine Layer Is Derived

Embeddings, graphs, summaries, indexes are derived artifacts.

They can always be rebuilt from markdown.

---

## 1.3 Knowledge Granularity = Section-Level

Jarvis reasons on sections, not files.

Every heading block becomes a semantic unit.

---

## 1.4 Retrieval Is Multi-Layered

Use:

- exact retrieval
- semantic retrieval
- graph traversal
- temporal weighting

Not embeddings alone.

---

# 2. Filesystem Layout

```text id="lsp9ry"
brain/
│
├── notes/
│   ├── concepts/
│   ├── projects/
│   ├── procedures/
│   ├── memories/
│   ├── journal/
│   ├── people/
│   └── inbox/
│
├── metadata/
│   ├── nodes.db
│   ├── aliases.db
│   └── tags.db
│
├── embeddings/
│   ├── vectors.db
│   └── model_info.json
│
├── graph/
│   ├── edges.db
│   └── relations.db
│
├── cache/
│
├── derived/
│   ├── summaries/
│   ├── clusters/
│   └── reflections/
│
└── logs/
```

---

# 3. Markdown Specification

---

# 3.1 File Rules

Each note:

- UTF-8
- markdown only
- no binary content
- one conceptual domain per file

---

# 3.2 Frontmatter Schema

Every file MAY contain frontmatter:

```md id="lnm0ng"
---
id: note_72831
type: concept
title: Transformers
tags:
  - ai
  - ml
aliases:
  - transformer architecture
importance: high
status: active
created: 2026-05-17
updated: 2026-05-17
---
```

---

# 3.3 Supported Types

```text id="qefr6v"
concept
project
procedure
memory
journal
person
task
research
system
idea
reference
```

---

# 3.4 Heading Semantics

Markdown headings define hierarchy.

Example:

```md id="8ib9ws"
# Transformers

## Attention

### Self Attention
```

Parsed as:

```text id="z3rtb0"
Transformers
 └── Attention
      └── Self Attention
```

---

# 3.5 Section = Atomic Knowledge Unit

Every heading block becomes:

```json id="iqk9a8"
{
  "node_id": "node_9182",
  "file_id": "note_72831",
  "title": "Self Attention",
  "level": 3,
  "parent": "Attention",
  "content": "...",
  "tags": [...],
  "links": [...],
  "embedding": [...]
}
```

---

# 4. Parsing Engine Specification

---

# 4.1 Parsing Pipeline

```text id="pzk21s"
Markdown File
    ↓
Lexer
    ↓
AST Builder
    ↓
Section Extractor
    ↓
Metadata Resolver
    ↓
Knowledge Object Generator
```

---

# 4.2 Parsing Rules

---

## RULE 1 — Headings Create Nodes

Each heading starts a new node.

---

## RULE 2 — Heading Scope

Content belongs to nearest parent heading.

---

## RULE 3 — Nested Context Inheritance

Child sections inherit:

- tags
- categories
- context

unless overridden.

---

## RULE 4 — Bullet Lists Become Structured Arrays

Example:

```md id="0c72bn"
- Redis
- PostgreSQL
```

Parsed:

```json id="5mh0hf"
{
	"items": ["Redis", "PostgreSQL"]
}
```

---

## RULE 5 — Code Blocks Stored Separately

````md id="3ef42y"
```python
print("hello")
```
````

````

Stored with:
- language
- hash
- content

---

## RULE 6 — Wiki Links Create Graph Edges

```md id="l1vcx3"
[[Transformers]]
````

Creates:

```json id="a0v6q8"
{
	"from": "Attention",
	"to": "Transformers",
	"relation": "reference"
}
```

---

## RULE 7 — Tags

```md id="fxb76r"
#ai
#ml
```

Stored separately.

---

# 5. Internal Knowledge Model

Every parsed node becomes:

```json id="p6r6cm"
{
  "id": "node_xxx",
  "file": "...",
  "type": "concept",
  "title": "...",
  "path": ["Transformers", "Attention"],
  "content": "...",
  "summary": "...",
  "tags": [...],
  "aliases": [...],
  "links": [...],
  "embedding_id": "...",
  "importance": 0.84,
  "confidence": 0.92,
  "created_at": "...",
  "updated_at": "...",
  "last_accessed": "..."
}
```

---

# 6. Embedding System

---

# 6.1 Embedding Granularity

Embeddings generated per:

- section
- not file

---

# 6.2 Embedding Pipeline

```text id="kz6ybd"
Node
  ↓
Text Cleaning
  ↓
Embedding Model
  ↓
Vector Storage
```

---

# 6.3 Embedding Rules

DO:

- embed semantic sections
- include heading context

DO NOT:

- embed raw huge files
- embed meaningless chunks

---

# 6.4 Suggested Metadata

```json id="49rd5z"
{
  "node_id": "...",
  "vector": [...],
  "model": "bge-small",
  "dimensions": 384
}
```

---

# 7. Knowledge Graph Specification

---

# 7.1 Edge Types

```text id="65n0cx"
reference
parent
child
related
contradicts
depends_on
extends
mentions
```

---

# 7.2 Graph Storage

Can use:

- SQLite
- LiteGraph
- Neo4j later

Start simple.

---

# 7.3 Graph Traversal Usage

Used for:

- contextual expansion
- associative recall
- topic exploration

---

# 8. Retrieval Engine Specification

---

# 8.1 Retrieval Pipeline

```text id="7i9k7m"
Query
  ↓
Intent Analysis
  ↓
Hybrid Retrieval
   ├── Exact Match
   ├── Tag Match
   ├── Embedding Similarity
   └── Graph Expansion
  ↓
Ranking
  ↓
Context Pack
```

---

# 8.2 Ranking Formula

Weighted score:

```text id="t8tx0l"
score =
semantic_similarity * 0.5 +
recency * 0.15 +
importance * 0.15 +
graph_relevance * 0.2
```

Tune later.

---

# 8.3 Retrieval Output

```json id="uxh1t5"
{
	"query": "...",
	"results": [
		{
			"node_id": "...",
			"score": 0.91,
			"reason": "semantic+graph"
		}
	]
}
```

---

# 9. Memory Classes

---

# 9.1 Semantic Memory

Facts and concepts.

---

# 9.2 Episodic Memory

Events and experiences.

Stored chronologically.

---

# 9.3 Procedural Memory

Step-by-step knowledge.

---

# 9.4 Working Memory

Temporary active context.

Stored in RAM/cache.

---

# 10. Active Context System

Jarvis maintains:

- active project
- recent topics
- current goals

This acts as pseudo-conscious continuity.

---

# 11. Incremental Indexing

Only changed files should be reprocessed.

Pipeline:

```text id="0w4n9v"
File Change
   ↓
AST Diff
   ↓
Affected Nodes
   ↓
Re-embed
   ↓
Graph Update
```

---

# 12. Memory Lifecycle

---

# 12.1 Creation

New notes enter inbox.

---

# 12.2 Promotion

Important notes moved to categories.

---

# 12.3 Compression

Old notes summarized.

---

# 12.4 Archival

Inactive notes deprioritized.

---

# 13. Reflection System

Jarvis can create:

- summaries
- inferred relationships
- topic clusters

Stored ONLY in:

```text id="8g2r1z"
derived/
```

Never overwrite original notes automatically.

---

# 14. Search Modes

---

## Mode A — Exact

Keyword/title.

---

## Mode B — Semantic

Embedding similarity.

---

## Mode C — Exploratory

Graph traversal.

---

## Mode D — Temporal

Recent memory recall.

---

# 15. Mobile Performance Constraints

---

# 15.1 Embedding Updates

Run async/background.

---

# 15.2 Large Files

Split aggressively.

---

# 15.3 Working Set

Only load active nodes into RAM.

---

# 16. Future Extensions

---

# 16.1 Multi-Modal Nodes

Images/audio/pdf parsing.

---

# 16.2 Procedural Extraction

Turn repeated actions into skills.

---

# 16.3 Self-Reflection

Jarvis-generated observations.

---

# 16.4 Knowledge Distillation

Summarize dense clusters automatically.

---

# 17. Recommended Initial Stack

---

## Storage

- Markdown files
- SQLite

---

## Parsing

- markdown-it / unified.js

---

## Embeddings

- bge-small
- nomic-embed

---

## Vector Search

- sqlite-vss
- Qdrant later

---

## Graph

- SQLite adjacency tables initially

---

# 18. Non-Negotiable Architectural Rules

---

## RULE A

Markdown is canonical truth.

---

## RULE B

Embeddings are derived, rebuildable artifacts.

---

## RULE C

Reasoning operates on nodes, not files.

---

## RULE D

Never trust embeddings alone.

---

## RULE E

World model and memory are separate systems.

---

# 19. Final System Identity

This architecture creates:

> A structured cognitive substrate for Jarvis

Not:

- a chatbot memory hack
- a vector DB wrapper
- a glorified notes app

This becomes:

- persistent memory
- semantic reasoning substrate
- long-term continuity engine
- model-independent cognition layer

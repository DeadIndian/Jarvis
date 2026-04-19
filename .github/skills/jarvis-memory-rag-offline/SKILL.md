---
name: jarvis-memory-rag-offline
description: "Use when implementing Jarvis memory design, including short-term context, markdown long-term storage, embedding generation, vector retrieval, and prompt injection for offline-first operation."
---

# Jarvis Memory RAG Offline

## Goal

Provide reliable memory storage and retrieval that improves assistant responses while remaining offline-first.

## Use When

- Building memory stores and retrieval.
- Adding new memory categories.
- Tuning retrieval quality and latency.

## Inputs

- Memory retention requirements.
- Embedding model and vector store choice.
- Query and ranking strategy.

## Workflow

1. Capture short-term interaction context in session memory.
2. Persist long-term facts in markdown storage with metadata.
3. Generate embeddings and index searchable chunks.
4. Retrieve top matches for each query with ranking and filters.
5. Inject compact retrieved context into LLM request pipeline.

## Output Contract

- Memory interfaces and storage adapters.
- Retrieval pipeline with ranking and filtering rules.
- Evaluation notes for recall and latency.

## Guardrails

- Avoid storing sensitive data without explicit policy.
- Limit injected context to prevent prompt bloat.
- Keep retrieval deterministic and measurable.

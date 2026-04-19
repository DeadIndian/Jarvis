---
name: jarvis-intent-hybrid-routing
description: "Use when implementing hybrid intent understanding for Jarvis, including fast-path classification, confidence thresholds, fallback to LLM intent parsing, and entity extraction."
---

# Jarvis Intent Hybrid Routing

## Goal

Provide low-latency intent resolution using deterministic fast path first and LLM fallback only when needed.

## Use When

- Adding intent categories.
- Tuning confidence thresholds.
- Improving latency or intent accuracy.

## Inputs

- Intent taxonomy.
- Fast-path patterns and classifier features.
- Fallback policy and confidence thresholds.

## Workflow

1. Apply keyword and regex match rules first.
2. Run lightweight classifier if fast rules are inconclusive.
3. Invoke LLM intent parsing for low confidence or ambiguous requests.
4. Normalize output to common intent schema with entities.
5. Emit decision telemetry for threshold tuning.

## Output Contract

- Unified intent schema.
- Fast-path and fallback routing logic.
- Metrics hooks for confidence, latency, and fallback rate.

## Guardrails

- Never bypass schema validation for LLM outputs.
- Keep deterministic intents out of LLM path when confidence is high.
- Log rationale for routing decisions.

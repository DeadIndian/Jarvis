---
name: jarvis-observability-performance
description: "Use when adding Jarvis logging, tracing, performance instrumentation, latency budgets, and diagnostics for intent, planning, execution, memory retrieval, and response stages."
---

# Jarvis Observability and Performance

## Goal

Make system behavior measurable and keep latency within defined service targets.

## Use When

- Adding structured logs.
- Defining performance metrics and traces.
- Investigating regressions in latency or reliability.

## Inputs

- Stage-level latency targets.
- Critical events for debugging.
- Required telemetry sink and retention policy.

## Workflow

1. Add structured logs for each pipeline stage.
2. Capture correlation ids across request lifecycle.
3. Instrument timers for STT, intent, planning, execution, retrieval, and response.
4. Add counters for retries, failures, and fallback usage.
5. Produce concise diagnostics reports from collected telemetry.

## Output Contract

- Logging schema and helper utilities.
- Performance instrumentation points.
- Regression checklist aligned to latency budgets.

## Guardrails

- Avoid logging sensitive user content by default.
- Keep telemetry overhead low.
- Ensure failures are actionable with context and stage tags.

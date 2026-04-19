---
name: Jarvis Testing and Quality Gates
description: "Use when writing or updating Jarvis tests, including state machine checks, planner and execution behavior, skill contracts, integration flows, and acceptance criteria validation."
applyTo: "**/src/test/**, **/src/androidTest/**, **/*Test.kt, **/*IT.kt"
---

# Jarvis Testing and Quality Gates

Follow these rules for test work.

1. Load these skills before significant testing updates.

- jarvis-test-harness
- jarvis-orchestrator-state-machine
- jarvis-planner-execution-engine
- jarvis-observability-performance
- jarvis-security-guardrails

2. Cover behavior, not just code paths.

- Unit tests for intent and planning logic.
- State transition tests across all Jarvis modes.
- Integration tests for event to response pipeline.
- Skill contract tests for validation and failure handling.

3. Align tests to acceptance criteria.

- Voice input to correct actions.
- Reliable skill execution.
- Memory retrieval improves response behavior.
- Correct mode behavior.
- No crash in normal usage.

4. Include diagnostics in failures.

- Stage name and correlation id.
- Expected and observed transition.
- Retry and timeout context when relevant.

Reference acceptance criteria in spec.md.

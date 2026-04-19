---
name: Jarvis Core Agent Workflows
description: "Use when implementing Jarvis core pipeline logic, event models, state transitions, intent routing, planning, execution, skill wiring, memory retrieval, and LLM routing in Kotlin modules."
applyTo: "core/**, intent/**, planner/**, execution/**, skills/**, memory/**, llm/**, logging/**"
---

# Jarvis Core Agent Workflows

Follow these rules for core assistant logic.

1. Load these skills before making major changes.

- jarvis-event-contracts
- jarvis-orchestrator-state-machine
- jarvis-intent-hybrid-routing
- jarvis-planner-execution-engine
- jarvis-skill-plugin-system
- jarvis-memory-rag-offline
- jarvis-llm-router
- jarvis-observability-performance
- jarvis-security-guardrails

2. Build flow in this order.

- Event contract
- State transition
- Intent routing
- Plan generation
- Step execution
- Memory retrieval and LLM injection
- Response and telemetry

3. Keep changes modular.

- Preserve clear boundaries between modules.
- Avoid direct Android framework calls in pure core modules.
- Add structured logs and correlation ids on all major stage boundaries.

4. Ship with tests.

- Add at least one unit test per behavior change.
- Add integration coverage for cross-module behavior.

Reference architecture and acceptance expectations in spec.md.

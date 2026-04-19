---
name: jarvis-orchestrator-state-machine
description: "Use when implementing Jarvis orchestrator flow, runtime state transitions, event routing, and pipeline coordination across idle, barn door, active, and house party modes."
---

# Jarvis Orchestrator State Machine

## Goal

Implement deterministic orchestration from input events through response output with explicit mode transitions.

## Use When

- Building the kernel orchestration loop.
- Implementing or updating mode transition logic.
- Debugging race conditions between states.

## Inputs

- Current state and incoming event.
- Transition rules and timeout policies.
- Allowed actions per state.

## Workflow

1. Encode state machine transitions in one central component.
2. Route events through intent, plan, execute, and respond phases.
3. Reject invalid transitions with structured errors.
4. Emit transition events for observability.
5. Add tests for nominal and edge-case transitions.

## Output Contract

- Orchestrator implementation with explicit transition table.
- State-aware event routing logic.
- Transition tests for all states.

## Guardrails

- Keep transition logic free from UI concerns.
- Ensure timeout and cancellation paths return to safe states.
- Prevent duplicate concurrent transitions for the same session.

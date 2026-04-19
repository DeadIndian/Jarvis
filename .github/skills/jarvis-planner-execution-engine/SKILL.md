---
name: jarvis-planner-execution-engine
description: "Use when building Jarvis planning and execution flow, including plan-step generation, sequential and parallel execution, retries, timeout policies, cancellation, and result aggregation."
---

# Jarvis Planner and Execution Engine

## Goal

Convert intents into executable plans and run them safely with reliability controls.

## Use When

- Adding plan schemas.
- Implementing execution scheduling.
- Handling retries, timeout, and cancellation behavior.

## Inputs

- Intent payload and extracted entities.
- Available skills and input schemas.
- Retry, timeout, and concurrency policies.

## Workflow

1. Build a structured plan with typed steps.
2. Validate each step against skill schema.
3. Execute steps sequentially or in parallel based on dependencies.
4. Apply retry policy and timeout per step.
5. Aggregate partial and final results into a stable response contract.

## Output Contract

- Plan model and planner implementation.
- Execution runtime with policy controls.
- Failure handling path and aggregated result model.

## Guardrails

- Do not run side-effect steps in parallel when order matters.
- Surface partial failures explicitly.
- Support cooperative cancellation at every step.

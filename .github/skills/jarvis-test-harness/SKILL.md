---
name: jarvis-test-harness
description: "Use when creating or expanding Jarvis tests, including unit tests, flow and state-machine tests, integration tests for orchestrator and skills, and acceptance criteria validation from the system spec."
---

# Jarvis Test Harness

## Goal

Create reliable automated tests that prove correctness for core flows, states, skills, and acceptance criteria.

## Use When

- Bootstrapping test infrastructure.
- Adding coverage for new pipeline features.
- Preventing regressions after refactors.

## Inputs

- Feature behavior and acceptance criteria.
- Test level targets: unit, integration, system.
- Required fakes, mocks, and fixtures.

## Workflow

1. Add fast unit tests for intent parsing, planning, and step execution policies.
2. Add state-machine tests for all transitions and timeout behavior.
3. Add integration tests for orchestrator end-to-end event flow.
4. Add skill contract tests for input validation and error handling.
5. Add acceptance tests matching the official system specification.

## Output Contract

- Test suite structure and naming conventions.
- Reusable fixtures and fake implementations.
- Coverage map aligned to spec requirements.

## Guardrails

- Keep unit tests deterministic and isolated.
- Avoid over-mocking stateful integration boundaries.
- Ensure failures explain behavior, not just snapshots.

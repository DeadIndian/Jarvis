---
name: jarvis-skill-plugin-system
description: "Use when defining Jarvis skill interfaces, skill registration, capability discovery, input validation, execution contracts, and plugin lifecycle management."
---

# Jarvis Skill Plugin System

## Goal

Create a consistent plugin architecture for skills with safe registration, discovery, and execution.

## Use When

- Adding a new skill.
- Refactoring skill interfaces.
- Implementing registry and dependency injection.

## Inputs

- Skill metadata and input schema.
- Required permissions and side effects.
- Execution constraints and timeout expectations.

## Workflow

1. Define skill interface and result contract.
2. Register skills with unique names and capabilities.
3. Validate input before execution.
4. Execute skill with timeout and structured error handling.
5. Emit execution telemetry and normalized results.

## Output Contract

- Stable skill interface definitions.
- Registry implementation with lookup and lifecycle methods.
- Example skill template for new plugins.

## Guardrails

- Prevent duplicate skill names.
- Enforce schema validation before execute.
- Restrict dangerous capabilities behind explicit permission checks.

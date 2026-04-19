---
name: jarvis-event-contracts
description: "Use when defining or changing Jarvis event models, payload schemas, sealed classes, and compatibility rules for event bus communication across modules."
---

# Jarvis Event Contracts

## Goal

Define stable, versionable event contracts used across the event-driven pipeline.

## Use When

- Adding a new event type.
- Changing event payloads.
- Standardizing result and error propagation.

## Inputs

- Producer module and consumer modules.
- Event payload fields and validation requirements.
- Backward compatibility constraints.

## Workflow

1. Create strongly typed event models for input, control, result, and error paths.
2. Separate domain events from transport details.
3. Include correlation identifiers for traceability.
4. Add validation and defaulting rules for optional fields.
5. Document compatibility impact for each change.

## Output Contract

- Updated event type definitions.
- Serialization and validation notes where needed.
- Migration notes when breaking changes are unavoidable.

## Guardrails

- Avoid ambiguous event names.
- Avoid untyped map payloads when typed models are possible.
- Never remove fields without migration strategy.

---
name: jarvis-llm-router
description: "Use when implementing Jarvis local-first LLM routing, provider selection, cloud fallback policy, request shaping, timeout handling, and response normalization."
---

# Jarvis LLM Router

## Goal

Route each LLM request to the best provider with local-first preference and controlled cloud fallback.

## Use When

- Integrating local models.
- Adding cloud providers.
- Tuning fallback and latency behavior.

## Inputs

- Request type and latency budget.
- Local model availability.
- Cloud fallback constraints and privacy policy.

## Workflow

1. Classify request by task type and required quality.
2. Attempt local model execution first.
3. Fallback to cloud only when local constraints are not met.
4. Normalize provider responses to a common schema.
5. Record route decisions and timing for optimization.

## Output Contract

- Provider-neutral request and response models.
- Routing policy implementation.
- Metrics for local success rate and fallback usage.

## Guardrails

- Respect privacy gates before cloud routing.
- Keep provider-specific logic isolated from callers.
- Enforce strict timeout and cancellation behavior.

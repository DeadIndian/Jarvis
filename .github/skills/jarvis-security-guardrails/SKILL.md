---
name: jarvis-security-guardrails
description: "Use when implementing Jarvis security controls, including skill permission boundaries, untrusted input validation, LLM output safety checks, and restricted file or system operations."
---

# Jarvis Security Guardrails

## Goal

Protect the device and user by enforcing capability boundaries and validating untrusted content.

## Use When

- Adding sensitive skills.
- Validating LLM-generated actions.
- Hardening file and system operation boundaries.

## Inputs

- Threat model and trust boundaries.
- Permission matrix by skill category.
- Validation requirements for actions and parameters.

## Workflow

1. Define per-skill permission levels and required approvals.
2. Validate all external and model-generated inputs.
3. Enforce allowlists for file paths, intents, and system actions.
4. Add fail-safe handling for ambiguous or high-risk requests.
5. Log security decisions with minimal sensitive data exposure.

## Output Contract

- Permission model and policy enforcement points.
- Validation layer for plan and execution inputs.
- Security test cases for abuse and boundary scenarios.

## Guardrails

- Default to deny for unknown capabilities.
- Never execute model output directly without validation.
- Keep security checks centralized and testable.

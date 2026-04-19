---
name: jarvis-android-services-permissions
description: "Use when implementing Android integration for Jarvis, including foreground service, microphone and overlay permissions, accessibility service wiring, wake-word lifecycle, and doze-safe behavior."
---

# Jarvis Android Services and Permissions

## Goal

Implement resilient Android runtime integration for continuous assistant behavior.

## Use When

- Creating foreground and accessibility services.
- Wiring mic, overlay, and wake-word lifecycle.
- Hardening behavior under doze and process pressure.

## Inputs

- Required Android components.
- Permission requirements by feature.
- Lifecycle and battery constraints.

## Workflow

1. Define service architecture and startup sequence.
2. Implement runtime permission checks and recovery paths.
3. Bind wake-word and STT lifecycles to Jarvis states.
4. Add doze and battery optimization handling.
5. Validate behavior across app background and restart scenarios.

## Output Contract

- Service classes and manifest declarations.
- Permission flow implementation and user prompts.
- Lifecycle tests and validation checklist.

## Guardrails

- Never start always-on audio without explicit state and permission.
- Avoid background behavior that violates modern Android limits.
- Provide clear degraded-mode behavior when permissions are denied.

# Jarvis Project Checkpoint

Last updated: 2026-04-19

## Why this file exists

This file is a working project snapshot so we can pause for side tasks and resume quickly without losing context.

## Completed so far

1. Project scaffolding

- Multi-module Kotlin structure created.
- Modules wired in Gradle: app, core, input, intent, planner, execution, skills, memory, llm, output, android, logging.
- Gradle wrapper added and working.

2. Core contracts and architecture baseline

- State model and event contracts implemented in core.
- Event bus interface and in-memory implementation added.
- Module-level interfaces added for intent, planner, execution, skills, memory, llm, output, logging.

3. Pipeline vertical slice (MVP logic)

- Rule-based intent routing for OPEN_APP intent.
- Simple planner mapping OPEN_APP to AppLauncher step.
- In-memory skill registry and AppLauncher mock skill.
- Skill execution engine.
- Pipeline orchestrator with guarded state transitions.
- State transitions handled for:
  - PowerButtonHeld -> BARN_DOOR
  - HousePartyToggle(true) -> HOUSE_PARTY
  - WakeWordDetected only from HOUSE_PARTY -> ACTIVE
  - TimeoutElapsed -> IDLE

4. Tests

- Orchestrator transition and pipeline tests added in execution test module.

5. Android MVP app

- New app module created and wired.
- Launcher activity implemented with simple controls to dispatch events.
- Basic UI added to:
  - Send text input
  - Trigger power hold
  - Enable house party
  - Trigger wake word
  - Trigger timeout
- UI output channel and Android logger adapters added.

6. Build status

- Debug APK successfully built:
  - app/build/outputs/apk/debug/app-debug.apk

## Current product state

This is a functional MVP shell, not a full assistant yet.

What works now:

- App installs and launches.
- Event -> intent -> plan -> execute -> respond loop works for text input path.
- State transitions can be tested from buttons in the app UI.

What is placeholder/mock:

- Real STT integration.
- Real wake-word engine.
- Real Android foreground/accessibility/overlay runtime services.
- Real device actions (AppLauncher is mock output).
- Real memory retrieval and llm routing.

## Remaining work to reach practical MVP

1. Input and runtime

- Integrate Android SpeechRecognizer path (partial + final).
- Wire microphone permission flow end-to-end.
- Add foreground service lifecycle for assistant runtime.

2. Skills and execution

- Implement first real Android skill (open installed app by package resolution).
- Add timeout/cancellation policy in execution runtime.
- Add failure and retry handling by plan step policy.

3. Memory and LLM

- Implement memory storage adapter and basic retrieval path.
- Add LLM router stub implementation with local-first contract.

4. Android integration hardening

- Improve manifest with required components and exported rules.
- Add runtime degrade behavior for denied permissions.
- Add simple diagnostics surface in UI.

5. Testing and release readiness

- Add integration tests for app module pipeline wiring.
- Add smoke instrumentation test for launch and command flow.
- Add signed build instructions and release checklist.

## Resume plan (recommended next execution order)

1. Implement real AppLauncher skill (Android package manager).
2. Integrate SpeechRecognizer with runtime permission checks.
3. Add foreground service and tie it to state transitions.
4. Add one persistence-backed memory store path.
5. Expand tests for service and permission behavior.

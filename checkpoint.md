# Jarvis Project Checkpoint

Last updated: 2026-04-19

## Why this file exists

This file is a working project snapshot so we can pause for side tasks and resume quickly without losing context.

## Status Summary

This section is intentionally strict and reality-based.

### Done

1. Project scaffolding

- Multi-module Kotlin structure is present.
- Modules are wired in Gradle: app, core, input, intent, planner, execution, skills, memory, llm, output, android, logging.
- Gradle wrapper works.

2. Core contracts and baseline wiring

- State model and event contracts implemented in core.
- Event bus interface and in-memory implementation added.
- Module-level interfaces exist for intent, planner, execution, skills, memory, llm, output, logging.

3. Pipeline vertical slice (text and voice input to skill output)

- Rule-based intent routing for OPEN_APP intent.
- Simple planner mapping OPEN_APP to AppLauncher step.
- Skill execution engine and in-memory skill registry.
- Pipeline orchestrator with guarded transitions.
- Implemented state transitions:
  - PowerButtonHeld -> BARN_DOOR
  - HousePartyToggle(true) -> HOUSE_PARTY
  - WakeWordDetected only from HOUSE_PARTY -> ACTIVE
  - TimeoutElapsed -> IDLE

4. Android MVP app shell

- App module is wired and launchable.
- Main activity provides controls for text input and state events.
- UI output channel and Android logger adapter are connected.

5. Real app launch behavior

- AppLauncher skill in app runtime delegates to package-manager-based launcher.
- Launcher queries installed launchable apps and starts matches.
- Manifest has launcher queries for package visibility.

6. SpeechRecognizer path and mic permission flow

- Android SpeechRecognizer is integrated with partial and final callbacks.
- RECORD_AUDIO runtime permission flow is wired from the UI mic action.
- Denied permission and recognizer errors show degraded-mode logs in app UI.

7. Foreground runtime service path

- Foreground service exists with notification channel and ongoing notification.
- Service start/stop is tied to state-driven policy in app flow.

8. Memory and LLM module implementations (module-complete)

- Memory: markdown file-backed store with key put/get and deterministic token search.
- LLM: local-first router with optional cloud fallback gate.

9. Unit test coverage for implemented slices

- Orchestrator transition and pipeline tests in execution module.
- Runtime policy tests in app module.
- App integration tests for pipeline wiring and launch command flow in app module.
- Android smoke instrumentation test for launch -> input -> action flow.
- Memory store unit tests in memory module.
- Local-first router unit tests in llm module.

10. Build artifact status

- Debug APK present at app/build/outputs/apk/debug/app-debug.apk.

11. Runtime memory + LLM orchestration integration

- Pipeline orchestrator now retrieves memory context per request.
- Unknown or failed action paths now use LLM-generated conversational fallback responses.
- Interaction summaries are persisted to memory store for future retrieval.
- App runtime wires MarkdownFileMemoryStore + LocalFirstLLMRouter (local provider enabled, cloud fallback disabled).

12. Execution policy hardening

- Skill execution runtime now supports per-step timeout policy.
- Skill execution runtime now supports per-step retry policy with capped retries.
- Execution path supports cooperative cancellation via coroutine cancellation propagation.
- Execution tests cover retry success, timeout failure, and cancellation behavior.

## Current Product State

This is a working foundation MVP, not a practical end-user assistant.

### Working now

- App installs, launches, and runs core event flow.
- Input -> intent -> plan -> execute -> respond works for current MVP paths.
- State transitions can be manually exercised from app UI controls.

### Still placeholder or not integrated

- Continuous conversation/runtime behavior beyond tap-to-listen capture.
- Real wake-word engine integration (button simulation exists, real detector does not).
- Accessibility and overlay service integration.
- On-device model artifact management and runtime readiness checks for LiteRT-LM.

## Remaining Work To Reach Practical MVP

1. Input/runtime completeness

- Integrate a real wake-word engine and lifecycle gating by state.
- Expand voice runtime behavior beyond current tap-to-listen MVP loop.

2. Android system integration hardening

- Add accessibility-service integration where required by product scope.
- Add overlay permission/service path where required by product scope.
- Validate background/doze behavior and restart reliability on device.

3. Testing and release readiness

- Add signed build instructions and release checklist.

4. Production on-device LLM readiness

- Ship and manage on-device model artifacts (for current target: deepseek-r1-distill-qwen-1.5b.task).
- Add runtime health checks and user guidance when model artifact is missing.
- Tune prompt shaping and sampling defaults for production behavior.

## Resume Plan (Recommended Next Order)

1. Finalize LiteRT-LM on-device model packaging/download path for deepseek-r1-distill-qwen-1.5b.task.
2. Integrate real wake-word engine lifecycle with state-gated activation.
3. Add signed build instructions and release checklist.
4. Harden accessibility and overlay service integration for product scope.

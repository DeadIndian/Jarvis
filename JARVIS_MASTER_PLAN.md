# Jarvis Master Plan

## Vision

Build a personal voice assistant named "Jarvis" with a modular architecture that is very easy to modify.

Core goals:

- Add new commands quickly.
- Change assistant name/configuration easily.
- Swap major components (wake word, STT, core model/logic, TTS, executors) with minimal code changes.
- Stay future-proof for mobile, server, and desktop layers.

## Product Layers

1. Mobile App (current focus)

- This is the active build target right now.
- Intended to become default digital assistant app on Samsung/Android.
- Should be triggerable via assistant invocation (including side button path when available) and wake word.

2. Server (future)

- Planned as a Go server.
- Will act as "advanced brain" for heavier reasoning, orchestration, and complex actions.

3. Other Apps (future)

- Desktop clients for Linux/Windows and potentially other platforms.
- Should reuse shared contracts (commands, events, policies, capabilities).

## Architectural Principles

1. Modularity first

- Everything behind interfaces/contracts.
- No hard binding between orchestrator and concrete engines.

2. Replaceability

- Swap one component without rewriting system core.
- Example swaps: `Porcupine -> other wake engine`, `local STT -> cloud STT`, `rule parser -> LLM parser`.

3. Predictable command structure

- Commands must follow a strict schema.
- Registry validates schema at startup.
- Command behavior should be declarative + handler-based.

4. Future-proof boundaries

- Keep mobile runtime and future server runtime separated by adapter/transport interfaces.
- Avoid deeply coupling mobile app logic to local-only assumptions.

## Command Contract (Draft)

Commands should be predictable and structured (not ad-hoc). Draft shape:

```yaml
id: "home.lights.on"
description: "Turn on room lights"
activates_on:
	intents: ["turn on lights", "lights on"]
	entities: ["room?"]
guards:
	requiresNetwork: false
	allowedModes: ["active", "home"]
executes:
	handler: "plugins/home/lights_on"
	timeoutMs: 5000
response:
	success: "Lights are on."
	failure: "I could not reach your light hub."
```

Minimum required fields (initial):

- `id`
- `activates_on`
- `executes`

Recommended optional fields:

- `description`
- `guards`
- `response`

## Mobile App Scope (Current Phase)

Primary responsibilities:

- Wake word listening using Porcupine.
- Assistant invocation integration as default digital assistant app.
- Voice pipeline orchestration: wake -> STT -> intent/command resolution -> execution -> TTS.
- Policy controls for when assistant is active/inactive.

Planned policy controls:

- Inactive while sleeping.
- Inactive during work windows.
- Active only at selected times.
- Manual user toggle (on/off).

## Core Mobile Modules (Pluggable)

- `WakeWordEngine`
- `SpeechToTextEngine`
- `IntentResolver`
- `CommandRegistry`
- `PolicyEngine`
- `ActionExecutor`
- `TextToSpeechEngine`
- `AssistantOrchestrator`

Each module should have:

- Interface/contract
- One default implementation
- Config-based selection (replace without major refactor)

## Runtime Flow (Target)

1. Wake trigger (assistant invoke or wake word).
2. `PolicyEngine` checks if assistant is currently allowed to activate.
3. Capture speech and run STT.
4. Resolve intent/command via command registry.
5. Execute command handler.
6. Return spoken/text response.
7. Log outcome for debugging and iteration.

## Configuration Strategy

Configuration should be externalized so changes do not require code edits where possible.

Config categories:

- Assistant identity (name, voice, language)
- Component selection (wake/STT/TTS/intent)
- Policy windows (sleep/work/active schedules)
- Enabled command packs

## Personal-Use Product Decision

This assistant is for personal use.

Current decision:

- No extra user-facing privacy/recording indicators are required in this phase.
- Focus on functionality and optimization first.

Note:

- This can be revisited later if distribution scope changes.

## MVP Checklist (Mobile First)

1. Android app skeleton with modular package structure.
2. Interface contracts for all core modules.
3. Porcupine wake-word integration as first `WakeWordEngine`.
4. Basic STT/TTS adapters.
5. Command schema + registry + validation.
6. 5 starter commands (time, open app, reminder, mode toggle, help).
7. Policy scheduling (sleep/work/manual mode).
8. Stub `BrainConnector` interface for future Go server.

## Future Server Integration (Go)

Planned interface:

- Request: user text/audio metadata + context + available capabilities.
- Response: structured action plan and/or direct response text.
- Transport: HTTP/WebSocket initially (to be finalized later).

Server responsibilities (future):

- Complex planning
- Long-context memory operations
- Multi-step tool orchestration
- Potential cross-device coordination

## Immediate Next Step

Start implementation with mobile architecture skeleton and command schema validator before feature-heavy command development.

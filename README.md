# Jarvis

Jarvis is an Android, modular, offline-first assistant project built around an agentic pipeline:

Input -> Intent -> Plan -> Execute -> Respond

The architecture is split into focused modules so each subsystem can evolve independently (input, intent, planner, execution, skills, memory, llm, output, android, logging).

## Product intro

Current MVP state:

- A runnable Android app module exists and builds to a debug APK.
- Core event-driven pipeline is wired and testable.
- State transitions (IDLE, BARN_DOOR, ACTIVE, HOUSE_PARTY) are implemented and controllable from UI actions.
- App launch skill path is wired to Android package manager resolution in app runtime.
- SpeechRecognizer mic input and foreground runtime service paths are integrated in MVP form.

This is a foundation MVP intended for rapid iteration toward full voice runtime, real skills, and persistent memory.

## How to use

1. Complete environment setup using [setup.md](setup.md).
2. Build the debug APK:

- ./gradlew :app:assembleDebug

3. Install it:

- adb install -r app/build/outputs/apk/debug/app-debug.apk

4. Launch it:

- adb shell am start -n com.jarvis.app/.MainActivity

5. In the app, test flow with buttons:

- Send text command (example: open WhatsApp)
- Trigger Power Hold, House Party, Wake Word, Timeout transitions

## Project context checkpoint

For an up-to-date work snapshot (done vs remaining), see checkpoint.md.

## Next milestone focus

- Integrate production on-device LLM runtime (LiteRT-LM, no HTTP server dependency)
- Load and run local model artifacts on phone (deepseek-r1-distill-qwen-1.5b.task)
- Keep memory retrieval + orchestration path local-first and private
- Add release signing instructions and checklist

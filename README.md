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
- Memory retrieval, LLM fallback response, and on-device model management are wired end-to-end.
- Execution engine supports timeout, retry, and cancellation policies per plan step.

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

## Compare Jarvis local vs Google on-device

Jarvis now includes an in-app LLM backend selector so you can compare output quality and latency:

- `Jarvis LiteRT (current)` uses your downloaded `.litertlm/.task` model artifacts.
- `Google Gemini Nano via AICore` uses ML Kit Prompt API on top of Android AICore.
- `Jarvis LiteRT + Gemini cloud fallback` keeps your current local-first flow with cloud fallback.
- `Gemini cloud only` routes directly to the cloud provider.

How to compare quickly:

1. Open `LLM Backend` in the app and pick one mode.
2. Tap `Apply Backend`.
3. Send the same prompt in each mode and compare `Jarvis:` output + response behavior in logs.

Notes for Google on-device mode:

- It only works on supported AICore/Gemini Nano devices.
- If status is not ready (`UNAVAILABLE`, `DOWNLOADABLE`, `DOWNLOADING`), Jarvis logs that state and returns no local AICore output for that request.
- For best reliability, keep device online after setup so AICore can finish initialization and model/config downloads.

## On-device model setup (step by step)

Jarvis uses LiteRT local model artifacts for on-device inference.

If you see an error about Hugging Face authentication or license acceptance, follow these exact steps.

1. Accept model access on Hugging Face

- Open https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
- Sign in and accept the model license/access terms.

2. Create a Hugging Face access token

- Open https://huggingface.co/settings/tokens
- Create a read token.

3. Add local config in .env (recommended)

- Create a local .env file at repo root:

```bash
cp .env.example .env
```

- Add values (example):

```env
JARVIS_HF_TOKEN=hf_your_token_here
JARVIS_LLM_BACKEND=local-only
```

Notes:

- .env is git-ignored in this repo.
- Build reads values from .env first, then Gradle properties, then system environment variables.

4. Optional: use hybrid mode with cloud fallback

If you want local-first with cloud fallback instead of strict local-only:

```env
JARVIS_LLM_BACKEND=hybrid
JARVIS_GEMINI_API_KEY=your_gemini_key
```

5. Rebuild and reinstall

- ./gradlew :app:assembleDebug
- adb install -r app/build/outputs/apk/debug/app-debug.apk

6. Download model in app

- Open Jarvis app
- Tap Refresh
- Select Gemma 3N entry
- Tap Download

7. Verify local runtime

- Status should move to Available
- Logs should no longer show authentication/licensing errors

### No-token fallback (manual sideload)

If you do not want to use a token in app builds:

1. Download a LiteRT model file manually from a model page you have access to (for example a .litertlm artifact).
2. Push it to device temp storage:

- adb push /path/to/model.litertlm /data/local/tmp/model.litertlm

3. Copy it into Jarvis app internal model directory (debug build):

- adb shell run-as com.jarvis.app mkdir -p files/llm
- adb shell run-as com.jarvis.app cp /data/local/tmp/model.litertlm files/llm/

4. Reopen Jarvis and tap Refresh. The model should appear as Available.

## Project context checkpoint

For an up-to-date work snapshot (done vs remaining), see checkpoint.md.

## Next milestone focus

- Validate on-device model download and inference flow end-to-end on real device
- Add user-facing model status UI (downloading, available, missing states)
- Integrate real wake-word engine lifecycle with state-gated activation
- Add signed build configuration and release instructions
- Harden accessibility and overlay service integration

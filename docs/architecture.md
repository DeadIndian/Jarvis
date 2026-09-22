# Architecture

Jarvis is a phone-first voice assistant: **the phone is the whole assistant.** No middle
server and no custom brain proxy are required — cloud/server is an optional upgrade, not a
dependency. This document describes the actual wired state of the code.

Android app (module `:app`, package `com.jarvis.app`, Kotlin + Compose, minSdk 26 / target 35, JVM 17).

## Pipeline

```
Trigger → STT → VoiceSessionManager → DeterministicRouter
                                        ├─ LocalTool     → validate → execute → speak
                                        ├─ Ambiguous     → on-device LLM (tool JSON) → else cloud agent
                                        └─ ServerHandoff  → cloud agent (tool loop + MCP)
```

- An activation **ding** (`LocalAudioFeedback`, `ToneGenerator`) fires on trigger, target <250ms.
- **Barge-in:** a new session stops in-flight TTS.
- All spoken replies funnel through one `say()` (mirror to UI, then TTS).

## Entry points

- **`MainActivity`** — chat UI + mic tap, with a fast-path `resolveLocally()` for time/date/battery/greeting.
- **`AssistantOverlayActivity`** — floating bubble via `ASSIST` / `VOICE_COMMAND` (side-button). Auto-starts a session, dings, listens.

Both construct an identical `VoiceSessionManager`. Also present: `SplashActivity`, `BootReceiver`.

## Modules (`app/src/main/kotlin/com/jarvis/app/`)

| Area | File | Role |
|---|---|---|
| Session | `core/session/VoiceSessionManager.kt` | Orchestrator: trigger → route → execute → speak |
| Routing | `core/router/DeterministicRouter.kt` | Regex/keyword hot path — 9 tool patterns + server keywords |
| On-device LLM | `core/llm/LocalBackend.kt` | LiteRT model + BYO cloud keys (Gemini/OpenAI/Anthropic) |
| LiteRT bridge | `LiteRtRuntimeBridge.kt` | MediaPipe `tasks-genai`, CPU/XNNPACK, cached client |
| Cloud agent | `core/llm/AgentBackend.kt` | OpenAI-compatible tool loop (max 5 rounds) + MCP tools |
| Tool schemas | `core/tools/ToolCatalog.kt` | OpenAI function schemas — single source of truth (9 tools) |
| Safety | `core/security/ActionValidator.kt` | Allowlist + arg ranges + sensitive-tool confirm gate |
| Execution | `core/tools/PhoneToolExecutor.kt` | Real Android side effects (audio, intents, torch, contacts) |
| Tool JSON | `core/tools/PhoneToolJsonParser.kt` | Parses on-device model output into a validated action |
| Memory | `core/memory/ConversationMemory.kt` | In-RAM ~1500-token window, 200-turn cap (short-term only) |
| MCP | `core/mcp/McpClient.kt` | Minimal Streamable-HTTP JSON-RPC client (agent-only) |
| Config | `core/config/AgentConfigRepository.kt` | EncryptedSharedPreferences: agent url/key/model, MCP |
| STT | `core/stt/AndroidSpeechToText.kt` | Android `SpeechRecognizer` |
| TTS | `core/audio/LocalTtsEngine.kt` | Android `TextToSpeech` |
| Trigger | `core/trigger/TriggerSource.kt` | PTT / side-button / test trigger interface |

## Phone tools (9)

`set_volume`, `toggle_bluetooth`, `toggle_wifi`, `media_control`, `open_app`, `make_call`,
`send_whatsapp`, `read_notifications`, `toggle_flashlight`. Every path (router / on-device / agent)
validates before execution — nothing runs a raw model/server tool call blindly.

## Real vs. documented

| Documented / aspirational | Actual code |
|---|---|
| `HermesBackend` streams SSE for ServerHandoff | Scaffold only; never instantiated. ServerHandoff → `AgentBackend` |
| Local neural TTS/STT | Android `TextToSpeech` + Google `SpeechRecognizer` |
| VAD engine | `VadEngine` interface + native `cpp/` exist but are unwired |
| No API keys in APK | Cloud keys baked via `BuildConfig`/`.env` at build time (known debt) |
| Custom "Jarvis" wake word | Not present |

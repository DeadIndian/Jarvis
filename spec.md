# JARVIS ANDROID SYSTEM — FULL TECHNICAL SPECIFICATION

**Platform:** Android (Kotlin)
**Architecture Style:** Modular, Event-Driven, Offline-First
**Core Paradigm:** Agentic system (Intent → Plan → Execute → Respond)

---

# 1. SYSTEM OVERVIEW

## 1.1 Objective

Build an on-device AI assistant that:

- Accepts multimodal input (voice primarily)
- Understands intent (hybrid system)
- Executes actions via modular skills
- Maintains persistent memory
- Operates across multiple modes/states
- Works offline with optional cloud augmentation

---

## 1.2 High-Level Pipeline

```text
Input (Voice/UI/System)
        ↓
Event Bus
        ↓
Orchestrator (Kernel)
        ↓
Intent Layer (Hybrid)
        ↓
Planner
        ↓
Execution Engine (Skills)
        ↓
Memory (RAG) + LLM
        ↓
Response (TTS/UI)
```

## 1.3 Runtime Model

**Online-First with Offline Fallback:**
- Primary: Online LLM providers (OpenAI, Anthropic, Gemini, etc.)
- Per-provider fallback with smart cooldown (e.g., rate limits, credit exhaustion)
- Offline support retained for no-internet scenarios and future on-device AI improvements

**See Also:** [memory-spec.md](memory-spec.md) for memory/RAG implementation details.

---

# 2. CORE ARCHITECTURE

## 2.1 Module Breakdown

```text
jarvis/
 ├── core/
 │    ├── orchestrator/
 │    ├── state/
 │    ├── eventbus/
 │
 ├── input/
 │    ├── stt/
 │    ├── wakeword/
 │
 ├── intent/
 │    ├── fastpath/
 │    ├── llm/
 │
 ├── planner/
 │
 ├── execution/
 │
 ├── skills/
 │    ├── registry/
 │    ├── implementations/
 │
 ├── memory/
 │
 ├── llm/
 │    ├── local/
 │    ├── cloud/
 │
 ├── output/
 │    ├── tts/
 │    ├── ui/
 │
 ├── android/
 │    ├── services/
 │    ├── permissions/
 │
 ├── logging/
```

---

# 3. ORCHESTRATOR (KERNEL)

## 3.1 Responsibility

- Central runtime controller
- Event routing
- State transitions
- Pipeline coordination

---

## 3.2 Interface

```kotlin
interface Orchestrator {
    fun dispatch(event: Event)
    fun transitionState(state: JarvisState)
    fun registerSkill(skill: Skill)
}
```

---

## 3.3 Execution Flow

```text
Event → Intent → Plan → Execute → Respond
```

---

# 4. EVENT BUS

## 4.1 Purpose

Decoupled communication between modules.

---

## 4.2 Event Types

```kotlin
sealed class Event {
    data class VoiceInput(val text: String) : Event()
    data class TextInput(val text: String) : Event()
    data class WakeWordDetected(val keyword: String) : Event()
    data class SkillResult(val result: SkillResult) : Event()
    data class Error(val error: Throwable) : Event()
}
```

---

# 5. STATE / MODE SYSTEM

## 5.1 States

```kotlin
enum class JarvisState {
    IDLE,
    BARN_DOOR,
    ACTIVE,
    THINKING,
    SPEAKING,
    HOUSE_PARTY
}
```

---

## 5.2 Behavior Matrix

| State       | Mic | Wake Word | Overlay  | Notes                  |
| ----------- | --- | --------- | -------- | ---------------------- |
| IDLE        | Off | No        | No       | Minimal power          |
| BARN_DOOR   | On  | No        | Yes      | Manual trigger         |
| ACTIVE      | On  | No        | Yes      | Conversation           |
| THINKING    | Off | No        | Yes      | Processing request     |
| SPEAKING    | Off | No        | Yes      | Responding to user     |
| HOUSE_PARTY | On  | Yes       | Optional | Always listening       |

---

## 5.3 Transitions

- Power button hold → BARN_DOOR
- Wake word → ACTIVE
- Timeout → IDLE
- Manual toggle → HOUSE_PARTY

---

# 6. INPUT SYSTEM

## 6.1 STT

- Implementation: Android SpeechRecognizer
- Mode: Streaming (partial + final results)

```kotlin
interface STT {
    fun startListening()
    fun stopListening()
}
```

---

## 6.2 Wake Word

- [x] **Engine:** Open-source alternative using Android SpeechRecognizer (`OpenWakeWordEngine`)
- Active in IDLE and ACTIVE states
- Keyword: "Jarvis"

---

# 7. INTENT SYSTEM (HYBRID)

## 7.1 Fast Path

- Keyword matching
- Regex
- Lightweight classifier

---

## 7.2 LLM Path

- Handles complex / ambiguous queries

---

## 7.3 Output Schema

```json
{
	"intent": "OPEN_APP",
	"confidence": 0.95,
	"entities": {
		"app": "WhatsApp"
	}
}
```

---

# 8. PLANNING LAYER

## 8.1 Responsibility

Convert intent → executable steps

---

## 8.2 Plan Schema

```json
{
	"steps": [{ "skill": "AppLauncher", "input": { "app": "WhatsApp" } }]
}
```

---

## 8.3 Requirements

- Sequential + parallel steps
- Retry support
- Failure handling

---

# 9. EXECUTION ENGINE

## 9.1 Responsibility

Execute plan steps and aggregate results

---

## 9.2 Features

- Async execution
- Timeout handling
- Cancellation support

---

# 10. SKILL SYSTEM (PLUGIN ARCHITECTURE)

## 10.1 Skill Interface

```kotlin
interface Skill {
    val name: String
    val description: String
    val inputSchema: JSONObject
    suspend fun execute(input: JSONObject): SkillResult
}
```

---

## 10.2 Skill Registry

```kotlin
interface SkillRegistry {
    fun register(skill: Skill)
    fun get(name: String): Skill
}
```

---

## 10.3 Categories

- System Control (WiFi, apps)
- Communication
- File operations
- Web/API

---

# 11. MEMORY SYSTEM

## 11.1 Design

Markdown (storage) + Hash-based embeddings (retrieval)
**See [memory-spec.md](memory-spec.md) for full details.**

## 11.2 Layers

- Short-term (RAM) - recent conversation context
- Long-term (Markdown + hash-based vector index)

## 11.3 RAG-Inspired Retrieval

```text
Query → Hash Embed → Cosine Similarity Search → Top-K Results → Inject into LLM Context
```

**Constraints:** Always respect context window limits (truncate if needed)

---

# 12. LLM SYSTEM

## 12.1 Online-First with Offline Fallback

**Primary:** Online LLM providers (OpenAI, Anthropic, Google Gemini, etc.)
- Smart fallback: If provider fails (rate limit, credit exhaustion, unavailable), automatically try next provider
- Per-provider cooldown: Track failure reasons and wait appropriate time (e.g., 5-24h for credit exhaustion)
- Offline support: Retained for no-internet scenarios and future on-device AI improvements

**On-Device Model:**
- Target: On-device model (quantized) for offline use when on-device AI becomes fast enough
- Current: Uses LiteRT-compatible models (Gemma, Qwen, etc.) via MediaPipe

## 12.2 LLM Router

```kotlin
interface LLMRouter {
    suspend fun complete(request: LLMRequest): LLMResponse
    fun getAvailableProviders(): List<LLMProvider>
    fun getProviderCooldown(provider: String): Duration
}
```

## 12.3 Responsibilities

- Intent parsing (fallback)
- Planning
- Response generation
- Memory context injection (RAG)

---

# 13. OUTPUT SYSTEM

## 13.1 TTS

- Android TTS API

---

## 13.2 UI

- Overlay screen (full-screen, not floating bubble)
- States:
  - Listening
  - Thinking
  - Speaking

---

# 14. ANDROID SYSTEM INTEGRATION

## 14.1 Required Components

- Foreground Service ✅
- Overlay Screen ✅
- Microphone access ✅
- [ ] **Accessibility Service** — Future scope (for direct system control)

---

## 14.2 Constraints

- Handle Doze mode
- Prevent process kill
- Battery optimization exemptions

---

## 14.3 Overlay

Current: Full-screen overlay activity (not floating bubble)
Future: May implement floating bubble as an Accessibility Service overlay

---

# 15. PERFORMANCE TARGETS

| Component | Target  |
| --------- | ------- |
| STT       | <800ms  |
| LLM       | <1500ms |
| Retrieval | <100ms  |

---

# 16. LOGGING & OBSERVABILITY

## 16.1 Events to Log

- Input events
- Intent decisions
- Plans
- Skill executions
- Errors

---

## 16.2 Format

```json
{
  "stage": "intent",
  "data": {...}
}
```

---

# 17. SECURITY (FOUNDATION)

- Skill permission model
- Validation layer for LLM outputs
- Restricted file system access

---

# 18. PARALLELISM MODEL

- Non-blocking pipeline
- Parallel skill execution where possible
- Streaming STT → LLM pipeline (future-ready)

---

# 19. EXTENSIBILITY

- Replaceable STT/TTS/LLM
- Add new modes
- Plugin-based skills
- Future dynamic skill generation

---

# 20. ACCEPTANCE CRITERIA

System is valid when:

- Voice input triggers correct actions
- Skills execute reliably
- Memory retrieval improves responses
- Modes behave correctly
- No crashes under normal usage

---

# FINAL ASSESSMENT

This specification defines a **modular, agentic assistant system** with:

- Clear separation of concerns
- Replaceable components
- Scalable architecture
- On-device feasibility

It is intentionally designed to:

- Avoid tight coupling
- Support iterative enhancement
- Enable future advanced capabilities (agents, dynamic skills, multi-modal input)

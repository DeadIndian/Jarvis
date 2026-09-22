<div align="center">

<!-- Optional logo: drop one at assets/logo.png and uncomment -->
<!-- <img src="assets/logo.png" alt="Jarvis logo" width="120" /> -->

# Jarvis

### Your phone is the whole assistant — no server, no wake-word cloud, offline first.

Jarvis is a phone-first voice assistant for Android. Trigger it, speak, and it routes your
request through an offline deterministic path for common phone commands, escalating to an
on-device LLM or an optional cloud agent only when a request is genuinely ambiguous or complex.
The phone is the brain; the cloud is an upgrade, not a dependency.

[![License](https://img.shields.io/github/license/DeadIndian/Jarvis?style=flat-square)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Made with Kotlin](https://img.shields.io/badge/made%20with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
[![Last commit](https://img.shields.io/github/last-commit/DeadIndian/Jarvis?style=flat-square)](https://github.com/DeadIndian/Jarvis/commits)
[![Stars](https://img.shields.io/github/stars/DeadIndian/Jarvis?style=flat-square)](https://github.com/DeadIndian/Jarvis/stargazers)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](CONTRIBUTING.md)

[Getting Started](#-installation) ·
[Architecture](docs/architecture.md) ·
[Report Bug](https://github.com/DeadIndian/Jarvis/issues) ·
[Request Feature](https://github.com/DeadIndian/Jarvis/issues)

<!-- Hero screenshot -->
<!-- USER: capture the main chat screen on a device/emulator and save it to assets/screenshots/hero.png -->
<img src="assets/screenshots/hero.png" alt="Jarvis screenshot" width="60%" />

</div>

---

## 📖 Table of Contents

- [About](#-about)
- [Features](#-features)
- [How It Works](#-how-it-works)
- [Screenshots](#-screenshots)
- [Installation](#-installation)
- [Usage](#-usage)
- [Configuration](#-configuration)
- [Contributing](#-contributing)
- [License](#-license)
- [Maintainers](#-maintainers)

---

## 🎯 About

Most voice assistants are thin clients: the phone captures audio and a server does the thinking.
Jarvis inverts that. Common commands — set volume, toggle Wi-Fi, open an app, place a call, send a
WhatsApp message, control media, flashlight — resolve **fully on the device** through a
deterministic router, with no network round-trip. Only ambiguous or conversational requests
escalate: first to an on-device LLM, then, if configured, to a cloud agent. Bring-your-own keys and
optional MCP tools are supported, but nothing about the core loop requires them.

Built for people who want a fast, private, offline-capable assistant on Android 8.0 (API 26) and up.

## ✨ Features

- **Offline hot path** — a deterministic regex/keyword router executes 9 phone tools with no model or server involved.
- **On-device LLM** — MediaPipe LiteRT (`tasks-genai`) runs a quantized model on-device for ambiguous commands.
- **Optional cloud agent** — OpenAI-compatible tool loop with MCP tool support for complex, conversational requests.
- **Two entry points** — in-app chat with mic tap, plus a floating assist bubble via the side-button (`ASSIST` / `VOICE_COMMAND`).
- **Safety gate** — every tool call is allowlisted and argument-validated before it runs; sensitive tools are confirmation-gated.
- **Low-latency feedback** — an activation "ding" fires within ~250ms of trigger; a new session barges in on in-flight speech.
- **Encrypted config** — agent URL/key/model and MCP servers stored in `EncryptedSharedPreferences` (Android Keystore).

## 🔧 How It Works

```
Trigger → STT → VoiceSessionManager → DeterministicRouter
                                        ├─ LocalTool     → validate → execute → speak
                                        ├─ Ambiguous     → on-device LLM (tool JSON) → else cloud agent
                                        └─ ServerHandoff  → cloud agent (tool loop + MCP)
```

All spoken replies funnel through a single `say()` path (UI mirror, then TTS). See
[docs/architecture.md](docs/architecture.md) for the full module map and the real-vs-documented status.

## 📸 Screenshots

<!-- USER: capture each view on a device/emulator and drop the files at the paths below.
     There is no automated capture for native Android apps — take these manually
     (Android Studio: Running Devices → camera icon, or `adb exec-out screencap -p > file.png`). -->

| Chat | Assist Overlay |
| :---: | :---: |
| <img src="assets/screenshots/chat.png" width="100%" /> | <img src="assets/screenshots/overlay.png" width="100%" /> |

## 🚀 Installation

> **Prerequisites:** JDK 17 · Android SDK (API 35) · NDK + CMake (for the native VAD build) · an Android device or emulator on API 26+.

```bash
# Clone the repo
git clone https://github.com/DeadIndian/Jarvis.git
cd Jarvis

# Build the debug APK
./gradlew :app:assembleDebug

# Install to a connected device / running emulator
./gradlew :app:installDebug
```

The on-device model weight (`mobile-actions_q8_ekv1024.litertlm`, ~284MB) is **not** committed — it is
distributed separately. Place it where `JARVIS_LITERT_MODEL_DIR` points, or let the in-app downloader
fetch it. The offline command path works without it.

## 💻 Usage

```bash
# Run the unit tests
./gradlew :app:testDebugUnitTest
```

Once installed:

1. Launch **Jarvis** (or trigger the assist bubble with your device's side-button / assistant gesture).
2. Wait for the activation ding, then speak a command — e.g. *"set volume to 40"*, *"open WhatsApp"*, *"turn on the flashlight"*.
3. Common commands run offline instantly; ambiguous ones fall through to the on-device model, then the cloud agent if one is configured in Settings.

## ⚙️ Configuration

Cloud LLM keys are injected at **build time** from a root `.env` file into `BuildConfig` (see `app/build.gradle.kts`). All keys are optional — the offline path and on-device model need none. Runtime agent settings (base URL, key, model, MCP servers) are configured in-app and stored encrypted.

Create a `.env` in the repo root (git-ignored) to set any of:

| Variable | Description | Default |
| --- | --- | --- |
| `JARVIS_LLM_BACKEND` | Backend mode selector | `hybrid` |
| `JARVIS_GEMINI_API_KEY` | BYO Google Gemini key (on-device fallback) | _(empty)_ |
| `JARVIS_OPENAI_API_KEY` | BYO OpenAI key | _(empty)_ |
| `JARVIS_ANTHROPIC_API_KEY` | BYO Anthropic key | _(empty)_ |
| `JARVIS_LITERT_MODEL_DIR` | Directory holding the `.litertlm` model weight | _(empty)_ |
| `JARVIS_HF_TOKEN` | Hugging Face token for model download | _(empty)_ |
| `JARVIS_REMOTE_AGENT_BASE_URL` | Default cloud agent endpoint | _(empty)_ |

> ⚠️ **Security note:** baking cloud keys into `BuildConfig` embeds them in a distributable APK. This is known debt — keep production keys out of shipped builds and prefer the in-app encrypted runtime config for anything sensitive.

## 🤝 Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and our [Code of Conduct](CODE_OF_CONDUCT.md) before opening a PR.

1. Fork the repo
2. Create your feature branch (`git checkout -b feature/amazing`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing`)
5. Open a Pull Request

## 📄 License

Distributed under the **GNU GPL v3.0** License. See [LICENSE](LICENSE) for details.

## 👥 Maintainers

- **DeadIndian** — [@DeadIndian](https://github.com/DeadIndian) · gollabharath2007@gmail.com

---

<div align="center">
<sub>Built with ❤️ by DeadIndian</sub>
</div>

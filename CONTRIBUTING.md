# Contributing to Jarvis

First off, thanks for taking the time to contribute! 🎉

The following is a set of guidelines for contributing. These are mostly guidelines, not rules — use your best judgment, and feel free to propose changes to this document in a pull request.

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold it. Please report unacceptable behavior to gollabharath2007@gmail.com.

## How Can I Contribute?

### Reporting Bugs

Open an issue with:
- A clear, descriptive title
- Steps to reproduce
- What you expected to happen vs. what actually happened
- Your environment (device/emulator model, Android API level, build variant)

### Suggesting Enhancements

Open an issue describing the enhancement, why it's useful, and any alternatives you considered.

### Pull Requests

1. Fork the repo and create your branch from `main`.
2. Build the project: `./gradlew :app:assembleDebug`
3. Make your changes.
4. Run the tests: `./gradlew :app:testDebugUnitTest`
5. Run lint: `./gradlew :app:lintDebug`
6. Make sure your code follows the existing Kotlin style and the offline-first architecture (validate every tool call before execution).
7. Write a clear commit message and open the PR.

## Development Setup

```bash
git clone https://github.com/DeadIndian/Jarvis.git
cd Jarvis
./gradlew :app:assembleDebug
```

Requirements: JDK 17, Android SDK (API 35), NDK + CMake for the native VAD, and a device/emulator on API 26+.

## Style Guide

- Kotlin, Jetpack Compose for UI. Match the surrounding code's conventions.
- Keep the deterministic offline path fast and dependency-free — don't route common phone commands through a model.
- Every phone tool must be declared in `ToolCatalog`, allowlisted in `ActionValidator`, and executed in `PhoneToolExecutor`. Nothing runs a raw model/server tool call without validation.
- No secrets in committed code. Cloud keys come from `.env`/`BuildConfig` at build time or the in-app encrypted config.

## Questions?

Feel free to open an issue or reach out to a maintainer.

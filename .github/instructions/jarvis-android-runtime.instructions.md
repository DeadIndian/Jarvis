---
name: Jarvis Android Runtime and Permissions
description: "Use when implementing Android services, microphone handling, wake word lifecycle, overlay behavior, accessibility integration, and foreground runtime reliability for Jarvis."
applyTo: "android/**, input/**, output/**, **/AndroidManifest.xml, **/*.xml"
---

# Jarvis Android Runtime and Permissions

Follow these rules for Android integration work.

1. Load these skills before major Android runtime changes.

- jarvis-android-services-permissions
- jarvis-android-module-scaffold
- jarvis-observability-performance
- jarvis-security-guardrails

2. Enforce runtime safety.

- Keep continuous assistant behavior inside foreground service constraints.
- Handle denied permissions with clear degraded mode behavior.
- Wire wake word only for allowed states.

3. Align lifecycle with state system.

- IDLE: no active listening resources.
- BARN_DOOR and ACTIVE: mic lifecycle managed by explicit user actions.
- HOUSE_PARTY: wake word lifecycle enabled and monitored.

4. Validate on real device behavior.

- Test app background and resume.
- Test doze and process pressure scenarios.
- Validate battery optimization and restart paths.

Reference architecture and state matrix in spec.md.

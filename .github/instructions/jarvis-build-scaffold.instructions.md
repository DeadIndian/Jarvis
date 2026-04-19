---
name: Jarvis Module and Build Scaffold
description: "Use when creating or restructuring Gradle modules, Kotlin package boundaries, settings includes, and dependency wiring for the Jarvis architecture."
applyTo: "**/settings.gradle, **/settings.gradle.kts, **/build.gradle, **/build.gradle.kts, **/gradle.properties"
---

# Jarvis Module and Build Scaffold

Follow these rules for module and build system changes.

1. Load these skills before structural edits.

- jarvis-android-module-scaffold
- jarvis-event-contracts
- jarvis-security-guardrails

2. Keep dependency graph clean.

- Define API interfaces in upstream modules.
- Keep implementation detail in downstream modules.
- Prevent circular dependencies.

3. Start from architecture map.

- core
- input
- intent
- planner
- execution
- skills
- memory
- llm
- output
- android
- logging

4. Use minimal dependencies.

- Add only libraries used by module responsibilities.
- Isolate cloud and platform dependencies behind interfaces.

Reference architecture module map in spec.md.

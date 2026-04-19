---
name: jarvis-android-module-scaffold
description: "Use when creating or restructuring Jarvis Android modules, Gradle setup, package boundaries, and shared interfaces for core, input, intent, planner, execution, skills, memory, llm, output, android, and logging."
---

# Jarvis Android Module Scaffold

## Goal

Establish and maintain a clean multi-module Kotlin Android structure that matches the Jarvis architecture spec.

## Use When

- The project is being bootstrapped from empty state.
- A new architecture module must be added.
- Module boundaries have drifted and require cleanup.

## Inputs

- Desired module list.
- Android minSdk and targetSdk.
- Dependency policy for local versus cloud components.

## Workflow

1. Create or verify module directories and Gradle includes.
2. Define module APIs first, implementations second.
3. Keep platform-specific code in android modules and pure logic in core modules.
4. Add placeholders for interfaces used by downstream modules.
5. Confirm each module has one clear responsibility.

## Output Contract

- Updated settings and module manifests.
- Per-module build files with minimal dependencies.
- A short module map documenting ownership and boundaries.

## Guardrails

- Avoid circular dependencies.
- Avoid placing Android framework dependencies in core logic modules.
- Preserve stable interfaces when refactoring.

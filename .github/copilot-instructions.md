# Project Guidelines

## Code Style

- Follow language-native formatters and linters once the project stack is present.
- Prefer small, focused changes that preserve existing APIs and file organization.

## Architecture

- Multi-module Kotlin architecture is scaffolded by domain: `core`, `input`, `intent`, `planner`, `execution`, `skills`, `memory`, `llm`, `output`, `android`, `logging`.
- Keep Android framework integration isolated to `android` and platform interfaces in `input` and `output`; keep orchestration and business logic platform-agnostic.

## Build and Test

- Build all modules: `./gradlew build`
- Run all tests: `./gradlew test`
- Build Android module only: `./gradlew :android:assemble`

## Conventions

- Link, do not duplicate: when docs such as `README.md`, `CONTRIBUTING.md`, or `docs/` are added, reference them from this file instead of embedding long guidance.
- Keep this file minimal and project-wide; add scoped instructions under `.github/instructions/*.instructions.md` when conventions differ by area.
- Preserve clear module boundaries and avoid circular dependencies across domain modules.

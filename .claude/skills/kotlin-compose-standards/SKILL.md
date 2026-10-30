---
name: kotlin-compose-standards
description: Kotlin craft for Downstep. Use when writing or reviewing Kotlin, coroutines/Flow or tests. Presentation (ViewModel, UiState, composables, insets) lives in compose-ui; layering in clean-architecture.
---

# Kotlin and Compose standards

Applies to all code in `com.damianogiusti.slowdowner`.

## Kotlin

- No `!!`. Use `?.`, `?:`, `requireNotNull(x) { "why" }` or an early return.
- `val` by default. Expose `List`/`Map`/`Set`, never `MutableList`.
- Composition over inheritance. No `open` for reuse; use `by` delegation. `sealed` for closed types is fine.
- Constructor injection. No `lateinit` unless a framework forces it.
- Default to `private`/`internal`.
- Functions do one thing, take at most 3 parameters (else a parameter `data class`), no boolean flag
  arguments, early returns.
- Name by role (see `clean-architecture`). Booleans read as questions: `isX`, `hasX`.

## Coroutines

- Structured concurrency. No `GlobalScope`. UI work in `viewModelScope`; app-lifetime work in an
  injected scope.
- Dispatchers are injected at repositories only.
- `suspend` for one-shot actions, `Flow` for observed data.
- Never expose `MutableStateFlow`/`MutableSharedFlow`. Expose the read-only type.

## ViewModel, UiState and composables

See skill `compose-ui`.

## Tests

- `runTest` and hand-written fakes. Domain: `kotlin.test` in `shared/src/commonTest/`. Data: JUnit4 in
  `shared-data/src/desktopTest/`. DI: `desktopApp/src/test/`. Turbine for ViewModel state
  (see `compose-ui`).
- Every new use-case, mapper and repository ships a test. Domain tests are common Kotlin: they also
  run on the iOS simulator.
- Backtick names: ``fun `when load fails then state is Error`()``.

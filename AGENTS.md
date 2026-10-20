# Agent guide

Rules for coding agents that work on Downstep. Each rule is meant to be checked against the diff.
[CONTRIBUTING.md](CONTRIBUTING.md) explains the reasons; read it when a rule is unclear.

## Scope of a change

- Make one change per pull request: one fix or one feature. Do not mix refactoring, formatting
  or renames into it.
- Do not change layout, themes, the icon, or add screens or settings without an issue that the
  maintainer agreed to.
- Do not refactor existing code that works, even if it breaks a rule below. New code follows the
  rules.
- This repository is a mirror: pull requests are not merged on GitHub. The maintainer applies
  accepted changes by hand, with a `Co-authored-by` trailer.

## Code used outside this repository

The shared modules have other consumers outside this repository. Without prior agreement, do not:

- change or remove a public class, function or constructor in `shared`, `shared-data`, `shared-ui`;
- change an interface (repositories, stores, data sources);
- change `useCasesModule`, `uiModule` or existing Koin bindings (adding a new binding is fine);
- change the database schema.

A database change, once agreed, needs a Room migration, the exported schema in
`shared-data/schemas/` and a migration test.

## Architecture

- Packages by feature in each layer: `domain/<feature>`, `data/<feature>`, `ui/<feature>`.
- Use cases live in `domain`: one action, `operator fun invoke`.
- Only repositories choose dispatchers: an injected `CoroutineDispatcher` with
  `withContext(dispatcher)` or `flowOn(dispatcher)`. No `Dispatchers.*` in use cases, ViewModels
  or composables.
- Repositories return cold `Flow`. Only ViewModels expose `StateFlow`.
- Domain code is common Kotlin: no `java.*`, no platform types, no library types (NewPipe, Room,
  HTTP). Map them in `...Mapper` classes in `data`.
- Errors: sealed result types, mapped in the repository. No exception from a library leaves the
  repository. No empty `catch`. No `runCatching { }.getOrNull()` that hides a failure.
- DI with Koin 4: use cases with `factoryOf` in `useCasesModule`, ViewModels with `viewModelOf`
  in `uiModule`, desktop bindings in `desktopModule`. Domain code never calls Koin.
- No interface without a real implementation. No abstraction for a future need.
- Names: `...UseCase`, `...Repository`, `Default...Repository`, `...DataSource`, `...Store`,
  `...Mapper`, `...Dto`, `...Adapter`. Never `Manager`, `Handler`, `Helper`, `Util`, `Service`.
- Logging: inject `Logger`. Log only rare, meaningful events. Property values come from a small
  set. Never log titles, file names, paths or other personal data.

## Presentation

- One immutable `UiState` per screen, one `StateFlow` from the ViewModel, built with
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. No `load()` in `init`.
- Loading, error and empty are explicit states. Never infer loading from an empty list.
- `UiState` holds render-ready values, not domain models.
- User intents are public typed methods on the ViewModel. Navigation is a callback lambda.
- No `StateFlow` or `Channel` outside ViewModels. One-shot events use
  `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`.
  Any exception must be stated and justified in the pull request description.
- Screen composable: gets the ViewModel with `koinViewModel()`, collects with
  `collectAsStateWithLifecycle()`. Content composable: stateless, takes state and lambdas.
- No business logic, I/O or use case calls in a `@Composable`.
- Theme and Material3 tokens only. No hardcoded colors or text sizes.
- Every text field has `Modifier.reportsTextInputFocus()`.
- No hardcoded user-visible strings. Add each string to every locale in
  `shared-ui/src/commonMain/composeResources` (today `values` and `values-it`). Italian text uses
  correct accents (`è`, `perché`, `più`), never `e'` or `piu`.
- In `strings.xml`, no `\"` or `\'`. Placeholders: only `%1$s`, `%1$d`, `%%`.

## Kotlin

- No `!!`. No `lateinit` unless a framework needs it. No `GlobalScope`.
- `val` by default. Expose read-only `List`, `Map`, `Set`, `StateFlow`, `SharedFlow`.
- No `open` classes for reuse: use composition or `by` delegation.
- `private` or `internal` by default.
- At most three parameters per function, else a parameter `data class`.
- No boolean flag arguments.
- Booleans are named `isX` or `hasX`.
- Comments explain why, not what.

## Tests

- Every new use case, mapper, repository and ViewModel has tests.
- Domain: `shared/src/commonTest/`, `kotlin.test`, common Kotlin only.
- Data: `shared-data/src/desktopTest/`, JUnit4.
- ViewModels: `shared-ui/src/desktopTest/`.
- Hand-written fakes in a `fake/` package. No mocking libraries. `runTest` for coroutines.
- Test names in backticks: ``fun `when import fails then result is Unavailable`()``.
- Use temporary directories only. Never read or write the real data folder
  (`~/Library/Application Support/SlowDowner`, `%APPDATA%\SlowDowner`).
- Never create links to real system folders. Never use a recursive delete that follows links.

## Desktop

- Visible name: `Downstep`. Internal names stay `SlowDowner`: package
  `com.damianogiusti.slowdowner`, classes, icon files, log tags, `slowdowner.log`, data folder.
- Windows builds only with MinGW-w64, in CI. Do not add MSVC-specific code or build steps.

## Dependencies

- A new runtime dependency must have a GPL-3.0 compatible license.
- Add it to `desktopApp/licenses/THIRD-PARTY-NOTICES.txt`, and add its license text to
  `desktopApp/licenses/` if missing. Copy texts from the dependency, never write them from memory.

## Before you finish

Run the tests for the modules you touched:

```sh
./gradlew :shared:jvmTest :shared-data:desktopTest :shared-ui:desktopTest
./gradlew :engine-desktop:test :desktopApp:test
```

Then check the diff against this file, and write:

- commits with a short imperative subject and a body with what, why and trade-offs;
- a factual pull request description: what changed, why, how it was tested, risks, any rule
  broken on purpose with the reason, and the statement that the contribution is released under
  GPL-3.0.

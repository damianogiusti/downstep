# Contributing to Downstep

Thank you for your interest in Downstep. This guide explains how a change gets into the app and
which rules the code follows. If you work with a coding agent, point it to [AGENTS.md](AGENTS.md):
it has the same rules as a short checklist.

## How contributions work

This repository is a mirror. I develop Downstep in a private repository, and a workflow publishes
the public modules here. The public history is rebuilt from the private one and pushed without
force. So I can't merge a pull request on GitHub: the next publish would fail.

This is what happens instead:

1. You open a pull request here, as usual.
2. We discuss it here, in the comments.
3. When we agree, I apply the change in the private repository. Each commit gets a
   `Co-authored-by` trailer with your name and email, so the credit stays with you.
4. The change appears here with the next publish, and I close the pull request.

Some practical points:

- **Keep a pull request small and scoped to one change.** One fix or one feature. I review and
  test every change by hand, on my own. A small change I can check properly. A large one waits,
  or I have to ask you to split it.
- **Open an issue first for UX or product changes:** layout, themes, the icon, new screens, new
  settings. I'd rather agree on the direction before you spend time on the code.
- Bug fixes, tests and performance work can go straight to a pull request.
- **License.** Downstep is GPL-3.0. In each pull request, write that you release your
  contribution under GPL-3.0.

## Code used outside this repository

The shared modules have other consumers outside this repository. You can't see that code, and a
change that compiles here can still break it. So please discuss it first, in an issue or in the
pull request, before you change:

- a public API: a public class, function or constructor in `shared`, `shared-data` or `shared-ui`;
- an interface (a repository, a store, a data source);
- the Koin modules (`useCasesModule`, `uiModule`) or their bindings;
- the database schema.

New classes and new use cases are usually fine. Changing an existing signature usually is not.

A database change needs three things: a Room migration, the new exported schema in
`shared-data/schemas/` (committed), and a test of the migration. Say it clearly in the pull
request: I have to register the migration in other places too.

## Modules

```
shared/          domain models and use cases, common Kotlin only
shared-data/     Room database, repositories, YouTube import, backups
shared-ui/       Compose Multiplatform: screens, ViewModels, theme, strings, icons
engine/          C++ audio engine (CMake)
engine-desktop/  Kotlin binding of the engine through FFM
desktopApp/      Compose Desktop app for macOS and Windows
website/         the site (python3 website/build.py)
```

## Architecture

The code follows clean architecture, with packages by feature inside each layer:
`domain/<feature>`, `data/<feature>`, `ui/<feature>`. Dependencies point toward the domain.

```
ui/<feature>  ->  UseCase  ->  Repository (interface, domain)  <-  Default...Repository (data)
                                                                      -> DataSource / Store / Mapper
```

- **Use cases** live in `domain`. One business action each, as `operator fun invoke`. A thin use
  case over one repository is fine: callers depend on one action, and tests fake one function.
- **Repositories are the concurrency boundary.** Only they choose a dispatcher. They get a
  `CoroutineDispatcher` in the constructor and use `withContext(dispatcher)` or
  `flowOn(dispatcher)`. Use cases and ViewModels never pick dispatchers.
- Repositories return cold `Flow`. Only ViewModels expose `StateFlow`.
- **The domain is common Kotlin.** No `java.*`, no platform types, no types from libraries like
  NewPipe or Room. The domain also builds for targets without a JVM.
- **Mappers** convert DTOs and library types to domain models at the data boundary.
- **Errors:** prefer a sealed result over exceptions, mapped in the repository (see
  `ImportResult`). A library or HTTP exception never leaves the repository. Never hide an error:
  no empty `catch`, no `runCatching { }.getOrNull()` that drops a failure.
- **DI is Koin 4.** Use cases go in `useCasesModule` (`shared-data`) with `factoryOf`, ViewModels
  in `uiModule` (`shared-ui`) with `viewModelOf`, desktop bindings in `desktopModule`
  (`desktopApp`). Domain code never calls the container.
- **No speculative abstractions.** No interface without a real implementation, no layer "for
  later". Add it when there is a second use.
- **Naming:** `...UseCase` (a verb), `...Repository` (interface), `Default...Repository`,
  `...DataSource`, `...Store`, `...Mapper`, `...Dto`, `...Adapter`. Avoid `Manager`, `Handler`,
  `Helper`, `Util` and `Service`.
- **Logging:** inject `Logger` (package `logging` in `shared`). Log only rare, meaningful events.
  Properties have few possible values (format, source, stage). Never log titles, file names or
  paths: release builds send logs to crash reporting. The `Logger` KDoc has the details.

Some older code predates these rules. Please don't refactor it in the same pull request. New code
follows the rules.

## Presentation

- **One immutable `UiState` per screen**, exposed by the ViewModel as one `StateFlow`, built with
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. No `load()` in `init`.
- **Loading, error and empty are explicit.** Use a sealed interface when the states exclude each
  other, or a data class with a status enum for an editor. An empty list never means "loading".
- `UiState` holds values ready to render, not domain models. Map them in the ViewModel.
- **Unidirectional flow:** state goes down, events go up as typed public methods on the
  ViewModel (`onQueryChanged`). Navigation is a callback passed to the screen, never a ViewModel
  concern.
- **No `StateFlow` or `Channel` outside ViewModels.** For a one-shot event, use a
  `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)` and
  expose the read-only type. If you need an exception, for example for performance, say it in the
  pull request and explain why.
- **Screen and Content split.** The Screen composable gets the ViewModel with `koinViewModel()`
  and collects the state with `collectAsStateWithLifecycle()`. The Content composable is
  stateless: it takes the state and lambdas, and it never sees the ViewModel.
- **No logic in composables.** No business rules, no I/O, no use case calls. Display logic goes
  in the `UiState` (derived getters) or in a small mapper.
- Use the app theme and Material3 tokens. No hardcoded colors or text sizes.
- Every text field gets `Modifier.reportsTextInputFocus()`: the desktop app takes Space and the
  arrows for playback, except while the user types.
- The layout changes at 840 dp. Check a new screen at both widths.

### Strings

- No hardcoded text that the user can see. Every string goes in
  `shared-ui/src/commonMain/composeResources`, in **every existing locale**: today `values`
  (English) and `values-it` (Italian). Italian needs correct accents: `è`, `perché`, `più`, never
  `e'` or `piu`.
- Write `"` and `'` as they are, without a backslash.
- Placeholders: only `%1$s`, `%1$d` and `%%`. Format numbers in Kotlin and pass them as `%1$s`.
  `FormatsTest` checks this.

## Kotlin

- No `!!`. Use `?.`, `?:`, `requireNotNull(x) { "why" }` or an early return.
- `val` by default. Expose `List`, `Map` and `Set`, never the mutable types.
- Never expose `MutableStateFlow` or `MutableSharedFlow`.
- Composition over inheritance. No `open` classes for reuse. `sealed` for closed types is fine.
- Constructor injection. No `lateinit` unless a framework needs it.
- `private` or `internal` by default.
- Functions do one thing and take at most three parameters. Use a parameter `data class` for more.
- No boolean flag arguments: write two functions, or use an enum.
- Booleans read as questions: `isPlaying`, `hasLoop`.
- Structured concurrency: no `GlobalScope`. `suspend` for one-shot actions, `Flow` for data that
  changes over time.
- Comments explain why, not what.

## Tests

Every new use case, mapper, repository and ViewModel comes with tests.

| What | Where | Framework |
|---|---|---|
| Domain | `shared/src/commonTest/` | `kotlin.test`, common Kotlin only |
| Data | `shared-data/src/desktopTest/` | JUnit4 |
| ViewModels, strings | `shared-ui/src/desktopTest/` | `kotlin.test` |
| Engine binding | `engine-desktop/src/test/` | `kotlin.test`, real engine without an audio device |
| Desktop app, DI graph | `desktopApp/src/test/` | `kotlin.test` |

- Use `runTest` from `kotlinx-coroutines-test` and hand-written fakes in a `fake/` package next to
  the tests. No mocking libraries.
- Test names in backticks: ``fun `when import fails then result is Unavailable`()``.
- **Tests never touch the user's data.** Use a temporary directory, never the real data folder
  (`~/Library/Application Support/SlowDowner` or `%APPDATA%\SlowDowner`).
- **No links to real system folders** in tests, and no recursive delete that follows links. A
  test that deletes a link to `/Applications` deletes the user's apps.
- When you add a Koin definition, keep the DI test in `desktopApp` green.

## Desktop

- The visible name is **Downstep**: window title, app bundle, installer, UI texts, backup names.
- Internal names stay **SlowDowner**: the package `com.damianogiusti.slowdowner`, class names,
  icon files, log tags, `slowdowner.log`, and the data folder `SlowDowner`. Existing libraries
  depend on that folder, so don't rename it.
- macOS and Windows. Windows is built and tested only in CI, with MinGW-w64: Bungee does not
  build with MSVC. Nobody on the project has a Windows PC, so for Windows UI changes, please add
  screenshots.

## Dependencies and licenses

The app is a combined work under GPL-3.0. Every new runtime dependency must:

- have a license compatible with GPL-3.0 (no GPL-2.0-only, AGPL, "non-commercial" or missing
  license);
- get an entry in `desktopApp/licenses/THIRD-PARTY-NOTICES.txt`, with its version and license;
- bring its full license text into `desktopApp/licenses/` if that license is not there yet.

Copy license texts and copyright lines from the dependency itself, never from memory. Test-only
and build-only dependencies don't need an entry. Ask before you add a large dependency.

## Commands

Requirements are in the [README](README.md).

```sh
./gradlew :desktopApp:run                 # start the app
./gradlew :shared:jvmTest :shared-data:desktopTest :shared-ui:desktopTest
./gradlew :shared:jvmTest --tests "com.damianogiusti.slowdowner.domain.library.*"
./gradlew :engine-desktop:test            # builds the engine with CMake, tests through FFM
./gradlew :desktopApp:test                # DI graph, import and play flow, off-screen render
./gradlew :desktopApp:createDistributable # the app folder
./gradlew :desktopApp:packageDmg          # unsigned DMG (macOS)
```

Run the tests of the modules you changed before you open a pull request.

## Commits and pull requests

- Commit subject: short and imperative, about one change. For example: `Decode M4A off the main
  thread`.
- Commit body: what changed, why, and any trade-off or risk.
- Pull request description: factual. What changed, why, how you tested it, any risk. Add the
  GPL-3.0 statement, and list any rule of this guide you broke on purpose, with the reason.
- No secrets, local paths or personal data in code, commits or logs.
- A CI check (`leak-check.yml`) rejects text that the maintainer keeps on a private list. To run the
  same check before you push, put one term per line in `~/.config/downstep/denylist.txt` and run
  `scripts/leak-check.sh` (also usable as a `pre-push` hook). Fork pull requests are reviewed by hand.

Thank you for reading this far. If something here is unclear, open an issue and ask.

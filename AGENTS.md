# Agent guide

Single guide for coding agents that work on Downstep (`CLAUDE.md` points here). Each rule is meant
to be checked against the diff. [CONTRIBUTING.md](CONTRIBUTING.md) explains the reasons; read it
when a rule is unclear.

Downstep slows a song down, changes its key and loops a part. Kotlin, Compose Multiplatform with
Material3 (no XML layouts), Room, Koin 4. A C++ audio engine sits under the desktop app.

## Modules

```
shared/          domain models and use cases, common Kotlin only (JVM + iOS targets)
shared-data/     Room database, repositories, YouTube import (NewPipe), backups
shared-ui/       Compose Multiplatform: screens, ViewModels, theme, strings in composeResources
engine/          C++ audio engine (CMake, Bungee time-stretch, miniaudio, LAME)
engine-desktop/  Kotlin binding of the engine through FFM (java.lang.foreign)
desktopApp/      Compose Desktop app for macOS and Windows
website/         the site (python3 website/build.py)
```

The shared modules also have an Android target, so Gradle needs the Android SDK to configure.

## Commands

```sh
./gradlew :desktopApp:run                 # start the app (uses the real data folder, see Desktop)
./gradlew :shared:jvmTest :shared-data:desktopTest :shared-ui:desktopTest
./gradlew :shared:jvmTest --tests "com.damianogiusti.slowdowner.domain.library.*"
./gradlew :shared:iosSimulatorArm64Test   # same domain tests on Kotlin/Native
./gradlew :engine-desktop:test            # builds engine/ with CMake, tests through FFM
./gradlew :desktopApp:test                # DI graph, import and play flow, off-screen render
./gradlew :desktopApp:createDistributable # app folder in desktopApp/build/compose/binaries/main/app
./gradlew :desktopApp:packageDmg          # unsigned DMG (macOS)
```

## Scope of a change

- Make one change per pull request: one fix or one feature. Do not mix refactoring, formatting
  or renames into it.
- Do not change layout, themes, the icon, or add screens or settings without an issue that the
  maintainer agreed to.
- Do not refactor existing code that works, even if it breaks a rule below. New code follows the
  rules.

## Changes that need care

Without prior agreement, do not:

- change or remove a public class, function or constructor in `shared`, `shared-data`, `shared-ui`;
- change an interface (repositories, stores, data sources);
- change `useCasesModule`, `uiModule` or existing Koin bindings (adding a new binding is fine);
- change the database schema or the backup file format.

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
- Layers: `ui` calls `domain` use cases, `domain` defines the repository interfaces, `data`
  implements them. `domain` never imports `data` or `ui`.
- Logging: inject `Logger` (package `logging` in `shared`). Log only rare, meaningful events.
  Property values come from a small set. Never log titles, file names, paths or other personal
  data. On desktop, a dev run prints to stdout; a release with a Sentry DSN sends info and above to
  Sentry Logs, and `e` with a throwable becomes an issue (`desktopApp/.../SentryLogger.kt`).

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
- Data is in `~/Library/Application Support/SlowDowner` (macOS) or `%APPDATA%\SlowDowner`
  (Windows); the log is `logs/slowdowner.log` there. On a developer's Mac that folder can hold a
  real library: tests use temporary directories, and `desktopApp:run` touches the real data.
- Texts that differ on desktop ("computer" instead of "phone", no share sheet) use
  `ui/common/isDesktop` (expect/actual in `shared-ui`). Window, menus and title bar live in
  `desktopApp`.
- The version comes from `-PappVersion` (CI: `1.0.<run number>`) and shows in the window title
  and the log.
- Updates: at launch the packaged app asks GitHub for the latest public release. If it is newer, a
  dialog and a banner offer it. The app downloads the DMG or zip itself, so there is no quarantine
  or Mark of the Web, and `desktopApp/.../update/` swaps the app with a script after it quits.
  `-Dslowdowner.updates.url` points to a fake release server.
- **Install and swap checks run only in CI** (`.github/workflows/macos.yml` and `windows.yml`,
  `-PupdateSwapTests=true`), never on a developer's computer: they replace an app in
  `/Applications` or its Windows equivalent. In tests: no links to real folders, no recursive
  delete that follows links.
- Windows is built and tested only in CI, with MinGW-w64 (Bungee does not build with MSVC). Do not
  add MSVC-specific code or build steps. Read a failure with `gh run view <id> --log-failed`.

## Releases and site

- This repository is where development happens. Merging to `main` publishes nothing by itself.
- A `v*` tag runs `release.yml`, which builds the DMG and the zip into a draft release. The notes
  come from the section of the version in `desktopApp/CHANGELOG.txt`.
- A release ships two Mac DMGs: `Downstep-mac.dmg` (Apple silicon, the name old installs look for) and
  `Downstep-mac-intel.dmg`, built by the `mac-intel` job on `macos-15-intel`. The updater picks the file by
  CPU architecture; a release without it is offered as a link. CI also tests on Intel (`intel` job in
  `macos.yml`). Never rename `Downstep-mac.dmg` or put another build under that name: installed apps
  match it exactly.
- The Windows file is `Downstep-windows-x64.zip`, under the same rule.
- statichost.eu builds the site from the branch `site`. To publish, fast-forward it to `main`
  (`git push origin main:site`), then start the build in the statichost.eu panel: no webhook does it.
  Never commit to `site` directly.
- `leak-check.yml` rejects text from a private list of terms kept in a secret. Run
  `scripts/leak-check.sh` before you push. Never add that list to the repository.

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

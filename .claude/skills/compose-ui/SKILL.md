---
name: compose-ui
description: Presentation standards for Compose Multiplatform + ViewModel in Downstep. Lean MVVM with a single immutable UiState and unidirectional data flow. Use for any task involving a Compose screen, @Composable, ViewModel, UiState, one-shot events, window insets or screen navigation. Layering lives in clean-architecture; Kotlin craft in kotlin-compose-standards.
---

# Compose presentation (Compose Multiplatform)

Governs the **presentation layer**. Domain/data layering is `clean-architecture`; general Kotlin
craft is `kotlin-compose-standards`. This skill is what those layers get *called into from*.

> **Scope.** The shared Compose Multiplatform UI in `shared-ui`, used by the desktop app. The
> ViewModel base is `androidx.lifecycle.ViewModel`, which works in `commonMain`.
>
> **Legacy.** Some screens predate parts of this skill. Do not refactor them unless asked. New code
> follows this skill.

---

## The pattern: lean MVVM, unidirectional

- **State down, events up.** The ViewModel owns a single immutable `UiState`; the View renders it
  and calls back typed methods on intent.
- **No sealed `Action` / MVI reducer by default.** User intents are **public typed methods** on the
  ViewModel (`onQueryChanged`, `onTrackRemoveConfirmed`). Introduce a sealed `Action` + single
  dispatch *only* when a screen has many intents or needs a uniform funnel, not as baseline
  ceremony.
- **No `Effect` bus by default.** Model one-shot events in state or with a small dedicated flow
  *at need* (see One-shot events).

---

## ViewModel

- Extend **`androidx.lifecycle.ViewModel`** (multiplatform artifact, works in `commonMain`).
- Expose **one** `StateFlow<UiState>` per screen, built reactively and lazily with
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <initial>)`. Merge sources with
  `combine()`. **No imperative `load()` in `init`**: state is produced on first subscription.
- Secondary independent signals may be their own `StateFlow<Boolean>`. That is the exception, not a
  reason to split the main state.
- Launch in **`viewModelScope`**. No dispatcher injection at this layer (that is the repository's
  job, per `clean-architecture`).
- Engine and slider updates can burst: keep conflation in the ViewModel, as `EffectsViewModel` does.

```kotlin
class LibraryViewModel(
    observeLibrary: ObserveLibraryUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val removeTrack: RemoveTrackUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<LibraryUiState> = combine(observeLibrary(), observeNowPlaying(), query) { tracks, nowPlaying, query ->
        val items = tracks.filter { it.matches(query) }.map { it.toTrackItem(nowPlaying) }
        if (items.isEmpty()) LibraryUiState.Empty(query) else LibraryUiState.Tracks(items, query)
    }.catch { emit(LibraryUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)
}
```

---

## Modeling UiState: loading, error, empty are explicit

Loading / error / empty are **never implicit** (no "empty list means either loading or no data").
Two shapes, pick by the state's nature:

**Sealed interface: states are mutually exclusive** (the common read-screen case):

```kotlin
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Error : LibraryUiState
    data class Empty(val query: String) : LibraryUiState
    data class Tracks(val tracks: List<TrackItem>, val query: String) : LibraryUiState
}
```

**Data class with derived getters: an editor with many independently-editable fields**, where
"loading/submitting" coexists with content:

```kotlin
data class PresetEditorUiState(
    val name: String = "",
    val speedPercent: Float = 100f,
    val pitchSemitones: Float = 0f,
    val submission: PresetSubmission = PresetSubmission.Idle,
) {
    val isSaveEnabled: Boolean get() = name.isNotBlank() && submission != PresetSubmission.InProgress
}
enum class PresetSubmission { Idle, InProgress, Saved, Error }
```

Rules:
- **Decision**: mutually-exclusive screen states → **sealed interface**; an editor that shows
  content *while* loading/submitting → **data class** + a lifecycle enum.
- `UiState` holds **render-ready** values, not domain models. Map in the ViewModel
  (`toTrackItem()`), never pass a repository/domain model straight to a `@Composable`.
- Derive, don't store: computed display logic goes in getters (`isSaveEnabled`), not in duplicated
  fields you have to keep in sync.

---

## Intents

Public typed methods; keep them thin: delegate to use-cases, update state.

```kotlin
fun onQueryChanged(text: String) { query.value = text }
fun onTrackRemoveConfirmed(track: LibraryTrack) {
    viewModelScope.launch { removeTrack(track) }
}
```

Handle every outcome of a use-case. If there is no UI feedback, log it and flag whether an error
state is owed.

---

## One-shot events (navigation / confirmations / messages)

- **Navigation is a callback, not a ViewModel concern.** The ViewModel never navigates; the screen
  composable receives `onClose`, `onTrackSelected`, … lambdas and the host wires them to the nav
  graph.
- For genuine fire-once events (a message or confirmation the UI must consume exactly once), use a
  `MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)`.
  **Do not use `Channel` in new code.** Expose the read-only `SharedFlow`, collect in
  `LaunchedEffect`. Add it only when a screen needs it; don't give every screen an event stream.
- Transient messages carry a `UiMessage` (see `ui/common`) and are shown through the existing
  `CollectMessages` helper.

```kotlin
private val _messages = MutableSharedFlow<UiMessage>(
    replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()
// …
_messages.tryEmit(UiMessage(Res.string.error_playlist_name))
```

> If a third screen needs the same flow, extract it as a small `SingleEventFlow` helper in
> `ui/common` instead of copying the constructor arguments.

---

## Composable structure

- **Two-layer split**: a *Screen* composable owns the ViewModel and collects state; a stateless
  *Content* composable takes `state` + lambdas. State is **hoisted**: Content never sees the
  ViewModel and is previewable.
- Collect with **`collectAsStateWithLifecycle()`**.
- **No business logic, no I/O, no use-case/repository calls inside a `@Composable`.** A composable
  is a pure function of state + callbacks. Complex display shaping goes in the `UiState` (derived
  getters) or a small mapper, not inline in the tree.
- Pass intents as method references (`viewModel::onQueryChanged`).
- Use `SlowDownerTheme` and Material3 tokens. No hard-coded colors or text sizes. Reusable widgets
  go in `ui/common` (e.g. `ValueSlider`, `TrackRow`).

```kotlin
@Composable
fun LibraryScreen(
    onTrackSelected: (LibraryTrack) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryContent(state = state, onQueryChanged = viewModel::onQueryChanged, onTrackSelected = onTrackSelected)
}

@Composable
internal fun LibraryContent(   // stateless, previewable, no ViewModel
    state: LibraryUiState,
    onQueryChanged: (String) -> Unit,
    onTrackSelected: (LibraryTrack) -> Unit,
) { when (state) { /* exhaustive render */ } }
```

---

## DI at the presentation layer

- ViewModels are created with Koin: `viewModel { }` in `di/modules/`, `koinViewModel()` as the
  default parameter of the Screen composable. `koin-compose-viewmodel` is multiplatform, so the
  same wiring works in `commonMain`.
- Screen parameters (a track id, a playlist id) go through `parametersOf(...)` or
  `SavedStateHandle`, never through a global.
- Only the Screen composable touches Koin. Content composables and ViewModels never call the
  container.

---

## Multiplatform notes

- Composables, ViewModels and `UiState` live in `commonMain`. Platform code (file pickers, share
  sheet, media session, window) stays behind an interface or a lambda passed to the Screen.
- Strings, plurals, icons and fonts come from Compose Resources (`Res.string.*`, `Res.drawable.*`) in
  `shared-ui/src/commonMain/composeResources`. `UiMessage` carries a `StringResource`.
- In `strings.xml` write `"` and `'` as they are: Compose Resources does not unescape `\"` or `\'`
  like Android resources did, and would show the backslash.
- Placeholders: only `%1$s`, `%1$d` and `%%`. Compose Resources leaves `%1$.1f` or `%1$+d` raw on
  screen: format numbers in Kotlin (`formatDecimal`, `formatSignedDecimal` in `ui/common`) and pass
  them as `%1$s`. `FormatsTest` fails the build otherwise.
- No `android.*` imports in `commonMain`. No `java.*` either, iOS has no JVM.
- Every text field gets `Modifier.reportsTextInputFocus()`: the desktop app takes Space and the
  arrows before any focused element, except while the user is typing in a text field.
- Layout adapts at 840 dp (`SlowDownerApp`): narrower is a one-screen layout with a mini player;
  wider puts the player in a fixed pane beside the library. Check new screens at both widths.

---

## Testing

- Test the **ViewModel** in `shared-ui` (`commonTest`, run by `desktopTest`): `runTest` + **Turbine**
  (`app.cash.turbine`) for state-sequence assertions; hand-written fakes from `fake/`, no
  Robolectric, no real I/O.
- Turbine's `state.test { }` keeps the collector alive, so the `WhileSubscribed` flow actually runs:
  no manual background collector. Set the main dispatcher
  (`Dispatchers.setMain(UnconfinedTestDispatcher())`) in setup. Assert the initial `Loading` as
  the first `awaitItem()`, or skip past it.

```kotlin
@Test
fun `when library fails then state is Error`() = runTest {
    val vm = LibraryViewModel(FakeObserveLibrary(error = RuntimeException("boom")), FakeObserveNowPlaying(), FakeRemoveTrack())
    vm.state.test {
        assertEquals(LibraryUiState.Loading, awaitItem())   // initial
        assertEquals(LibraryUiState.Error, awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

- Also assert use-case calls via the recording fake.
- **Every new screen ships ViewModel tests.** Turbine may need adding to the test dependencies the
  first time.

---

## Window insets

On desktop the insets are zero. Still, shared screens may run where system bars exist, so keep
the rules simple:

- Use a Material3 `Scaffold` for a screen with a top or bottom bar, and apply its `innerPadding`.
- Without a Scaffold, pad the edges you pin: `statusBarsPadding()` on top, `navigationBarsPadding()`
  at the bottom, `imePadding()` for a screen with a `TextField`.
- Never add `systemBarsPadding()` on a screen that already applies a Scaffold's `innerPadding`.
- Backgrounds, artwork and scrims stay full-bleed: put the inset modifier on the content, not on
  the node that draws the background.
- A `@Preview` renders insets as zero, so it is not evidence that an inset change is right.

---

## Anti-patterns

- Multiple ad-hoc `StateFlow`s for one screen instead of one `UiState` (one independent boolean
  signal is the only sanctioned extra)
- Implicit loading/empty (inferring it from an empty list)
- Business logic, I/O, or use-case calls inside a `@Composable`
- ViewModel that navigates, or holds a nav controller
- Passing the ViewModel (or a domain model) down into child composables
- `Effect` bus / sealed `Action` added to a simple screen "for consistency"
- `Channel` for one-shot events in new code (use a `MutableSharedFlow` with `DROP_OLDEST`)
- Exposing a `StateFlow` from anything other than a ViewModel
- Imperative `load()` in `init` instead of `stateIn(WhileSubscribed)`
- Koin reached into from a Content composable or a ViewModel
- `android.*` or `java.*` imports in code meant for `commonMain`
- `systemBarsPadding()` on a screen that already applies a Scaffold's `innerPadding`
- Inset padding on the node that draws a background, artwork or scrim (kills the full bleed)

---

## TL;DR

- Lean MVVM: one immutable `UiState`, state down / events up, intents as typed methods
- `androidx.lifecycle.ViewModel` + single `StateFlow` via `stateIn(WhileSubscribed(5000))`
- Loading/error/empty explicit: sealed interface (exclusive) or data class + enum (editors)
- Navigation = callbacks; one-shot events = `MutableSharedFlow` (not `Channel`) only at need
- Screen composable owns the VM via `koinViewModel()`; stateless Content takes state + lambdas;
  no logic in `@Composable`
- ViewModel tests with Turbine; mind the `WhileSubscribed` collector + skip initial `Loading`
- Insets: `Scaffold` or an explicit top/bottom pair; backgrounds stay full-bleed; text fields get
  `imePadding()`

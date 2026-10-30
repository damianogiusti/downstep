---
name: clean-architecture
description: Data and domain layering for Downstep. Use when structuring a new feature, deciding where logic or I/O belongs, naming a new type in data/domain, or reviewing layering. Covers use-cases, repositories, data sources/stores, mappers, error model, concurrency boundary. UI is covered by kotlin-compose-standards. Advisory only: never force-refactor working legacy code.
---

# Clean architecture: data and domain

Dependencies point inward, toward the domain. Domain imports no `android.*`, no `java.*`, no NewPipe/Room/OkHttp types.

```
ui/<feature>  ->  UseCase  ->  Repository (interface, domain)  <-  Default...Repository (data)
                                                                      -> DataSource / Store / Adapter
```

Layout: `domain/<feature>`, `data/<feature>`, `ui/<feature>`. `domain/` lives in module `shared`
(`commonMain`), with `data/playback/InMemoryNowPlayingRepository`; `data/` is in `shared-data` and
`ui/` in `shared-ui`. All of `shared/commonMain` is common Kotlin: no Android, no `java.*`.
Features: effects, export, library, playback, playlists, practice, source, streaming.

| Layer | Does | Must not |
|---|---|---|
| UseCase | One business action. `operator fun invoke`, usually over one repository. Thin is fine. | touch UI, Android, raw I/O |
| Repository | Domain-facing contract. Owns sync/cache and source-of-truth. Returns domain models. Maps errors. Is the concurrency boundary. | contain business rules |
| DataSource / Store | Talks to one origin (disk, network, SDK). Returns DTOs. | know other sources or domain rules |
| Mapper | DTO/SDK <-> domain. | hold state |
| Adapter | Wraps a system or native API behind a domain interface (e.g. the adapter over the audio engine). | hold business logic |

## Real examples

```kotlin
class ImportTrackFromUrlUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(url: String, onProgress: (ImportProgress) -> Unit): ImportResult =
        repository.importFromUrl(url, onProgress)
}
```

- Thin use-cases are on purpose: a caller depends on one action, not the whole `LibraryRepository`,
  and its test fakes one function.
- `DefaultLibraryRepository` takes an injected `CoroutineDispatcher` and wraps work in
  `withContext(dispatcher)`; reactive reads use `flowOn(dispatcher)`. Use-cases and ViewModels never
  pick dispatchers.
- Local persistence: `...Store` classes. Network or SDK:
  `...DataSource`.
- Repositories return cold `Flow`. Only ViewModels expose `StateFlow`.

## Error model

Prefer a sealed result over exceptions, mapped at the repository boundary, so callers handle every
outcome. Existing model: `ImportResult` (`Success`, `Unavailable`, `NetworkError`, `Failure`).
A NewPipe or HTTP exception never escapes the repository. Never swallow errors: no empty `catch`,
no `runCatching { }.getOrNull()` that hides a failure. No UI for it yet? Log it and flag the gap.

## Naming for new types

`...UseCase` (verb), `...Repository` (interface), `Default...Repository` (impl), `...DataSource`,
`...Store` (local storage with its own concern), `...Mapper`, `...Dto`, `...Adapter` (boundary only).
Avoid `Manager`, `Handler`, `Helper`, `Util`, `Service` in new domain/data code.

## DI (Koin)

Koin 4. Shared modules: `useCasesModule` (`shared-data`) and `uiModule` (`shared-ui`). Platform
bindings live in `desktopModule` (`desktopApp`).

- Repositories and engines: `singleOf(::X) { bind<I>() }`. Use-cases: `factoryOf(::X)`.
  ViewModels: `viewModelOf(::X)`. Use an explicit lambda only when arguments need hand-wiring.
- Dispatchers are injected and go to repositories only.
- Composables take `viewModel: X = koinViewModel()`.
- Domain classes never call the container. The DI test in `desktopApp` builds the graph: keep it
  green when adding definitions.

## Workflow for a new feature

1. Does it have domain logic over data? If it is presentation only, this skill does not apply.
2. Name the entities and the use-case verbs.
3. Define the repository interface in `domain/<feature>`, with a sealed result.
4. Implement data sources, mappers and `Default...Repository` in `data/<feature>`.
5. Wire Koin.
6. Test use-cases and the repository with fakes (see AGENTS.md, Tests).

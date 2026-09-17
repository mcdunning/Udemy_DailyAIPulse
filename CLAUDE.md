# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Start here

Read `docs/PROJECT_STATUS.md` first, at the start of any session — it's a running handoff summary (current state, files touched, mistakes to avoid, open problems) meant to be enough by itself to work productively without re-deriving context. Update it, following the rules at the top of that file, after finishing significant work (a feature, a merged PR, a major fix).

## Workflow

- **Design doc before code.** Every feature planning/brainstorming session must produce a written design doc under `docs/superpowers/specs/` that the user has explicitly approved before any code is written — even for changes that would otherwise look small or "bounded."
- **Feature branch + PR, always.** All work happens on a feature branch; never commit directly to `main`. Open a PR and get it reviewed/approved before merging.
- **Answer every PR review question on the PR.** When a reviewer leaves a question in a PR comment, reply to it directly on the PR (e.g. `gh pr comment`) — not only in chat with the user.

## Commands

```bash
./gradlew assembleDebug              # Build the debug APK
./gradlew :app:compileDebugKotlin    # Fast compile check without running tests
./gradlew testDebugUnitTest          # Run all unit tests
./gradlew testDebugUnitTest --tests "com.dailyaipulse.articles.presentation.ArticleListViewModelTest"  # Run a single test class
./gradlew connectedDebugAndroidTest  # Run Compose UI / instrumented tests (requires a connected device or emulator)
./gradlew installDebug               # Install the debug build on a connected device/emulator
```

Requires a `NEWS_API_KEY` in the developer's **global** `~/.gradle/gradle.properties` (outside this repo, never committed) — exposed to app code as `BuildConfig.NEWS_API_KEY`. See `README.md` for details and the NewsAPI free-plan `localhost`-only constraint.

## Architecture

`docs/superpowers/specs/2026-09-12-architecture-design.md` is the source of truth for this project's conventions — read it before making any structural decision. Each feature also has its own design spec under `docs/superpowers/specs/` (e.g. `2026-09-12-article-list-design.md`); read the relevant one before touching that feature. Implementation plans live in `docs/superpowers/plans/`.

Summary, so routine work doesn't require opening the docs:

- **MVVM + Repository, no domain/use-case layer, no shared base classes.** These are explicit, deliberate, repeated decisions — do not introduce a domain layer, use-case classes, or `BaseViewModel`/`BaseRepository`-style shared base classes unless the user explicitly asks for one.
- **Feature-first packages** under `com.dailyaipulse`: each feature (`articles`, and eventually `sources`, `summary`) has exactly three sub-packages — `ui/`, `presentation/`, `data/` — no more, no fewer. `core/` holds shared infrastructure (`core/network`, `core/di`), and is not a feature itself. `navigation/` holds `AppNavigation.kt` (the `NavHost`).
- **Data layer per feature**: a data model matching the raw API response (Moshi `@JsonClass(generateAdapter = true)`), a Retrofit `ApiService` interface, and a single concrete `Repository` class — no Repository interface/impl split, no separate remote-data-source wrapper. Repository functions are `suspend fun` and let exceptions propagate to the caller rather than catching them.
- **Presentation layer per feature**: a `@HiltViewModel`, a sealed `UiState` (not boolean flags — e.g. `Loading`/`Success`/`Error` as distinct, mutually exclusive variants), and a presentation model distinct from the data-layer model. The ViewModel maps the data model to the presentation model itself (no separate mapper class) and exposes state via `StateFlow`, collected in Compose via `collectAsStateWithLifecycle()`.
- **UI layer**: a screen is split into a thin ViewModel-collecting wrapper (e.g. `ArticleListScreen`) and a stateless content composable (e.g. `ArticleListContent`) that takes the `UiState` and callbacks directly, with no ViewModel dependency. This split exists specifically so the content composable is unit-testable and previewable without a Hilt graph.
- **Every Composable must have `@Preview` coverage** — a stated guiding principle, not optional polish. Preview the stateless content composable with one `@Preview` per meaningful `UiState` variant; the ViewModel-collecting wrapper itself is exempt, since `hiltViewModel()` can't resolve a Hilt graph inside a preview.
- **DI**: Hilt. Per-feature Hilt modules live inside that feature's own `data/` package. `core/di/NetworkModule` provides the shared `Retrofit`/`OkHttpClient`/`Moshi`; `core/network/NewsApiKeyInterceptor` attaches the NewsAPI key to every request as an `X-Api-Key` header (not a query param).
- **Network error handling**: never surface a raw exception message to the user. `core/network/NetworkErrorMapper.kt`'s `Throwable.toUserMessage()` logs the full exception via Timber first, then returns a two-line, generic user-facing message (with specific handling for NewsAPI's HTTP 429 rate limit, using the `Retry-After` header when present). Every feature calling NewsAPI should reuse this rather than reinventing it.
- **Testing**: MockK for mocking (Repository classes are concrete, not interfaces, and MockK handles mocking concrete Kotlin classes/`suspend fun`s more cleanly than Mockito here), `kotlinx-coroutines-test` for coroutine-based code, Turbine for asserting `StateFlow` emission sequences, Compose UI testing for the stateless content composables.

## Build system notes

- AGP 9.3.2 with **built-in Kotlin** — there is no `org.jetbrains.kotlin.android` plugin applied; Kotlin compilation is built into AGP itself.
- `gradle.properties` sets `android.disallowKotlinSourceSets=false` — a required interim workaround because KSP doesn't yet fully support AGP's built-in Kotlin (upstream issue: google/ksp#2729). Don't remove it without checking whether that's been fixed upstream.
- `app/build.gradle.kts` forces `kotlin-stdlib` to the project's declared Kotlin version via `resolutionStrategy` — needed because Coil 3.x transitively pulls in a newer stdlib than this project's Kotlin compiler can read.
- Core library desugaring is enabled (`isCoreLibraryDesugaringEnabled = true`) so `java.time` (used for date formatting) can be used despite `minSdk = 24`.

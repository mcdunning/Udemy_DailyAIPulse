# DailyAIPulse — MVP Architecture Design

**Date:** 2026-09-12
**Status:** Approved (architecture only — feature specs are separate documents)

## Overview

DailyAIPulse is a native Android MVP built with Jetpack Compose. It ships as three independent features, each designed, planned, and implemented on its own:

1. **Article List** — fetches and displays a list of articles from an API.
2. **Source List** — fetches and displays a list of article sources from an API.
3. **AI Summarization** — generates AI summaries of articles (depends on Article List existing).

Build order: **Article List → Source List → AI Summarization**.

This document defines the architecture shared by all three features. Each feature gets its own design spec layered on top of this foundation.

## Guiding Principles

- **Simplicity** — the overriding priority. When a pattern adds structure without solving a problem the app actually has yet, it's left out.
- **Separation of concerns** — each layer has one clear job.
- **Testability** — layers are separated enough to test independently.
- **Maintainability** — consistent structure so future features slot in without rework.

These are the *general principles* commonly associated with Clean Architecture. This project deliberately does **not** adopt Clean Architecture's domain/use-case layer (see below).

## Explicit Non-Goals

- **No domain layer, no use-case classes.** Every feature is exactly 3 layers: `ui`, `presentation`, `data`. Do not add a domain layer or use cases unless explicitly requested in the future.
- **No shared/abstract base classes** (no `BaseViewModel`, `BaseRepository`, etc.). Each class stands on its own.
- **No local database or on-device storage.** Data layer talks to the remote API only.
- **No Repository interfaces.** Each feature's Repository is a single concrete class — no interface/impl split, no separate remote-data-source wrapper class. The Repository calls the Retrofit API service directly.

## Package Layout

Top-level packaging is strictly feature-based. Each feature package contains **exactly** 3 sub-packages: `ui`, `presentation`, `data` — no more, no fewer.

```
com.dailyaipulse/
├── articles/
│   ├── ui/            # ArticleListScreen
│   ├── presentation/  # ArticleListViewModel, ArticleListUiState, Article (presentation model)
│   └── data/          # ArticleData, ArticleApiService, ArticleRepository, ArticleModule (Hilt)
├── sources/
│   ├── ui/            # SourceListItem, SourceListContent, SourceListScreen
│   ├── presentation/  # SourceListViewModel, SourceListUiState, Source (presentation model)
│   └── data/          # SourceData, SourceApiService, SourceRepository, SourceModule (Hilt)
├── summary/
│   ├── ui/
│   ├── presentation/
│   └── data/
├── core/                  # shared infrastructure only — not a feature
│   ├── network/           # Retrofit instance, base API client config
│   ├── di/                # shared Hilt modules (Retrofit, OkHttpClient, base network config)
│   └── ui/                # shared Compose components, theme
├── navigation/
│   └── AppNavigation.kt   # NavHost + route definitions
└── MainActivity.kt        # single Activity — calls AppNavigation()
```

## Layer Responsibilities

### UI Layer (`ui/`)

- Single-Activity app. Compose screens only — no business or state logic.
- Screens render whatever `UiState` the ViewModel exposes; they don't make decisions.
- Navigation via **Navigation Compose** — see the Navigation section below.
- **Every Composable must have `@Preview` coverage.** **Added 2026-09-14**, from PR review — a guiding principle for the project: a developer should be able to review UI changes in the IDE's preview canvas without pushing to a device. For a screen split into a ViewModel-collecting wrapper (e.g. `ArticleListScreen`) and a stateless content composable (e.g. `ArticleListContent`) — see the Presentation Layer section — preview the stateless content composable, with one `@Preview` per meaningful `UiState` variant (loading, error, empty, success, pagination-loading, pagination-error, etc.). The thin wrapper itself typically isn't previewable, since `hiltViewModel()` can't resolve a real Hilt graph in a preview — that's expected, not a gap to work around.

### Presentation Layer (`presentation/`)

Per feature, contains:
- **ViewModel** — owns UI logic and UI state; calls the Repository.
- **UiState class(es)** — the data shape representing what's currently on screen (loading / content / error).
- **Presentation model** (e.g. `Article`) — the model the UI actually renders. Distinct from the data layer's API model.

State is exposed from ViewModel to Compose via **`StateFlow`** (e.g. `StateFlow<ArticleListUiState>`), collected in the Composable via `collectAsStateWithLifecycle()`.

Whether the mapping from the data layer's model (e.g. `ArticleData`) to the presentation model (e.g. `Article`) happens inside the Repository (`data/`) or inside the ViewModel is a **per-feature decision** — each feature spec states its own answer (Article List: mapping happens in the ViewModel, since the Repository returns the data-layer model directly).

### Data Layer (`data/`)

Per feature, contains:
- **Data model** (e.g. `ArticleData`) — shape matching the API response.
- **API service** (e.g. `ArticleApiService`) — a Retrofit interface; this is the remote data source. No local database or on-device storage.
- **Repository** (e.g. `ArticleRepository`) — a single **concrete class**, not an interface/impl pair. Calls the Retrofit API service directly (no separate remote-data-source wrapper). Functions are `suspend fun`, using Kotlin Coroutines.
- **Hilt module** (e.g. `ArticleModule`) — provides this feature's `ApiService` and `Repository`.

## Navigation

**Navigation Compose** (`androidx.navigation:navigation-compose`), not Navigation 3. Type-safe routes (`@Serializable object <Feature>Route`). The nav graph and any app-level navigation chrome live in one file, `navigation/AppNavigation.kt`, not inline in `MainActivity`.

**Added 2026-09-17**, resolving the navigation-entry-point decision this doc originally left open: a Material 3 **bottom navigation bar** is the app's top-level navigation chrome, wrapping the `NavHost` in a `Scaffold`. It's the standard pattern for 2–5 equally-important, always-visible top-level destinations — the case this app is in (Article List, Source List, and potentially future top-level features). A top-bar icon was rejected for implying Source List is subordinate to Article List rather than a peer destination; a navigation drawer was rejected as overkill for only two destinations.

Tab selection state comes from `currentBackStackEntryAsState()`. Switching tabs uses the standard Material bottom-nav navigation options — `popUpTo(graph.findStartDestination().id) { saveState = true }`, `launchSingleTop = true`, `restoreState = true` — so tab switches don't stack the back stack or lose each tab's own state:

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentDestination?.hasRoute<ArticleListRoute>() == true,
                    onClick = { navController.navigateToTab(ArticleListRoute) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Articles") }
                )
                NavigationBarItem(
                    selected = currentDestination?.hasRoute<SourceListRoute>() == true,
                    onClick = { navController.navigateToTab(SourceListRoute) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Sources") }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = ArticleListRoute,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable<ArticleListRoute> { ArticleListScreen() }
            composable<SourceListRoute> { SourceListScreen() }
        }
    }
}

private fun <T : Any> NavController.navigateToTab(route: T) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

`navigateToTab` is a small private helper local to `AppNavigation.kt` rather than a shared abstraction — it's a one-line convenience specific to this nav bar. Any future top-level feature (e.g. if AI Summarization ever became its own tab, rather than reached from within Article List) adds a third `NavigationBarItem` here rather than introducing a different navigation pattern.

## External APIs

**Provider: [NewsAPI.org](https://newsapi.org/)**, used by Article List and (likely) Source List. Endpoint-level detail (exact routes, query params, response shape) belongs in each feature's own design spec, not here — this section covers only what's cross-cutting.

- **Authentication:** API key stored as `NEWS_API_KEY` in the developer's **global** `~/.gradle/gradle.properties` (outside the repo, never committed) and exposed to app code via a `BuildConfig` field (implemented — see `app/build.gradle.kts`).
- **Known constraint:** NewsAPI's free "Developer" plan restricts key usage to `localhost` — it explicitly disallows use from a live/distributed app. Accepted for now since this is a learning project; revisit (paid plan or backend proxy) before any real distribution.
- **Key attachment:** a shared OkHttp interceptor in `core/network` attaches the key automatically to every NewsAPI request (rather than a `@Query("apiKey")` parameter repeated on each Retrofit method). Decided once here since it's cross-cutting — applies to every feature calling NewsAPI. Each feature's API service interface should carry a comment noting that auth happens via this interceptor, since it's otherwise invisible from the interface alone.

## Dependency Injection

**Hilt.**

- Per-feature Hilt modules live inside that feature's own `data/` package (e.g. `articles/data/ArticleModule.kt`).
- `core/di/` holds only shared, app-wide providers: the Retrofit instance, `OkHttpClient`, base network config.

### `core/network` — Auth Interceptor

```kotlin
class NewsApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-Api-Key", BuildConfig.NEWS_API_KEY)
            .build()
        return chain.proceed(request)
    }
}
```

Attaches the NewsAPI key as an `X-Api-Key` header to every request — this is what "key attachment via interceptor" (decided above) actually is.

### `core/network` — Error Mapping

**Added 2026-09-14**, from Article List's PR review: raw exception messages (e.g. `e.message`) were being shown directly to users — a technical/internal string, and one that gave no special handling to NewsAPI's rate limiting (HTTP 429), which came up during manual testing. This is cross-cutting (any feature calling NewsAPI can hit the same rate limit), so it lives here rather than per-feature:

```kotlin
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val RETRY_AFTER_HEADER = "Retry-After"

// Never surfaces the raw exception message to the user (it's a developer-facing
// string); logs the full exception via Timber first so it's still debuggable.
fun Throwable.toUserMessage(): String {
    Timber.e(this, "Network call failed")
    return if (this is HttpException && code() == HTTP_TOO_MANY_REQUESTS) {
        val retryAfterSeconds = response()?.headers()?.get(RETRY_AFTER_HEADER)?.toIntOrNull()
        val instruction = if (retryAfterSeconds != null) {
            "Please try again in $retryAfterSeconds seconds."
        } else {
            "Please try again later."
        }
        "You've made too many requests.\n$instruction"
    } else {
        "Something went wrong.\nPlease try again."
    }
}
```

Every feature's ViewModel should call `Throwable.toUserMessage()` in its catch blocks instead of using the raw exception message — this is the standing pattern for turning any network failure into UI-facing text, not just an Article List detail. **Updated 2026-09-14** (further PR review): the message is two lines (`\n`-separated) — a short description, then the retry instruction on its own line underneath — not one run-on sentence. When rendering this in Compose, center it (`textAlign = TextAlign.Center`) and give it `fillMaxWidth()`; without `fillMaxWidth()`, a wrapped multi-line `Text` still renders each line left-aligned even when the `Text` composable itself is positioned in the center of its parent.

### `core/di` — `NetworkModule`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(NewsApiKeyInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://newsapi.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
}
```

Shared across every feature that calls NewsAPI — a feature's own Hilt module (e.g. `ArticleModule`) takes the `Retrofit` this provides and builds its feature-specific API service from it.

## Async & State Management

- **Kotlin Coroutines** for all asynchronous work — API calls and any data processing/mapping. Repository functions are `suspend fun`.
- **StateFlow** carries UI state from ViewModel to Compose UI.

## Tech Stack

**Generic:** Hilt, Kotlin Coroutines, Timber

**UI:** Jetpack Compose, Material 3, Navigation Compose, Hilt Navigation Compose, StateFlow, Coil (image loading), Compose Material Icons (`material-icons-core` — see note below)

**Presentation:** StateFlow

**Data:** Retrofit, Moshi + Moshi Kotlin codegen, OkHttp, OkHttp logging interceptor

**Testing:** MockK (mocking — chosen over Mockito since our Repository classes are concrete, not interfaces, and MockK mocks concrete Kotlin classes/`suspend fun`s cleanly), kotlinx-coroutines-test (`runTest`/test dispatchers for `suspend fun` and `viewModelScope` code), Turbine (asserting `StateFlow` emission sequences). Compose UI testing already covered by the project template's `androidx-compose-ui-test-junit4`/`androidx-espresso-core`/`androidx-junit`. `hilt-android-testing` intentionally deferred — our ViewModel/Repository are plain constructor-injected classes, unit-testable without swapping the Hilt DI graph.

### Supporting Pieces Required (Not Separate Choices, But Easy to Miss)

- **Gradle plugins:** Hilt Android Gradle plugin; KSP (Hilt and Moshi codegen both use KSP, not kapt); `org.jetbrains.kotlin.plugin.serialization` — required for Navigation Compose's type-safe route arguments, which use `kotlinx.serialization`. This is a *separate* serialization mechanism from Moshi: Moshi handles API JSON only, kotlinx.serialization handles nav route args only.
- **Connective libraries:** `com.squareup.retrofit2:converter-moshi` (bridges Retrofit ↔ Moshi), `org.jetbrains.kotlinx:kotlinx-serialization-json` (nav routes), `org.jetbrains.kotlinx:kotlinx-coroutines-android` (Android dispatcher support), `com.google.dagger:hilt-android-compiler` (via ksp), `androidx.lifecycle:lifecycle-runtime-compose` (for `collectAsStateWithLifecycle()`).
- **Manifest/code, not a dependency:** `INTERNET` permission in `AndroidManifest.xml`; a custom `Application` class annotated `@HiltAndroidApp`, registered in the manifest. (Not yet added — needed once a feature actually wires up Hilt/networking.)
- **Core library desugaring:** `minSdk = 24` is below API 26, which is required to use `java.time` (`Instant`, `Duration`, `DateTimeFormatter`) natively. Rather than avoiding `java.time` or adding a separate date library, this project uses **core library desugaring** — Google's standard recommendation for exactly this situation — via the `com.android.tools:desugar_jdk_libs` dependency (`coreLibraryDesugaring` configuration) plus `isCoreLibraryDesugaringEnabled = true` in `compileOptions`. No app code needs to know it's happening.

### Resolved Dependency Versions (added 2026-09-12)

Added to `gradle/libs.versions.toml`, `build.gradle.kts`, and `app/build.gradle.kts`, verified via `./gradlew :app:dependencies` and `./gradlew :app:compileDebugKotlin` against this project's AGP `9.3.2` / Kotlin `2.2.10`:

| Library | Version |
|---|---|
| Hilt (`hilt-android`, `hilt-android-compiler`, Hilt Gradle plugin) | 2.60.1 |
| Hilt Navigation Compose (`androidx.hilt:hilt-navigation-compose`) | 1.4.0 |
| Hilt Compiler (`androidx.hilt:hilt-compiler`) | 1.4.0 |
| KSP (`com.google.devtools.ksp`) | 2.2.10-2.0.2 |
| Navigation Compose (`androidx.navigation:navigation-compose`) | 2.10.1 |
| Lifecycle Runtime Compose (`androidx.lifecycle:lifecycle-runtime-compose`) | 2.11.0 |
| Kotlin Coroutines Android (`kotlinx-coroutines-android`) | 1.10.2 |
| kotlinx-serialization-json | 1.9.0 |
| Retrofit (`retrofit`, `converter-moshi`) | 3.0.0 |
| Moshi (`moshi`, `moshi-kotlin-codegen`) | 1.15.2 |
| OkHttp (`okhttp`, `logging-interceptor`) | 4.12.0 |
| Timber | 5.0.1 |
| Coil (`coil-compose`, `coil-network-okhttp`) | 3.6.2 |
| Core library desugaring (`com.android.tools:desugar_jdk_libs`) | 2.1.5 |
| MockK | 1.14.11 |
| Turbine | 1.2.1 |
| Compose Material Icons Core (`androidx.compose.material:material-icons-core`) | BOM-managed (no separate version) |
| kotlinx-coroutines-test | 1.10.2 (matches `kotlinx-coroutines-android`, pinned for the same Kotlin-`2.2.10`-compatibility reason) |

**Added 2026-09-17**, during the navigation shell's implementation (see `docs/superpowers/specs/2026-09-16-navigation-shell-design.md`): `androidx.compose.material:material-icons-core` had to be added as an explicit dependency to resolve `Icons.*` references (e.g. `Icons.Filled.Home`) — `material3` does **not** transitively pull in even the base icon set. Any feature using a Material icon needs this dependency present; it's already added. This project intentionally does not add `material-icons-extended` (the much larger full icon set) — the base `material-icons-core` set has been sufficient so far.

Two judgment calls worth flagging so they don't read as stale later:
- **OkHttp/Retrofit** are pinned to `4.12.0`/`3.0.0` rather than OkHttp's still-alpha 5.x line — Retrofit's own latest stable (`3.0.0`) itself depends on OkHttp 4.12, so this keeps the pair aligned with what Retrofit was actually built and tested against.
- **Coroutines/serialization** are pinned to `1.10.2`/`1.9.0` rather than each library's newest release (`1.11.0`/`1.11.0`), because those newest releases require a newer Kotlin (2.2.20 / 2.3.20 respectively) than this project declares (`2.2.10`).

**Known build quirk:** this project's AGP uses **built-in Kotlin** compilation (no separate `org.jetbrains.kotlin.android` plugin needed/applied). The current KSP release doesn't yet fully support that mode — it still adds sources via the classic `kotlin.sourceSets` DSL, which built-in Kotlin rejects by default (a confirmed, still-open upstream issue: [google/ksp#2729](https://github.com/google/ksp/issues/2729)). `gradle.properties` sets `android.disallowKotlinSourceSets=false` to work around this — this is Android's own documented interim compatibility flag ([migrate-to-built-in-kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)), not an ad-hoc hack. Remove it once KSP ships proper built-in-Kotlin support.

## Testing

Each layer is independently testable given the separation above:
- **Repository** — unit-testable by faking the API service.
- **ViewModel** — unit-testable by faking the Repository, asserting `StateFlow` emissions.
- **Compose UI** — testable via Compose UI testing, driven by fixed `UiState` values, independent of ViewModel/data internals.

(Detailed test plans belong in each feature's own design spec, once that feature's behavior is fully defined.)

## Feature Specs

Feature-specific details (exact endpoints, request params, response shapes, UI) live in each feature's own design spec:
- Article List: `docs/superpowers/specs/2026-09-12-article-list-design.md`
- Source List: `docs/superpowers/specs/2026-09-17-source-list-design.md`

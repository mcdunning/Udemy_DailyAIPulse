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
│   ├── ui/
│   ├── presentation/
│   └── data/
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
- Navigation via **Navigation Compose** (`androidx.navigation:navigation-compose`), not Navigation 3. Type-safe routes. The nav graph lives in its own file, `navigation/AppNavigation.kt`, not inline in `MainActivity`.

### Presentation Layer (`presentation/`)

Per feature, contains:
- **ViewModel** — owns UI logic and UI state; calls the Repository.
- **UiState class(es)** — the data shape representing what's currently on screen (loading / content / error).
- **Presentation model** (e.g. `Article`) — the model the UI actually renders. Distinct from the data layer's API model.

State is exposed from ViewModel to Compose via **`StateFlow`** (e.g. `StateFlow<ArticleListUiState>`), collected in the Composable via `collectAsStateWithLifecycle()`.

**Open decision:** whether the mapping from the data layer's model (e.g. `ArticleData`) to the presentation model (e.g. `Article`) happens inside the Repository (`data/`) or inside the ViewModel. Deferred until the API is chosen and reviewed.

### Data Layer (`data/`)

Per feature, contains:
- **Data model** (e.g. `ArticleData`) — shape matching the API response.
- **API service** (e.g. `ArticleApiService`) — a Retrofit interface; this is the remote data source. No local database or on-device storage.
- **Repository** (e.g. `ArticleRepository`) — a single **concrete class**, not an interface/impl pair. Calls the Retrofit API service directly (no separate remote-data-source wrapper). Functions are `suspend fun`, using Kotlin Coroutines.
- **Hilt module** (e.g. `ArticleModule`) — provides this feature's `ApiService` and `Repository`.

**Open decision:** the actual API/backend for Article List and Source List has not yet been chosen.

## Dependency Injection

**Hilt.**

- Per-feature Hilt modules live inside that feature's own `data/` package (e.g. `articles/data/ArticleModule.kt`).
- `core/di/` holds only shared, app-wide providers: the Retrofit instance, `OkHttpClient`, base network config.

## Async & State Management

- **Kotlin Coroutines** for all asynchronous work — API calls and any data processing/mapping. Repository functions are `suspend fun`.
- **StateFlow** carries UI state from ViewModel to Compose UI.

## Tech Stack

**Generic:** Hilt, Kotlin Coroutines, Timber

**UI:** Jetpack Compose, Material 3, Navigation Compose, Hilt Navigation Compose, StateFlow

**Presentation:** StateFlow

**Data:** Retrofit, Moshi + Moshi Kotlin codegen, OkHttp, OkHttp logging interceptor

**Testing:** not yet defined — deferred to a later design session.

### Supporting Pieces Required (Not Separate Choices, But Easy to Miss)

- **Gradle plugins:** Hilt Android Gradle plugin; KSP (Hilt and Moshi codegen both use KSP, not kapt); `org.jetbrains.kotlin.plugin.serialization` — required for Navigation Compose's type-safe route arguments, which use `kotlinx.serialization`. This is a *separate* serialization mechanism from Moshi: Moshi handles API JSON only, kotlinx.serialization handles nav route args only.
- **Connective libraries:** `com.squareup.retrofit2:converter-moshi` (bridges Retrofit ↔ Moshi), `org.jetbrains.kotlinx:kotlinx-serialization-json` (nav routes), `org.jetbrains.kotlinx:kotlinx-coroutines-android` (Android dispatcher support), `com.google.dagger:hilt-android-compiler` (via ksp), `androidx.lifecycle:lifecycle-runtime-compose` (for `collectAsStateWithLifecycle()`).
- **Manifest/code, not a dependency:** `INTERNET` permission in `AndroidManifest.xml`; a custom `Application` class annotated `@HiltAndroidApp`, registered in the manifest.

## Testing

Each layer is independently testable given the separation above:
- **Repository** — unit-testable by faking the API service.
- **ViewModel** — unit-testable by faking the Repository, asserting `StateFlow` emissions.
- **Compose UI** — testable via Compose UI testing, driven by fixed `UiState` values, independent of ViewModel/data internals.

(Detailed test plans belong in each feature's own design spec, once that feature's behavior is fully defined.)

## Open Items Carried Into Feature Specs

1. API/backend choice for Article List and Source List.
2. Location of the `ArticleData → Article` (data-model → presentation-model) mapping — Repository vs. ViewModel.
3. Testing library choices (e.g. MockK, Turbine, kotlinx-coroutines-test).

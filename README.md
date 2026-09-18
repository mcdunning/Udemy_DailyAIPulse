# DailyAIPulse

A native Android MVP that shows a daily pulse of technology news, pulled from [NewsAPI.org](https://newsapi.org/). Built as a Udemy course project on building an Android app with Claude Code.

## Features

Built as three independent features, one at a time:

1. **Article List** — paginated list of technology headlines, with loading/empty/error states. ✅ Implemented.
2. **Source List** — list of technology news sources, with name/description per item. ✅ Implemented.
3. **AI Summarization** — AI-generated summaries of articles. Planned.

## Architecture

MVVM + Repository, deliberately without a Clean Architecture domain/use-case layer — see `docs/superpowers/specs/2026-09-12-architecture-design.md` for the full rationale and conventions. In short:

- Feature-first packages, each with exactly three sub-packages: `ui/`, `presentation/`, `data/`.
- Hilt for dependency injection.
- Retrofit + Moshi + OkHttp for networking, with a shared `core/network` auth interceptor and error mapper.
- Kotlin Coroutines + `StateFlow` for async work and UI state.
- Jetpack Compose + Material 3 + Navigation Compose for UI, with every Composable covered by `@Preview`.
- Coil for image loading.

Each feature has its own design spec under `docs/superpowers/specs/`, building on the shared architecture doc.

## Requirements

- Android Studio with AGP 9.3.2 / Kotlin 2.2.10 support
- `minSdk` 24, `compileSdk` 37
- A [NewsAPI.org](https://newsapi.org/) API key

## Setup

This app calls NewsAPI.org, which requires an API key. Add it to your **global** Gradle properties file (`~/.gradle/gradle.properties` — *not* a file inside this project, so it never gets committed):

```properties
NEWS_API_KEY=your_key_here
```

It's picked up automatically at build time and exposed as `BuildConfig.NEWS_API_KEY`.

> NewsAPI's free "Developer" plan only permits calls from `localhost`. This is fine for local development but not for a distributed app — see the architecture doc's "External APIs" section before shipping anywhere beyond your own device/emulator.

## Building, testing, running

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # unit tests (Repository, ViewModel, date formatting, error mapping)
./gradlew connectedDebugAndroidTest  # Compose UI tests (requires a connected device/emulator)
./gradlew installDebug           # install on a connected device/emulator
```

## Project docs

- `docs/superpowers/specs/2026-09-12-architecture-design.md` — shared architecture, tech stack, cross-cutting conventions.
- `docs/superpowers/specs/2026-09-12-article-list-design.md` — Article List feature spec.
- `docs/superpowers/plans/2026-09-13-article-list-implementation.md` — Article List implementation plan.
- `docs/superpowers/specs/2026-09-17-source-list-design.md` — Source List feature spec.
- `docs/superpowers/plans/2026-09-18-source-list.md` — Source List implementation plan.

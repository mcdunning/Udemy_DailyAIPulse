# Project Status

Running handoff summary of this project's current state. Read this first when starting a new session — it's meant to be enough, by itself, to work productively without re-deriving context from git history or the full conversation.

## Update rules

Update this document after completing significant work (finishing a feature, merging a PR, resolving a major issue). Replace the content below entirely — don't append a history log. Follow these rules exactly:

- Exactly three paragraphs. No more, no fewer.
- Facts only — no assumptions, theories, or speculation about why something happened or what might be true.
- Written as instructions to another developer, as if this document were the *only* context they would get.
- **Paragraph 1:** what was done and the current state of the project.
- **Paragraph 2:** files updated/created, plus mistakes to avoid and key insights/lessons learned.
- **Paragraph 3:** current problems/errors/open items, and anything else needed to work productively (setup requirements, what's unverified, what's not started).

**Last updated:** 2026-09-17, after the Navigation Shell (PR #3) merged to `main`.

---

**Current state and what was done:** DailyAIPulse is a native Android MVP (Kotlin, Jetpack Compose, MVVM+Repository, Hilt DI, no domain layer). Article List (PRs #1, #2) is complete and merged, unchanged since the last update. This session added a navigation shell on top of it: a Material 3 bottom navigation bar with two tabs, "Articles" (Article List, start destination) and "Sources" (a new blank placeholder screen), resolving the nav-entry-point decision the architecture doc had explicitly left open. The work shipped as PR #3 (`feature/navigation-shell`), reviewed and merged to `main`; the branch has been deleted both locally and on the remote. Source List's real feature (NewsAPI endpoint, data layer, presentation layer, actual list UI) has **not** been started — only its `ui/` package exists, holding the blank placeholder screen.

**Files updated/created, mistakes to avoid, and lessons learned:** New design doc `docs/superpowers/specs/2026-09-16-navigation-shell-design.md`. Changed: `app/src/main/java/com/dailyaipulse/navigation/AppNavigation.kt` (added `SourceListRoute`, wrapped `NavHost` in a `Scaffold` with `NavigationBar`, added a private `navigateToTab` helper using the standard `popUpTo`/`launchSingleTop`/`restoreState` pattern so tab switches don't stack the back stack or lose per-tab state), `app/build.gradle.kts` and `gradle/libs.versions.toml` (added `androidx.compose.material:material-icons-core` as an explicit dependency — **lesson:** `material3` does **not** transitively pull in even the base icon set, so any future use of `Icons.*` needs this dependency present; it's already added now). New file: `app/src/main/java/com/dailyaipulse/sources/ui/SourceListScreen.kt` (blank, stateless, `@Preview`-covered — deliberately has no `presentation/`/`data/` packages yet, since there's nothing to build those around before Source List's real design spec exists). Two new standing process rules from the user, now saved to Claude's cross-session memory and expected to apply to every future feature in this project: (1) a written design doc in `docs/superpowers/specs/` must be created and explicitly approved *before* any code is written, even for small "bounded" changes; (2) all work must happen on a feature branch with a PR opened for approval — never commit directly to `main`.

**Problems, requirements, and open items:** No known open bugs. `./gradlew :app:compileDebugKotlin` and `./gradlew testDebugUnitTest` both pass (existing suite unchanged — no new automated tests were added for this nav-only change, per the design doc's own testing section). Manually verified on a physical Pixel 5: Articles tab loads real data and the bottom bar correctly switches to/highlights the blank Sources placeholder. Setup requirements are unchanged from before: a `NEWS_API_KEY` in the developer's global `~/.gradle/gradle.properties` is required for the app to authenticate with NewsAPI, and NewsAPI's free plan is `localhost`-only with a rate limit that can surface as an HTTP 429 during testing. Source List's actual design (NewsAPI endpoint, data/presentation layers, real list UI) is still not started and needs its own design spec before implementation, per the mandatory design-doc-first rule above. AI Summarization has not been started at all. The repo is `mcdunning/Udemy_DailyAIPulse` on GitHub; `main` is up to date with `origin/main` at commit `d545e15` (the PR #3 merge commit).

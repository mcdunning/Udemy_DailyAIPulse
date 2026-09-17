# Navigation Shell — Design

**Date:** 2026-09-16
**Status:** Design complete — pending implementation
**Builds on:** `docs/superpowers/specs/2026-09-12-architecture-design.md` (package layout, layering, `@Preview` rule, and the existing `navigation/AppNavigation.kt` all apply here and aren't repeated below)

## Overview

The architecture doc flagged the navigation entry point from Article List to Source List as an explicitly deferred decision. This doc resolves that decision and scopes the first piece of Source List work to just enough to prove the nav shell out: a bottom navigation bar with two destinations, Article List (already built) and a blank Source List placeholder screen. Source List's real feature — its NewsAPI endpoint, data layer, presentation layer, and actual list UI — is **out of scope** here and gets its own design spec later, per the architecture doc's build order (Article List → Source List → AI Summarization).

## Decision: Bottom Navigation Bar

Two top-level, equally-important destinations (Article List, Source List) is exactly the case Material 3's bottom navigation bar is designed for (2–5 top-level destinations, always visible, one tap to switch). A top-bar icon was rejected because it implies Source List is subordinate to Article List rather than a peer screen; a navigation drawer was rejected as overkill for only two destinations.

Tab order and labels, as directed: **"Articles"** (Article List) first, **"Sources"** (Source List) second.

Icons: the project has no `material-icons-extended` dependency, and this doc doesn't add one. `Icons.Filled.Home` for Articles, `Icons.AutoMirrored.Filled.List` for Sources. **Updated 2026-09-16**, during implementation: `material3` does not transitively pull in even the base icon set — `androidx.compose.material:material-icons-core` (not `-extended`) had to be added explicitly as a new dependency, version-managed by the Compose BOM already in `app/build.gradle.kts`, to resolve `Icons.Filled.Home`/`Icons.AutoMirrored.Filled.List` at all.

## Package Layout

```
com.dailyaipulse.sources/
└── ui/            # SourceListScreen (blank placeholder)
```

Only `ui/` exists for now — no `presentation/` or `data/` packages. A blank placeholder screen has no state or data to manage; adding empty layers ahead of any real need would be structure without a problem, which the architecture doc's own "simplicity" guiding principle rules out. `presentation/` and `data/` get added when Source List's real design spec is written.

This is a deliberate, temporary exception to the architecture doc's "exactly 3 sub-packages per feature" rule — justified only because the feature has no behavior yet. Once Source List is implemented for real, it must have all three (`ui/`, `presentation/`, `data/`), like every other feature.

## `SourceListScreen`

A single, stateless, blank `@Composable`, with `@Preview` coverage per the architecture doc's Composable-preview rule (still applies even to a screen with no meaningful state):

```kotlin
@Composable
fun SourceListScreen() {
}

@Preview(showBackground = true)
@Composable
private fun SourceListScreenPreview() {
    SourceListScreen()
}
```

No ViewModel, no `hiltViewModel()` call — there's nothing to collect state from yet. This also means, unlike `ArticleListScreen`, no split into a thin wrapper + stateless content is needed: the whole screen already is the stateless piece.

## `AppNavigation.kt` Changes

Add a second route and register it in the `NavHost`:

```kotlin
@Serializable
object SourceListRoute

// ...
composable<SourceListRoute> {
    SourceListScreen()
}
```

Wrap the existing `NavHost` in a `Scaffold` with a Material 3 `NavigationBar` as `bottomBar`. Selected-tab state is derived from the current back stack entry; tapping a tab uses the standard Material bottom-nav navigation pattern so switching tabs doesn't pile up back-stack entries or lose each tab's own state:

```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute?.hasRoute<ArticleListRoute>() == true,
                    onClick = { navController.navigateToTab(ArticleListRoute) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Articles") }
                )
                NavigationBarItem(
                    selected = currentRoute?.hasRoute<SourceListRoute>() == true,
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

`navigateToTab` is a small private helper rather than inlining the same options block twice — kept local to this file since it's a one-line convenience specific to this nav bar, not a shared abstraction.

The `Scaffold`/`NavigationBar` chrome lives in `AppNavigation.kt` itself rather than a separate file. The architecture doc's package layout diagram already designates this one file as the app's entire navigation layer ("`navigation/AppNavigation.kt` — NavHost + route definitions"); splitting the bottom bar into its own file for two tabs would be structure without a problem, same reasoning as the package-layout decision above.

## Testing

No unit or instrumented tests are added for this change. `SourceListScreen` has no logic to test — its `@Preview` is the coverage the project's guiding principle calls for. `AppNavigation`'s wiring (route registration, tab selection, `popUpTo`/`launchSingleTop`/`restoreState`) is standard Navigation Compose usage without custom logic of its own; it's exercised by manually running the app and switching tabs, not by an automated test written specifically for this change.

## Out of Scope

- Source List's NewsAPI endpoint, data layer, presentation layer (`ViewModel`, `UiState`, presentation model), and real list UI — deferred to Source List's own design spec.
- AI Summarization and any navigation entry point for it.
- Any `material-icons-extended` dependency.

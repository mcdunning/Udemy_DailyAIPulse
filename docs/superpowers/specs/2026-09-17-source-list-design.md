# Source List — Feature Design

**Date:** 2026-09-17
**Status:** Design complete — pending implementation
**Builds on:** `docs/superpowers/specs/2026-09-12-architecture-design.md` (package layout, layering, DI, tech stack, and shared API/auth decisions all apply here and aren't repeated below). Mirrors `docs/superpowers/specs/2026-09-12-article-list-design.md`'s shape closely — this feature deliberately reuses the same layers, class names, tech stack, and error handling as Article List, minus pagination (see below).

## Overview

The Source List screen fetches and displays the list of technology news sources available from NewsAPI.org. It's the second of three MVP features (Article List → Source List → AI Summarization). It replaces the blank placeholder `SourceListScreen` added alongside the navigation shell (PR #3).

## Package Layout

Per the architecture doc's feature-first convention:

```
com.dailyaipulse.sources/
├── ui/            # SourceListItem, SourceListContent, SourceListScreen
├── presentation/  # SourceListViewModel, SourceListUiState, Source (presentation model)
└── data/          # SourceData, SourceApiService, SourceRepository, SourceModule (Hilt)
```

`ui/SourceListScreen.kt` already exists as a blank placeholder (added with the navigation shell) — this feature replaces its contents; `presentation/` and `data/` don't exist yet.

## API Contract

**Provider:** NewsAPI.org (auth handling is cross-cutting — see architecture doc's External APIs section).

- **Endpoint:** `GET /v2/top-headlines/sources` — **not** `/v2/top-headlines`. The response shape given for this feature (`{"status", "sources": [...]}`) matches NewsAPI's dedicated sources endpoint; plain `/v2/top-headlines` returns an `articles` array instead and doesn't fit. Confirmed with the user during design.
- **Query params:**
  - `country=us`
  - `category=technology`
  - No `page`/`pageSize` — this endpoint isn't paginated, unlike `/top-headlines`.
- **Response shape:**

```json
{
  "status": "ok",
  "sources": [
    {
      "id": "string",
      "name": "string",
      "description": "string"
    }
  ]
}
```

This shape maps directly to `SourceData` in `sources/data/`:

```kotlin
@JsonClass(generateAdapter = true)
data class TopHeadlinesSourcesResponseData(
    val status: String,
    val sources: List<SourceData>
)

@JsonClass(generateAdapter = true)
data class SourceData(
    val id: String,
    val name: String,
    val description: String
)
```

No `@Json(name = ...)` renames needed — unlike `ArticleData`, every field name here already matches its JSON key.

## API Service

```kotlin
interface SourceApiService {
    // Auth: the NewsAPI key is attached automatically by a shared OkHttp
    // interceptor (see core/network) — no apiKey param needed on this call.
    @GET("v2/top-headlines/sources")
    suspend fun getSources(
        @Query("country") country: String = "us",
        @Query("category") category: String = "technology"
    ): TopHeadlinesSourcesResponseData
}
```

## Repository

```kotlin
class SourceRepository(
    private val sourceApiService: SourceApiService
) {
    // This call can fail (network errors, non-2xx responses, timeouts, etc.).
    // Exceptions propagate to the caller (ViewModel) rather than being caught
    // here — the ViewModel is responsible for catching and translating them
    // into UI error state.
    suspend fun getSources(): List<SourceData> {
        return sourceApiService.getSources().sources
    }
}
```

No `page` parameter and no pagination state — `/top-headlines/sources` returns the full list in one call, so `SourceRepository` is simpler than `ArticleRepository` (which is stateless for a different reason: to avoid `@Singleton`-scoped pagination state leaking across screen visits — not a concern here since there's no pagination state to begin with).

## Dependency Injection

`SourceModule` is feature-specific — it takes the shared `Retrofit` instance (from the architecture doc's `core/di` `NetworkModule`) and builds this feature's `SourceApiService` and `SourceRepository` from it, mirroring `ArticleModule` exactly.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideSourceApiService(retrofit: Retrofit): SourceApiService =
        retrofit.create(SourceApiService::class.java)

    @Provides
    @Singleton
    fun provideSourceRepository(sourceApiService: SourceApiService): SourceRepository =
        SourceRepository(sourceApiService)
}
```

## Presentation Layer

### Source (presentation model)

```kotlin
data class Source(
    val name: String,
    val description: String
)
```

Drops `id` from `SourceData` — not needed for display, and this feature has no per-source navigation/tap action (see UI Layer below). No formatting/mapping logic needed beyond the 1:1 field copy, unlike `Article`'s date formatting.

### SourceListUiState

Sealed, not boolean flags, same pattern as `ArticleListUiState` — but with no pagination-related fields, since this feature has nothing to paginate:

```kotlin
sealed interface SourceListUiState {
    data object Loading : SourceListUiState
    data class Success(val sources: List<Source>) : SourceListUiState
    data class Error(val message: String) : SourceListUiState
}
```

- `Loading` — shown for the (only) fetch, full-screen.
- `Success` — holds the loaded sources.
- `Error` — full-screen error.

### SourceListViewModel

```kotlin
@HiltViewModel
class SourceListViewModel @Inject constructor(
    private val sourceRepository: SourceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SourceListUiState>(SourceListUiState.Loading)
    val uiState: StateFlow<SourceListUiState> = _uiState.asStateFlow()

    init {
        loadSources()
    }

    private fun loadSources() {
        viewModelScope.launch {
            emit(SourceListUiState.Loading)
            try {
                val sources = sourceRepository.getSources().map { it.toSource() }
                emit(SourceListUiState.Success(sources = sources))
            } catch (e: Exception) {
                emit(SourceListUiState.Error(message = e.toUserMessage()))
            }
        }
    }

    private fun emit(newState: SourceListUiState) {
        Timber.d("SourceListUiState emitted: $newState")
        _uiState.value = newState
    }

    private fun SourceData.toSource(): Source = Source(
        name = name,
        description = description
    )
}
```

Notes:
- Same `emit()`-with-Timber-log pattern as `ArticleListViewModel`, for the same reason: guarantees every state transition is logged, no call site can forget it.
- No `loadNextPage()`, no `currentPage`, no `isLoadingMore`/`paginationError` — this feature has a single fetch and nothing to retry beyond re-entering the screen (no pull-to-refresh in this MVP, same as Article List).
- Catch block calls `Throwable.toUserMessage()` (architecture doc's `core/network` section), same as Article List — never surfaces the raw exception message, logs full exception via Timber, returns the same two-line generic/429-aware message.

## UI Layer

Same wrapper/stateless-content split as Article List, for the same reason (testability + `@Preview` without a Hilt graph — see architecture doc's `@Preview` guiding principle).

### SourceListItem

```kotlin
@Composable
fun SourceListItem(source: Source, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = source.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SourceListItemPreview() {
    SourceListItem(
        source = Source(
            name = "TechCrunch",
            description = "The latest technology news and information on startups, from a sample preview source."
        )
    )
}
```

No image — NewsAPI's source object has no image field, so unlike `ArticleListItem` there's no `AsyncImage`/Coil usage here. Title (`name`) on top, description below, same padding conventions as `ArticleListItem`.

### SourceListContent (stateless)

```kotlin
@Composable
fun SourceListContent(
    uiState: SourceListUiState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is SourceListUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).testTag("fullScreenLoading")
                )
            }
            is SourceListUiState.Error -> {
                Text(
                    text = uiState.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp)
                )
            }
            is SourceListUiState.Success -> {
                if (uiState.sources.isEmpty()) {
                    Text(
                        text = "No sources found",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp)
                    )
                } else {
                    LazyColumn {
                        items(uiState.sources) { source ->
                            SourceListItem(source)
                        }
                    }
                }
            }
        }
    }
}
```

- `Loading` → centered `CircularProgressIndicator`, full screen — same as `ArticleListContent`.
- `Error` → centered, `fillMaxWidth()`, `textAlign = TextAlign.Center` — same fix Article List needed for its two-line `toUserMessage()` output.
- `Success` with an empty list → same centered/full-width/center-aligned treatment, `Text("No sources found")`.
- `Success` with sources → plain `LazyColumn` of `SourceListItem`s, no pagination trigger, no trailing spinner/retry row.
- Takes no callback parameter (unlike `ArticleListContent`'s `onLoadNextPage`) — there's no user-triggered action on this screen.

**`@Preview` — one per meaningful state**, per the architecture doc's guiding principle:

```kotlin
@Preview(showBackground = true)
@Composable
private fun SourceListContentLoadingPreview() {
    SourceListContent(uiState = SourceListUiState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun SourceListContentErrorPreview() {
    SourceListContent(uiState = SourceListUiState.Error("Something went wrong.\nPlease try again."))
}

@Preview(showBackground = true)
@Composable
private fun SourceListContentEmptyPreview() {
    SourceListContent(uiState = SourceListUiState.Success(sources = emptyList()))
}

@Preview(showBackground = true)
@Composable
private fun SourceListContentSuccessPreview() {
    SourceListContent(
        uiState = SourceListUiState.Success(sources = listOf(/* two sample Sources */))
    )
}
```

### SourceListScreen (thin wrapper)

```kotlin
@Composable
fun SourceListScreen(viewModel: SourceListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sources") }) }
    ) { paddingValues ->
        SourceListContent(
            uiState = uiState,
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        )
    }
}
```

Top bar title: "Sources" — this satisfies the "Sources tile on top" requirement (a top app bar, same pattern as Article List's "Articles" title), with the list of source items below it. No `@Preview` here, same reasoning as `ArticleListScreen` (`hiltViewModel()` can't resolve a real Hilt graph in a preview); `SourceListContent`'s previews above already cover every visual state this screen can show.

Replaces the existing blank placeholder `SourceListScreen` (added with the navigation shell, PR #3) — same file path, real implementation.

The screen is **non-interactive for the MVP** — no tap action on sources, no pull-to-refresh. Consistent with Article List's own MVP scope.

## Navigation

No changes needed. `AppNavigation.kt` (architecture doc's Navigation section) already wires the `Sources` tab's `composable<SourceListRoute> { SourceListScreen() }` to this screen — it was added with the navigation shell in anticipation of this feature.

## Testing

Same three-layer approach as Article List (architecture doc's Testing section):
- **`SourceRepository`** — unit-tested against a faked `SourceApiService`, asserting it returns the `sources` list from the response.
- **`SourceListViewModel`** — unit-tested with a MockK-faked `SourceRepository`, using `kotlinx-coroutines-test`/Turbine to assert the `Loading → Success` and `Loading → Error` emission sequences. `Error` case should assert `toUserMessage()` is what's surfaced, not the raw exception message.
- **`SourceListContent`** — Compose UI tests driven by fixed `SourceListUiState` values (Loading/Error/Empty/Success), independent of the ViewModel/data layers.

## Build & Run Prerequisites

None beyond what Article List already established (Hilt setup, `INTERNET` permission, `AppNavigation` wiring) — all already in place from PR #1–#3. No new dependencies needed (no image loading, no date formatting, no new libraries) — this feature uses a strict subset of Article List's tech stack.

# Article List — Feature Design

**Date:** 2026-09-12
**Status:** Implemented (PRs #1, #2)
**Builds on:** `docs/superpowers/specs/2026-09-12-architecture-design.md` (package layout, layering, DI, tech stack, and shared API/auth decisions all apply here and aren't repeated below)

## Overview

The Article List screen fetches and displays a paginated list of technology news articles from NewsAPI.org. It's the first of three MVP features (Article List → Source List → AI Summarization).

## Package Layout

Per the architecture doc's feature-first convention:

```
com.dailyaipulse.articles/
├── ui/            # ArticleListScreen, ArticleListContent, ArticleListItem
├── presentation/  # ArticleListViewModel, ArticleListUiState, Article (presentation model), DateFormatter
└── data/          # ArticleData, ArticleApiService, ArticleRepository, ArticleModule (Hilt)
```

(Diagram reflects the final implemented shape — `ArticleListScreen` was later split into itself, `ArticleListContent`, and `ArticleListItem`; see "Updated 2026-09-14" below.)

## API Contract

**Provider:** NewsAPI.org (auth handling is cross-cutting — see architecture doc's External APIs section).

- **Endpoint:** `GET /v2/top-headlines`
- **Query params:**
  - `country=us`
  - `category=technology`
  - `pageSize=20`
  - `page={page}` — for pagination/infinite scroll
- **Response shape** (per NewsAPI docs):

```json
{
  "status": "ok",
  "totalResults": 42,
  "articles": [
    {
      "title": "string",
      "description": "string|null",
      "urlToImage": "string|null",
      "publishedAt": "string (ISO 8601)"
    }
  ]
}
```

This shape maps directly to `ArticleData` in `articles/data/`:

```kotlin
@JsonClass(generateAdapter = true)
data class TopHeadlinesResponseData(
    val status: String,
    val totalResults: Int,
    val articles: List<ArticleData>
)

@JsonClass(generateAdapter = true)
data class ArticleData(
    val title: String,
    val description: String?,
    @Json(name = "urlToImage") val imageUrl: String?,
    @Json(name = "publishedAt") val date: String
)
```

**Updated 2026-09-20**, for AI Summarization (`docs/superpowers/specs/2026-09-20-ai-summarization-design.md`): `ArticleData` gained `content: String?` and `url: String`, both already present in NewsAPI's response and now actually mapped — `url` is used for the article's own presentation-model identity and to open it externally, `content` is one of the summarization prompt's inputs.

`imageUrl`/`date` are renamed from the raw JSON keys (`urlToImage`/`publishedAt`) via `@Json(name = ...)`, since Moshi codegen needs that annotation whenever the Kotlin property name doesn't match the JSON key. `date` stays a raw `String` (ISO 8601) here — any date parsing/formatting is a presentation-layer concern, not this data class's job.

## API Service

```kotlin
interface ArticleApiService {
    // Auth: the NewsAPI key is attached automatically by a shared OkHttp
    // interceptor (see core/network) — no apiKey param needed on this call.
    @GET("v2/top-headlines")
    suspend fun getTopHeadlines(
        @Query("page") page: Int,
        @Query("country") country: String = "us",
        @Query("category") category: String = "technology",
        @Query("pageSize") pageSize: Int = 20
    ): TopHeadlinesResponseData
}
```

## Repository

```kotlin
class ArticleRepository(
    private val articleApiService: ArticleApiService
) {
    // This call can fail (network errors, non-2xx responses, timeouts, etc.).
    // Exceptions propagate to the caller (ViewModel) rather than being caught
    // here — the ViewModel is responsible for catching and translating them
    // into UI error state.
    suspend fun getTopHeadlines(page: Int): List<ArticleData> {
        return articleApiService.getTopHeadlines(page = page).articles
    }
}
```

`page` is an explicit parameter — the Repository is stateless and holds no pagination state itself. `ArticleModule` provides this Repository as `@Singleton`, so if it tracked "current page" internally, that state would incorrectly persist across unrelated screen visits (e.g. leaving and re-entering the Article List would resume mid-pagination instead of starting fresh). Pagination state (current page, whether more pages exist) lives in the ViewModel instead, which already needs it to drive the UI (e.g. a "loading more" spinner, stopping requests once results run out).

## Dependency Injection

`ArticleModule` is feature-specific — it takes the shared `Retrofit` instance (from the architecture doc's `core/di` `NetworkModule`) and builds this feature's `ArticleApiService` and `ArticleRepository` from it. It knows nothing about how `Retrofit`/`OkHttpClient` are configured.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ArticleModule {

    @Provides
    @Singleton
    fun provideArticleApiService(retrofit: Retrofit): ArticleApiService =
        retrofit.create(ArticleApiService::class.java)

    @Provides
    @Singleton
    fun provideArticleRepository(articleApiService: ArticleApiService): ArticleRepository =
        ArticleRepository(articleApiService)
}
```

## Presentation Layer

### Article (presentation model)

```kotlin
data class Article(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val date: String,  // formatted: relative ("2 hours ago") if <12h old, absolute ("Sep 12, 2026") otherwise
    val content: String?,
    val url: String
)
```

**Updated 2026-09-20**, for AI Summarization: `content`/`url` were added, matching `ArticleData` (see above) — added by that feature, not this one, but documented here since this is `Article`'s canonical definition.

`date` is a display-ready `String`, not a raw date type — formatting (relative vs. absolute based on a 12-hour cutoff) happens once during `ArticleData → Article` mapping, in the ViewModel.

### ArticleListUiState

Sealed, not boolean flags — states are mutually exclusive by construction (can't be `Loading` and have an error at the same time):

```kotlin
sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(
        val articles: List<Article>,
        val isLoadingMore: Boolean = false,
        val paginationError: String? = null
    ) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}
```

- `Loading` — shown only for the initial fetch (full-screen).
- `Success` — holds the loaded articles. `isLoadingMore` drives a small spinner at the list's bottom during pagination; `paginationError` drives an inline "failed to load more, tap to retry" row, without discarding the already-loaded list.
- `Error` — full-screen error, only for a failure on the *initial* load (a failure loading a later page stays in `Success` with `paginationError` set instead).

### ArticleListViewModel

```kotlin
@HiltViewModel
class ArticleListViewModel @Inject constructor(
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArticleListUiState>(ArticleListUiState.Loading)
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    private var currentPage = 1

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            emit(ArticleListUiState.Loading)
            try {
                val articles = articleRepository.getTopHeadlines(page = currentPage).map { it.toArticle() }
                emit(ArticleListUiState.Success(articles = articles))
            } catch (e: Exception) {
                emit(ArticleListUiState.Error(message = e.toUserMessage()))
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state !is ArticleListUiState.Success || state.isLoadingMore) return
        // isLoadingMore must be set synchronously (before launching), not inside the
        // coroutine body: viewModelScope.launch defers actual execution until the
        // dispatcher runs it, so a second synchronous call to loadNextPage() before
        // that would still see the old (isLoadingMore = false) state and re-enter.
        val loadingState = state.copy(isLoadingMore = true, paginationError = null)
        emit(loadingState)
        viewModelScope.launch {
            val nextPage = currentPage + 1
            try {
                val nextArticles = articleRepository.getTopHeadlines(page = nextPage).map { it.toArticle() }
                currentPage = nextPage
                emit(loadingState.copy(articles = loadingState.articles + nextArticles, isLoadingMore = false))
            } catch (e: Exception) {
                emit(loadingState.copy(isLoadingMore = false, paginationError = e.toUserMessage()))
            }
        }
    }

    private fun emit(newState: ArticleListUiState) {
        Timber.d("ArticleListUiState emitted: $newState")
        _uiState.value = newState
    }

    private fun ArticleData.toArticle(): Article = Article(
        title = title,
        description = description,
        imageUrl = imageUrl,
        date = formatDisplayDate(date)
    )
}
```

Notes:
- Every state change routes through the single `emit()` helper, guaranteeing the Timber log fires for every transition — no call site can forget it.
- `isLoadingMore` is set synchronously, before `viewModelScope.launch` — since `launch` defers actual execution, two rapid, synchronous calls to `loadNextPage()` would otherwise both read the stale `isLoadingMore = false` state and both fire a request. This was caught during implementation (a test asserting "no-op while already loading more" failed against the original design), not anticipated during design.
- `loadNextPage()` is a no-op if already loading more or not currently in `Success` — guards against duplicate pagination requests (e.g. fast scrolling).
- Retrying after a pagination failure is just calling `loadNextPage()` again — no separate retry function.
- `currentPage` only advances after a successful fetch, so a failed page load can be retried at the same page number.
- Both catch blocks call `Throwable.toUserMessage()` (see architecture doc's `core/network` section) rather than exposing the raw exception message. **Added 2026-09-14**, from PR review: the raw message was a technical/internal string, not fit for end users, and gave no special treatment to NewsAPI rate-limiting (HTTP 429) — a real failure encountered during manual testing. `toUserMessage()` logs the full exception via Timber first (so it's still debuggable) and returns a two-line (`\n`-separated) message: a short description, then the retry instruction underneath — either a specific "try again in N seconds" (when NewsAPI's `Retry-After` header is present) or a generic "try again later"/"try again" otherwise.

### formatDisplayDate

Relies on core library desugaring (see architecture doc's Tech Stack) to use `java.time` despite `minSdk = 24`:

```kotlin
private fun formatDisplayDate(isoDate: String): String {
    val publishedInstant = Instant.parse(isoDate)
    val duration = Duration.between(publishedInstant, Instant.now())
    return if (duration.toHours() < 12) {
        when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            else -> "${duration.toHours()}h ago"
        }
    } else {
        DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
            .format(publishedInstant)
    }
}
```

Absolute format: `"MMM d, yyyy"` (e.g. `"Sep 12, 2026"`).

## UI Layer

**Updated 2026-09-14** to match what was actually implemented: `ArticleListScreen` is split into itself (a thin wrapper that resolves the ViewModel and collects state) and `ArticleListContent` (a stateless composable driven entirely by `ArticleListUiState`). This isn't a conversational-doc simplification that got lost — it's required by the architecture doc's own testing/preview approach: a stateless composable is both testable with fixed `UiState` values (Compose UI testing, independent of ViewModel/data internals) and previewable (`@Preview`) without a Hilt graph. The original single-function sketch in this doc is kept below only as a record of the earlier version; the actual shape is `ArticleListItem` + `ArticleListContent` + `ArticleListScreen`.

### ArticleListItem

```kotlin
@Composable
fun ArticleListItem(article: Article, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = article.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = article.date,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArticleListItemPreview() {
    ArticleListItem(
        article = Article(
            title = "Sample Headline About Technology",
            description = "A short sample description of the article content, for preview purposes.",
            imageUrl = null,
            date = "Sep 13, 2026"
        )
    )
}
```

Image loaded via **Coil** (`AsyncImage`), fixed 180dp height, `ContentScale.Crop`. `imageUrl = null` in the preview simply renders no image (Coil doesn't make network calls inside `@Preview`) — that's expected, not a bug.

### ArticleListContent (stateless)

```kotlin
@Composable
fun ArticleListContent(
    uiState: ArticleListUiState,
    onLoadNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is ArticleListUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).testTag("fullScreenLoading")
                )
            }
            is ArticleListUiState.Error -> {
                Text(
                    text = uiState.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp)
                )
            }
            is ArticleListUiState.Success -> {
                if (uiState.articles.isEmpty()) {
                    Text(
                        text = "No articles found",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(uiState.articles) { index, article ->
                            ArticleListItem(article)
                            if (index == uiState.articles.lastIndex && !uiState.isLoadingMore) {
                                LaunchedEffect(Unit) { onLoadNextPage() }
                            }
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.testTag("paginationLoading"))
                                }
                            }
                        }
                        if (uiState.paginationError != null) {
                            item {
                                Text(
                                    text = "Failed to load more — tap to retry",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().clickable { onLoadNextPage() }.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- `Loading` → centered `CircularProgressIndicator`, full screen.
- `Error` → centered, `fillMaxWidth()`, `textAlign = TextAlign.Center` — **fixed 2026-09-14**, PR review: without `fillMaxWidth()`, a multi-line message (see `toUserMessage()`'s two-line format above) rendered with its lines left-aligned even though the `Text` composable itself was centered as a block; `fillMaxWidth()` + `textAlign = Center` centers each line.
- `Success` with an empty list → same centered/full-width/center-aligned treatment, `Text("No articles found")`.
- `Success` with articles → `LazyColumn` of `ArticleListItem`s. Pagination triggers when the last visible item composes (no scroll-position math needed), guarded by `!uiState.isLoadingMore` so it only fires once per page. `isLoadingMore`/`paginationError` render as trailing list items (spinner / tap-to-retry row) without disturbing the already-loaded content above them.
- Takes `onLoadNextPage: () -> Unit` rather than a `ViewModel` — this is what makes it previewable/testable with fixed `ArticleListUiState` values, per the architecture doc's `@Preview` guiding principle.

**`@Preview` — one per meaningful state**, added 2026-09-14 per the architecture doc's guiding principle:

```kotlin
@Preview(showBackground = true)
@Composable
private fun ArticleListContentLoadingPreview() {
    ArticleListContent(uiState = ArticleListUiState.Loading, onLoadNextPage = {})
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentErrorPreview() {
    ArticleListContent(uiState = ArticleListUiState.Error("Something went wrong.\nPlease try again."), onLoadNextPage = {})
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentEmptyPreview() {
    ArticleListContent(uiState = ArticleListUiState.Success(articles = emptyList()), onLoadNextPage = {})
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentSuccessPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(articles = listOf(/* two sample Articles */)),
        onLoadNextPage = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentLoadingMorePreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(articles = listOf(/* one sample Article */), isLoadingMore = true),
        onLoadNextPage = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentPaginationErrorPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(articles = listOf(/* one sample Article */), paginationError = "Failed to load more"),
        onLoadNextPage = {}
    )
}
```

### ArticleListScreen (thin wrapper)

```kotlin
@Composable
fun ArticleListScreen(viewModel: ArticleListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Articles") }) }
    ) { paddingValues ->
        ArticleListContent(
            uiState = uiState,
            onLoadNextPage = viewModel::loadNextPage,
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        )
    }
}
```

Top bar title: "Articles". No `@Preview` here — `hiltViewModel()` can't resolve a real Hilt graph in a preview, which is expected (see the architecture doc's `@Preview` guiding principle); `ArticleListContent`'s previews above already cover every visual state this screen can show.

**Updated 2026-09-20:** the screen is no longer non-interactive. AI Summarization (`docs/superpowers/specs/2026-09-20-ai-summarization-design.md`) added a "Summarize" button to each `ArticleListItem` and made the whole card tappable to open the article's `url` externally in the device browser — the `url`-based tap action originally deferred here was implemented by that feature rather than this one.

## Build & Run Prerequisites (resolved)

This section originally listed app-wide pieces required before Article List could run, none of which existed yet at design time. All of them were added during implementation and have existed since Article List shipped (PRs #1, #2) and the navigation shell followed (PRs #3, #4):

1. `INTERNET` permission in `AndroidManifest.xml`.
2. `DailyAiPulseApplication`, a custom `Application` class annotated `@HiltAndroidApp`, registered in the manifest.
3. `MainActivity` has `@AndroidEntryPoint` and calls `AppNavigation()`.
4. `AppNavigation.kt` exists with a `NavHost` wiring `ArticleListRoute` to `ArticleListScreen` (and, since Source List, `SourceListRoute` to `SourceListScreen`).
5. `Coil` and core library desugaring are both in `app/build.gradle.kts`.

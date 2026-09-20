# AI Summarization — Feature Design

**Date:** 2026-09-20
**Status:** Design complete — pending implementation
**Builds on:** `docs/superpowers/specs/2026-09-12-architecture-design.md` (package layout, layering, DI, tech stack, and shared API/auth decisions all apply here and aren't repeated below) and `docs/superpowers/specs/2026-09-12-article-list-design.md` (this feature extends Article List's existing data/presentation/UI rather than introducing a new screen)

## Overview

AI Summarization is the third and final MVP feature (Article List → Source List → AI Summarization). Unlike the other two features, it does **not** introduce its own screen: it adds an on-demand "Summarize" action to each item in the existing Article List, using Google's **Gemini API free tier** to generate a short summary from the article's already-fetched text, and adds a tap-to-open-in-browser action for the article's original source URL. This decision — inline enhancement rather than a dedicated summary screen/tab — was made explicitly during brainstorming after considering a detail-screen and a separate tab, both rejected as unnecessary indirection for what the user actually wants: a quick summary right where the article already is, before deciding whether to read the full thing externally.

Provider choice: Anthropic's Claude API (what Claude Code itself runs on) has no ongoing free tier and would cost real money per call from the app. On-device summarization (Android's ML Kit GenAI / Gemini Nano) is free but only runs on newer devices with AICore support (Pixel 8+, Galaxy S24+), which would block development/testing on other hardware. **Gemini's cloud API free tier** was chosen instead: real AI-generated summaries, no hardware restriction, works like any other cloud API call — the same shape as the existing NewsAPI integration.

## Non-Goals

- **Fetching/parsing the full article page.** Considered and explicitly rejected: NewsAPI's `url` points to arbitrary third-party sites, many paywalled or JavaScript-rendered, and reliably extracting article text from arbitrary HTML (what a "reader mode" library like Readability does) is a project on its own — far bigger than this feature's peers. Instead, the article's original URL is simply handed to the device's browser via an external `Intent`; the app never fetches or parses that page's content itself.
- **Caching summaries.** Consistent with the architecture doc's existing "no local database or on-device storage" non-goal — a summary is regenerated on each tap, never persisted. Given the on-demand (button-triggered, not automatic) design below, this doesn't create excessive API usage.
- **A dedicated summary screen, route, or bottom-nav tab.** Rejected during brainstorming in favor of an inline enhancement to the existing Article List UI (see Overview).
- **A `SummaryViewModel`.** There's no screen for one to own; the orchestration logic lives in the existing `ArticleListViewModel` (see Presentation Layer below).

## Package Layout

```
com.dailyaipulse.summary/
├── ui/            # (empty — no screen; ArticleListItem in articles/ui/ renders summary state directly)
├── presentation/  # SummaryUiState
└── data/          # SummaryRequestData, SummaryResponseData, GeminiApiService, SummaryRepository, SummaryModule (Hilt)
```

`summary/ui/` stays an empty placeholder, same as how `articles/`, `sources/`, `summary/` all started as empty scaffolded directories per the architecture doc — it exists for package-layout consistency, not because it will ever hold a screen for this feature as currently scoped.

Two existing files change:
- `articles/data/ArticleData` gains `content: String?` and `url: String`.
- `articles/presentation/Article` gains the same two fields.

Both fields are already present in NewsAPI's `/v2/top-headlines` response; they were deliberately left unmapped in Article List's original spec ("adding `url` back... is intentionally out of scope for now") and are added now because this feature needs them.

## External API: Gemini

**Provider:** [Gemini API](https://ai.google.dev/) (Google AI Studio), free tier.

- **Endpoint:** `POST v1beta/models/{model}:generateContent` (e.g. `model = "gemini-2.0-flash"`)
- **Auth:** API key via `x-goog-api-key` header (different from NewsAPI's `X-Api-Key` — a separate interceptor is needed; see `core/di` below). Key stored as `GEMINI_API_KEY` in the developer's global `~/.gradle/gradle.properties`, exposed via `BuildConfig`, same pattern as `NEWS_API_KEY`.
- **Request shape** (fields relevant to this feature):

```json
{
  "contents": [
    { "parts": [ { "text": "<prompt>" } ] }
  ]
}
```

- **Response shape** (success case):

```json
{
  "candidates": [
    { "content": { "parts": [ { "text": "<generated summary>" } ] } }
  ]
}
```

- **Response shape (content blocked by safety filters)** — a `200` response with no usable candidate:

```json
{
  "promptFeedback": { "blockReason": "<reason>" }
}
```

This third shape is not an HTTP error; `SummaryRepository` must check for it explicitly (see Repository below).

### Prompt construction

```
Summarize this news article in 2-3 sentences, using only the information given below. Do not add information that isn't stated here.

Title: {title}
Description: {description}
Content: {content}
```

`description`/`content` are omitted from the prompt when null/blank. The explicit "using only the information given... do not add information" instruction exists because the input is short (title + 1-2 sentence description + NewsAPI's ~200-character truncated `content`, which always cuts off mid-sentence on the free plan) — without this instruction, Gemini could fill gaps from its own background knowledge and state things the article never actually said.

## `core/di` Changes

`NetworkModule` currently provides one `OkHttpClient`/`Retrofit` pair, hardcoded to NewsAPI's base URL and auth header. This feature adds a second API with a different base URL and a different auth header, so a second, qualified pair is added alongside the existing one — the existing NewsAPI wiring is otherwise unchanged:

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Existing NewsAPI OkHttpClient/Retrofit — unchanged.

    @Provides
    @Singleton
    fun provideGeminiOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(GeminiApiKeyInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()

    @Provides
    @Singleton
    @GeminiRetrofit
    fun provideGeminiRetrofit(geminiOkHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(geminiOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
}
```

`SummaryModule` (in `summary/data/`) injects the `@GeminiRetrofit`-qualified instance to build `GeminiApiService`, the same way `ArticleModule`/`SourceModule` use the unqualified (NewsAPI) one.

### `core/network/GeminiApiKeyInterceptor`

```kotlin
class GeminiApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .build()
        return chain.proceed(request)
    }
}
```

## Data Layer (`summary/data/`)

```kotlin
@JsonClass(generateAdapter = true)
data class SummaryRequestData(val contents: List<ContentData>) {
    @JsonClass(generateAdapter = true)
    data class ContentData(val parts: List<PartData>)
    @JsonClass(generateAdapter = true)
    data class PartData(val text: String)
}

@JsonClass(generateAdapter = true)
data class SummaryResponseData(
    val candidates: List<CandidateData>?,
    val promptFeedback: PromptFeedbackData?
) {
    @JsonClass(generateAdapter = true)
    data class CandidateData(val content: SummaryRequestData.ContentData)
    @JsonClass(generateAdapter = true)
    data class PromptFeedbackData(val blockReason: String?)
}
```

```kotlin
interface GeminiApiService {
    // Auth: the Gemini API key is attached automatically by GeminiApiKeyInterceptor
    // (see core/network) — no key param needed on this call.
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(@Body request: SummaryRequestData): SummaryResponseData
}
```

```kotlin
class SummaryRepository(
    private val geminiApiService: GeminiApiService
) {
    suspend fun summarize(title: String, description: String?, content: String?): String {
        val prompt = buildPrompt(title, description, content)
        val request = SummaryRequestData(
            contents = listOf(SummaryRequestData.ContentData(parts = listOf(SummaryRequestData.PartData(prompt))))
        )
        val response = geminiApiService.generateContent(request)
        val summaryText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        // A 200 response with no candidate means Gemini's safety filters blocked the
        // content — not an HTTP error, so it must be checked explicitly here rather
        // than relying on an exception. Thrown so it flows through the same catch
        // block/toUserMessage() path as every other failure.
        return summaryText ?: throw IllegalStateException(
            "Summary blocked: ${response.promptFeedback?.blockReason ?: "unknown reason"}"
        )
    }

    private fun buildPrompt(title: String, description: String?, content: String?): String = buildString {
        appendLine("Summarize this news article in 2-3 sentences, using only the information given below. Do not add information that isn't stated here.")
        appendLine()
        appendLine("Title: $title")
        if (!description.isNullOrBlank()) appendLine("Description: $description")
        if (!content.isNullOrBlank()) appendLine("Content: $content")
    }
}
```

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SummaryModule {

    @Provides
    @Singleton
    fun provideGeminiApiService(@GeminiRetrofit retrofit: Retrofit): GeminiApiService =
        retrofit.create(GeminiApiService::class.java)

    @Provides
    @Singleton
    fun provideSummaryRepository(geminiApiService: GeminiApiService): SummaryRepository =
        SummaryRepository(geminiApiService)
}
```

## Presentation Layer

### `summary/presentation/SummaryUiState`

The only class in `summary/presentation/` — no `Summary` presentation model, since a plain `String` is all there is to display, and no `SummaryViewModel`, since there's no screen for one to own:

```kotlin
sealed interface SummaryUiState {
    data object Idle : SummaryUiState
    data object Loading : SummaryUiState
    data class Success(val text: String) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}
```

### `articles/presentation/ArticleListViewModel` changes

Injects `SummaryRepository` alongside the existing `ArticleRepository`. `ArticleListUiState.Success` gains one field:

```kotlin
data class Success(
    val articles: List<Article>,
    val isLoadingMore: Boolean = false,
    val paginationError: String? = null,
    val summaries: Map<String, SummaryUiState> = emptyMap()  // keyed by Article.url
) : ArticleListUiState
```

`url` is the map key because NewsAPI articles have no other stable identifier and `url` is realistically unique per article.

```kotlin
fun summarize(article: Article) {
    val state = _uiState.value
    if (state !is ArticleListUiState.Success) return
    if (state.summaries[article.url] is SummaryUiState.Loading) return
    // Set synchronously, before viewModelScope.launch — same reason as
    // isLoadingMore in loadNextPage(): launch defers execution, so a fast
    // double-tap would otherwise both read the stale non-Loading state.
    val loadingState = state.copy(summaries = state.summaries + (article.url to SummaryUiState.Loading))
    emit(loadingState)
    viewModelScope.launch {
        try {
            val text = summaryRepository.summarize(article.title, article.description, article.content)
            emit(loadingState.copy(summaries = loadingState.summaries + (article.url to SummaryUiState.Success(text))))
        } catch (e: Exception) {
            emit(loadingState.copy(summaries = loadingState.summaries + (article.url to SummaryUiState.Error(e.toUserMessage()))))
        }
    }
}
```

This is a one-way dependency — `articles/presentation` depends on `summary/presentation`/`summary/data` — matching the architecture doc's stated build order (AI Summarization depends on Article List existing).

## UI Layer

### `articles/ui/ArticleListItem` changes

Gains `summaryState: SummaryUiState = SummaryUiState.Idle`, `onSummarize: () -> Unit`, and `onOpenArticle: () -> Unit` parameters:

- **`Idle`** → a "Summarize" button.
- **`Loading`** → the button is replaced by a small inline `CircularProgressIndicator` (same swap-in-place pattern as the list's pagination spinner).
- **`Success`** → the button is replaced by the summary `Text`, styled distinctly (e.g. italic, tinted background) so it visually reads as AI-generated content rather than part of the article's own description. No regenerate action — matches the "no caching, no need to re-fetch" simplicity call.
- **`Error`** → the button reappears (re-labeled e.g. "Retry summarize") alongside the error message — same tap-to-retry pattern as the list's existing `paginationError` row.

The whole `Card` becomes `clickable`, calling `onOpenArticle()`. Compose's standard nested-clickable handling means tapping the "Summarize"/"Retry summarize" button (itself a separately clickable composable) fires only the button's own `onClick`, never bubbling up to the card's `onOpenArticle` — no accidental "open browser" when the user meant to tap the button.

### `articles/ui/ArticleListContent` changes

Looks up each article's state — `uiState.summaries[article.url] ?: SummaryUiState.Idle` — when building each `ArticleListItem`, passing `onSummarize = { onSummarizeClick(article) }` and `onOpenArticle = { onOpenArticleClick(article) }` through from new parameters on `ArticleListContent` itself.

### `articles/ui/ArticleListScreen` changes

Supplies the two new callbacks: `onSummarizeClick = viewModel::summarize`, and `onOpenArticleClick = { article -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) }` (via `LocalContext.current`).

### `@Preview` coverage

Per the project's guiding principle, `ArticleListItem` gets one `@Preview` per `SummaryUiState` variant (Idle, Loading, Success, Error) in addition to its existing preview.

## Error Handling

Gemini failures reuse `Throwable.toUserMessage()` (`core/network/NetworkErrorMapper.kt`) unchanged — it already handles HTTP 429 (with or without a `Retry-After` header) generically, which covers Gemini's free-tier rate limiting the same way it covers NewsAPI's.

The one Gemini-specific case that isn't a thrown HTTP exception — a `200` response with safety-filter-blocked content — is converted to a thrown exception inside `SummaryRepository.summarize()` (see Data Layer above), so it flows through `ArticleListViewModel.summarize()`'s existing catch block and `toUserMessage()` like any other failure, without needing a separate `SummaryUiState` variant.

`summarize(article)` is a no-op if that article's `summaries[url]` is already `Loading` — guards against a fast double-tap firing two requests, the same guard `loadNextPage()` already uses for pagination.

## Testing

- **`SummaryRepositoryTest`** (unit, MockK-faked `GeminiApiService`): prompt construction (including omitting null/blank `description`/`content`), response unwrapping to plain text, exception propagation on HTTP failure, and the blocked-content case (empty `candidates`, `promptFeedback.blockReason` set) throwing as expected.
- **`ArticleListViewModelTest`** (extends the existing test class): Turbine-asserted `StateFlow` sequences for `summarize()` — `Idle → Loading → Success`, `Idle → Loading → Error` — and the no-op-while-`Loading` guard, using a MockK-faked `SummaryRepository` alongside the existing faked `ArticleRepository`.
- **`ArticleListContentTest`** (instrumented Compose UI test, extends the existing one): each `SummaryUiState` variant renders correctly on `ArticleListItem`, tapping "Summarize" fires `onSummarize`, and tapping the card (not the button) fires `onOpenArticle` without also firing `onSummarize`.

**Automated tests must never call the real Gemini API.** Both unit tests (MockK-faked `GeminiApiService`/`SummaryRepository`) and the instrumented Compose UI test (driven by fixed `SummaryUiState` values, independent of ViewModel/data internals — same convention as every other feature's UI test) are structured so this is true by construction; whoever implements this should not wire any automated test to a real `GeminiApiService`/`SummaryRepository` instance. The only point a real Gemini call happens at all is manual, on-device verification — which should be a small number of deliberate taps, not repeated/looped testing, given the free tier's rate limit.

## Build & Run Prerequisites

Beyond this feature's own code:

1. A `GEMINI_API_KEY` in the developer's global `~/.gradle/gradle.properties`, alongside the existing `NEWS_API_KEY` — obtained from [Google AI Studio](https://ai.google.dev/).
2. `app/build.gradle.kts` needs a `buildConfigField` for `GEMINI_API_KEY`, mirroring the existing `NEWS_API_KEY` field.
3. No new Android permission is needed for the "open in browser" action — `ACTION_VIEW` on an external `http(s)` URL doesn't require a manifest permission beyond the `INTERNET` permission already present.

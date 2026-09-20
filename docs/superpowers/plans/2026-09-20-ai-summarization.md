# AI Summarization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an on-demand "Summarize" action to each Article List item (calling the Gemini API free tier) and a tap-to-open-in-browser action for the article's original URL — an inline enhancement to the existing Article List feature, not a new screen.

**Architecture:** New `summary/data` (Gemini API service, repository, Hilt module) and `summary/presentation` (`SummaryUiState` only — no `summary/ui`, no `SummaryViewModel`, per the spec's explicit deviation) packages. `articles/presentation/ArticleListViewModel` owns the orchestration, injecting `SummaryRepository` directly and tracking a per-article `Map<String, SummaryUiState>` keyed by URL. `articles/data`/`articles/presentation` gain `content`/`url` fields on `ArticleData`/`Article`. A second, qualified `Retrofit`/`OkHttpClient` pair is added to `core/di`/`core/network` for Gemini's different base URL and auth header.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit + Moshi, OkHttp, Kotlin Coroutines + StateFlow, Timber, MockK, kotlinx-coroutines-test, Turbine, Compose UI testing. No new library dependencies — Gemini is called via the same Retrofit/Moshi/OkHttp stack already in the project.

**Spec:** `docs/superpowers/specs/2026-09-20-ai-summarization-design.md`

## Global Constraints

- Gemini endpoint: `POST v1beta/models/gemini-2.0-flash:generateContent`, base URL `https://generativelanguage.googleapis.com/`.
- Auth: Gemini key attached via `x-goog-api-key` header by `GeminiApiKeyInterceptor` — never add it as a query param or request body field.
- A `200` response with no `candidates` (safety-filter block) is *not* an HTTP exception — `SummaryRepository` must check for it explicitly and throw.
- Every ViewModel catch block calls `Throwable.toUserMessage()` (from `com.dailyaipulse.core.network`) — never surface a raw exception message.
- Every `StateFlow` state change in `ArticleListViewModel` routes through the existing private `emit()` helper.
- `summarize(article)` must set `Loading` into `_uiState` *synchronously*, before `viewModelScope.launch` — same reason as the existing `isLoadingMore` pattern in `loadNextPage()`: `launch` defers execution, so two fast synchronous calls would otherwise both see the stale non-`Loading` state.
- `article.url` is the map key for `ArticleListUiState.Success.summaries` — there is no other stable per-article identifier.
- `summary/ui/` does not exist — do not create it. `summary/presentation/` contains only `SummaryUiState`, no `SummaryViewModel`.
- `SummaryRepository` is a single concrete class (no interface/impl split); its function is `suspend fun` and lets exceptions propagate uncaught.
- Package layout: `summary/{presentation,data}` only (no `ui`); `articles/{ui,presentation,data}` unchanged in shape.
- Every Composable needs `@Preview` coverage — one `@Preview` per meaningful state variant on stateless content composables.
- **Automated tests must never call the real Gemini API.** Unit tests fake `GeminiApiService`/`SummaryRepository` with MockK; the instrumented Compose UI test is driven by fixed `SummaryUiState` values. The only real Gemini call happens during manual, on-device verification (Task 10) — keep it to a handful of taps, given the free tier's rate limit.

---

### Task 1: `core/network`/`core/di` — Gemini Retrofit Wiring

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/core/di/GeminiRetrofit.kt`
- Create: `app/src/main/java/com/dailyaipulse/core/network/GeminiApiKeyInterceptor.kt`
- Modify: `app/src/main/java/com/dailyaipulse/core/di/NetworkModule.kt`
- Modify: `app/build.gradle.kts:26-36`
- Modify: `README.md:32-42`

**Interfaces:**
- Produces: `@Qualifier annotation class GeminiRetrofit`, a `@GeminiRetrofit`-qualified `OkHttpClient` and `Retrofit` binding (base URL `https://generativelanguage.googleapis.com/`), `BuildConfig.GEMINI_API_KEY`.

- [ ] **Step 1: Create the `@GeminiRetrofit` qualifier**

```kotlin
package com.dailyaipulse.core.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRetrofit
```

- [ ] **Step 2: Create `GeminiApiKeyInterceptor.kt`**

```kotlin
package com.dailyaipulse.core.network

import com.dailyaipulse.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class GeminiApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
            .build()
        return chain.proceed(request)
    }
}
```

- [ ] **Step 3: Add the Gemini `OkHttpClient`/`Retrofit` providers to `NetworkModule.kt`**

Replace the full file contents with:

```kotlin
package com.dailyaipulse.core.di

import com.dailyaipulse.core.network.GeminiApiKeyInterceptor
import com.dailyaipulse.core.network.NewsApiKeyInterceptor
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

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

    // @GeminiRetrofit qualifies BOTH providers below, not just the Retrofit one.
    // Without it on the OkHttpClient provider too, there would be two @Provides
    // functions both returning plain (unqualified) OkHttpClient, which Hilt
    // rejects at compile time as a duplicate binding for the same type.
    @Provides
    @Singleton
    @GeminiRetrofit
    fun provideGeminiOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(GeminiApiKeyInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()

    @Provides
    @Singleton
    @GeminiRetrofit
    fun provideGeminiRetrofit(@GeminiRetrofit geminiOkHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(geminiOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
}
```

- [ ] **Step 4: Add the `GEMINI_API_KEY` `BuildConfig` field**

In `app/build.gradle.kts`, in both `buildTypes { release { ... } }` and `buildTypes { debug { ... } }`, add a line alongside the existing `NEWS_API_KEY` one:

```kotlin
        release {
            optimization {
                enable = false
            }
            buildConfigField("String", "NEWS_API_KEY", "\"${project.findProperty("NEWS_API_KEY") ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
        }

        debug {
            buildConfigField("String", "NEWS_API_KEY", "\"${project.findProperty("NEWS_API_KEY") ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
        }
```

- [ ] **Step 5: Document the new setup requirement in `README.md`**

In the `## Setup` section, replace:

```properties
NEWS_API_KEY=your_key_here
```

with:

```properties
NEWS_API_KEY=your_key_here
GEMINI_API_KEY=your_key_here
```

and add a sentence right after the existing "It's picked up automatically..." line:

> `GEMINI_API_KEY` is a free-tier key from [Google AI Studio](https://ai.google.dev/), used by the AI Summarization feature; it's exposed as `BuildConfig.GEMINI_API_KEY` the same way.

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/core/di/GeminiRetrofit.kt app/src/main/java/com/dailyaipulse/core/network/GeminiApiKeyInterceptor.kt app/src/main/java/com/dailyaipulse/core/di/NetworkModule.kt app/build.gradle.kts README.md
git commit -m "Add second qualified Retrofit for the Gemini API"
```

---

### Task 2: `summary/data` — Models & API Service

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/summary/data/SummaryData.kt`
- Create: `app/src/main/java/com/dailyaipulse/summary/data/GeminiApiService.kt`

**Interfaces:**
- Produces: `SummaryRequestData(contents: List<ContentData>)`, `SummaryRequestData.ContentData(parts: List<PartData>)`, `SummaryRequestData.PartData(text: String)`, `SummaryResponseData(candidates: List<CandidateData>?, promptFeedback: PromptFeedbackData?)`, `SummaryResponseData.CandidateData(content: SummaryRequestData.ContentData)`, `SummaryResponseData.PromptFeedbackData(blockReason: String?)`, `interface GeminiApiService { suspend fun generateContent(request: SummaryRequestData): SummaryResponseData }`

- [ ] **Step 1: Create `SummaryData.kt`**

```kotlin
package com.dailyaipulse.summary.data

import com.squareup.moshi.JsonClass

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

- [ ] **Step 2: Create `GeminiApiService.kt`**

```kotlin
package com.dailyaipulse.summary.data

import retrofit2.http.Body
import retrofit2.http.POST

interface GeminiApiService {
    // Auth: the Gemini API key is attached automatically by GeminiApiKeyInterceptor
    // (see core/network) — no key param needed on this call.
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(@Body request: SummaryRequestData): SummaryResponseData
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/summary/data/SummaryData.kt app/src/main/java/com/dailyaipulse/summary/data/GeminiApiService.kt
git commit -m "Add Gemini API data models and service"
```

---

### Task 3: `summary/data` — `SummaryRepository` (TDD)

**Files:**
- Create: `app/src/test/java/com/dailyaipulse/summary/data/SummaryRepositoryTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/summary/data/SummaryRepository.kt`

**Interfaces:**
- Consumes: `GeminiApiService.generateContent(request): SummaryResponseData` (Task 2), `SummaryRequestData`/`SummaryResponseData` (Task 2)
- Produces: `class SummaryRepository(private val geminiApiService: GeminiApiService) { suspend fun summarize(title: String, description: String?, content: String?): String }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dailyaipulse.summary.data

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryRepositoryTest {

    private fun successResponse(text: String) = SummaryResponseData(
        candidates = listOf(
            SummaryResponseData.CandidateData(
                content = SummaryRequestData.ContentData(parts = listOf(SummaryRequestData.PartData(text)))
            )
        ),
        promptFeedback = null
    )

    @Test
    fun `summarize sends title, description, and content in the prompt and returns the generated text`() = runTest {
        val apiService = mockk<GeminiApiService>()
        val requestSlot = slot<SummaryRequestData>()
        coEvery { apiService.generateContent(capture(requestSlot)) } returns successResponse("A concise summary.")
        val repository = SummaryRepository(apiService)

        val result = repository.summarize(
            title = "Some Title",
            description = "Some description",
            content = "Some truncated content"
        )

        assertEquals("A concise summary.", result)
        val prompt = requestSlot.captured.contents.first().parts.first().text
        assertTrue(prompt.contains("Title: Some Title"))
        assertTrue(prompt.contains("Description: Some description"))
        assertTrue(prompt.contains("Content: Some truncated content"))
    }

    @Test
    fun `summarize omits blank description and content from the prompt`() = runTest {
        val apiService = mockk<GeminiApiService>()
        val requestSlot = slot<SummaryRequestData>()
        coEvery { apiService.generateContent(capture(requestSlot)) } returns successResponse("A concise summary.")
        val repository = SummaryRepository(apiService)

        repository.summarize(title = "Some Title", description = null, content = "")

        val prompt = requestSlot.captured.contents.first().parts.first().text
        assertTrue(prompt.contains("Title: Some Title"))
        assertFalse(prompt.contains("Description:"))
        assertFalse(prompt.contains("Content:"))
    }

    @Test(expected = RuntimeException::class)
    fun `summarize propagates exceptions from the API service`() = runTest {
        val apiService = mockk<GeminiApiService>()
        coEvery { apiService.generateContent(any()) } throws RuntimeException("network error")
        val repository = SummaryRepository(apiService)

        repository.summarize(title = "Some Title", description = null, content = null)
    }

    @Test
    fun `summarize throws when Gemini blocks the content instead of returning a candidate`() = runTest {
        val apiService = mockk<GeminiApiService>()
        coEvery { apiService.generateContent(any()) } returns SummaryResponseData(
            candidates = null,
            promptFeedback = SummaryResponseData.PromptFeedbackData(blockReason = "SAFETY")
        )
        val repository = SummaryRepository(apiService)

        val exception = try {
            repository.summarize(title = "Some Title", description = null, content = null)
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("SAFETY"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.summary.data.SummaryRepositoryTest"`
Expected: FAIL to compile — `SummaryRepository` is unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.dailyaipulse.summary.data

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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.summary.data.SummaryRepositoryTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/dailyaipulse/summary/data/SummaryRepositoryTest.kt app/src/main/java/com/dailyaipulse/summary/data/SummaryRepository.kt
git commit -m "Add SummaryRepository with tests"
```

---

### Task 4: `summary/data` — Hilt Module

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/summary/data/SummaryModule.kt`

**Interfaces:**
- Consumes: `GeminiApiService` (Task 2), `SummaryRepository` (Task 3), `@GeminiRetrofit`-qualified `Retrofit` (Task 1)
- Produces: Hilt-injectable `GeminiApiService` and `SummaryRepository` singletons

- [ ] **Step 1: Create `SummaryModule.kt`**

```kotlin
package com.dailyaipulse.summary.data

import com.dailyaipulse.core.di.GeminiRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

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

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/summary/data/SummaryModule.kt
git commit -m "Add SummaryModule Hilt bindings"
```

---

### Task 5: `summary/presentation` — `SummaryUiState`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/summary/presentation/SummaryUiState.kt`

**Interfaces:**
- Produces: `sealed interface SummaryUiState { data object Idle; data object Loading; data class Success(val text: String); data class Error(val message: String) }`

- [ ] **Step 1: Create `SummaryUiState.kt`**

```kotlin
package com.dailyaipulse.summary.presentation

sealed interface SummaryUiState {
    data object Idle : SummaryUiState
    data object Loading : SummaryUiState
    data class Success(val text: String) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/summary/presentation/SummaryUiState.kt
git commit -m "Add SummaryUiState"
```

---

### Task 6: `articles/data`/`articles/presentation` — Add `content`/`url` Fields

**Files:**
- Modify: `app/src/main/java/com/dailyaipulse/articles/data/ArticleData.kt`
- Modify: `app/src/main/java/com/dailyaipulse/articles/presentation/Article.kt`
- Modify: `app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListViewModel.kt:68-73`
- Modify: `app/src/test/java/com/dailyaipulse/articles/data/ArticleRepositoryTest.kt:13-26`
- Modify: `app/src/test/java/com/dailyaipulse/articles/presentation/ArticleListViewModelTest.kt:28-33`
- Modify: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListItem.kt:60-67`
- Modify: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListContent.kt:97-102`
- Modify: `app/src/androidTest/java/com/dailyaipulse/articles/ui/ArticleListContentTest.kt:17-22`

**Interfaces:**
- Produces: `ArticleData` gains `content: String?`, `url: String`. `Article` gains the same two fields.

This task only adds the two fields and updates every existing construction call site to keep the whole project compiling — it does not add any new behavior (no summarize button, no browser-open action yet). Those come in Tasks 8-10.

- [ ] **Step 1: Add `content`/`url` to `ArticleData.kt`**

```kotlin
package com.dailyaipulse.articles.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

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
    @Json(name = "publishedAt") val date: String,
    val content: String?,
    val url: String
)
```

- [ ] **Step 2: Add `content`/`url` to `Article.kt`**

```kotlin
package com.dailyaipulse.articles.presentation

data class Article(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val date: String,
    val content: String?,
    val url: String
)
```

- [ ] **Step 3: Update `ArticleListViewModel.toArticle()` mapping**

In `ArticleListViewModel.kt`, replace:

```kotlin
    private fun ArticleData.toArticle(): Article = Article(
        title = title,
        description = description,
        imageUrl = imageUrl,
        date = formatDisplayDate(date)
    )
```

with:

```kotlin
    private fun ArticleData.toArticle(): Article = Article(
        title = title,
        description = description,
        imageUrl = imageUrl,
        date = formatDisplayDate(date),
        content = content,
        url = url
    )
```

- [ ] **Step 4: Update `ArticleRepositoryTest.kt` fixtures**

Replace the `fakeArticles` list with:

```kotlin
        val fakeArticles = listOf(
            ArticleData(
                title = "Title 1",
                description = "Desc 1",
                imageUrl = "https://img.example/1.png",
                date = "2026-09-13T10:00:00Z",
                content = "Truncated content 1",
                url = "https://example.com/article-1"
            ),
            ArticleData(
                title = "Title 2",
                description = null,
                imageUrl = null,
                date = "2026-09-13T09:00:00Z",
                content = null,
                url = "https://example.com/article-2"
            )
        )
```

- [ ] **Step 5: Update `ArticleListViewModelTest.kt` fixtures**

Replace:

```kotlin
    private val page1 = listOf(
        ArticleData(title = "Page 1 Article", description = "Desc", imageUrl = "https://img/1.png", date = "2026-09-13T11:59:30Z")
    )
    private val page2 = listOf(
        ArticleData(title = "Page 2 Article", description = "Desc", imageUrl = "https://img/2.png", date = "2026-09-13T11:59:30Z")
    )
```

with:

```kotlin
    private val page1 = listOf(
        ArticleData(
            title = "Page 1 Article",
            description = "Desc",
            imageUrl = "https://img/1.png",
            date = "2026-09-13T11:59:30Z",
            content = "Truncated content",
            url = "https://example.com/page-1-article"
        )
    )
    private val page2 = listOf(
        ArticleData(
            title = "Page 2 Article",
            description = "Desc",
            imageUrl = "https://img/2.png",
            date = "2026-09-13T11:59:30Z",
            content = "Truncated content",
            url = "https://example.com/page-2-article"
        )
    )
```

- [ ] **Step 6: Update `ArticleListItem.kt`'s preview**

Replace:

```kotlin
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

with:

```kotlin
@Preview(showBackground = true)
@Composable
private fun ArticleListItemPreview() {
    ArticleListItem(
        article = Article(
            title = "Sample Headline About Technology",
            description = "A short sample description of the article content, for preview purposes.",
            imageUrl = null,
            date = "Sep 13, 2026",
            content = "Sample truncated article content for preview purposes...",
            url = "https://example.com/sample-article"
        )
    )
}
```

- [ ] **Step 7: Update `ArticleListContent.kt`'s `previewArticle`**

Replace:

```kotlin
private val previewArticle = Article(
    title = "Sample Headline About Technology",
    description = "A short sample description of the article content, for preview purposes.",
    imageUrl = null,
    date = "Sep 13, 2026"
)
```

with:

```kotlin
private val previewArticle = Article(
    title = "Sample Headline About Technology",
    description = "A short sample description of the article content, for preview purposes.",
    imageUrl = null,
    date = "Sep 13, 2026",
    content = "Sample truncated article content for preview purposes...",
    url = "https://example.com/sample-article"
)
```

- [ ] **Step 8: Update `ArticleListContentTest.kt`'s `article` fixture**

Replace:

```kotlin
    private val article = Article(
        title = "Some Headline",
        description = "Some description",
        imageUrl = null,
        date = "Sep 13, 2026"
    )
```

with:

```kotlin
    private val article = Article(
        title = "Some Headline",
        description = "Some description",
        imageUrl = null,
        date = "Sep 13, 2026",
        content = "Some truncated content",
        url = "https://example.com/some-headline"
    )
```

- [ ] **Step 9: Verify everything still compiles and passes**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing tests pass unchanged (no new tests yet — this task only adds fields, no new behavior)

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/data/ArticleData.kt app/src/main/java/com/dailyaipulse/articles/presentation/Article.kt app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListViewModel.kt app/src/test/java/com/dailyaipulse/articles/data/ArticleRepositoryTest.kt app/src/test/java/com/dailyaipulse/articles/presentation/ArticleListViewModelTest.kt app/src/main/java/com/dailyaipulse/articles/ui/ArticleListItem.kt app/src/main/java/com/dailyaipulse/articles/ui/ArticleListContent.kt app/src/androidTest/java/com/dailyaipulse/articles/ui/ArticleListContentTest.kt
git commit -m "Add content/url fields to ArticleData and Article"
```

---

### Task 7: `articles/presentation` — `ArticleListViewModel.summarize()` (TDD)

**Files:**
- Modify: `app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListUiState.kt`
- Modify: `app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListViewModel.kt`
- Modify: `app/src/test/java/com/dailyaipulse/articles/presentation/ArticleListViewModelTest.kt`

**Interfaces:**
- Consumes: `SummaryRepository.summarize(title, description, content): String` (Task 3), `SummaryUiState` (Task 5), `Article` (Task 6, now has `url`/`content`)
- Produces: `ArticleListUiState.Success` gains `val summaries: Map<String, SummaryUiState> = emptyMap()`; `ArticleListViewModel` gains `fun summarize(article: Article)` and a second constructor parameter `summaryRepository: SummaryRepository`

- [ ] **Step 1: Add `summaries` to `ArticleListUiState.Success`**

Replace `ArticleListUiState.kt`'s full contents with:

```kotlin
package com.dailyaipulse.articles.presentation

import com.dailyaipulse.summary.presentation.SummaryUiState

sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(
        val articles: List<Article>,
        val isLoadingMore: Boolean = false,
        val paginationError: String? = null,
        val summaries: Map<String, SummaryUiState> = emptyMap()
    ) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}
```

- [ ] **Step 2: Write the failing tests**

Replace `ArticleListViewModelTest.kt`'s full contents with (the 5 existing tests, each now creating and passing a `summaryRepository` mock since the constructor takes two parameters, plus 3 new tests for `summarize()`):

```kotlin
package com.dailyaipulse.articles.presentation

import app.cash.turbine.test
import com.dailyaipulse.articles.data.ArticleData
import com.dailyaipulse.articles.data.ArticleRepository
import com.dailyaipulse.summary.data.SummaryRepository
import com.dailyaipulse.summary.presentation.SummaryUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val page1 = listOf(
        ArticleData(
            title = "Page 1 Article",
            description = "Desc",
            imageUrl = "https://img/1.png",
            date = "2026-09-13T11:59:30Z",
            content = "Truncated content",
            url = "https://example.com/page-1-article"
        )
    )
    private val page2 = listOf(
        ArticleData(
            title = "Page 2 Article",
            description = "Desc",
            imageUrl = "https://img/2.png",
            date = "2026-09-13T11:59:30Z",
            content = "Truncated content",
            url = "https://example.com/page-2-article"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Loading then Success with mapped articles on successful initial load`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            val success = awaitItem() as ArticleListUiState.Success
            assertEquals(1, success.articles.size)
            assertEquals("Page 1 Article", success.articles.first().title)
            assertFalse(success.isLoadingMore)
            assertNull(success.paginationError)
        }
    }

    @Test
    fun `emits Loading then Error when the initial load fails`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } throws RuntimeException("boom")
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            val error = awaitItem() as ArticleListUiState.Error
            assertEquals("Something went wrong.\nPlease try again.", error.message)
        }
    }

    @Test
    fun `loadNextPage appends the next page and clears isLoadingMore on success`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { repository.getTopHeadlines(page = 2) } returns page2
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            awaitItem() // initial Success(page1)

            viewModel.loadNextPage()

            val loadingMore = awaitItem() as ArticleListUiState.Success
            assertTrue(loadingMore.isLoadingMore)

            val appended = awaitItem() as ArticleListUiState.Success
            assertFalse(appended.isLoadingMore)
            assertEquals(2, appended.articles.size)
            assertEquals("Page 2 Article", appended.articles[1].title)
        }
    }

    @Test
    fun `loadNextPage sets paginationError and keeps existing articles on failure`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { repository.getTopHeadlines(page = 2) } throws RuntimeException("pagination failed")
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            awaitItem() // initial Success(page1)

            viewModel.loadNextPage()

            awaitItem() // Success(isLoadingMore = true)

            val failed = awaitItem() as ArticleListUiState.Success
            assertFalse(failed.isLoadingMore)
            assertEquals("Something went wrong.\nPlease try again.", failed.paginationError)
            assertEquals(1, failed.articles.size)
        }
    }

    @Test
    fun `loadNextPage is a no-op while already loading more`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { repository.getTopHeadlines(page = 2) } returns page2
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            awaitItem() // initial Success(page1)

            viewModel.loadNextPage()
            viewModel.loadNextPage() // should be ignored — already loading more

            awaitItem() // Success(isLoadingMore = true)
            awaitItem() // Success(page1 + page2, isLoadingMore = false)
        }
        coVerify(exactly = 1) { repository.getTopHeadlines(page = 2) }
    }

    @Test
    fun `summarize emits Loading then Success for the tapped article`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { summaryRepository.summarize(any(), any(), any()) } returns "A concise summary."
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            val initial = awaitItem() as ArticleListUiState.Success
            val article = initial.articles.first()

            viewModel.summarize(article)

            val loading = awaitItem() as ArticleListUiState.Success
            assertEquals(SummaryUiState.Loading, loading.summaries[article.url])

            val summarized = awaitItem() as ArticleListUiState.Success
            assertEquals(SummaryUiState.Success("A concise summary."), summarized.summaries[article.url])
        }
    }

    @Test
    fun `summarize emits Loading then Error when summarization fails`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { summaryRepository.summarize(any(), any(), any()) } throws RuntimeException("boom")
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            val initial = awaitItem() as ArticleListUiState.Success
            val article = initial.articles.first()

            viewModel.summarize(article)

            awaitItem() // Loading for this article
            val failed = awaitItem() as ArticleListUiState.Success
            assertEquals(SummaryUiState.Error("Something went wrong.\nPlease try again."), failed.summaries[article.url])
        }
    }

    @Test
    fun `summarize is a no-op while already loading for that article`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { summaryRepository.summarize(any(), any(), any()) } returns "A concise summary."
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            val initial = awaitItem() as ArticleListUiState.Success
            val article = initial.articles.first()

            viewModel.summarize(article)
            viewModel.summarize(article) // should be ignored — already loading

            awaitItem() // Loading
            awaitItem() // Success
        }
        coVerify(exactly = 1) { summaryRepository.summarize(any(), any(), any()) }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.articles.presentation.ArticleListViewModelTest"`
Expected: FAIL to compile — `ArticleListViewModel(repository, summaryRepository)` doesn't match the current one-arg constructor, and `viewModel.summarize` is unresolved

- [ ] **Step 4: Write minimal implementation**

Replace `ArticleListViewModel.kt`'s full contents with:

```kotlin
package com.dailyaipulse.articles.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyaipulse.articles.data.ArticleData
import com.dailyaipulse.articles.data.ArticleRepository
import com.dailyaipulse.core.network.toUserMessage
import com.dailyaipulse.summary.data.SummaryRepository
import com.dailyaipulse.summary.presentation.SummaryUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val summaryRepository: SummaryRepository
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

    private fun emit(newState: ArticleListUiState) {
        Timber.d("ArticleListUiState emitted: $newState")
        _uiState.value = newState
    }

    private fun ArticleData.toArticle(): Article = Article(
        title = title,
        description = description,
        imageUrl = imageUrl,
        date = formatDisplayDate(date),
        content = content,
        url = url
    )
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.articles.presentation.ArticleListViewModelTest"`
Expected: PASS (8 tests: 5 existing + 3 new)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListUiState.kt app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListViewModel.kt app/src/test/java/com/dailyaipulse/articles/presentation/ArticleListViewModelTest.kt
git commit -m "Add ArticleListViewModel.summarize() with tests"
```

---

### Task 8: `articles/ui` — `ArticleListItem` Summarize Button & Open-Article Tap

**Files:**
- Modify: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListItem.kt`

**Interfaces:**
- Consumes: `SummaryUiState` (Task 5), `Article` (Task 6)
- Produces: `@Composable fun ArticleListItem(article: Article, summaryState: SummaryUiState = SummaryUiState.Idle, onSummarize: () -> Unit = {}, onOpenArticle: () -> Unit = {}, modifier: Modifier = Modifier)`

- [ ] **Step 1: Replace `ArticleListItem.kt`'s full contents**

```kotlin
package com.dailyaipulse.articles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.summary.presentation.SummaryUiState

@Composable
fun ArticleListItem(
    article: Article,
    summaryState: SummaryUiState = SummaryUiState.Idle,
    onSummarize: () -> Unit = {},
    onOpenArticle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenArticle)
    ) {
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
            SummarySection(summaryState = summaryState, onSummarize = onSummarize)
        }
    }
}

@Composable
private fun SummarySection(summaryState: SummaryUiState, onSummarize: () -> Unit) {
    when (summaryState) {
        is SummaryUiState.Idle -> {
            Button(
                onClick = onSummarize,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Summarize")
            }
        }
        is SummaryUiState.Loading -> {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("summaryLoading")
            )
        }
        is SummaryUiState.Success -> {
            Text(
                text = summaryState.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        is SummaryUiState.Error -> {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = summaryState.message,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onSummarize) {
                    Text("Retry summarize")
                }
            }
        }
    }
}

private val previewArticle = Article(
    title = "Sample Headline About Technology",
    description = "A short sample description of the article content, for preview purposes.",
    imageUrl = null,
    date = "Sep 13, 2026",
    content = "Sample truncated article content for preview purposes...",
    url = "https://example.com/sample-article"
)

@Preview(showBackground = true)
@Composable
private fun ArticleListItemPreview() {
    ArticleListItem(article = previewArticle)
}

@Preview(showBackground = true)
@Composable
private fun ArticleListItemSummaryLoadingPreview() {
    ArticleListItem(article = previewArticle, summaryState = SummaryUiState.Loading)
}

@Preview(showBackground = true)
@Composable
private fun ArticleListItemSummarySuccessPreview() {
    ArticleListItem(
        article = previewArticle,
        summaryState = SummaryUiState.Success(
            "A concise, two-sentence AI-generated summary of the article, shown here for preview purposes."
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListItemSummaryErrorPreview() {
    ArticleListItem(
        article = previewArticle,
        summaryState = SummaryUiState.Error("Something went wrong.\nPlease try again.")
    )
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/ui/ArticleListItem.kt
git commit -m "Add Summarize button and open-article tap to ArticleListItem"
```

---

### Task 9: `articles/ui` — `ArticleListContent` Wiring (TDD, Compose UI test)

**Files:**
- Modify: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListContent.kt`
- Modify: `app/src/androidTest/java/com/dailyaipulse/articles/ui/ArticleListContentTest.kt`

**Interfaces:**
- Consumes: `ArticleListItem` (Task 8), `SummaryUiState` (Task 5), `ArticleListUiState.Success.summaries` (Task 7)
- Produces: `@Composable fun ArticleListContent(uiState: ArticleListUiState, onLoadNextPage: () -> Unit, onSummarizeClick: (Article) -> Unit, onOpenArticleClick: (Article) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing tests**

Replace `ArticleListContentTest.kt`'s full contents with:

```kotlin
package com.dailyaipulse.articles.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.articles.presentation.ArticleListUiState
import com.dailyaipulse.summary.presentation.SummaryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ArticleListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val article = Article(
        title = "Some Headline",
        description = "Some description",
        imageUrl = null,
        date = "Sep 13, 2026",
        content = "Some truncated content",
        url = "https://example.com/some-headline"
    )

    @Test
    fun showsFullScreenLoadingIndicator_whenStateIsLoading() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Loading, onLoadNextPage = {}, onSummarizeClick = {}, onOpenArticleClick = {})
        }

        composeTestRule.onNodeWithTag("fullScreenLoading").assertExists()
    }

    @Test
    fun showsErrorMessage_whenStateIsError() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Error("Something went wrong"),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    fun showsNoArticlesFound_whenSuccessWithEmptyList() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = emptyList()),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("No articles found").assertExists()
    }

    @Test
    fun showsArticles_whenSuccessWithArticles() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article)),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("Some Headline").assertExists()
    }

    @Test
    fun showsPaginationLoadingIndicator_whenIsLoadingMore() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article), isLoadingMore = true),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithTag("paginationLoading").assertExists()
    }

    @Test
    fun tappingRetryRow_invokesOnLoadNextPage_whenPaginationErrorSet() {
        var retried = false
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article), paginationError = "failed"),
                onLoadNextPage = { retried = true },
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("Failed to load more — tap to retry").performClick()

        assert(retried)
    }

    @Test
    fun showsSummarizeButton_whenSummaryStateIsIdle() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article)),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("Summarize").assertExists()
    }

    @Test
    fun showsSummaryLoadingIndicator_whenSummaryStateIsLoading() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(
                    articles = listOf(article),
                    summaries = mapOf(article.url to SummaryUiState.Loading)
                ),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithTag("summaryLoading").assertExists()
    }

    @Test
    fun showsSummaryText_whenSummaryStateIsSuccess() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(
                    articles = listOf(article),
                    summaries = mapOf(article.url to SummaryUiState.Success("A concise AI-generated summary."))
                ),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("A concise AI-generated summary.").assertExists()
    }

    @Test
    fun showsRetryButtonAndErrorMessage_whenSummaryStateIsError() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(
                    articles = listOf(article),
                    summaries = mapOf(article.url to SummaryUiState.Error("Something went wrong.\nPlease try again."))
                ),
                onLoadNextPage = {},
                onSummarizeClick = {},
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("Something went wrong.\nPlease try again.").assertExists()
        composeTestRule.onNodeWithText("Retry summarize").assertExists()
    }

    @Test
    fun tappingSummarizeButton_invokesOnSummarizeClick_withTappedArticle() {
        var summarized: Article? = null
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article)),
                onLoadNextPage = {},
                onSummarizeClick = { summarized = it },
                onOpenArticleClick = {}
            )
        }

        composeTestRule.onNodeWithText("Summarize").performClick()

        assertEquals(article, summarized)
    }

    @Test
    fun tappingCard_invokesOnOpenArticleClick_withoutInvokingOnSummarizeClick() {
        var opened: Article? = null
        var summarized: Article? = null
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article)),
                onLoadNextPage = {},
                onSummarizeClick = { summarized = it },
                onOpenArticleClick = { opened = it }
            )
        }

        composeTestRule.onNodeWithText("Some Headline").performClick()

        assertEquals(article, opened)
        assertNull(summarized)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew connectedDebugAndroidTest --tests "com.dailyaipulse.articles.ui.ArticleListContentTest"`
Expected: FAIL to compile — `ArticleListContent` doesn't have `onSummarizeClick`/`onOpenArticleClick` params yet. (Requires a connected device/emulator; if none is available, skip to Step 3 and verify by reading the implementation against each assertion instead of running the suite.)

- [ ] **Step 3: Write minimal implementation**

Replace `ArticleListContent.kt`'s full contents with:

```kotlin
package com.dailyaipulse.articles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.articles.presentation.ArticleListUiState
import com.dailyaipulse.summary.presentation.SummaryUiState

@Composable
fun ArticleListContent(
    uiState: ArticleListUiState,
    onLoadNextPage: () -> Unit,
    onSummarizeClick: (Article) -> Unit,
    onOpenArticleClick: (Article) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is ArticleListUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("fullScreenLoading")
                )
            }
            is ArticleListUiState.Error -> {
                Text(
                    text = uiState.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
            is ArticleListUiState.Success -> {
                if (uiState.articles.isEmpty()) {
                    Text(
                        text = "No articles found",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(uiState.articles) { index, article ->
                            ArticleListItem(
                                article = article,
                                summaryState = uiState.summaries[article.url] ?: SummaryUiState.Idle,
                                onSummarize = { onSummarizeClick(article) },
                                onOpenArticle = { onOpenArticleClick(article) }
                            )
                            if (index == uiState.articles.lastIndex && !uiState.isLoadingMore) {
                                LaunchedEffect(Unit) { onLoadNextPage() }
                            }
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.testTag("paginationLoading"))
                                }
                            }
                        }
                        if (uiState.paginationError != null) {
                            item {
                                Text(
                                    text = "Failed to load more — tap to retry",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onLoadNextPage() }
                                        .padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val previewArticle = Article(
    title = "Sample Headline About Technology",
    description = "A short sample description of the article content, for preview purposes.",
    imageUrl = null,
    date = "Sep 13, 2026",
    content = "Sample truncated article content for preview purposes...",
    url = "https://example.com/sample-article"
)

@Preview(showBackground = true)
@Composable
private fun ArticleListContentLoadingPreview() {
    ArticleListContent(uiState = ArticleListUiState.Loading, onLoadNextPage = {}, onSummarizeClick = {}, onOpenArticleClick = {})
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentErrorPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Error("Something went wrong.\nPlease try again."),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentEmptyPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(articles = emptyList()),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentSuccessPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(
            articles = listOf(
                previewArticle,
                previewArticle.copy(
                    title = "Second Sample Headline",
                    date = "3h ago",
                    url = "https://example.com/sample-article-2"
                )
            )
        ),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentLoadingMorePreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(articles = listOf(previewArticle), isLoadingMore = true),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentPaginationErrorPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(
            articles = listOf(previewArticle),
            paginationError = "Failed to load more"
        ),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ArticleListContentSummarizedPreview() {
    ArticleListContent(
        uiState = ArticleListUiState.Success(
            articles = listOf(previewArticle),
            summaries = mapOf(
                previewArticle.url to SummaryUiState.Success(
                    "A concise, two-sentence AI-generated summary of the article, shown here for preview purposes."
                )
            )
        ),
        onLoadNextPage = {},
        onSummarizeClick = {},
        onOpenArticleClick = {}
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew connectedDebugAndroidTest --tests "com.dailyaipulse.articles.ui.ArticleListContentTest"`
Expected: PASS (12 tests). If no connected device/emulator is available, run `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` instead to confirm it compiles, and note in the PR description that the instrumented suite still needs to be run on a device before merge.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/ui/ArticleListContent.kt app/src/androidTest/java/com/dailyaipulse/articles/ui/ArticleListContentTest.kt
git commit -m "Wire summarize/open-article callbacks through ArticleListContent, with tests"
```

---

### Task 10: `articles/ui` — Wire `ArticleListScreen`

**Files:**
- Modify: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListScreen.kt`

**Interfaces:**
- Consumes: `ArticleListViewModel.summarize(article)` (Task 7), `ArticleListContent` (Task 9)
- Produces: `ArticleListScreen` now supplies real `onSummarizeClick`/`onOpenArticleClick` behavior — no new public interface, this is the final integration point.

- [ ] **Step 1: Replace `ArticleListScreen.kt`'s full contents**

```kotlin
package com.dailyaipulse.articles.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyaipulse.articles.presentation.ArticleListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(viewModel: ArticleListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Articles") }) }
    ) { paddingValues ->
        ArticleListContent(
            uiState = uiState,
            onLoadNextPage = viewModel::loadNextPage,
            onSummarizeClick = viewModel::summarize,
            onOpenArticleClick = { article ->
                context.startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri()))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}
```

No `@Preview` here — same reasoning as before: `hiltViewModel()` can't resolve a real Hilt graph in a preview, and `ArticleListContent`'s previews already cover every visual state.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (existing Article List/Source List tests + new SummaryRepository/ArticleListViewModel tests)

- [ ] **Step 4: Add `GEMINI_API_KEY` and manually verify on a device/emulator**

Add `GEMINI_API_KEY=your_key_here` to `~/.gradle/gradle.properties` (a free-tier key from [Google AI Studio](https://ai.google.dev/)) if not already present. Run: `./gradlew installDebug`, open the app, on the "Articles" tab tap "Summarize" on one article.
Expected: the button is replaced by a small spinner, then by an italicized AI-generated summary paragraph. Tapping anywhere else on that same article's card opens it in the device's browser. Keep this manual check to a handful of taps — Gemini's free tier is rate-limited (see Global Constraints).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/ui/ArticleListScreen.kt
git commit -m "Wire Summarize and open-article actions into ArticleListScreen"
```

---

## After This Plan

Per this repo's workflow rules: open a PR from this feature branch, get it reviewed/approved, reply to every review comment on the PR itself, then merge (never commit directly to `main`). Update `docs/PROJECT_STATUS.md` per its own header rules once this merges, and in the same change validate `README.md` (the "Features" status line for AI Summarization, and the "Project docs" index) and every spec under `docs/superpowers/specs/` for now-stale "planned"/"not yet built" mentions.

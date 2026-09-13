# Article List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Article List MVP feature end-to-end — fetches a paginated list of technology headlines from NewsAPI.org and displays them, with loading/empty/error states — and wire it up so the app actually runs and shows it.

**Architecture:** MVVM + Repository (no domain layer), per the project's architecture doc (`docs/superpowers/specs/2026-09-12-architecture-design.md`) and the fully-specced feature doc (`docs/superpowers/specs/2026-09-12-article-list-design.md`). Each feature package has exactly `ui/`, `presentation/`, `data/`. Hilt for DI, Retrofit+Moshi+OkHttp for networking (auth via a shared interceptor), Coroutines+StateFlow for async/state, Coil for images.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit, Moshi, OkHttp, Coroutines, Coil, Navigation Compose, Timber. Testing: MockK, kotlinx-coroutines-test, Turbine, Compose UI testing.

**One implementation-level addition beyond the design doc's conversational code samples:** the design doc's `ArticleListScreen` combined ViewModel-collection and UI rendering in one composable. This plan splits that into `ArticleListScreen` (thin wrapper: gets the ViewModel, collects state) and `ArticleListContent` (stateless: takes `ArticleListUiState` + a callback, renders it). This is required by the architecture doc's own stated testing approach — "Compose UI — testable via Compose UI testing, driven by fixed `UiState` values, independent of ViewModel/data internals" — which isn't possible without a stateless composable to feed fixed values into. No visual/behavioral change, purely a testability split. Also, `formatDisplayDate` moves from a private `ArticleListViewModel` function into its own top-level function in `DateFormatter.kt` (with an injectable `now: Instant` defaulting to `Instant.now()`) so it's independently unit-testable and deterministic in tests — again no behavior change for real callers.

---

## Execution: Two Branches, Reviewed Separately

This plan executes as two feature branches, each opened as a PR and merged before the next one starts:

- **Bucket 1 — Architecture (branch `feature/architecture-setup`):** Tasks 1–5. Cross-cutting infra from the architecture doc — Gradle dependencies, the `INTERNET` permission, the Hilt `Application` class, the NewsAPI auth interceptor, and the shared `NetworkModule`. None of these reference Article-specific code.
- **Bucket 2 — Feature (branch `feature/article-list`, cut from `main` *after* Bucket 1 merges):** Tasks 6–18. Everything from the feature doc — the `articles/` package (data/presentation/ui), plus wiring it into `AppNavigation`/`MainActivity` (Tasks 16–17 necessarily depend on the feature existing, so they belong here, not in Bucket 1), plus final verification (Task 18).

Stop after each bucket's PR is opened — do not merge it and do not start the next bucket's branch until it has been reviewed and merged.

---

## File Structure

```
app/src/main/java/com/dailyaipulse/
├── DailyAiPulseApplication.kt          [new]
├── MainActivity.kt                     [modify]
├── core/
│   ├── network/
│   │   └── NewsApiKeyInterceptor.kt    [new]
│   └── di/
│       └── NetworkModule.kt            [new]
├── navigation/
│   └── AppNavigation.kt                [new]
└── articles/
    ├── data/
    │   ├── ArticleData.kt              [new] (ArticleData + TopHeadlinesResponseData)
    │   ├── ArticleApiService.kt        [new]
    │   ├── ArticleRepository.kt        [new]
    │   └── ArticleModule.kt            [new]
    ├── presentation/
    │   ├── Article.kt                  [new]
    │   ├── DateFormatter.kt            [new]
    │   ├── ArticleListUiState.kt       [new]
    │   └── ArticleListViewModel.kt     [new]
    └── ui/
        ├── ArticleListItem.kt          [new]
        ├── ArticleListContent.kt       [new]
        └── ArticleListScreen.kt        [new]

app/src/main/AndroidManifest.xml        [modify]
app/build.gradle.kts                    [modify]
gradle/libs.versions.toml               [modify]

app/src/test/java/com/dailyaipulse/articles/
├── data/ArticleRepositoryTest.kt              [new]
└── presentation/
    ├── DateFormatterTest.kt                   [new]
    └── ArticleListViewModelTest.kt             [new]

app/src/androidTest/java/com/dailyaipulse/articles/ui/
└── ArticleListContentTest.kt                  [new]
```

---

## Task 1: Add Gradle dependencies (Coil, core library desugaring, test libraries)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version/library entries to `gradle/libs.versions.toml`**

In the `[versions]` block, add after `timber = "5.0.1"`:

```toml
coil = "3.6.2"
desugarJdkLibs = "2.1.5"
mockk = "1.14.11"
turbine = "1.2.1"
```

In the `[libraries]` block, add after `timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }`:

```toml
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }
desugar-jdk-libs = { group = "com.android.tools", name = "desugar_jdk_libs", version.ref = "desugarJdkLibs" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

(`kotlinx-coroutines-test` reuses the existing `kotlinxCoroutines = "1.10.2"` version ref — same pin as `kotlinx-coroutines-android`, for the same Kotlin-2.2.10-compatibility reason documented in the architecture doc.)

- [ ] **Step 2: Enable core library desugaring in `app/build.gradle.kts`**

Find the `compileOptions` block:

```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
```

Replace with:

```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
```

- [ ] **Step 3: Add the new dependencies in `app/build.gradle.kts`**

Find the `dependencies` block. After the existing `implementation(libs.timber)` line, add:

```kotlin
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
```

After the existing `testImplementation(libs.junit)` line, add:

```kotlin
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
```

- [ ] **Step 4: Verify it resolves and compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Add Coil, core library desugaring, and test dependencies"
```

---

## Task 2: Add `INTERNET` permission

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add the permission**

Current file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
```

Change to:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "Add INTERNET permission"
```

---

## Task 3: Add Hilt `Application` class

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/DailyAiPulseApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the Application class**

```kotlin
package com.dailyaipulse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DailyAiPulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

- [ ] **Step 2: Register it in the manifest**

Find:

```xml
    <application
        android:allowBackup="true"
```

Change to:

```xml
    <application
        android:name=".DailyAiPulseApplication"
        android:allowBackup="true"
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/DailyAiPulseApplication.kt app/src/main/AndroidManifest.xml
git commit -m "Add Hilt Application class"
```

---

## Task 4: `core/network` — auth interceptor

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/core/network/NewsApiKeyInterceptor.kt`

- [ ] **Step 1: Create the interceptor**

```kotlin
package com.dailyaipulse.core.network

import com.dailyaipulse.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class NewsApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-Api-Key", BuildConfig.NEWS_API_KEY)
            .build()
        return chain.proceed(request)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/core/network/NewsApiKeyInterceptor.kt
git commit -m "Add NewsAPI auth interceptor"
```

---

## Task 5: `core/di` — `NetworkModule`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/core/di/NetworkModule.kt`

- [ ] **Step 1: Create the module**

```kotlin
package com.dailyaipulse.core.di

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
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/core/di/NetworkModule.kt
git commit -m "Add shared NetworkModule (Retrofit/OkHttp/Moshi)"
```

---

## Task 6: `articles/data` — `ArticleData`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/articles/data/ArticleData.kt`

- [ ] **Step 1: Create the data models**

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
    @Json(name = "publishedAt") val date: String
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/data/ArticleData.kt
git commit -m "Add ArticleData data models"
```

---

## Task 7: `articles/data` — `ArticleApiService`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/articles/data/ArticleApiService.kt`

- [ ] **Step 1: Create the Retrofit interface**

```kotlin
package com.dailyaipulse.articles.data

import retrofit2.http.GET
import retrofit2.http.Query

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

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/data/ArticleApiService.kt
git commit -m "Add ArticleApiService"
```

---

## Task 8: `articles/data` — `ArticleRepository` (TDD)

**Files:**
- Create: `app/src/test/java/com/dailyaipulse/articles/data/ArticleRepositoryTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/articles/data/ArticleRepository.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dailyaipulse.articles.data

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleRepositoryTest {

    @Test
    fun `getTopHeadlines returns the articles list from the API response`() = runTest {
        val fakeArticles = listOf(
            ArticleData(
                title = "Title 1",
                description = "Desc 1",
                imageUrl = "https://img.example/1.png",
                date = "2026-09-13T10:00:00Z"
            ),
            ArticleData(
                title = "Title 2",
                description = null,
                imageUrl = null,
                date = "2026-09-13T09:00:00Z"
            )
        )
        val apiService = mockk<ArticleApiService>()
        coEvery {
            apiService.getTopHeadlines(page = 1, country = "us", category = "technology", pageSize = 20)
        } returns TopHeadlinesResponseData(status = "ok", totalResults = 2, articles = fakeArticles)
        val repository = ArticleRepository(apiService)

        val result = repository.getTopHeadlines(page = 1)

        assertEquals(fakeArticles, result)
    }

    @Test(expected = RuntimeException::class)
    fun `getTopHeadlines propagates exceptions from the API service`() = runTest {
        val apiService = mockk<ArticleApiService>()
        coEvery {
            apiService.getTopHeadlines(page = 1, country = "us", category = "technology", pageSize = 20)
        } throws RuntimeException("network error")
        val repository = ArticleRepository(apiService)

        repository.getTopHeadlines(page = 1)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailyaipulse.articles.data.ArticleRepositoryTest"`
Expected: FAIL — `ArticleRepository` is unresolved (doesn't exist yet)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.dailyaipulse.articles.data

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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailyaipulse.articles.data.ArticleRepositoryTest"`
Expected: `BUILD SUCCESSFUL`, both tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/dailyaipulse/articles/data/ArticleRepositoryTest.kt app/src/main/java/com/dailyaipulse/articles/data/ArticleRepository.kt
git commit -m "Add ArticleRepository"
```

---

## Task 9: `articles/data` — `ArticleModule`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/articles/data/ArticleModule.kt`

- [ ] **Step 1: Create the module**

```kotlin
package com.dailyaipulse.articles.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

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

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/data/ArticleModule.kt
git commit -m "Add ArticleModule (Hilt)"
```

---

## Task 10: `articles/presentation` — `DateFormatter` (TDD)

**Files:**
- Create: `app/src/test/java/com/dailyaipulse/articles/presentation/DateFormatterTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/articles/presentation/DateFormatter.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dailyaipulse.articles.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DateFormatterTest {

    @Test
    fun `formats a date less than a minute old as Just now`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-13T11:59:30Z")

        assertEquals("Just now", formatDisplayDate(published.toString(), now))
    }

    @Test
    fun `formats a date under an hour old in minutes`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-13T11:45:00Z")

        assertEquals("15m ago", formatDisplayDate(published.toString(), now))
    }

    @Test
    fun `formats a date under twelve hours old in hours`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-13T05:00:00Z")

        assertEquals("7h ago", formatDisplayDate(published.toString(), now))
    }

    @Test
    fun `formats a date twelve or more hours old as an absolute date`() {
        val now = Instant.parse("2026-09-13T12:00:00Z")
        val published = Instant.parse("2026-09-12T23:00:00Z")

        // Computed the same way the production code computes it, rather than
        // hardcoded, so this test isn't dependent on the machine's timezone.
        val expected = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
            .format(published)

        assertEquals(expected, formatDisplayDate(published.toString(), now))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailyaipulse.articles.presentation.DateFormatterTest"`
Expected: FAIL — `formatDisplayDate` is unresolved (doesn't exist yet)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.dailyaipulse.articles.presentation

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatDisplayDate(isoDate: String, now: Instant = Instant.now()): String {
    val publishedInstant = Instant.parse(isoDate)
    val duration = Duration.between(publishedInstant, now)
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

(`now` defaults to `Instant.now()` for real callers — the parameter only exists so tests can pass a fixed instant instead of depending on wall-clock time.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailyaipulse.articles.presentation.DateFormatterTest"`
Expected: `BUILD SUCCESSFUL`, all 4 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/dailyaipulse/articles/presentation/DateFormatterTest.kt app/src/main/java/com/dailyaipulse/articles/presentation/DateFormatter.kt
git commit -m "Add formatDisplayDate"
```

---

## Task 11: `articles/presentation` — `Article` and `ArticleListUiState`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/articles/presentation/Article.kt`
- Create: `app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListUiState.kt`

- [ ] **Step 1: Create the presentation model**

```kotlin
package com.dailyaipulse.articles.presentation

data class Article(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val date: String
)
```

- [ ] **Step 2: Create the UI state**

```kotlin
package com.dailyaipulse.articles.presentation

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

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/presentation/Article.kt app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListUiState.kt
git commit -m "Add Article presentation model and ArticleListUiState"
```

---

## Task 12: `articles/presentation` — `ArticleListViewModel` (TDD)

**Files:**
- Create: `app/src/test/java/com/dailyaipulse/articles/presentation/ArticleListViewModelTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListViewModel.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dailyaipulse.articles.presentation

import app.cash.turbine.test
import com.dailyaipulse.articles.data.ArticleData
import com.dailyaipulse.articles.data.ArticleRepository
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
        ArticleData(title = "Page 1 Article", description = "Desc", imageUrl = "https://img/1.png", date = "2026-09-13T11:59:30Z")
    )
    private val page2 = listOf(
        ArticleData(title = "Page 2 Article", description = "Desc", imageUrl = "https://img/2.png", date = "2026-09-13T11:59:30Z")
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
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        val viewModel = ArticleListViewModel(repository)

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
        coEvery { repository.getTopHeadlines(page = 1) } throws RuntimeException("boom")
        val viewModel = ArticleListViewModel(repository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            val error = awaitItem() as ArticleListUiState.Error
            assertEquals("boom", error.message)
        }
    }

    @Test
    fun `loadNextPage appends the next page and clears isLoadingMore on success`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { repository.getTopHeadlines(page = 2) } returns page2
        val viewModel = ArticleListViewModel(repository)

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
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { repository.getTopHeadlines(page = 2) } throws RuntimeException("pagination failed")
        val viewModel = ArticleListViewModel(repository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            awaitItem() // initial Success(page1)

            viewModel.loadNextPage()

            awaitItem() // Success(isLoadingMore = true)

            val failed = awaitItem() as ArticleListUiState.Success
            assertFalse(failed.isLoadingMore)
            assertEquals("pagination failed", failed.paginationError)
            assertEquals(1, failed.articles.size)
        }
    }

    @Test
    fun `loadNextPage is a no-op while already loading more`() = runTest(testDispatcher) {
        val repository = mockk<ArticleRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns page1
        coEvery { repository.getTopHeadlines(page = 2) } returns page2
        val viewModel = ArticleListViewModel(repository)

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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailyaipulse.articles.presentation.ArticleListViewModelTest"`
Expected: FAIL — `ArticleListViewModel` is unresolved (doesn't exist yet)

- [ ] **Step 3: Write the implementation**

```kotlin
package com.dailyaipulse.articles.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyaipulse.articles.data.ArticleData
import com.dailyaipulse.articles.data.ArticleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
                emit(ArticleListUiState.Error(message = e.message ?: "Unknown error"))
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state !is ArticleListUiState.Success || state.isLoadingMore) return
        viewModelScope.launch {
            emit(state.copy(isLoadingMore = true, paginationError = null))
            val nextPage = currentPage + 1
            try {
                val nextArticles = articleRepository.getTopHeadlines(page = nextPage).map { it.toArticle() }
                currentPage = nextPage
                emit(state.copy(articles = state.articles + nextArticles, isLoadingMore = false))
            } catch (e: Exception) {
                emit(state.copy(isLoadingMore = false, paginationError = e.message ?: "Failed to load more"))
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailyaipulse.articles.presentation.ArticleListViewModelTest"`
Expected: `BUILD SUCCESSFUL`, all 5 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/dailyaipulse/articles/presentation/ArticleListViewModelTest.kt app/src/main/java/com/dailyaipulse/articles/presentation/ArticleListViewModel.kt
git commit -m "Add ArticleListViewModel"
```

---

## Task 13: `articles/ui` — `ArticleListItem`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListItem.kt`

- [ ] **Step 1: Create the composable**

```kotlin
package com.dailyaipulse.articles.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dailyaipulse.articles.presentation.Article

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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/ui/ArticleListItem.kt
git commit -m "Add ArticleListItem"
```

---

## Task 14: `articles/ui` — `ArticleListContent` (TDD)

**Files:**
- Create: `app/src/androidTest/java/com/dailyaipulse/articles/ui/ArticleListContentTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListContent.kt`

- [ ] **Step 1: Write the failing Compose UI tests**

```kotlin
package com.dailyaipulse.articles.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dailyaipulse.articles.presentation.Article
import com.dailyaipulse.articles.presentation.ArticleListUiState
import org.junit.Rule
import org.junit.Test

class ArticleListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val article = Article(
        title = "Some Headline",
        description = "Some description",
        imageUrl = null,
        date = "Sep 13, 2026"
    )

    @Test
    fun showsFullScreenLoadingIndicator_whenStateIsLoading() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Loading, onLoadNextPage = {})
        }

        composeTestRule.onNodeWithTag("fullScreenLoading").assertExists()
    }

    @Test
    fun showsErrorMessage_whenStateIsError() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Error("Something went wrong"), onLoadNextPage = {})
        }

        composeTestRule.onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    fun showsNoArticlesFound_whenSuccessWithEmptyList() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Success(articles = emptyList()), onLoadNextPage = {})
        }

        composeTestRule.onNodeWithText("No articles found").assertExists()
    }

    @Test
    fun showsArticles_whenSuccessWithArticles() {
        composeTestRule.setContent {
            ArticleListContent(uiState = ArticleListUiState.Success(articles = listOf(article)), onLoadNextPage = {})
        }

        composeTestRule.onNodeWithText("Some Headline").assertExists()
    }

    @Test
    fun showsPaginationLoadingIndicator_whenIsLoadingMore() {
        composeTestRule.setContent {
            ArticleListContent(
                uiState = ArticleListUiState.Success(articles = listOf(article), isLoadingMore = true),
                onLoadNextPage = {}
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
                onLoadNextPage = { retried = true }
            )
        }

        composeTestRule.onNodeWithText("Failed to load more — tap to retry").performClick()

        assert(retried)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.dailyaipulse.articles.ui.ArticleListContentTest"`
Expected: FAIL — `ArticleListContent` is unresolved (doesn't exist yet). (Requires a connected emulator/device — see Task 17 if none is available yet.)

- [ ] **Step 3: Write the implementation**

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
import androidx.compose.ui.unit.dp
import com.dailyaipulse.articles.presentation.ArticleListUiState

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
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("fullScreenLoading")
                )
            }
            is ArticleListUiState.Error -> {
                Text(text = uiState.message, modifier = Modifier.align(Alignment.Center))
            }
            is ArticleListUiState.Success -> {
                if (uiState.articles.isEmpty()) {
                    Text(text = "No articles found", modifier = Modifier.align(Alignment.Center))
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.dailyaipulse.articles.ui.ArticleListContentTest"`
Expected: `BUILD SUCCESSFUL`, all 6 tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/dailyaipulse/articles/ui/ArticleListContentTest.kt app/src/main/java/com/dailyaipulse/articles/ui/ArticleListContent.kt
git commit -m "Add ArticleListContent"
```

---

## Task 15: `articles/ui` — `ArticleListScreen`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/articles/ui/ArticleListScreen.kt`

- [ ] **Step 1: Create the screen**

```kotlin
package com.dailyaipulse.articles.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyaipulse.articles.presentation.ArticleListViewModel

@Composable
fun ArticleListScreen(viewModel: ArticleListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Articles") }) }
    ) { paddingValues ->
        ArticleListContent(
            uiState = uiState,
            onLoadNextPage = viewModel::loadNextPage,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/articles/ui/ArticleListScreen.kt
git commit -m "Add ArticleListScreen"
```

---

## Task 16: `navigation` — `AppNavigation`

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/navigation/AppNavigation.kt`

- [ ] **Step 1: Create the nav graph**

```kotlin
package com.dailyaipulse.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dailyaipulse.articles.ui.ArticleListScreen
import kotlinx.serialization.Serializable

@Serializable
object ArticleListRoute

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ArticleListRoute) {
        composable<ArticleListRoute> {
            ArticleListScreen()
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/navigation/AppNavigation.kt
git commit -m "Add AppNavigation"
```

---

## Task 17: Wire up `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/dailyaipulse/MainActivity.kt`

- [ ] **Step 1: Replace the default template content**

Current file:

```kotlin
package com.dailyaipulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dailyaipulse.ui.theme.DailyAIPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailyAIPulseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DailyAIPulseTheme {
        Greeting("Android")
    }
}
```

Replace the entire file with:

```kotlin
package com.dailyaipulse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dailyaipulse.navigation.AppNavigation
import com.dailyaipulse.ui.theme.DailyAIPulseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailyAIPulseTheme {
                AppNavigation()
            }
        }
    }
}
```

(The template's `Greeting`/`GreetingPreview` are removed — they're no longer used by anything.)

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/MainActivity.kt
git commit -m "Wire MainActivity to AppNavigation"
```

---

## Task 18: Full verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` — `ArticleRepositoryTest`, `DateFormatterTest`, and `ArticleListViewModelTest` all pass (11 tests total)

- [ ] **Step 2: Run the full instrumented test suite** (requires a connected emulator or device)

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL` — `ArticleListContentTest` passes (6 tests)

- [ ] **Step 3: Full assemble**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manual smoke test**

Install and launch the app on an emulator/device (e.g. via the `run` skill, or `./gradlew :app:installDebug` then launch manually). Confirm:
- The app opens directly to the "Articles" screen (top bar reads "Articles").
- A loading spinner appears briefly, then a scrollable list of technology articles with images, titles, descriptions, and dates.
- Scrolling to the bottom loads more articles (small spinner appears briefly at the bottom).
- Logcat (filter: `Timber`/`ArticleListUiState`) shows a log line for every state transition.

If NewsAPI's key is invalid/rate-limited, the screen should show the centered error message instead of crashing — confirm that too, if it comes up.

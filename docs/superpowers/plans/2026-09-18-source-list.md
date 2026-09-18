# Source List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the blank `SourceListScreen` placeholder with a real Source List feature that fetches technology news sources from NewsAPI and displays them in a list.

**Architecture:** MVVM + Repository, three layers (`data`/`presentation`/`ui`) under `com.dailyaipulse.sources`, mirroring the already-built Article List feature's classes/patterns exactly, minus pagination (the `/top-headlines/sources` endpoint returns the full list in one call).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Retrofit + Moshi, OkHttp, Kotlin Coroutines + StateFlow, Timber, MockK, kotlinx-coroutines-test, Turbine, Compose UI testing. No Coil (no images in this feature) and no `java.time` date formatting (no dates in this feature).

**Spec:** `docs/superpowers/specs/2026-09-17-source-list-design.md`

## Global Constraints

- Endpoint: `GET v2/top-headlines/sources` with query params `country=us`, `category=technology` — no `page`/`pageSize`.
- Auth: NewsAPI key attached automatically by the existing shared `core/network` OkHttp interceptor — never add an `apiKey` query param.
- Every ViewModel catch block must call `Throwable.toUserMessage()` (from `com.dailyaipulse.core.network`) — never surface a raw exception message.
- Every `StateFlow` state change routes through a single private `emit()` helper that logs via `Timber.d` before setting `_uiState.value`.
- `SourceRepository` is a single concrete class (no interface/impl split); its function is `suspend fun` and lets exceptions propagate uncaught.
- Every Composable needs `@Preview` coverage — one `@Preview` per meaningful `UiState` variant on the stateless content composable; the `hiltViewModel()`-collecting wrapper is exempt.
- Package layout is exactly `sources/{ui,data,presentation}` — no additional sub-packages.

---

### Task 1: Data Layer — Models & API Service

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/sources/data/SourceData.kt`
- Create: `app/src/main/java/com/dailyaipulse/sources/data/SourceApiService.kt`

**Interfaces:**
- Produces: `data class SourceData(val id: String, val name: String, val description: String)`, `data class TopHeadlinesSourcesResponseData(val status: String, val sources: List<SourceData>)`, `interface SourceApiService { suspend fun getSources(country: String = "us", category: String = "technology"): TopHeadlinesSourcesResponseData }`

- [ ] **Step 1: Create `SourceData.kt`**

```kotlin
package com.dailyaipulse.sources.data

import com.squareup.moshi.JsonClass

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

- [ ] **Step 2: Create `SourceApiService.kt`**

```kotlin
package com.dailyaipulse.sources.data

import retrofit2.http.GET
import retrofit2.http.Query

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

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/sources/data/SourceData.kt app/src/main/java/com/dailyaipulse/sources/data/SourceApiService.kt
git commit -m "Add Source List data model and API service"
```

---

### Task 2: Data Layer — Repository (TDD)

**Files:**
- Create: `app/src/test/java/com/dailyaipulse/sources/data/SourceRepositoryTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/sources/data/SourceRepository.kt`

**Interfaces:**
- Consumes: `SourceApiService.getSources(country, category): TopHeadlinesSourcesResponseData` (Task 1), `SourceData` (Task 1)
- Produces: `class SourceRepository(private val sourceApiService: SourceApiService) { suspend fun getSources(): List<SourceData> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dailyaipulse.sources.data

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRepositoryTest {

    @Test
    fun `getSources returns the sources list from the API response`() = runTest {
        val fakeSources = listOf(
            SourceData(id = "techcrunch", name = "TechCrunch", description = "Startup and tech news"),
            SourceData(id = "the-verge", name = "The Verge", description = "Technology, science, art, and culture")
        )
        val apiService = mockk<SourceApiService>()
        coEvery {
            apiService.getSources(country = "us", category = "technology")
        } returns TopHeadlinesSourcesResponseData(status = "ok", sources = fakeSources)
        val repository = SourceRepository(apiService)

        val result = repository.getSources()

        assertEquals(fakeSources, result)
    }

    @Test(expected = RuntimeException::class)
    fun `getSources propagates exceptions from the API service`() = runTest {
        val apiService = mockk<SourceApiService>()
        coEvery {
            apiService.getSources(country = "us", category = "technology")
        } throws RuntimeException("network error")
        val repository = SourceRepository(apiService)

        repository.getSources()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.sources.data.SourceRepositoryTest"`
Expected: FAIL to compile — `SourceRepository` is unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.dailyaipulse.sources.data

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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.sources.data.SourceRepositoryTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/dailyaipulse/sources/data/SourceRepositoryTest.kt app/src/main/java/com/dailyaipulse/sources/data/SourceRepository.kt
git commit -m "Add SourceRepository with tests"
```

---

### Task 3: Data Layer — Hilt Module

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/sources/data/SourceModule.kt`

**Interfaces:**
- Consumes: `SourceApiService` (Task 1), `SourceRepository` (Task 2), the app-wide `Retrofit` instance provided by `com.dailyaipulse.core.di.NetworkModule`
- Produces: Hilt-injectable `SourceApiService` and `SourceRepository` singletons

- [ ] **Step 1: Create `SourceModule.kt`**

```kotlin
package com.dailyaipulse.sources.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

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

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/sources/data/SourceModule.kt
git commit -m "Add SourceModule Hilt bindings"
```

---

### Task 4: Presentation Layer — Models

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/sources/presentation/Source.kt`
- Create: `app/src/main/java/com/dailyaipulse/sources/presentation/SourceListUiState.kt`

**Interfaces:**
- Produces: `data class Source(val name: String, val description: String)`, `sealed interface SourceListUiState { data object Loading; data class Success(val sources: List<Source>); data class Error(val message: String) }`

- [ ] **Step 1: Create `Source.kt`**

```kotlin
package com.dailyaipulse.sources.presentation

data class Source(
    val name: String,
    val description: String
)
```

- [ ] **Step 2: Create `SourceListUiState.kt`**

```kotlin
package com.dailyaipulse.sources.presentation

sealed interface SourceListUiState {
    data object Loading : SourceListUiState
    data class Success(val sources: List<Source>) : SourceListUiState
    data class Error(val message: String) : SourceListUiState
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/sources/presentation/Source.kt app/src/main/java/com/dailyaipulse/sources/presentation/SourceListUiState.kt
git commit -m "Add Source presentation model and SourceListUiState"
```

---

### Task 5: Presentation Layer — ViewModel (TDD)

**Files:**
- Create: `app/src/test/java/com/dailyaipulse/sources/presentation/SourceListViewModelTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/sources/presentation/SourceListViewModel.kt`

**Interfaces:**
- Consumes: `SourceRepository.getSources(): List<SourceData>` (Task 2), `SourceData` (Task 1), `Source`/`SourceListUiState` (Task 4), `Throwable.toUserMessage()` (existing, `com.dailyaipulse.core.network`)
- Produces: `class SourceListViewModel(sourceRepository: SourceRepository) : ViewModel() { val uiState: StateFlow<SourceListUiState> }`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dailyaipulse.sources.presentation

import app.cash.turbine.test
import com.dailyaipulse.sources.data.SourceData
import com.dailyaipulse.sources.data.SourceRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sources = listOf(
        SourceData(id = "techcrunch", name = "TechCrunch", description = "Startup and tech news"),
        SourceData(id = "the-verge", name = "The Verge", description = "Technology, science, art, and culture")
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
    fun `emits Loading then Success with mapped sources on successful load`() = runTest(testDispatcher) {
        val repository = mockk<SourceRepository>()
        coEvery { repository.getSources() } returns sources
        val viewModel = SourceListViewModel(repository)

        viewModel.uiState.test {
            assertEquals(SourceListUiState.Loading, awaitItem())
            val success = awaitItem() as SourceListUiState.Success
            assertEquals(2, success.sources.size)
            assertEquals("TechCrunch", success.sources.first().name)
            assertEquals("Startup and tech news", success.sources.first().description)
        }
    }

    @Test
    fun `emits Loading then Error when the load fails`() = runTest(testDispatcher) {
        val repository = mockk<SourceRepository>()
        coEvery { repository.getSources() } throws RuntimeException("boom")
        val viewModel = SourceListViewModel(repository)

        viewModel.uiState.test {
            assertEquals(SourceListUiState.Loading, awaitItem())
            val error = awaitItem() as SourceListUiState.Error
            assertEquals("Something went wrong.\nPlease try again.", error.message)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.sources.presentation.SourceListViewModelTest"`
Expected: FAIL to compile — `SourceListViewModel` is unresolved

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.dailyaipulse.sources.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyaipulse.core.network.toUserMessage
import com.dailyaipulse.sources.data.SourceData
import com.dailyaipulse.sources.data.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.dailyaipulse.sources.presentation.SourceListViewModelTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/dailyaipulse/sources/presentation/SourceListViewModelTest.kt app/src/main/java/com/dailyaipulse/sources/presentation/SourceListViewModel.kt
git commit -m "Add SourceListViewModel with tests"
```

---

### Task 6: UI Layer — SourceListItem

**Files:**
- Create: `app/src/main/java/com/dailyaipulse/sources/ui/SourceListItem.kt`

**Interfaces:**
- Consumes: `Source` (Task 4)
- Produces: `@Composable fun SourceListItem(source: Source, modifier: Modifier = Modifier)`

- [ ] **Step 1: Create `SourceListItem.kt`**

```kotlin
package com.dailyaipulse.sources.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dailyaipulse.sources.presentation.Source

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

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/sources/ui/SourceListItem.kt
git commit -m "Add SourceListItem composable"
```

---

### Task 7: UI Layer — SourceListContent (TDD, Compose UI test)

**Files:**
- Create: `app/src/androidTest/java/com/dailyaipulse/sources/ui/SourceListContentTest.kt`
- Create: `app/src/main/java/com/dailyaipulse/sources/ui/SourceListContent.kt`

**Interfaces:**
- Consumes: `SourceListItem` (Task 6), `Source`/`SourceListUiState` (Task 4)
- Produces: `@Composable fun SourceListContent(uiState: SourceListUiState, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.dailyaipulse.sources.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.dailyaipulse.sources.presentation.Source
import com.dailyaipulse.sources.presentation.SourceListUiState
import org.junit.Rule
import org.junit.Test

class SourceListContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val source = Source(
        name = "TechCrunch",
        description = "Startup and tech news"
    )

    @Test
    fun showsFullScreenLoadingIndicator_whenStateIsLoading() {
        composeTestRule.setContent {
            SourceListContent(uiState = SourceListUiState.Loading)
        }

        composeTestRule.onNodeWithTag("fullScreenLoading").assertExists()
    }

    @Test
    fun showsErrorMessage_whenStateIsError() {
        composeTestRule.setContent {
            SourceListContent(uiState = SourceListUiState.Error("Something went wrong"))
        }

        composeTestRule.onNodeWithText("Something went wrong").assertExists()
    }

    @Test
    fun showsNoSourcesFound_whenSuccessWithEmptyList() {
        composeTestRule.setContent {
            SourceListContent(uiState = SourceListUiState.Success(sources = emptyList()))
        }

        composeTestRule.onNodeWithText("No sources found").assertExists()
    }

    @Test
    fun showsSources_whenSuccessWithSources() {
        composeTestRule.setContent {
            SourceListContent(uiState = SourceListUiState.Success(sources = listOf(source)))
        }

        composeTestRule.onNodeWithText("TechCrunch").assertExists()
        composeTestRule.onNodeWithText("Startup and tech news").assertExists()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew connectedDebugAndroidTest --tests "com.dailyaipulse.sources.ui.SourceListContentTest"`
Expected: FAIL to compile — `SourceListContent` is unresolved. (Requires a connected device/emulator; if none is available, skip straight to Step 3 and verify by reading the implementation against each assertion instead of running the suite.)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.dailyaipulse.sources.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dailyaipulse.sources.presentation.Source
import com.dailyaipulse.sources.presentation.SourceListUiState

@Composable
fun SourceListContent(
    uiState: SourceListUiState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            is SourceListUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("fullScreenLoading")
                )
            }
            is SourceListUiState.Error -> {
                Text(
                    text = uiState.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
            is SourceListUiState.Success -> {
                if (uiState.sources.isEmpty()) {
                    Text(
                        text = "No sources found",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
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

private val previewSource = Source(
    name = "TechCrunch",
    description = "The latest technology news and information on startups, from a sample preview source."
)

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
        uiState = SourceListUiState.Success(
            sources = listOf(
                previewSource,
                previewSource.copy(name = "The Verge", description = "Technology, science, art, and culture")
            )
        )
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew connectedDebugAndroidTest --tests "com.dailyaipulse.sources.ui.SourceListContentTest"`
Expected: PASS (4 tests). If no connected device/emulator is available, run `./gradlew :app:compileDebugKotlin` instead to confirm it compiles, and note in the PR description that the instrumented suite still needs to be run on a device before merge.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/dailyaipulse/sources/ui/SourceListContentTest.kt app/src/main/java/com/dailyaipulse/sources/ui/SourceListContent.kt
git commit -m "Add SourceListContent composable with tests"
```

---

### Task 8: UI Layer — SourceListScreen (replace placeholder)

**Files:**
- Modify: `app/src/main/java/com/dailyaipulse/sources/ui/SourceListScreen.kt` (currently the blank placeholder — full contents shown below for reference: `@Composable fun SourceListScreen() {}` plus an empty `@Preview`)

**Interfaces:**
- Consumes: `SourceListViewModel` (Task 5), `SourceListContent` (Task 7)
- Produces: `@Composable fun SourceListScreen(viewModel: SourceListViewModel = hiltViewModel())` — same signature shape `AppNavigation.kt`'s existing `composable<SourceListRoute> { SourceListScreen() }` call already expects, so no navigation changes are needed.

- [ ] **Step 1: Replace the placeholder contents**

```kotlin
package com.dailyaipulse.sources.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailyaipulse.sources.presentation.SourceListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceListScreen(viewModel: SourceListViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sources") }) }
    ) { paddingValues ->
        SourceListContent(
            uiState = uiState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}
```

No `@Preview` here — `hiltViewModel()` can't resolve a real Hilt graph in a preview (same reasoning as `ArticleListScreen`); `SourceListContent`'s previews already cover every visual state.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (existing Article List tests + new Source List tests)

- [ ] **Step 4: Manually verify on a device/emulator**

Run: `./gradlew installDebug`, open the app, tap the "Sources" bottom nav tab.
Expected: top bar reads "Sources", a loading spinner appears briefly, then a list of technology sources (name bold on top, description below) renders. If `NEWS_API_KEY` isn't set or the request fails, a two-line error message appears instead, centered.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dailyaipulse/sources/ui/SourceListScreen.kt
git commit -m "Wire up real SourceListScreen, replacing the placeholder"
```

---

## After This Plan

Per this repo's workflow rules: open a PR from this feature branch, get it reviewed/approved, reply to every review comment on the PR itself, then merge (never commit directly to `main`). Update `docs/PROJECT_STATUS.md` per its own header rules once this merges.

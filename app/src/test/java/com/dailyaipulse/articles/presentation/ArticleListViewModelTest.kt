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

    @Test
    fun `summarize of two different articles concurrently resolves both without clobbering`() = runTest(testDispatcher) {
        val twoArticles = listOf(
            ArticleData(
                title = "Article A",
                description = "Desc A",
                imageUrl = "https://img/a.png",
                date = "2026-09-13T11:59:30Z",
                content = "Content A",
                url = "https://example.com/article-a"
            ),
            ArticleData(
                title = "Article B",
                description = "Desc B",
                imageUrl = "https://img/b.png",
                date = "2026-09-13T11:59:30Z",
                content = "Content B",
                url = "https://example.com/article-b"
            )
        )
        val repository = mockk<ArticleRepository>()
        val summaryRepository = mockk<SummaryRepository>()
        coEvery { repository.getTopHeadlines(page = 1) } returns twoArticles
        coEvery { summaryRepository.summarize("Article A", "Desc A", "Content A") } returns "Summary A"
        coEvery { summaryRepository.summarize("Article B", "Desc B", "Content B") } returns "Summary B"
        val viewModel = ArticleListViewModel(repository, summaryRepository)

        viewModel.uiState.test {
            assertEquals(ArticleListUiState.Loading, awaitItem())
            val initial = awaitItem() as ArticleListUiState.Success
            val articleA = initial.articles[0]
            val articleB = initial.articles[1]

            viewModel.summarize(articleA)
            viewModel.summarize(articleB)

            // Both summarize() calls are in flight concurrently, and MutableStateFlow
            // conflates rapid updates, so the exact number of intermediate emissions
            // isn't guaranteed. Drain items until BOTH articles have resolved to a
            // terminal Success state rather than asserting on an exact emission count
            // — this is what actually exercises the bug: with the old (buggy) code,
            // one article's completion would overwrite the other's still-pending
            // Loading entry using a stale captured snapshot, so this loop would never
            // terminate and the test would time out.
            var latest: ArticleListUiState.Success
            do {
                latest = awaitItem() as ArticleListUiState.Success
            } while (
                latest.summaries[articleA.url] !is SummaryUiState.Success ||
                latest.summaries[articleB.url] !is SummaryUiState.Success
            )

            assertEquals(SummaryUiState.Success("Summary A"), latest.summaries[articleA.url])
            assertEquals(SummaryUiState.Success("Summary B"), latest.summaries[articleB.url])
            cancelAndIgnoreRemainingEvents()
        }
    }
}

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

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

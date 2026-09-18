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

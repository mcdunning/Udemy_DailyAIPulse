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

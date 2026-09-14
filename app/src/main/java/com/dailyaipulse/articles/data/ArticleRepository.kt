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

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

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

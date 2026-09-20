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
    @Json(name = "publishedAt") val date: String,
    val content: String?,
    val url: String
)

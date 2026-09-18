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

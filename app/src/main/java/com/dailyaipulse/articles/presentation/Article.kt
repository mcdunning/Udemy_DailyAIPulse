package com.dailyaipulse.articles.presentation

data class Article(
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val date: String,
    val content: String?,
    val url: String
)

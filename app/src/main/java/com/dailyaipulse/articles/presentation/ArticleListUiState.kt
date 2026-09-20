package com.dailyaipulse.articles.presentation

import com.dailyaipulse.summary.presentation.SummaryUiState

sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(
        val articles: List<Article>,
        val isLoadingMore: Boolean = false,
        val paginationError: String? = null,
        val summaries: Map<String, SummaryUiState> = emptyMap()
    ) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}

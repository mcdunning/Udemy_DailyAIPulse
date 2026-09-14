package com.dailyaipulse.articles.presentation

sealed interface ArticleListUiState {
    data object Loading : ArticleListUiState
    data class Success(
        val articles: List<Article>,
        val isLoadingMore: Boolean = false,
        val paginationError: String? = null
    ) : ArticleListUiState
    data class Error(val message: String) : ArticleListUiState
}

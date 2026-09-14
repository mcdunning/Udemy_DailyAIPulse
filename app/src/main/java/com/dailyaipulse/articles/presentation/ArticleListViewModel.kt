package com.dailyaipulse.articles.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyaipulse.articles.data.ArticleData
import com.dailyaipulse.articles.data.ArticleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArticleListViewModel @Inject constructor(
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArticleListUiState>(ArticleListUiState.Loading)
    val uiState: StateFlow<ArticleListUiState> = _uiState.asStateFlow()

    private var currentPage = 1

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            emit(ArticleListUiState.Loading)
            try {
                val articles = articleRepository.getTopHeadlines(page = currentPage).map { it.toArticle() }
                emit(ArticleListUiState.Success(articles = articles))
            } catch (e: Exception) {
                emit(ArticleListUiState.Error(message = e.message ?: "Unknown error"))
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state !is ArticleListUiState.Success || state.isLoadingMore) return
        // isLoadingMore must be set synchronously (before launching), not inside the
        // coroutine body: viewModelScope.launch defers actual execution until the
        // dispatcher runs it, so a second synchronous call to loadNextPage() before
        // that would still see the old (isLoadingMore = false) state and re-enter.
        val loadingState = state.copy(isLoadingMore = true, paginationError = null)
        emit(loadingState)
        viewModelScope.launch {
            val nextPage = currentPage + 1
            try {
                val nextArticles = articleRepository.getTopHeadlines(page = nextPage).map { it.toArticle() }
                currentPage = nextPage
                emit(loadingState.copy(articles = loadingState.articles + nextArticles, isLoadingMore = false))
            } catch (e: Exception) {
                emit(loadingState.copy(isLoadingMore = false, paginationError = e.message ?: "Failed to load more"))
            }
        }
    }

    private fun emit(newState: ArticleListUiState) {
        Timber.d("ArticleListUiState emitted: $newState")
        _uiState.value = newState
    }

    private fun ArticleData.toArticle(): Article = Article(
        title = title,
        description = description,
        imageUrl = imageUrl,
        date = formatDisplayDate(date)
    )
}

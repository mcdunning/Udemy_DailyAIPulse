package com.dailyaipulse.sources.presentation

sealed interface SourceListUiState {
    data object Loading : SourceListUiState
    data class Success(val sources: List<Source>) : SourceListUiState
    data class Error(val message: String) : SourceListUiState
}

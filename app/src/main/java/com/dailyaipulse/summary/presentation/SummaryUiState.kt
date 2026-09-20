package com.dailyaipulse.summary.presentation

sealed interface SummaryUiState {
    data object Idle : SummaryUiState
    data object Loading : SummaryUiState
    data class Success(val text: String) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}

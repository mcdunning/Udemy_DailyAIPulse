package com.dailyaipulse.sources.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailyaipulse.core.network.toUserMessage
import com.dailyaipulse.sources.data.SourceData
import com.dailyaipulse.sources.data.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SourceListViewModel @Inject constructor(
    private val sourceRepository: SourceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SourceListUiState>(SourceListUiState.Loading)
    val uiState: StateFlow<SourceListUiState> = _uiState.asStateFlow()

    init {
        loadSources()
    }

    private fun loadSources() {
        viewModelScope.launch {
            emit(SourceListUiState.Loading)
            try {
                val sources = sourceRepository.getSources().map { it.toSource() }
                emit(SourceListUiState.Success(sources = sources))
            } catch (e: Exception) {
                emit(SourceListUiState.Error(message = e.toUserMessage()))
            }
        }
    }

    private fun emit(newState: SourceListUiState) {
        Timber.d("SourceListUiState emitted: $newState")
        _uiState.value = newState
    }

    private fun SourceData.toSource(): Source = Source(
        name = name,
        description = description
    )
}

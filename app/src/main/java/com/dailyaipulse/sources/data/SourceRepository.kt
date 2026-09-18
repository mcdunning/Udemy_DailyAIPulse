package com.dailyaipulse.sources.data

class SourceRepository(
    private val sourceApiService: SourceApiService
) {
    // This call can fail (network errors, non-2xx responses, timeouts, etc.).
    // Exceptions propagate to the caller (ViewModel) rather than being caught
    // here — the ViewModel is responsible for catching and translating them
    // into UI error state.
    suspend fun getSources(): List<SourceData> {
        return sourceApiService.getSources().sources
    }
}

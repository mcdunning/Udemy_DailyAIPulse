package com.dailyaipulse.summary.data

import retrofit2.http.Body
import retrofit2.http.POST

interface GeminiApiService {
    // Auth: the Gemini API key is attached automatically by GeminiApiKeyInterceptor
    // (see core/network) — no key param needed on this call.
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(@Body request: SummaryRequestData): SummaryResponseData
}

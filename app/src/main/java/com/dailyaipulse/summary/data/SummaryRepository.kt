package com.dailyaipulse.summary.data

class SummaryRepository(
    private val geminiApiService: GeminiApiService
) {
    suspend fun summarize(title: String, description: String?, content: String?): String {
        val prompt = buildPrompt(title, description, content)
        val request = SummaryRequestData(
            contents = listOf(SummaryRequestData.ContentData(parts = listOf(SummaryRequestData.PartData(prompt))))
        )
        val response = geminiApiService.generateContent(request)
        val summaryText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        // A 200 response with no candidate means Gemini's safety filters blocked the
        // content — not an HTTP error, so it must be checked explicitly here rather
        // than relying on an exception. Thrown so it flows through the same catch
        // block/toUserMessage() path as every other failure.
        return summaryText ?: throw IllegalStateException(
            "Summary blocked: ${response.promptFeedback?.blockReason ?: "unknown reason"}"
        )
    }

    private fun buildPrompt(title: String, description: String?, content: String?): String = buildString {
        appendLine("Summarize this news article in 2-3 sentences, using only the information given below. Do not add information that isn't stated here.")
        appendLine()
        appendLine("Title: $title")
        if (!description.isNullOrBlank()) appendLine("Description: $description")
        if (!content.isNullOrBlank()) appendLine("Content: $content")
    }
}

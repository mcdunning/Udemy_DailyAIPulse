package com.dailyaipulse.summary.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SummaryRequestData(val contents: List<ContentData>) {
    @JsonClass(generateAdapter = true)
    data class ContentData(val parts: List<PartData>)

    @JsonClass(generateAdapter = true)
    data class PartData(val text: String)
}

@JsonClass(generateAdapter = true)
data class SummaryResponseData(
    val candidates: List<CandidateData>?,
    val promptFeedback: PromptFeedbackData?
) {
    @JsonClass(generateAdapter = true)
    data class CandidateData(val content: SummaryRequestData.ContentData)

    @JsonClass(generateAdapter = true)
    data class PromptFeedbackData(val blockReason: String?)
}

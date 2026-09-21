package com.dailyaipulse.summary.data

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryRepositoryTest {

    private fun successResponse(text: String) = SummaryResponseData(
        candidates = listOf(
            SummaryResponseData.CandidateData(
                content = SummaryRequestData.ContentData(parts = listOf(SummaryRequestData.PartData(text)))
            )
        ),
        promptFeedback = null
    )

    @Test
    fun `summarize sends title, description, and content in the prompt and returns the generated text`() = runTest {
        val apiService = mockk<GeminiApiService>()
        val requestSlot = slot<SummaryRequestData>()
        coEvery { apiService.generateContent(capture(requestSlot)) } returns successResponse("A concise summary.")
        val repository = SummaryRepository(apiService)

        val result = repository.summarize(
            title = "Some Title",
            description = "Some description",
            content = "Some truncated content"
        )

        assertEquals("A concise summary.", result)
        val prompt = requestSlot.captured.contents.first().parts.first().text
        assertTrue(prompt.contains("Title: Some Title"))
        assertTrue(prompt.contains("Description: Some description"))
        assertTrue(prompt.contains("Content: Some truncated content"))
    }

    @Test
    fun `summarize omits blank description and content from the prompt`() = runTest {
        val apiService = mockk<GeminiApiService>()
        val requestSlot = slot<SummaryRequestData>()
        coEvery { apiService.generateContent(capture(requestSlot)) } returns successResponse("A concise summary.")
        val repository = SummaryRepository(apiService)

        repository.summarize(title = "Some Title", description = null, content = "")

        val prompt = requestSlot.captured.contents.first().parts.first().text
        assertTrue(prompt.contains("Title: Some Title"))
        assertFalse(prompt.contains("Description:"))
        assertFalse(prompt.contains("Content:"))
    }

    @Test
    fun `summarize omits whitespace-only description and content from the prompt`() = runTest {
        val apiService = mockk<GeminiApiService>()
        val requestSlot = slot<SummaryRequestData>()
        coEvery { apiService.generateContent(capture(requestSlot)) } returns successResponse("A concise summary.")
        val repository = SummaryRepository(apiService)

        repository.summarize(title = "Some Title", description = "   ", content = "\n\t ")

        val prompt = requestSlot.captured.contents.first().parts.first().text
        assertTrue(prompt.contains("Title: Some Title"))
        assertFalse(prompt.contains("Description:"))
        assertFalse(prompt.contains("Content:"))
    }

    @Test
    fun `summarize propagates the exact exception thrown by the API service, unwrapped`() = runTest {
        val apiService = mockk<GeminiApiService>()
        // A distinct type from the IllegalStateException the repository itself throws for
        // the "blocked" case below, so this test can't accidentally pass by matching that.
        val expected = IllegalArgumentException("network error")
        coEvery { apiService.generateContent(any()) } throws expected
        val repository = SummaryRepository(apiService)

        val actual = try {
            repository.summarize(title = "Some Title", description = null, content = null)
            null
        } catch (e: Exception) {
            e
        }

        assertSame(expected, actual)
    }

    @Test
    fun `summarize throws when Gemini blocks the content instead of returning a candidate`() = runTest {
        val apiService = mockk<GeminiApiService>()
        coEvery { apiService.generateContent(any()) } returns SummaryResponseData(
            candidates = null,
            promptFeedback = SummaryResponseData.PromptFeedbackData(blockReason = "SAFETY")
        )
        val repository = SummaryRepository(apiService)

        val exception = try {
            repository.summarize(title = "Some Title", description = null, content = null)
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertNotNull(exception)
        assertTrue(exception!!.message!!.contains("SAFETY"))
    }
}

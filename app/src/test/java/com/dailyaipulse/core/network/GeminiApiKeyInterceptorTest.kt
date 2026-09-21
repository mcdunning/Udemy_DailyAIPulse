package com.dailyaipulse.core.network

import com.dailyaipulse.BuildConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiApiKeyInterceptorTest {

    @Test
    fun `attaches GEMINI_API_KEY as x-goog-api-key header to the outgoing request`() {
        val originalRequest = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models").build()
        val chain = mockk<Interceptor.Chain>()
        val requestSlot = slot<Request>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(requestSlot)) } returns fakeResponse(originalRequest)

        GeminiApiKeyInterceptor().intercept(chain)

        assertEquals(BuildConfig.GEMINI_API_KEY, requestSlot.captured.header("x-goog-api-key"))
    }

    private fun fakeResponse(request: Request) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()
}

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

class NewsApiKeyInterceptorTest {

    @Test
    fun `attaches NEWS_API_KEY as X-Api-Key header to the outgoing request`() {
        val originalRequest = Request.Builder().url("https://newsapi.org/v2/top-headlines").build()
        val chain = mockk<Interceptor.Chain>()
        val requestSlot = slot<Request>()
        every { chain.request() } returns originalRequest
        every { chain.proceed(capture(requestSlot)) } returns fakeResponse(originalRequest)

        NewsApiKeyInterceptor().intercept(chain)

        assertEquals(BuildConfig.NEWS_API_KEY, requestSlot.captured.header("X-Api-Key"))
    }

    private fun fakeResponse(request: Request) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()
}

package com.dailyaipulse.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException

class NetworkErrorMapperTest {

    private fun httpException(code: Int, retryAfterSeconds: String? = null): HttpException {
        val responseBuilder = Response.Builder()
            .code(code)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://newsapi.org/v2/top-headlines").build())
        if (retryAfterSeconds != null) {
            responseBuilder.header("Retry-After", retryAfterSeconds)
        }
        val body = "".toResponseBody("application/json".toMediaType())
        val retrofitResponse = retrofit2.Response.error<Any>(body, responseBuilder.build())
        return HttpException(retrofitResponse)
    }

    @Test
    fun `maps 429 with Retry-After header to a specific wait-time message`() {
        val exception = httpException(code = 429, retryAfterSeconds = "120")

        assertEquals(
            "You've made too many requests. Please try again in 120 seconds.",
            exception.toUserMessage()
        )
    }

    @Test
    fun `maps 429 without Retry-After header to a generic rate-limit message`() {
        val exception = httpException(code = 429)

        assertEquals(
            "You've made too many requests. Please try again later.",
            exception.toUserMessage()
        )
    }

    @Test
    fun `maps a non-429 HTTP error to the generic message`() {
        val exception = httpException(code = 500)

        assertEquals("Something went wrong. Please try again.", exception.toUserMessage())
    }

    @Test
    fun `maps any other exception to the generic message`() {
        val exception = RuntimeException("some internal detail that shouldn't reach the user")

        assertEquals("Something went wrong. Please try again.", exception.toUserMessage())
    }
}

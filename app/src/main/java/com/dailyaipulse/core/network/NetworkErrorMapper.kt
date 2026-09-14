package com.dailyaipulse.core.network

import retrofit2.HttpException
import timber.log.Timber

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val RETRY_AFTER_HEADER = "Retry-After"

// Never surfaces the raw exception message to the user (it's a developer-facing
// string); logs the full exception via Timber first so it's still debuggable.
fun Throwable.toUserMessage(): String {
    Timber.e(this, "Network call failed")
    return if (this is HttpException && code() == HTTP_TOO_MANY_REQUESTS) {
        val retryAfterSeconds = response()?.headers()?.get(RETRY_AFTER_HEADER)?.toIntOrNull()
        if (retryAfterSeconds != null) {
            "You've made too many requests. Please try again in $retryAfterSeconds seconds."
        } else {
            "You've made too many requests. Please try again later."
        }
    } else {
        "Something went wrong. Please try again."
    }
}

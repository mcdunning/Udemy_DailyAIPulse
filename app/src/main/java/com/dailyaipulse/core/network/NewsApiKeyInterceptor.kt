package com.dailyaipulse.core.network

import com.dailyaipulse.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

class NewsApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("X-Api-Key", BuildConfig.NEWS_API_KEY)
            .build()
        return chain.proceed(request)
    }
}

package com.dailyaipulse.core.di

import com.dailyaipulse.BuildConfig
import com.dailyaipulse.core.network.GeminiApiKeyInterceptor
import com.dailyaipulse.core.network.NewsApiKeyInterceptor
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(NewsApiKeyInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://newsapi.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    // @GeminiRetrofit qualifies BOTH providers below, not just the Retrofit one.
    // Without it on the OkHttpClient provider too, there would be two @Provides
    // functions both returning plain (unqualified) OkHttpClient, which Hilt
    // rejects at compile time as a duplicate binding for the same type.
    @Provides
    @Singleton
    @GeminiRetrofit
    fun provideGeminiOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(GeminiApiKeyInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            })
            .build()

    @Provides
    @Singleton
    @GeminiRetrofit
    fun provideGeminiRetrofit(@GeminiRetrofit geminiOkHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(geminiOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
}

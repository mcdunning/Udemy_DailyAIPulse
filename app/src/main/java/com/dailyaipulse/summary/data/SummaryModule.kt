package com.dailyaipulse.summary.data

import com.dailyaipulse.core.di.GeminiRetrofit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SummaryModule {

    @Provides
    @Singleton
    fun provideGeminiApiService(@GeminiRetrofit retrofit: Retrofit): GeminiApiService =
        retrofit.create(GeminiApiService::class.java)

    @Provides
    @Singleton
    fun provideSummaryRepository(geminiApiService: GeminiApiService): SummaryRepository =
        SummaryRepository(geminiApiService)
}

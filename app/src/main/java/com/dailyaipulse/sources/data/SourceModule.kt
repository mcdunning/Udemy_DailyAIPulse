package com.dailyaipulse.sources.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @Singleton
    fun provideSourceApiService(retrofit: Retrofit): SourceApiService =
        retrofit.create(SourceApiService::class.java)

    @Provides
    @Singleton
    fun provideSourceRepository(sourceApiService: SourceApiService): SourceRepository =
        SourceRepository(sourceApiService)
}

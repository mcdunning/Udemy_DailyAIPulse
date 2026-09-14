package com.dailyaipulse.articles.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArticleModule {

    @Provides
    @Singleton
    fun provideArticleApiService(retrofit: Retrofit): ArticleApiService =
        retrofit.create(ArticleApiService::class.java)

    @Provides
    @Singleton
    fun provideArticleRepository(articleApiService: ArticleApiService): ArticleRepository =
        ArticleRepository(articleApiService)
}

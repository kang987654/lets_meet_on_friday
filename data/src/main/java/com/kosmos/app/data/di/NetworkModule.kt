package com.kosmos.app.data.di

import com.kosmos.app.data.tool.WikipediaSearchToolImpl
import com.kosmos.app.domain.tool.WikipediaSearchTool
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolBindingModule {

    @Binds
    @Singleton
    abstract fun bindWikipediaSearchTool(
        impl: WikipediaSearchToolImpl
    ): WikipediaSearchTool
}

package com.kosmos.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LLMDispatcher

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @LLMDispatcher
    fun provideLLMDispatcher(): CoroutineDispatcher {
        // MediaPipe LlmInference 등 JNI/블로킹 연산을 위한 전용 제한 코루틴 풀
        return Dispatchers.IO.limitedParallelism(1)
    }
}

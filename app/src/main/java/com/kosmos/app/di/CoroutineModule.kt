package com.kosmos.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

// [WHY] @Target 을 생략하면 기본 타깃 집합에 PROPERTY 가 포함되어, 생성자 `val` 파라미터에
// 붙일 때 "파라미터에만 적용되지만 앞으로는 프로퍼티에도 적용된다"는 경고가 난다. Hilt 가
// 읽는 것은 @Provides 함수와 생성자 파라미터뿐이므로 그 둘로 좁혀 모호성을 없앤다.
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
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

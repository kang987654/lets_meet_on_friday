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
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 대용량 모델 다운로드 전용 OkHttpClient를 구분하는 한정자입니다.
 */
// [WHY] @Target 을 생략하면 기본 타깃 집합에 PROPERTY 가 포함되어, 생성자 `val` 파라미터에
// 붙일 때 "파라미터에만 적용되지만 앞으로는 프로퍼티에도 적용된다"는 경고가 난다. Hilt 가
// 읽는 것은 @Provides 함수와 생성자 파라미터뿐이므로 그 둘로 좁혀 모호성을 없앤다.
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class DownloadClient

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

    // [WHY] 공유 클라이언트의 read 30s / 무제한 callTimeout 설정은 위키·검색 같은 짧은 요청에
    // 맞춰져 있어 수 GB 스트리밍에는 부적합하다. 다운로드만 별도 클라이언트로 분리해
    // 짧은 요청의 타임아웃을 느슨하게 만들지 않는다. (ADR-006)
    @Provides
    @Singleton
    @DownloadClient
    fun provideDownloadOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // [WHY] callTimeout 0 = 무제한. 3.6GB 다운로드는 수십 분이 걸리므로
            // 전체 호출에 상한을 두면 정상 다운로드가 끊긴다.
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
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

package com.kosmos.app.data.embedder

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TextEmbedder as DomainTextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MediaPipeTextEmbedder]
 * MediaPipe Tasks API를 활용하여 온디바이스에서 텍스트 임베딩 벡터를 추출하는 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Data (Embedding)
 * - **Dependencies**: [Context], [TextEmbedder] (MediaPipe)
 *
 * ### Key Flow
 * 1. 첫 [embed] 호출 시 `assets/models/`의 `.tflite` 그래프를 지연 로드합니다.
 * 2. 로드와 추론 모두 [Dispatchers.Default]에서 수행합니다.
 * 3. 실패하면 다음 호출에서 다시 시도합니다.
 *
 * [WHY] 이전 구현은 생성자 `init` 블록에서 그래프를 로드하고 실패를 `catch(Throwable)` +
 * `Log.e` 로 넘겼다. 그러면 두 가지가 함께 잘못된다 — (1) 첫 주입 시 호출 스레드(메인일 수
 * 있음)가 그래프 로드만큼 블로킹되고, (2) **한 번 실패하면 필드가 영구히 null 로 남아**
 * 재시도 경로가 없다. 임베더가 죽으면 `SaveKnowledgeUseCase` 가 폴백 없이 실패하므로
 * 메모리 저장이 앱 재시작 전까지 통째로 막힌다. `.tflite` 는 assets 에 포함돼 있어 파일
 * 부재로 실패할 일이 없고, 남는 실패 원인은 저사양 기기의 메모리 압박처럼 **일시적**이다.
 * 그래서 실패를 기억하지 않는 지연 초기화가 실질적인 수정이다.
 */
@Singleton
class MediaPipeTextEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DomainTextEmbedder {

    @Volatile
    private var embedder: TextEmbedder? = null

    // [WHY] 첫 호출이 동시에 여러 개 들어와도 그래프를 한 번만 로드하도록 직렬화한다.
    // 코루틴 안에서 블로킹 `synchronized` 를 쓰지 않기 위해 Mutex 를 쓴다.
    private val initMutex = Mutex()

    override suspend fun embed(text: String): AppResult<FloatArray> = withContext(Dispatchers.Default) {
        val instance = obtainEmbedder()
            ?: return@withContext AppResult.Failure(
                AppError.SearchError("임베딩 모델을 불러오지 못했습니다: $lastInitError")
            )

        try {
            val result = instance.embed(text)
            val embedding = result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
            if (embedding != null) {
                AppResult.Success(embedding)
            } else {
                AppResult.Failure(AppError.SearchError("Embedding result is null or empty"))
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppResult.Failure(AppError.SearchError("Failed to embed text: ${e.message}"))
        }
    }

    /**
     * [WHY] 실패 시 `embedder` 를 null 로 남긴다 — 다음 호출이 다시 시도할 수 있어야 한다.
     * 실패 원인만 [lastInitError] 에 남겨 오류 메시지에 실어 보낸다(이전에는 "not
     * initialized" 라는 결과만 남고 원인이 사라졌다).
     */
    private suspend fun obtainEmbedder(): TextEmbedder? {
        embedder?.let { return it }
        return initMutex.withLock {
            embedder ?: createEmbedder()?.also { embedder = it }
        }
    }

    @Volatile
    private var lastInitError: String = "원인 미확인"

    private fun createEmbedder(): TextEmbedder? = try {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET_PATH)
            .build()
        val options = TextEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()
        TextEmbedder.createFromOptions(context, options).also {
            Log.d(TAG, "TextEmbedder initialized successfully")
        }
    } catch (e: Throwable) {
        lastInitError = e.message ?: e::class.java.simpleName
        Log.e(TAG, "Failed to initialize TextEmbedder (will retry on next call)", e)
        null
    }

    private companion object {
        const val TAG = "MediaPipeTextEmbedder"
        const val MODEL_ASSET_PATH = "models/universal_sentence_encoder.tflite"
    }
}

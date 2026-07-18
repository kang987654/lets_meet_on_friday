package com.kosmos.app.data.embedder

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TextEmbedder as DomainTextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * 1. Hilt를 통해 애플리케이션 시작 시 싱글톤으로 초기화됩니다.
 * 2. `assets/models/` 경로에서 `.tflite` 모델 파일을 읽어와 옵션을 구성합니다.
 * 3. 텍스트 입력 시 `FloatArray` 형태의 임베딩 결과(AppResult)를 반환합니다.
 */
@Singleton
class MediaPipeTextEmbedder @Inject constructor(
    @ApplicationContext private val context: Context
) : DomainTextEmbedder {

    private var textEmbedder: TextEmbedder? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("models/universal_sentence_encoder.tflite")
                .build()

            val options = TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()

            textEmbedder = TextEmbedder.createFromOptions(context, options)
            Log.d("MediaPipeTextEmbedder", "TextEmbedder initialized successfully")
        } catch (e: Throwable) {
            Log.e("MediaPipeTextEmbedder", "Failed to initialize TextEmbedder", e)
        }
    }

    override fun embed(text: String): AppResult<FloatArray> {
        val embedder = textEmbedder ?: return AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError("TextEmbedder is not initialized"))
        
        return try {
            val result = embedder.embed(text)
            val embedding = result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
            if (embedding != null) {
                AppResult.Success(embedding)
            } else {
                AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError("Embedding result is null or empty"))
            }
        } catch (e: Throwable) {
            AppResult.Failure(com.kosmos.app.core.common.AppError.SearchError("Failed to embed text: ${e.message}"))
        }
    }
}

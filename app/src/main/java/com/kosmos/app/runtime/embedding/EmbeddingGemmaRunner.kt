package com.kosmos.app.runtime.embedding

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.kosmos.app.BuildConfig
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.library.EmbedderProvider
import com.kosmos.app.domain.library.TextEmbedder
import com.kosmos.app.domain.library.normalized
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [EmbeddingGemmaRunner]
 * EmbeddingGemma 2 740M 으로 글을 뜻 벡터로 바꿉니다 (0.41.2 M2) — LiteRT-LM `EmbeddingEngine`.
 *
 * ### Architecture Context
 * - **Layer**: Runtime (Embedding) — 도메인 [TextEmbedder] 구현. 모델은 선택 설치라 파일이 없으면 [installed] 가 false 이고 호출하는 쪽이 바이그램만 쓴다.
 * - 채팅 엔진(GemmaModelRunner)과 별개 엔진이라 채팅 KV 와 무관하다.
 *
 * ### Key Flow
 * 1. 첫 호출에 엔진을 올린다(뮤텍스 — 엔진 하나를 순서대로 쓴다). 2. 묶음을 `computeEmbeddingBatch` 로, 결과는 L2 정규화.
 * 3. [release] — 앱이 백그라운드로 가면(KosmosApp onStop·onTrimMemory) 내린다. 다음 호출이 다시 올린다.
 *
 * [WHY] 백그라운드에서 올리지 않는다(AGENTS §2-⑥) — 호출하는 쪽(검색 툴·사용자가 시작한 벡터 만들기)이 전경에서만 부른다.
 * [WHY] 기본 백엔드는 [EmbeddingBackend.DEFAULT] — 휴대폰 실측(0.41.2 M2)으로 정한다. 디버그 빌드는 앱 외부 파일 폴더의
 * [EmbeddingBackend.DEBUG_FILE_NAME] 으로 바꾼다.
 */
@Singleton
class EmbeddingGemmaRunner @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TextEmbedder, EmbedderProvider {

    override val modelId: String = Constants.EMBEDDING_MODEL_ID

    /** 모델 파일이 있을 때만 자기 자신 — 없으면 검색·벡터 만들기가 바이그램으로 남는다. */
    override fun current(): TextEmbedder? = takeIf { installed }

    private val mutex = Mutex()
    @Volatile private var engine: EmbeddingEngine? = null
    private var backend: EmbeddingBackend = EmbeddingBackend.DEFAULT

    /** 설치된 모델 파일 — 외부(adb push) 먼저, 다음 내부(앱 다운로드). 없으면 null. */
    fun modelFile(): File? =
        listOfNotNull(context.getExternalFilesDir(Constants.EMBEDDING_DIR_NAME), File(context.filesDir, Constants.EMBEDDING_DIR_NAME))
            .map { File(it, Constants.EMBEDDING_MODEL_FILENAME) }
            .firstOrNull { it.isFile }

    val installed: Boolean get() = modelFile() != null

    override suspend fun embedQuery(query: String): FloatArray? = embed(listOf(query))?.firstOrNull()

    override suspend fun embedDocuments(inputs: List<String>): List<FloatArray>? = embed(inputs)

    private suspend fun embed(inputs: List<String>): List<FloatArray>? {
        if (inputs.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = engine ?: load() ?: return@withLock null
                val start = System.nanoTime()
                runCatching {
                    // [WHY] 입력이 모델 길이를 넘으면 실패할 수 있다(Kotlin API 에는 파이썬의 TRUNCATE 옵션이 없다) — 글자로 미리 자른다.
                    current.computeEmbeddingBatch(inputs.map { listOf(InputData.Text(it.take(MAX_INPUT_CHARS))) }, OPTIONS)
                        .map { it.embedding.normalized() }
                }.onSuccess {
                    if (BuildConfig.DEBUG) {
                        val ms = (System.nanoTime() - start) / 1_000_000
                        Log.d(TAG, "embed: n=${inputs.size}, ${ms}ms, ${ms / inputs.size}ms/개, backend=$backend")
                    }
                }.onFailure { Log.e(TAG, "임베딩 실패(n=${inputs.size})", it) }.getOrNull()
            }
        }
    }

    private fun load(): EmbeddingEngine? {
        val file = modelFile() ?: return null
        backend = EmbeddingBackend.resolve(BuildConfig.DEBUG, context.getExternalFilesDir(null))
        val loaded = runCatching {
            val start = System.nanoTime()
            EmbeddingEngine(
                EmbeddingEngineConfig(
                    modelPath = file.absolutePath,
                    backend = if (backend == EmbeddingBackend.GPU) Backend.GPU() else Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath,
                    maxInputLength = MAX_INPUT_TOKENS
                )
            ).also {
                it.initialize()
                Log.d(TAG, "embedding engine ready: backend=$backend, ${(System.nanoTime() - start) / 1_000_000}ms")
            }
        }.onFailure { Log.e(TAG, "임베딩 엔진 초기화 실패(backend=$backend)", it) }.getOrNull()
        engine = loaded
        return loaded
    }

    /** 엔진을 내린다 — 앱 백그라운드. 진행 중인 호출이 있으면 그 뒤에 닫힌다. */
    suspend fun release() {
        mutex.withLock {
            engine?.let { runCatching { it.close() } }
            engine = null
        }
    }

    private companion object {
        const val TAG = "EmbeddingGemmaRunner"

        /** 조각 본문 상한(LibraryChunker 500자) + 제목 — 한국어는 글자당 1토큰 안팎이라 512 토큰 안에 든다. */
        const val MAX_INPUT_CHARS = 700
        const val MAX_INPUT_TOKENS = 512
        val OPTIONS = EmbeddingOptions(normalize = true)
    }
}

/**
 * 임베딩 엔진 백엔드 — 휴대폰 실측(0.41.2 M2)으로 기본값을 정한다.
 *
 * [WHY] NPU 는 넣지 않는다 — Qualcomm NPU 는 앱에 QNN 라이브러리를 함께 실어야 하고(공식 안내에 절차 없음) 모델 카드상 S26 Ultra 텍스트 임베딩이
 * CPU 27.1ms · GPU 25.9ms 로 이미 짧다. CPU 면 채팅 GPU 와 다투지 않는다.
 */
enum class EmbeddingBackend {
    CPU,
    GPU;

    companion object {
        val DEFAULT: EmbeddingBackend = CPU
        const val DEBUG_FILE_NAME = "debug_embed.txt"

        fun parse(text: String?): EmbeddingBackend? = text?.trim()?.uppercase()?.let { key -> entries.firstOrNull { it.name == key } }

        fun resolve(debug: Boolean, debugDir: File?): EmbeddingBackend {
            if (!debug || debugDir == null) return DEFAULT
            val text = runCatching { File(debugDir, DEBUG_FILE_NAME).takeIf { it.isFile }?.readText() }.getOrNull()
            return parse(text) ?: DEFAULT
        }
    }
}

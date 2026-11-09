package com.kosmos.app.runtime.embedding

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.ActivationDataType
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
        val primary = EmbeddingBackend.resolve(BuildConfig.DEBUG, context.getExternalFilesDir(null))
        // [WHY] GPU 가 안 뜨면 CPU 로 한 번 더(0.41.6 M3) — 채팅 엔진과 같은 결. 없으면 GPU 메모리가 모자라거나 에뮬레이터일 때 뜻 검색이 조용히 꺼졌다.
        val opened = EmbeddingBackend.openWithFallback(primary) { candidate -> open(file, candidate) } ?: return null
        backend = opened.first
        engine = opened.second
        return opened.second
    }

    private fun open(file: File, candidate: EmbeddingBackend): EmbeddingEngine? = runCatching {
        val start = System.nanoTime()
        EmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = file.absolutePath,
                backend = if (candidate == EmbeddingBackend.GPU) Backend.GPU() else Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
                maxInputLength = MAX_INPUT_TOKENS,
                // [WHY] GPU 는 FP32 를 명시한다(0.41.7 M6) — 미지정이면 FP16 으로 돌아 검색 품질이 조용히 깎였다(exp83, 모델 카드도 fp16 금지).
                // 채팅 엔진과 같은 결(ADR-021). CPU 는 기본값(이미 FP32 수준 — exp83 CPU 와 FP32 코사인 평균 0.9995).
                activationDataType = activationFor(candidate)
            )
        ).also {
            it.initialize()
            Log.d(TAG, "embedding engine ready: backend=$candidate, ${(System.nanoTime() - start) / 1_000_000}ms")
        }
    }.onFailure { Log.e(TAG, "임베딩 엔진 초기화 실패(backend=$candidate)", it) }.getOrNull()

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
 * 임베딩 엔진의 활성 정밀도 — GPU 만 FP32 를 명시하고 CPU 는 기본값(0.41.7 M6).
 *
 * [WHY] GPU 를 미지정으로 두면 FP16 이었다 — exp83: 미지정·FP16 @1 61/95, FP32 64/95, CPU 63/95(EmbeddingGemma 2 카드도 fp16 금지).
 * 바꾸면 벡터 좌표가 달라지므로 [Constants.EMBEDDING_MODEL_ID] 도 함께 바꿨다(옛 벡터는 다음 저장 때 지워진다).
 */
internal fun activationFor(candidate: EmbeddingBackend): ActivationDataType? =
    if (candidate == EmbeddingBackend.GPU) ActivationDataType.FLOAT32 else null

/**
 * 임베딩 엔진 백엔드 — 휴대폰 실측(0.41.2 M2)으로 기본값을 정한다.
 *
 * [WHY] 기본은 GPU(0.41.2 Q3 사용자 결정) — S25 Ultra 실측 민법 1,136조각에서 GPU 43ms/개·엔진 2.5초, CPU 83ms/개·7.5초.
 * 채팅 GPU 와의 겹침은 LibraryEmbedder 가 채팅 중 멈추고 늦게 이어 가는 것으로 피한다.
 * [WHY] NPU 는 넣지 않는다 — Qualcomm NPU 는 앱에 QNN 라이브러리를 함께 실어야 하고 LiteRT-LM 공식 예제에도 쓰임이 없다(별도 조사 회차 몫).
 */
enum class EmbeddingBackend {
    CPU,
    GPU;

    companion object {
        val DEFAULT: EmbeddingBackend = GPU
        const val DEBUG_FILE_NAME = "debug_embed.txt"

        fun parse(text: String?): EmbeddingBackend? = text?.trim()?.uppercase()?.let { key -> entries.firstOrNull { it.name == key } }

        fun resolve(debug: Boolean, debugDir: File?): EmbeddingBackend {
            if (!debug || debugDir == null) return DEFAULT
            val text = runCatching { File(debugDir, DEBUG_FILE_NAME).takeIf { it.isFile }?.readText() }.getOrNull()
            return parse(text) ?: DEFAULT
        }

        /** [primary] 로 열고, GPU 가 실패하면 CPU 로 한 번 더 — 연 백엔드와 엔진, 둘 다 실패면 null. */
        fun <T : Any> openWithFallback(primary: EmbeddingBackend, open: (EmbeddingBackend) -> T?): Pair<EmbeddingBackend, T>? {
            open(primary)?.let { return primary to it }
            if (primary == CPU) return null
            return open(CPU)?.let { CPU to it }
        }
    }
}

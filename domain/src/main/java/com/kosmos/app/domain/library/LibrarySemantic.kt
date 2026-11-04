package com.kosmos.app.domain.library

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * [TextEmbedder]
 * 글을 뜻 좌표(벡터)로 바꾸는 엔진의 도메인 경계입니다 (0.41.2 M1) — 구현은 앱 런타임(EmbeddingGemma 2, LiteRT-LM `EmbeddingEngine`).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Library) — 임베딩 모델은 선택 설치라, 설치되지 않았으면 이 인터페이스를 주입받는 쪽이 바이그램 검색만 쓴다.
 *
 * ### Key Flow
 * 1. [embedQuery] — 검색어 하나([LibraryEmbedding.queryInput] 로 감싼다). 2. [embedDocuments] — 조각 묶음([LibraryEmbedding.documentInput]).
 * 3. 돌려주는 벡터는 L2 정규화돼 있다 — 내적이 곧 코사인이다. 실패하면 null(검색은 바이그램으로 계속).
 */
interface TextEmbedder {
    /** 벡터를 만든 모델 — 저장된 벡터와 다르면 그 벡터는 버린다(모델 교체). */
    val modelId: String

    suspend fun embedQuery(query: String): FloatArray?

    suspend fun embedDocuments(inputs: List<String>): List<FloatArray>?
}

/**
 * [EmbedderProvider]
 * 지금 쓸 수 있는 임베더 — 임베딩 모델이 설치되지 않았으면 null(호출하는 쪽은 바이그램만, 0.41.2).
 *
 * [WHY] 모델은 선택 설치라 [TextEmbedder] 를 바로 주입하면 "없음"을 표현할 수 없다. 설치 여부를 매번 물어 받기·지우기에 바로 따른다.
 */
fun interface EmbedderProvider {
    fun current(): TextEmbedder?
}

/**
 * [LibraryEmbedding]
 * EmbeddingGemma 2 의 입력 형식과 문서 조각의 임베딩 글자입니다.
 *
 * [WHY] 모델 카드의 검색 과제 형식을 따른다 — 질의는 `task: search result | query:`, 문서는 `title: … | text: …`. 실험실(exp76·exp78)과 같은 형식이어야
 * 그 회수율이 앱에 그대로 온다. 제목은 문서 이름 + 조각 머리말의 마지막 단계(예 "민법 제750조(불법행위의 내용)").
 */
object LibraryEmbedding {
    fun queryInput(query: String): String = "task: search result | query: ${query.trim()}"

    fun documentInput(documentName: String, chunk: LibraryChunk): String {
        val section = chunk.sectionPath.split(" > ").last().trim()
        val title = listOf(documentName.trim(), section).filter { it.isNotEmpty() }.distinct().joinToString(" ")
        return "title: $title | text: ${chunk.text}"
    }
}

/**
 * [Int8Vector]
 * 벡터 하나를 int8 로 줄여 저장한 값 — 원소마다 `value * scale` 이 원래 값에 가깝다.
 *
 * [WHY] float32(768차원 3KB/조각) 대신 int8 + 척도(약 0.77KB) — 회수율이 float 와 같았다(exp78 ④: 번호·용어·바꿔 말함 @1·@3 동일 또는 +1).
 * 책 한 권(조각 약 3,600) 11MB → 약 2.7MB.
 */
class Int8Vector(val values: ByteArray, val scale: Float) {
    val dimension: Int get() = values.size

    /** 정규화된 질의 벡터와의 내적 — 차원이 다르면 0. */
    fun dot(query: FloatArray): Float {
        if (query.size != values.size) return 0f
        var sum = 0f
        for (i in values.indices) sum += values[i] * query[i]
        return sum * scale
    }

    /** 저장 형식 — 앞 4바이트 척도(float, 빅 엔디언) + 원소 바이트. */
    fun toBytes(): ByteArray {
        val bits = scale.toRawBits()
        val out = ByteArray(4 + values.size)
        out[0] = (bits ushr 24).toByte(); out[1] = (bits ushr 16).toByte(); out[2] = (bits ushr 8).toByte(); out[3] = bits.toByte()
        values.copyInto(out, 4)
        return out
    }

    companion object {
        /** 벡터마다 최대 절댓값을 127 로 맞춘다. 모두 0 이면 척도 0. */
        fun quantize(vector: FloatArray): Int8Vector {
            val max = vector.maxOfOrNull { abs(it) } ?: 0f
            if (max == 0f) return Int8Vector(ByteArray(vector.size), 0f)
            val scale = max / 127f
            return Int8Vector(ByteArray(vector.size) { (vector[it] / scale).roundToInt().coerceIn(-127, 127).toByte() }, scale)
        }

        /** [toBytes] 의 역 — 4바이트보다 짧으면 null. */
        fun fromBytes(bytes: ByteArray): Int8Vector? {
            if (bytes.size < 4) return null
            val bits = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            return Int8Vector(bytes.copyOfRange(4, bytes.size), Float.fromBits(bits))
        }
    }
}

/** L2 정규화 — 길이 0 이면 그대로. */
fun FloatArray.normalized(): FloatArray {
    var sum = 0.0
    for (x in this) sum += x * x
    val norm = sqrt(sum).toFloat()
    return if (norm == 0f) copyOf() else FloatArray(size) { this[it] / norm }
}

/**
 * [LibraryFusion]
 * 뜻(dense) 점수와 바이그램(sparse) 점수를 합친 순위입니다.
 *
 * [WHY] `dense + w × (sparse / 이 질의의 sparse 최고점)` — RRF·sparse 비중 큰 결합은 바꿔 말한 질문의 dense 이득을 지웠다(exp76b). 질의는 모델이 문서 용어로
 * 바꾼 검색어다(exp78b — 원문을 섞으면 나아지지 않음). sparse 는 색인 후보에만 있고 나머지는 0 이다. [SPARSE_WEIGHT] 는 0.41.2 M4 에서 질문을 늘려 확정.
 */
object LibraryFusion {
    /** exp78b: w0.05 바꿔 말함 @3 6/7·용어 9/10, w0.1 4/7·10/10 — 표본이 작아 방향만(0.41.2 M4 에서 확정). */
    const val SPARSE_WEIGHT = 0.05

    /** 키(조각 id)별 결합 점수 내림차순 — 같은 점수면 키 순(결정적). */
    fun <K : Comparable<K>> fuse(dense: Map<K, Double>, sparse: Map<K, Double>, weight: Double = SPARSE_WEIGHT): List<Pair<K, Double>> {
        val top = sparse.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        return (dense.keys + sparse.keys)
            .map { k -> k to ((dense[k] ?: 0.0) + weight * (sparse[k] ?: 0.0) / top) }
            .sortedWith(compareByDescending<Pair<K, Double>> { it.second }.thenBy { it.first })
    }
}

/**
 * 보관함의 뜻 검색 준비 상태 — 화면("준비 안 된 문서 n개")과 검색 경로 선택이 같이 쓴다.
 *
 * @param modelId 설치된 임베딩 모델 — null 이면 미설치
 * @param readyChunks 색인이 끝난(READY) 문서의 조각 수
 * @param embeddedChunks 그중 지금 모델로 만든 벡터가 있는 조각 수
 * @param pendingDocuments 벡터가 모자란 READY 문서 수
 */
data class SemanticStatus(
    val modelId: String?,
    val readyChunks: Int,
    val embeddedChunks: Int,
    val pendingDocuments: Int
) {
    /**
     * 결합 검색을 써도 되는가 — 모델이 있고 READY 조각 **전부**에 벡터가 있을 때만.
     *
     * [WHY] 일부만 벡터가 있으면 벡터 있는 조각만 dense 점수를 받아 순위가 왜곡된다 — 다 될 때까지 바이그램(사용자 결정 2026-11-04).
     */
    val semanticReady: Boolean get() = modelId != null && readyChunks > 0 && embeddedChunks >= readyChunks
}

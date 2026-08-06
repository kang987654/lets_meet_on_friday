package com.kosmos.app.core.common

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [FloatBytes]
 * 임베딩 벡터를 DB BLOB 으로 저장하기 위한 바이트 인코딩입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common) — Pure Kotlin, `:data`의 저장/조회와 Room 마이그레이션에서 사용
 * - **Dependencies**: 없음 (`java.nio` 만)
 *
 * ### Key Flow
 * 1. 저장 시 [encode]로 `FloatArray`를 BLOB 바이트로 바꿉니다.
 * 2. 조회 시 [decode]로 되돌립니다.
 *
 * [WHY] 이전에는 임베딩을 콤마 구분 문자열로 저장했다. `searchByVector`가 1000행을
 * `split(",")` + `toFloatOrNull` 로 파싱해 RAG 질의 한 번에 30만 개 남짓 단기 할당이
 * 발생했고, `toDomain()`이 같은 문자열을 다시 파싱하는 이중 파싱도 있었다. 정밀도 손실은
 * 없었지만(`Float.toString`은 라운드트립 보장) `mapNotNull`이 파싱 실패 항목을 조용히 버려
 * 길이 비교에서 탈락한 노트가 벡터 검색에서 영구히 안 보이는 실패 모드가 있었다.
 *
 * [WHY] 바이트 순서를 **명시적으로 little-endian 으로 고정**한다. `ByteBuffer` 기본값은
 * big-endian 이므로, 저장 형식이 플랫폼 기본값을 따라가게 두면 나중에 조용히 깨진다.
 * 마이그레이션과 런타임이 반드시 같은 함수를 써야 하므로 `:core`에 둔다.
 */
object FloatBytes {

    private const val BYTES_PER_FLOAT = 4

    fun encode(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /**
     * [WHY] 길이가 4의 배수가 아니면 손상된 BLOB 이므로 빈 배열을 돌려준다. 예외를 던지면
     * 한 행이 깨졌을 때 벡터 검색 전체가 실패한다 — 호출부는 빈 배열을 "임베딩 없음"과
     * 같게 취급해 그 행만 건너뛴다.
     */
    fun decode(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty() || bytes.size % BYTES_PER_FLOAT != 0) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / BYTES_PER_FLOAT) { buffer.getFloat() }
    }
}

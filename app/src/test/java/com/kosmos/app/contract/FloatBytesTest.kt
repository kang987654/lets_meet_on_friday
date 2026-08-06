package com.kosmos.app.contract

import com.kosmos.app.core.common.FloatBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FloatBytesTest]
 * 임베딩 BLOB 인코딩을 검증합니다.
 *
 * [WHY] `:core`에 test source set이 없으므로 `SqlLikeEscapeTest`와 같은 방식으로
 * `:app` 테스트에서 검증한다.
 */
class FloatBytesTest {

    @Test
    fun `왕복하면 같은 값이 나온다`() {
        val original = floatArrayOf(0f, 1f, -1f, 0.125f, -3.75f, 12345.678f)
        assertArrayEquals(original, FloatBytes.decode(FloatBytes.encode(original)), 0f)
    }

    @Test
    fun `빈 배열도 왕복한다`() {
        assertEquals(0, FloatBytes.encode(FloatArray(0)).size)
        assertEquals(0, FloatBytes.decode(ByteArray(0)).size)
    }

    @Test
    fun `바이트 순서가 little-endian 으로 고정돼 있다`() {
        // [WHY] ByteBuffer 기본값은 big-endian 이다. 저장 형식이 플랫폼 기본값을 따라가면
        // 나중에 조용히 깨지므로, 알려진 값의 바이트 시퀀스를 리터럴로 못 박는다.
        // 1.0f = 0x3F800000 → little-endian 이면 00 00 80 3F
        val encoded = FloatBytes.encode(floatArrayOf(1.0f))
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), encoded)
    }

    @Test
    fun `길이가 4의 배수가 아니면 빈 배열이다`() {
        // [WHY] 예외를 던지면 한 행이 깨졌을 때 벡터 검색 전체가 실패한다.
        assertEquals(0, FloatBytes.decode(byteArrayOf(1, 2, 3)).size)
        assertEquals(0, FloatBytes.decode(byteArrayOf(1, 2, 3, 4, 5)).size)
    }

    @Test
    fun `벡터 길이가 보존된다`() {
        val original = FloatArray(384) { it * 0.01f }
        val decoded = FloatBytes.decode(FloatBytes.encode(original))
        // [WHY] 길이가 어긋나면 searchByVector 의 크기 비교에서 탈락해 노트가 조용히
        // 벡터 검색에서 사라진다.
        assertEquals(384, decoded.size)
        assertArrayEquals(original, decoded, 0f)
    }
}

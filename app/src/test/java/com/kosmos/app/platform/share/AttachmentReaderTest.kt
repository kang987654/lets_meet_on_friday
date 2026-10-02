package com.kosmos.app.platform.share

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/**
 * [AttachmentReaderTest]
 * 입력바 첨부 분기 (0.35.0 M4) — 문서 파일은 여기서 읽지 않고 비동기 추출로 넘기고, 글자가 아닌 파일은 거부하며,
 * 텍스트는 예전과 같이 동기로 앞 300자. 예전에는 xlsx·zip 바이트가 깨진 문자열로 모델에 들어갔다.
 */
@RunWith(RobolectricTestRunner::class)
class AttachmentReaderTest {

    private val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
    private val reader = AttachmentReader(resolver)

    private fun register(name: String, bytes: ByteArray): Uri =
        Uri.parse("content://test.docs/$name").also { Shadows.shadowOf(resolver).registerInputStream(it, bytes.inputStream()) }

    @Test
    fun `문서 파일은 읽지 않고 비동기 추출로 넘긴다`() {
        listOf("가계부.xlsx", "회의록.docx", "공문.hwpx", "보고서.pdf").forEach { name ->
            val result = reader.read(register(name, "PK".toByteArray()))
            assertTrue(name, result.documentFile)
            assertNull(name, result.input)
        }
    }

    @Test
    fun `글자가 아닌 파일은 거부한다`() {
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00, 0x08, 0x00) + ByteArray(64) { (it * 7).toByte() }
        val result = reader.read(register("묶음.zip", zipBytes))
        assertTrue(result.rejected)
        assertNull(result.input)
    }

    @Test
    fun `텍스트 파일은 예전처럼 동기로 읽는다`() {
        val result = reader.read(register("메모.txt", "장보기: 우유, 계란".toByteArray()))
        assertFalse(result.documentFile || result.rejected)
        assertEquals("장보기: 우유, 계란", (result.input as SharedInput.Document).textContent)
    }

    @Test
    fun `바이너리 판정`() {
        assertTrue(AttachmentReader.looksBinary("abc\u0000def"))
        assertTrue(AttachmentReader.looksBinary("��ab"))
        assertFalse(AttachmentReader.looksBinary("한글 텍스트 � 하나쯤은 괜찮다 — 열 글자 넘게 정상"))
        assertFalse(AttachmentReader.looksBinary(""))
    }
}

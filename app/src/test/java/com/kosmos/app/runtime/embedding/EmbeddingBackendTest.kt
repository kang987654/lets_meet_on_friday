package com.kosmos.app.runtime.embedding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [EmbeddingBackendTest]
 * 임베딩 엔진 백엔드 고르기(0.41.2 M2) — 휴대폰 실측은 디버그 파일로 CPU/GPU 를 바꿔 가며 한다. 릴리스는 파일을 읽지 않는다.
 */
class EmbeddingBackendTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun write(text: String) = File(temp.root, EmbeddingBackend.DEBUG_FILE_NAME).writeText(text)

    @Test
    fun `글자는 대소문자·공백을 무시하고 모르는 값은 null`() {
        assertEquals(EmbeddingBackend.GPU, EmbeddingBackend.parse(" gpu\n"))
        assertEquals(EmbeddingBackend.CPU, EmbeddingBackend.parse("CPU"))
        assertNull(EmbeddingBackend.parse("npu"))
        assertNull(EmbeddingBackend.parse(null))
    }

    @Test
    fun `디버그는 파일 값, 없거나 틀리면 기본값, 릴리스는 늘 기본값`() {
        assertEquals(EmbeddingBackend.DEFAULT, EmbeddingBackend.resolve(debug = true, debugDir = temp.root))
        write("gpu")
        assertEquals(EmbeddingBackend.GPU, EmbeddingBackend.resolve(debug = true, debugDir = temp.root))
        assertEquals(EmbeddingBackend.DEFAULT, EmbeddingBackend.resolve(debug = false, debugDir = temp.root))
        write("??")
        assertEquals(EmbeddingBackend.DEFAULT, EmbeddingBackend.resolve(debug = true, debugDir = temp.root))
        assertEquals(EmbeddingBackend.DEFAULT, EmbeddingBackend.resolve(debug = true, debugDir = null))
    }
}

package com.kosmos.app.runtime.embedding

import com.google.ai.edge.litertlm.ActivationDataType
import com.kosmos.app.core.common.Constants
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

    @Test
    fun `GPU 가 실패하면 CPU 로 한 번 더 열고 CPU 도 실패하면 null`() {
        val tried = mutableListOf<EmbeddingBackend>()
        val opened = EmbeddingBackend.openWithFallback(EmbeddingBackend.GPU) { tried += it; if (it == EmbeddingBackend.CPU) "cpu-engine" else null }
        assertEquals(EmbeddingBackend.CPU to "cpu-engine", opened)
        assertEquals(listOf(EmbeddingBackend.GPU, EmbeddingBackend.CPU), tried)

        assertNull(EmbeddingBackend.openWithFallback(EmbeddingBackend.GPU) { null as String? })
    }

    @Test
    fun `GPU 가 열리면 CPU 를 시도하지 않고 CPU 로 시작했으면 다시 시도하지 않는다`() {
        val tried = mutableListOf<EmbeddingBackend>()
        assertEquals(EmbeddingBackend.GPU to "gpu-engine", EmbeddingBackend.openWithFallback(EmbeddingBackend.GPU) { tried += it; "gpu-engine" })
        assertEquals(listOf(EmbeddingBackend.GPU), tried)

        tried.clear()
        assertNull(EmbeddingBackend.openWithFallback(EmbeddingBackend.CPU) { tried += it; null as String? })
        assertEquals(listOf(EmbeddingBackend.CPU), tried)
    }

    @Test
    fun `GPU 는 FP32 를 명시하고 CPU 는 기본값이며 모델 표지에 정밀도가 들어 있다(0·41·7 M6)`() {
        assertEquals(ActivationDataType.FLOAT32, activationFor(EmbeddingBackend.GPU))
        assertNull(activationFor(EmbeddingBackend.CPU))
        // 표지가 바뀌어야 옛(FP16) 벡터를 버린다.
        assertEquals("embeddinggemma-2-740m-fp32", Constants.EMBEDDING_MODEL_ID)
    }
}

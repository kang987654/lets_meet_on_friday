package com.kosmos.app.runtime.gemma

import com.google.ai.edge.litertlm.SpeculativeDecodingMode
import com.kosmos.app.domain.modelrunner.ChatPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [MtpModeTest]
 * 추측 디코딩(MTP) 모드가 GPU 엔진에서만 대화 설정에 실리는지, 디버그 덮어쓰기 파일을 어떻게 읽는지 못박습니다 (0.41.1 M1).
 *
 * [WHY] 휴대폰 실측은 디버그 파일로 모드를 바꿔 가며 한다 — 파일이 틀렸을 때 조용히 엉뚱한 모드로 재면 비교가 무너진다. CPU 엔진에 실리면
 * 검증하지 않은 경로(에뮬레이터·폴백)가 바뀐다.
 */
class MtpModeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val prompt = ChatPrompt(sessionId = "s", systemInstruction = "sys", history = emptyList(), currentInput = "안녕")

    @Test
    fun `모드는 런타임 설정의 같은 이름 값으로 바뀐다`() {
        assertEquals(SpeculativeDecodingMode.OFF, MtpMode.OFF.toConfig().mode)
        assertEquals(SpeculativeDecodingMode.ON, MtpMode.ON.toConfig().mode)
        assertEquals(SpeculativeDecodingMode.ADAPTIVE, MtpMode.ADAPTIVE.toConfig().mode)
    }

    @Test
    fun `릴리스 기본값은 ADAPTIVE 다(0·41·1 M2 휴대폰 실측, ADR-036)`() {
        assertEquals(MtpMode.ADAPTIVE, MtpMode.DEFAULT)
    }

    @Test
    fun `파일 글자는 대소문자·공백을 무시하고 모르는 값은 null`() {
        assertEquals(MtpMode.ON, MtpMode.parse(" on\n"))
        assertEquals(MtpMode.ADAPTIVE, MtpMode.parse("Adaptive"))
        assertNull(MtpMode.parse("fast"))
        assertNull(MtpMode.parse(""))
        assertNull(MtpMode.parse(null))
    }

    @Test
    fun `CPU 엔진은 모드를 싣지 않는다`() {
        writeDebug("on")
        assertNull(MtpMode.resolve(gpu = false, debug = true, debugDir = temp.root))
    }

    @Test
    fun `GPU 디버그 빌드는 파일 값을 쓰고 없거나 틀리면 기본값`() {
        assertEquals(MtpMode.DEFAULT, MtpMode.resolve(gpu = true, debug = true, debugDir = temp.root))
        writeDebug("adaptive")
        assertEquals(MtpMode.ADAPTIVE, MtpMode.resolve(gpu = true, debug = true, debugDir = temp.root))
        writeDebug("??")
        assertEquals(MtpMode.DEFAULT, MtpMode.resolve(gpu = true, debug = true, debugDir = temp.root))
        assertEquals(MtpMode.DEFAULT, MtpMode.resolve(gpu = true, debug = true, debugDir = null))
    }

    @Test
    fun `릴리스 빌드는 파일이 있어도 읽지 않는다`() {
        writeDebug("on")
        assertEquals(MtpMode.DEFAULT, MtpMode.resolve(gpu = true, debug = false, debugDir = temp.root))
    }

    @Test
    fun `채팅·oneShot 대화 설정에 같은 값이 실리고 넘기지 않으면 미지정`() {
        val on = MtpMode.ON.toConfig()
        assertEquals(on, chatConversationConfig(prompt, on).speculativeDecodingConfig)
        assertEquals(on, oneShotConversationConfig(prompt.copy(oneShot = true), on).speculativeDecodingConfig)
        assertNull(chatConversationConfig(prompt).speculativeDecodingConfig)
        assertNull(oneShotConversationConfig(prompt.copy(oneShot = true)).speculativeDecodingConfig)
    }

    @Test
    fun `대화 설정에 생각 모드 끔이 실리고 앞부분 미리 읽기는 넘길 때만 켜진다`() {
        // [WHY] 앞부분 미리 읽기는 앞부분을 대화 설정의 생각 모드로 렌더링한다 — send 와 같은 끔이 설정에도 있어야 한다(0.41.9 M2).
        val chat = chatConversationConfig(prompt)
        assertEquals(false, chat.thinkingConfig?.enableThinking)
        assertEquals(false, oneShotConversationConfig(prompt.copy(oneShot = true)).thinkingConfig?.enableThinking)
        assertEquals(false, chat.prefillPrefaceOnInit)
        assertEquals(true, chatConversationConfig(prompt, prefillPreface = true).prefillPrefaceOnInit)
    }

    private fun writeDebug(text: String) {
        File(temp.root, MtpMode.DEBUG_FILE_NAME).writeText(text)
    }
}

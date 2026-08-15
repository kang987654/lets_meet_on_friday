package com.kosmos.app.assistant.orchestrator

import com.kosmos.app.assistant.agent.KosmosAgent
import com.kosmos.app.assistant.context.ContextBuilder
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.agent.AgentResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.usecase.TranscribeAudioUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [VoiceTranscriptionFailureTest]
 * 전사가 실패했을 때 **아무 흔적도 남지 않던** 문제를 못박습니다.
 *
 * [WHY] 2026-08-12 실기기에서 음성이 계속 "응답을 만들지 못했어요" 로 끝났는데, 감사 로그에
 * **한 줄도 남지 않았다.** 이 경로가 `BaseAgent.handleErrorAndReturn`(감사 기록 담당)까지 가지
 * 않고 오케스트레이터에서 바로 반환하기 때문이다. 결과적으로 원인을 로그가 아니라 소스 대조로
 * 찾아야 했다. 실패한 기능이 진단 흔적조차 남기지 않으면 두 번째 조사가 첫 번째만큼 비싸진다.
 *
 * [WHY] 임시 파일 삭제도 함께 본다. `return` 이 삭제를 건너뛰어 마지막 녹음이 캐시에 남았다
 * (실측: `kosmos_audio_input.wav` 138KB 잔존). 마이크 녹음이 앱 캐시에 계속 남는 것은
 * 개인정보 문제이기도 하다.
 */
class VoiceTranscriptionFailureTest {

    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val contextBuilder: ContextBuilder = mockk(relaxed = true)
    private val agent: KosmosAgent = mockk(relaxed = true)
    private val transcribe: TranscribeAudioUseCase = mockk()

    private val auditErrors = mutableListOf<String>()
    private val savedMessages = mutableListOf<ChatMessage>()

    private val auditTrailService: AuditTrailService = mockk(relaxed = true)

    private fun orchestrator(): AssistantOrchestrator {
        coEvery { auditTrailService.logError(any(), any()) } answers {
            auditErrors += arg<String>(1)
        }
        coEvery { conversationRepository.save(any()) } answers {
            savedMessages += arg<ChatMessage>(0)
            AppResult.Success(Unit)
        }
        return AssistantOrchestrator(
            conversationRepository = conversationRepository,
            contextBuilder = contextBuilder,
            auditTrailService = auditTrailService,
            agent = agent,
            transcribeAudioUseCase = transcribe,
            // [WHY] relaxed — 이 테스트의 관심사는 전사 실패 경로이고, 경계 판정(null 반환)은
            // "미배정 저장" 폴백이라 기존 단언에 영향이 없다.
            episodeBoundaryManager = mockk(relaxed = true)
        )
    }

    private fun tempAudio(): File =
        File.createTempFile("kosmos_audio_input", ".wav").apply { writeBytes(ByteArray(64)) }

    @Test
    fun `전사 실패는 감사에 남고 사용자 메시지를 저장하지 않는다`() = runBlocking {
        val audio = tempAudio()
        coEvery { transcribe(any()) } returns AppResult.Failure(AppError.SttError("전사 결과가 비었습니다"))

        val result = orchestrator().processRequest(
            ChatRequest(sessionId = SESSION, message = "", audioFilePath = audio.absolutePath)
        )

        assertTrue("SttError 로 끝나야 한다", (result as AgentResult.Error).error is AppError.SttError)
        assertEquals("감사에 정확히 한 건 남아야 한다", 1, auditErrors.size)
        assertTrue("무엇이 실패했는지 알 수 있어야 한다", auditErrors.first().contains("음성 전사 실패"))
        assertTrue("빈 말풍선을 만들면 안 된다 (PRD EC3)", savedMessages.isEmpty())
    }

    @Test
    fun `전사 실패해도 임시 오디오 파일을 지운다`() = runBlocking {
        val audio = tempAudio()
        coEvery { transcribe(any()) } returns AppResult.Failure(AppError.SttError("실패"))

        orchestrator().processRequest(
            ChatRequest(sessionId = SESSION, message = "", audioFilePath = audio.absolutePath)
        )

        assertFalse("녹음이 캐시에 남으면 안 된다", audio.exists())
    }

    @Test
    fun `전사 성공 시에도 임시 오디오 파일을 지우고 전사문을 저장한다`() = runBlocking {
        val audio = tempAudio()
        coEvery { transcribe(any()) } returns AppResult.Success(TRANSCRIPT)
        coEvery { contextBuilder.build(any()) } returns AppResult.Failure(AppError.DbReadError("여기서 멈춘다"))

        orchestrator().processRequest(
            ChatRequest(sessionId = SESSION, message = "", audioFilePath = audio.absolutePath)
        )

        assertFalse(audio.exists())
        assertTrue(
            "전사문이 USER 메시지로 저장돼야 한다. 저장된 것: ${savedMessages.map { it.role to it.content }}",
            savedMessages.any { it.role == ChatMessage.Role.USER && it.content == TRANSCRIPT }
        )
    }

    @Test
    fun `문맥 구성 실패 시 말풍선에 원문 오류가 아닌 사용자 문구가 저장된다`() = runBlocking {
        // [WHY] 0.11.0 에서 `BaseAgent` 쪽은 고쳤는데 오케스트레이터가 빠져 있었다.
        coEvery { contextBuilder.build(any()) } returns AppResult.Failure(AppError.DbReadError("SQLITE_BUSY"))

        orchestrator().processRequest(ChatRequest(sessionId = SESSION, message = "안녕"))

        val assistant = savedMessages.single { it.role == ChatMessage.Role.ASSISTANT }
        assertFalse("내부 오류 문자열이 노출되면 안 된다", assistant.content.contains("DbReadError"))
        assertFalse(assistant.content.contains("SQLITE_BUSY"))
        assertTrue("사람이 읽을 문구여야 한다", assistant.content.isNotBlank())
    }

    private companion object {
        const val SESSION = "session-voice-failure"
        const val TRANSCRIPT = "내일 오후 세 시에 치과 예약 잡아줘"
    }
}

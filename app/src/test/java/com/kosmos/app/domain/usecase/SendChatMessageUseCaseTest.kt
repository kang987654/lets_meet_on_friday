package com.kosmos.app.domain.usecase

import com.kosmos.app.assistant.orchestrator.AssistantOrchestrator
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [SendChatMessageUseCaseTest]
 * 파이프라인 예외의 두 갈래를 고정합니다 — 취소는 되던지고 아무것도 저장하지 않으며,
 * 진짜 오류는 사람이 읽는 문구(ErrorMessages)로 말풍선을 남긴다(예외 원문 금지).
 */
class SendChatMessageUseCaseTest {

    private val orchestrator: AssistantOrchestrator = mockk()
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val useCase = SendChatMessageUseCase(orchestrator, conversationRepository, mockk())

    @Test
    fun `취소는 되던지고 오류 말풍선을 저장하지 않는다`() = runTest {
        coEvery { orchestrator.processRequest(any()) } throws CancellationException("left screen")

        try {
            useCase("s1", "안녕")
            fail("CancellationException 이 전파돼야 한다")
        } catch (e: CancellationException) {
            // 기대 경로
        }

        coVerify(exactly = 0) { conversationRepository.save(any()) }
    }

    @Test
    fun `일반 오류는 예외 원문 없이 사용자 문구로 저장한다`() = runTest {
        coEvery { orchestrator.processRequest(any()) } throws IllegalStateException("native handle 0xdead")
        val saved = slot<ChatMessage>()
        coEvery { conversationRepository.save(capture(saved)) } returns AppResult.Success(Unit)

        val result = useCase("s1", "안녕")

        assertTrue(result is AppResult.Failure)
        assertTrue(saved.isCaptured)
        assertFalse(saved.captured.content.contains("0xdead"))
        assertTrue(saved.captured.content.isNotBlank())
    }
}

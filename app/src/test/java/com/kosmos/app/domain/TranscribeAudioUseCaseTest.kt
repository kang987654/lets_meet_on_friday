package com.kosmos.app.domain

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.usecase.TranscribeAudioUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TranscribeAudioUseCaseTest]
 * 음성 메시지가 **전사문으로** 저장되기 위한 첫 관문을 검증합니다.
 *
 * [WHY] 예전에는 사용자 메시지를 `"(음성 메시지)"` 로 저장해, 대화를 다시 열면 자기가 무슨
 * 말을 했는지 남아 있지 않았다. 전사로 바꾸면서 두 가지가 계약이 된다 — 모델이 붙이기 쉬운
 * 껍데기(따옴표·화자 라벨)를 벗기는 것, 그리고 무음이면 **빈 메시지를 만들지 않는 것**.
 */
class TranscribeAudioUseCaseTest {

    private val modelRunner: ModelRunner = mockk()

    private fun useCase() = TranscribeAudioUseCase(modelRunner)

    private fun givenTranscript(text: String) {
        coEvery { modelRunner.generateWithAudio(any(), any(), any()) } returns
            AppResult.Success(ModelTurn(text))
    }

    @Test
    fun `전사문을 그대로 돌려준다`() = runBlocking {
        givenTranscript("내일 3시에 치과 예약 잡아줘")

        val result = useCase()("/tmp/a.wav")

        assertTrue(result is AppResult.Success)
        assertEquals("내일 3시에 치과 예약 잡아줘", (result as AppResult.Success).data)
    }

    @Test
    fun `감싼 따옴표를 벗긴다`() = runBlocking {
        givenTranscript("\"오늘 일정 알려줘\"")

        val result = useCase()("/tmp/a.wav")

        assertEquals("오늘 일정 알려줘", (result as AppResult.Success).data)
    }

    @Test
    fun `화자 라벨을 벗긴다`() = runBlocking {
        givenTranscript("사용자: 커피보다 녹차가 좋아")

        val result = useCase()("/tmp/a.wav")

        assertEquals("커피보다 녹차가 좋아", (result as AppResult.Success).data)
    }

    @Test
    fun `본문 안의 따옴표는 건드리지 않는다`() = runBlocking {
        givenTranscript("그 사람이 \"안녕\"이라고 했어")

        val result = useCase()("/tmp/a.wav")

        assertEquals("그 사람이 \"안녕\"이라고 했어", (result as AppResult.Success).data)
    }

    @Test
    fun `전사가 비면 STT 오류로 올린다`() = runBlocking {
        // [WHY] 무음 녹음이다. 빈 사용자 메시지를 저장하면 대화 기록에 내용 없는 말풍선이
        // 남는다. 오류로 올려야 화면이 "음성을 인식하지 못했어요" 를 띄운다 (PRD EC3).
        givenTranscript("   \n  ")

        val result = useCase()("/tmp/a.wav")

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.SttError)
    }

    @Test
    fun `모델 실패는 그대로 전달한다`() = runBlocking {
        coEvery { modelRunner.generateWithAudio(any(), any(), any()) } returns
            AppResult.Failure(AppError.ModelNotReady("not ready"))

        val result = useCase()("/tmp/a.wav")

        assertTrue((result as AppResult.Failure).error is AppError.ModelNotReady)
    }

    @Test
    fun `전사는 일회성 프롬프트로 나가고 툴을 선언하지 않는다`() = runBlocking {
        // [WHY] 이것이 성능 계약이다. oneShot 이 아니면 시스템 지시가 채팅과 달라 캐시된
        // 대화가 파괴되고, 음성 한 번에 채팅이 전체 프리필을 다시 낸다 (ADR-010·014).
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generateWithAudio(capture(prompt), any(), any()) } returns
            AppResult.Success(ModelTurn("안녕"))

        useCase()("/tmp/a.wav")

        assertTrue("oneShot 이어야 한다", prompt.captured.oneShot)
        assertTrue("툴을 선언하면 안 된다", prompt.captured.enabledTools.isEmpty())
        assertTrue("히스토리를 실으면 안 된다", prompt.captured.history.isEmpty())
    }
}

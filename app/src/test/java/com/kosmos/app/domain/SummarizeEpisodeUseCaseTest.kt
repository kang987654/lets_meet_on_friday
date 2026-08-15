package com.kosmos.app.domain

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.usecase.SummarizeEpisodeUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SummarizeEpisodeUseCaseTest]
 * 에피소드 요약이 **부수 계산으로 나가는지**와 파서 계약을 못박습니다.
 *
 * [WHY] 이 호출이 `oneShot` 이 아니면 시스템 지시와 sessionId 가 채팅과 달라 런타임이 캐시된
 * 채팅 대화를 파괴한다 — 에피소드가 닫힐 때마다 다음 채팅 턴이 전체 프리필을 다시 내게 된다
 * (ADR-010·014, SummarizeScheduleUseCaseTest 와 같은 계약).
 */
class SummarizeEpisodeUseCaseTest {

    private val modelRunner: ModelRunner = mockk()
    private val tokenizer: Tokenizer = mockk<Tokenizer>().also {
        io.mockk.every { it.sizeInTokens(any()) } answers { firstArg<String>().length / 2 }
    }

    private fun useCase() = SummarizeEpisodeUseCase(modelRunner, tokenizer)

    private fun msg(role: ChatMessage.Role, content: String, at: Long = 0) = ChatMessage(
        id = "m$at", sessionId = "s1", role = role, content = content,
        inputType = InputType.TEXT, createdAt = at
    )

    private val validOutput = """
        제목: 자전거 자물쇠 메모
        태그: 자전거, 비밀번호, 자물쇠
        요약: 사용자가 자전거 비밀번호를 1234로 저장했다. 이후 4321로 바꿨다가 되돌렸다.
    """.trimIndent()

    @Test
    fun `요약은 일회성 프롬프트로 나가고 툴과 히스토리를 싣지 않는다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn(validOutput))

        useCase()(listOf(msg(ChatMessage.Role.USER, "자전거 비밀번호 1234 기억해줘")))

        assertTrue("oneShot 이어야 캐시된 채팅 대화를 깨지 않는다", prompt.captured.oneShot)
        assertTrue("툴을 선언하면 프리필이 비싸진다", prompt.captured.enabledTools.isEmpty())
        assertTrue(prompt.captured.history.isEmpty())
        assertEquals("episode-summary", prompt.captured.sessionId)
    }

    @Test
    fun `원문이 사용자-비서 라벨로 프롬프트에 들어간다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn(validOutput))

        useCase()(
            listOf(
                msg(ChatMessage.Role.USER, "치과 예약 잡아줘", 1),
                msg(ChatMessage.Role.ASSISTANT, "내일 3시로 등록했어요", 2)
            )
        )

        assertTrue(prompt.captured.currentInput.contains("사용자: 치과 예약 잡아줘"))
        assertTrue(prompt.captured.currentInput.contains("비서: 내일 3시로 등록했어요"))
    }

    @Test
    fun `단일 문서를 파싱한다`() = runBlocking {
        coEvery { modelRunner.generate(any(), any()) } returns AppResult.Success(ModelTurn(validOutput))

        val docs = (useCase()(listOf(msg(ChatMessage.Role.USER, "x"))) as AppResult.Success).data

        assertEquals(1, docs.size)
        assertEquals("자전거 자물쇠 메모", docs[0].title)
        assertEquals(listOf("자전거", "비밀번호", "자물쇠"), docs[0].tags)
        assertTrue(docs[0].summary.contains("1234"))
    }

    @Test
    fun `다중 문서는 --- 로 갈라 파싱한다`() = runBlocking {
        // [WHY] M0 게이트에서 검증된 동작 — 혼합 주제 에피소드가 문서 2개로 나온다(exp33).
        val multi = validOutput + "\n---\n" + """
            제목: 위키 트와이스 검색
            태그: 트와이스, 위키, 검색
            요약: 사용자가 트와이스 정보를 위키에서 찾아달라고 요청했다.
        """.trimIndent()
        coEvery { modelRunner.generate(any(), any()) } returns AppResult.Success(ModelTurn(multi))

        val docs = (useCase()(listOf(msg(ChatMessage.Role.USER, "x"))) as AppResult.Success).data

        assertEquals(2, docs.size)
        assertEquals("위키 트와이스 검색", docs[1].title)
    }

    @Test
    fun `형식 위반은 실패로 올라간다`() = runBlocking {
        // [WHY] 빈 목록 성공으로 내리면 요약 없는 에피소드가 SUMMARIZED 로 오기록되고,
        // 재시도 카운트에도 계상되지 않는다.
        coEvery { modelRunner.generate(any(), any()) } returns
            AppResult.Success(ModelTurn("요약만 있고 제목과 태그가 없는 출력"))

        val result = useCase()(listOf(msg(ChatMessage.Role.USER, "x")))

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `부분 형식 블록은 문서로 인정하지 않는다`() = runBlocking {
        // [WHY] 태그 없는 문서는 태그 회수에 안 걸려 검색 품질을 조용히 깎는다 — 실패로
        // 취급해 재시도가 낫다.
        coEvery { modelRunner.generate(any(), any()) } returns
            AppResult.Success(ModelTurn("제목: 있음\n요약: 태그가 빠진 블록"))

        val result = useCase()(listOf(msg(ChatMessage.Role.USER, "x")))

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `상한을 넘는 원문은 앞쪽을 보존하며 잘린다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn(validOutput))
        // 메시지당 약 500토큰(1000자/2) — 10개면 상한(1400)을 넘는다.
        val long = (1..10).map { msg(ChatMessage.Role.USER, "메시지$it " + "가".repeat(995), it.toLong()) }

        useCase()(long)

        assertTrue("첫 메시지는 보존돼야 한다", prompt.captured.currentInput.contains("메시지1"))
        assertTrue("꼬리는 잘려야 한다", !prompt.captured.currentInput.contains("메시지9"))
    }

    @Test
    fun `빈 에피소드는 검증 오류다`() = runBlocking {
        val result = useCase()(emptyList())

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.ValidationError)
    }
}

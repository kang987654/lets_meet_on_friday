package com.kosmos.app.domain

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.tool.Tokenizer
import com.kosmos.app.domain.usecase.ExtractFactsUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExtractFactsUseCaseTest]
 * 자동 추출이 **부수 계산으로 나가는지**와 exp36 에서 결정한 파서 계약을 못박습니다 (C′2 M2).
 *
 * [WHY] 파서 케이스는 exp36 실측에서 모델이 실제로 낸 형태다 — 라벨 반복, 빈 절 생략,
 * 한 줄 다항목. 이 셋을 수용하지 않으면 실측 15/15 가 앱에서는 13/15 로 떨어진다.
 */
class ExtractFactsUseCaseTest {

    private val modelRunner: ModelRunner = mockk()
    private val tokenizer: Tokenizer = mockk<Tokenizer>().also {
        io.mockk.every { it.sizeInTokens(any()) } answers { firstArg<String>().length / 2 }
    }

    private fun useCase() = ExtractFactsUseCase(modelRunner, tokenizer)

    private fun msg(role: ChatMessage.Role, content: String, at: Long = 0) = ChatMessage(
        id = "m$at", sessionId = "s1", role = role, content = content,
        inputType = InputType.TEXT, createdAt = at
    )

    private fun stubOutput(text: String) {
        coEvery { modelRunner.generate(any(), any()) } returns AppResult.Success(ModelTurn(text))
    }

    @Test
    fun `추출은 일회성 프롬프트로 나가고 툴과 히스토리를 싣지 않는다`() = runBlocking {
        val prompt = slot<ChatPrompt>()
        coEvery { modelRunner.generate(capture(prompt), any()) } returns
            AppResult.Success(ModelTurn("프로필: 없음\n지식: 없음"))

        useCase()(
            listOf(
                msg(ChatMessage.Role.USER, "나는 진우야", 1),
                msg(ChatMessage.Role.ASSISTANT, "반가워요 진우님", 2)
            )
        )

        assertTrue("oneShot 이어야 캐시된 채팅 대화를 깨지 않는다", prompt.captured.oneShot)
        assertTrue(prompt.captured.enabledTools.isEmpty())
        assertTrue(prompt.captured.history.isEmpty())
        assertEquals(ExtractFactsUseCase.SESSION_ID, prompt.captured.sessionId)
        assertTrue("요약과 같은 라벨 전사여야 한다", prompt.captured.currentInput.contains("사용자: 나는 진우야"))
        assertTrue(prompt.captured.currentInput.contains("비서: 반가워요 진우님"))
    }

    @Test
    fun `프로필과 지식을 절별로 파싱한다`() = runBlocking {
        stubOutput("프로필: 거주지: 부산\n지식: 어머니 생신: 3월 12일")

        val facts = (useCase()(listOf(msg(ChatMessage.Role.USER, "x"))) as AppResult.Success).data

        assertEquals(listOf("거주지" to "부산"), facts.profile)
        assertEquals(listOf("어머니 생신: 3월 12일"), facts.knowledge)
    }

    @Test
    fun `둘 다 없음이면 빈 성공이다`() = runBlocking {
        // [WHY] 요약과 다르다 — 기억할 것이 없는 에피소드는 정상이며 재시도 대상이 아니다.
        stubOutput("프로필: 없음\n지식: 없음")

        val result = useCase()(listOf(msg(ChatMessage.Role.USER, "오늘 날씨 어때?")))

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isEmpty)
    }

    @Test
    fun `라벨 줄이 하나도 없으면 형식 실패다`() = runBlocking {
        stubOutput("사용자는 부산에 살고 있습니다.")

        val result = useCase()(listOf(msg(ChatMessage.Role.USER, "x")))

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.ModelInferenceError)
    }

    @Test
    fun `반복 라벨과 생략된 빈 절을 수용한다`() {
        // exp36 실측 원문: 프로필 줄 없이 지식 라벨을 항목마다 반복
        val facts = useCase().parse("지식: 자전거 자물쇠 비밀번호: 4936\n지식: 집 와이파이 비밀번호: kosmos123")

        assertTrue(facts != null)
        assertTrue(facts!!.profile.isEmpty())
        assertEquals(listOf("자전거 자물쇠 비밀번호: 4936", "집 와이파이 비밀번호: kosmos123"), facts.knowledge)
    }

    @Test
    fun `한 줄에 쉼표로 이어진 프로필 항목을 가른다`() {
        // exp36 실측 원문: "프로필: 이름: 진우, 직업: 개발자"
        val facts = useCase().parse("프로필: 이름: 진우, 직업: 개발자\n지식: 말투 선호: 반말")

        assertEquals(listOf("이름" to "진우", "직업" to "개발자"), facts!!.profile)
    }

    @Test
    fun `콜론 없는 쉼표 조각은 앞 값에 붙는다`() {
        val facts = useCase().parse("프로필: 말투: 친근하게, 존댓말\n지식: 없음")

        assertEquals(listOf("말투" to "친근하게, 존댓말"), facts!!.profile)
    }

    @Test
    fun `라벨 다음 불릿 줄도 그 절의 항목이다`() {
        val facts = useCase().parse("프로필:\n- 이름: 진우\n지식:\n- 회식은 마지막 금요일\n- 와이파이 kosmos123")

        assertEquals(listOf("이름" to "진우"), facts!!.profile)
        assertEquals(listOf("회식은 마지막 금요일", "와이파이 kosmos123"), facts.knowledge)
    }

    @Test
    fun `합계 3개 상한은 프로필을 우선한다`() {
        val facts = useCase().parse(
            "프로필: 이름: 진우, 직업: 개발자, 거주지: 부산\n지식: 사실1\n지식: 사실2\n지식: 사실3"
        )

        assertEquals(2, facts!!.profile.size)
        assertEquals(listOf("사실1"), facts.knowledge)
    }

    @Test
    fun `프로필이 없으면 지식은 3개까지다`() {
        val facts = useCase().parse("프로필: 없음\n지식: a\n지식: b\n지식: c\n지식: d")

        assertEquals(listOf("a", "b", "c"), facts!!.knowledge)
    }

    @Test
    fun `콜론 없는 프로필 조각만 있으면 프로필은 비고 지식만 남는다`() {
        val facts = useCase().parse("프로필: 개발자\n지식: 없음")

        assertTrue(facts!!.profile.isEmpty())
        assertTrue(facts.knowledge.isEmpty())
        assertTrue(facts.isEmpty)
    }

    @Test
    fun `라벨이 없는 출력은 null 이다`() {
        assertNull(useCase().parse(""))
        assertNull(useCase().parse("규칙: 없음"))
    }

    @Test
    fun `빈 에피소드는 검증 오류다`() = runBlocking {
        val result = useCase()(emptyList())

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.ValidationError)
    }
}

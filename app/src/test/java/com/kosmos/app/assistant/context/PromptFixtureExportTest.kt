package com.kosmos.app.assistant.context

import com.kosmos.app.runtime.gemma.KosmosToolDeclarations
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [PromptFixtureExportTest]
 * 실제 [PromptAssembler] 가 만드는 시스템 지시를 PC 실험실(`scratch/lab/`)이 읽을 픽스처로
 * 내보냅니다.
 *
 * [WHY] 실험실의 `kosmos_lab.py` 는 시스템 지시를 **손으로 베낀 사본**으로 들고 있었다. 앱은
 * 0.9.0 에서 날짜 블록을 넣고 0.10.0 에서 `search_memory` 트리거를 추가했는데 사본은 0.8.6
 * 시절에 멈춰 있었다 — 그 상태로 돌린 실험은 앱과 다른 프롬프트를 측정한다. 프롬프트 표류는
 * 조용히 일어나고, 결론이 틀렸다는 사실도 조용히 남는다.
 *
 * 그래서 사본을 지우고 **여기서 내보낸다.** 앱 코드가 바뀌면 다음 테스트 실행에서 픽스처도
 * 함께 바뀌므로 사본이 낡을 수 없다.
 *
 * [WHY] `scratch/lab/fixtures/` 가 없으면 쓰지 않고 내용 단언만 한다 — 그 디렉터리는
 * `.gitignore` 대상이라 CI 나 다른 체크아웃에는 존재하지 않는다. 쓰기를 필수로 만들면 실험실을
 * 쓰지 않는 환경에서 테스트가 깨진다.
 */
class PromptFixtureExportTest {

    @Test
    fun `시스템 지시와 툴 목록을 실험실 픽스처로 내보낸다`() {
        val prompt = PromptAssembler().assembleWithTools(
            context = ContextBuilder.Context(
                recentConversations = emptyList(),
                sessionId = "lab-fixture",
                responseStyle = "DEFAULT",
                webSearchEnabled = true
            ),
            userInput = "(사용자 발화는 실험에서 주입한다)",
            availableTools = ALL_TOOLS,
            systemRole = SYSTEM_ROLE
        )

        // 표류 감시 — 아래 단언이 깨지면 프롬프트가 바뀐 것이고, 픽스처를 다시 내보내야 한다.
        assertTrue("날짜 블록이 시스템 지시에 있어야 한다 (ADR-010)", prompt.systemInstruction.contains("[System Data] Today:"))
        assertTrue("다음주 월요일 파생값이 있어야 한다", prompt.systemInstruction.contains("다음주 월요일="))
        assertTrue(prompt.systemInstruction.contains("[Tool Usage Guidelines]"))
        ALL_SNAKE_NAMES.forEach { name ->
            assertTrue("툴 트리거 규칙에 `$name` 이 지목돼야 한다", prompt.systemInstruction.contains("`$name`"))
        }
        assertTrue(
            "선언 이름 매핑이 실험실 툴 목록과 어긋나면 안 된다",
            KosmosToolDeclarations.CANONICAL_NAMES.values.toSet() == ALL_TOOLS.toSet()
        )

        val dir = File(FIXTURE_DIR)
        if (!dir.isDirectory) return

        File(dir, "system_instruction.txt").writeText(prompt.systemInstruction)
        File(dir, "tools.txt").writeText(
            ALL_SNAKE_NAMES.joinToString("\n", postfix = "\n")
        )
    }

    @Test
    fun `전사 지시문도 실험실 픽스처로 내보낸다`() = kotlinx.coroutines.runBlocking {
        // [WHY] exp16(전사 실험)이 지시문을 손으로 베끼면 같은 표류가 반복된다.
        //
        // [WHY] 상수를 읽지 않고 **실제로 나가는 `ChatPrompt` 를 붙잡는다.** 상수는 `private`
        // 이고, 테스트 편의로 가시성을 넓히는 것보다 이 방법이 정확하다 — 조립 과정에서 무엇이
        // 덧붙든 실험실이 받는 것은 언제나 앱이 보내는 것과 같다.
        val runner: com.kosmos.app.domain.modelrunner.ModelRunner = mockk()
        val captured = slot<com.kosmos.app.domain.modelrunner.ChatPrompt>()
        coEvery {
            runner.generateWithAudio(capture(captured), any(), any())
        } returns com.kosmos.app.core.common.AppResult.Success(
            com.kosmos.app.domain.modelrunner.ModelTurn("받아쓴 문장")
        )

        com.kosmos.app.domain.usecase.TranscribeAudioUseCase(runner)("/dev/null.wav")

        val prompt = captured.captured
        assertTrue("전사 전용 지시여야 한다", prompt.systemInstruction.contains("transcrib", ignoreCase = true))
        assertTrue(
            "무음일 때 아무것도 내지 않도록 지시해야 한다 (PRD EC3)",
            prompt.systemInstruction.contains("no intelligible")
        )
        assertTrue("툴을 선언하면 안 된다", prompt.enabledTools.isEmpty())
        assertTrue("oneShot 이어야 채팅 대화를 깨지 않는다", prompt.oneShot)

        val dir = File(FIXTURE_DIR)
        if (dir.isDirectory) {
            File(dir, "transcribe_system.txt").writeText(prompt.systemInstruction)
            File(dir, "transcribe_user.txt").writeText(prompt.currentInput)
        }
    }

    private companion object {
        // [WHY] 앱이 채팅에서 선언하는 5종 그대로다. 실험실이 4종만 선언하던 동안은 선언 크기와
        // 트리거 목록이 달라 툴 선택 성향 실험이 앱과 어긋났다.
        val ALL_TOOLS = listOf(
            "AddSchedule", "GetSchedule", "AddMemory", "SearchMemory", "SearchWikipedia"
        )
        val ALL_SNAKE_NAMES = listOf(
            "add_schedule", "get_schedule", "add_memory", "search_memory", "search_wikipedia"
        )

        // KosmosAgent 가 넘기는 역할 문구와 같아야 한다.
        const val SYSTEM_ROLE = "personal assistant named Kosmos"

        const val FIXTURE_DIR = "../scratch/lab/fixtures"
    }
}

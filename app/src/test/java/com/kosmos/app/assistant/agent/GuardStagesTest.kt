package com.kosmos.app.assistant.agent

import com.kosmos.app.domain.memory.MemoSaveIntent
import com.kosmos.app.domain.tool.ToolNames
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 툴 루프의 두 단계(0.39.2 M1) — 실행 전 가드 [ToolCallGuards] 와 답 후처리 [ReplyFinisher], 막아 둔 메모 [PendingMemos].
 * 루프 전체의 행동은 BaseAgentMemoGuardTest·BaseAgentStreamTest 가 지키고, 여기서는 단계 단위의 판정을 고정한다.
 * Robolectric — 툴 결과 JSON 이 org.json(안드로이드 스텁)으로 만들어진다.
 */
@RunWith(RobolectricTestRunner::class)
class GuardStagesTest {

    private val all = listOf(ToolNames.ADD_MEMORY, ToolNames.SEARCH_MEMORY)

    @Test
    fun `allowlist 밖 툴은 감사 오류와 함께 막힌다`() {
        val block = ToolCallGuards.check(ToolNames.SEARCH_WIKIPEDIA, all, "에스파 검색해줘", null, guardFact = true)

        assertFalse(block!!.outcome.executed)
        assertTrue(block.outcome.resultJson.contains("웹 검색 토글"))
        assertEquals("Blocked tool call outside allowlist: ${ToolNames.SEARCH_WIKIPEDIA}", block.auditError)
    }

    @Test
    fun `되묻기에 불린 메모 저장은 값을 답하라는 결과로 막힌다`() {
        val block = ToolCallGuards.check(ToolNames.ADD_MEMORY, all, "송장번호 다시 알려줘", null, guardFact = true)

        assertTrue(block!!.outcome.resultJson.contains(ToolCallGuards.REPEAT_NOT_SAVE))
        assertFalse(block.outcome.factBlocked)
        assertNull(block.auditError)
    }

    @Test
    fun `저장 부탁 없는 메모 저장은 막고 먼저 묻게 한다 — 앱이 연 저장은 이 가드를 건너뛴다`() {
        val block = ToolCallGuards.check(ToolNames.ADD_MEMORY, all, "내 사물함 번호는 214번이야", null, guardFact = true)

        assertTrue(block!!.outcome.factBlocked)
        assertNull(ToolCallGuards.check(ToolNames.ADD_MEMORY, all, "내 사물함 번호는 214번이야", null, guardFact = false))
        assertNull(ToolCallGuards.check(ToolNames.ADD_MEMORY, all, "214번이야 기억해줘", null, guardFact = true))
        assertNull(ToolCallGuards.check(ToolNames.ADD_MEMORY, all, "응", "메모해 둘까요?", guardFact = true))
        assertNull(ToolCallGuards.check(ToolNames.SEARCH_MEMORY, all, "사물함 번호 뭐였지?", null, guardFact = true))
    }

    private fun finish(reply: String, user: String, previous: MemoSaveIntent.PreviousTurn, carried: String? = null, executed: Boolean = false) =
        runBlocking { ReplyFinisher.finish(reply, user, { previous }, carried, anyExecuted = executed, memoCalled = false, memoAllowed = true) }

    @Test
    fun `저장 물음에 응이면 앱이 저장을 연다 — 막아 둔 메모가 앞 사용자 말보다 먼저`() {
        val asked = MemoSaveIntent.PreviousTurn(assistant = "메모해 둘까요?", user = "내 사물함 번호는 214번이야")

        assertEquals(ReplyFinisher.Finish.AppSave("내 사물함 번호는 214번이야", MemoSaveIntent.ASK), finish("메모해 두었어요.", "응", asked))
        assertEquals("사물함 번호 214번", (finish("네!", "응", asked, carried = "사물함 번호 214번") as ReplyFinisher.Finish.AppSave).memo)
    }

    @Test
    fun `툴을 실행한 턴은 고치지 않고, 약속 문장만 있으면 물음으로 바꾼다`() {
        val none = MemoSaveIntent.PreviousTurn(null, null)

        assertEquals(ReplyFinisher.Finish.Keep, finish("기억해 둘게요.", "콩이야", none, executed = true))
        assertEquals(ReplyFinisher.Finish.Rewrite(MemoSaveIntent.ASK), finish("기억해 둘게요.", "콩이야", none))
        assertEquals(ReplyFinisher.Finish.Keep, finish("좋은 이름이네요.", "콩이야", none))
    }

    @Test
    fun `앞 턴은 응일 때만 읽는다`() {
        var reads = 0
        runBlocking {
            ReplyFinisher.finish("좋아요.", "점심 추천해 줘", { reads++; MemoSaveIntent.PreviousTurn(null, null) }, null, false, false, true)
        }

        assertEquals(0, reads)
    }

    @Test
    fun `거절 답이거나 거절을 되물은 턴이면 다음 턴에 대화를 다시 만든다`() {
        assertTrue(ReplyFinisher.needsRebuild("죄송해요, 그런 기능은 없어요.", refusalAsked = false))
        assertTrue(ReplyFinisher.needsRebuild("운동 루틴은 제가 짜 드릴 수 없다고 했어요.", refusalAsked = true))
        assertFalse(ReplyFinisher.needsRebuild("김치찌개 레시피예요.", refusalAsked = false))
    }

    @Test
    fun `막아 둔 메모는 대화방마다 따로, 한 번 꺼내면 사라진다`() {
        val memos = PendingMemos()
        memos.put("a", "사물함 214")
        memos.put("b", "주차 C-14")

        assertNull(memos.take("c"))
        assertEquals("사물함 214", memos.take("a"))
        assertNull(memos.take("a"))
        assertEquals("주차 C-14", memos.take("b"))
    }
}

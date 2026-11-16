package com.kosmos.app.feature.chat

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.ChatMessage.Role
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.41.14 M1 — 한 화면 세션에서 페이지 크기보다 많이 쌓인 뒤 다시 읽어도 앵커 뒤 메시지가 빠지지 않는다. */
class LiveTailTest {

    private val anchor = 1_000_000L

    private fun msg(i: Int) = ChatMessage(
        id = "m$i", sessionId = "s", role = if (i % 2 == 0) Role.USER else Role.ASSISTANT,
        content = "메시지 $i", inputType = InputType.TEXT, createdAt = anchor + i
    )

    /** DB 흉내 — 이 세션의 앵커 앞 5건 + 앵커 뒤 [after]건, `getRecentBySession` 은 최근 limit 건을 시간순으로. */
    private fun repo(after: Int): ConversationRepository {
        val before = (1..5).map { msg(-it) }.sortedBy { it.createdAt }
        val all = before + (0 until after).map { msg(it) }
        return mockk {
            coEvery { countNewerThan(any()) } answers { AppResult.Success(all.count { it.createdAt >= firstArg<Long>() }) }
            coEvery { getRecentBySession("s", any()) } answers { AppResult.Success(all.takeLast(secondArg<Int>())) }
        }
    }

    @Test
    fun `앵커 뒤 메시지가 페이지 크기보다 많아도 전부 읽는다`() = runBlocking {
        val after = Constants.CHAT_TIMELINE_PAGE_SIZE + 11
        val result = loadLiveTail(repo(after), "s", anchor) as AppResult.Success
        assertEquals(after, result.data.size)
        assertEquals("m0", result.data.first().id)
    }

    @Test
    fun `앵커 앞 메시지는 넣지 않는다`() = runBlocking {
        val result = loadLiveTail(repo(3), "s", anchor) as AppResult.Success
        assertEquals(listOf("m0", "m1", "m2"), result.data.map { it.id })
    }
}

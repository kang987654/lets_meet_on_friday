package com.kosmos.app.data

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.repository.AuditRepositoryImpl
import com.kosmos.app.data.local.repository.ConversationRepositoryImpl
import com.kosmos.app.data.local.repository.DataResetRepositoryImpl
import com.kosmos.app.data.local.repository.EpisodeRepositoryImpl
import com.kosmos.app.data.local.repository.KnowledgeRepositoryImpl
import com.kosmos.app.data.local.repository.ProfileRepositoryImpl
import com.kosmos.app.data.local.repository.ProfileSuggestionRepositoryImpl
import com.kosmos.app.data.local.repository.TaskRepositoryImpl
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.domain.model.AuditEventType
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import com.kosmos.app.domain.model.TaskItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [DataResetRepositoryTest]
 * 데이터 초기화(0.37.1)가 **고른 대상의 테이블만** 비우는지 고정합니다 — 실제 Room 스키마 위에서.
 */
@RunWith(RobolectricTestRunner::class)
class DataResetRepositoryTest {

    @get:org.junit.Rule
    val dbRule = com.kosmos.app.testing.InMemoryKosmosDbRule()

    private lateinit var db: KosmosDatabase

    private val tables = listOf("conversation", "episode", "audit_log", "profile_suggestion", "task_item", "knowledge_note", "profile")

    @Before
    fun seed() = runBlocking {
        db = dbRule.db
        ConversationRepositoryImpl(db.conversationDao()).save(
            ChatMessage(id = "m1", sessionId = "s1", role = ChatMessage.Role.USER, content = "안녕", inputType = InputType.TEXT, createdAt = 1)
        ).ok()
        EpisodeRepositoryImpl(db.episodeDao()).insert(
            Episode(id = "e1", sessionId = "s1", status = EpisodeStatus.SUMMARIZED, title = "t", summary = "s", tags = emptyList(),
                startAt = 1, endAt = 2, messageCount = 1, retryCount = 0, createdAt = 1, updatedAt = 1)
        ).ok()
        AuditRepositoryImpl(db.auditDao()).save(AuditEvent("a1", AuditEventType.TOOL_CALL, "s1", "d", 1)).ok()
        ProfileSuggestionRepositoryImpl(db.profileSuggestionDao()).insert(
            ProfileSuggestion("p1", "호칭", "진우님", "e1", ProfileSuggestionStatus.PENDING, 1, 1)
        ).ok()
        TaskRepositoryImpl(db.taskDao()).save(TaskItem(id = "t1", title = "장보기", isCompleted = false, createdAt = 1)).ok()
        KnowledgeRepositoryImpl(db.knowledgeDao()).save(KnowledgeNote(id = "k1", content = "메모", createdAt = 1, updatedAt = 1)).ok()
        ProfileRepositoryImpl(db.profileDao()).upsert("호칭", "진우님", "manual").ok()
        tables.forEach { assertEquals("$it 시드", 1, count(it)) }
    }

    private fun AppResult<*>.ok() = assertTrue(toString(), this is AppResult.Success)

    private fun count(table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getInt(0) }

    private fun reset(vararg targets: ResetTarget) = runBlocking {
        DataResetRepositoryImpl(db).reset(targets.toSet()).ok()
    }

    @Test
    fun `대화 기록은 대화에서 파생된 것까지 지우고 나머지는 남긴다`() {
        reset(ResetTarget.CONVERSATIONS)

        listOf("conversation", "episode", "audit_log", "profile_suggestion").forEach { assertEquals(it, 0, count(it)) }
        listOf("task_item", "knowledge_note", "profile").forEach { assertEquals(it, 1, count(it)) }
    }

    @Test
    fun `할 일과 메모는 각자 테이블만 지운다`() {
        reset(ResetTarget.TASKS, ResetTarget.KNOWLEDGE)

        assertEquals(0, count("task_item"))
        assertEquals(0, count("knowledge_note"))
        listOf("conversation", "episode", "audit_log", "profile_suggestion", "profile").forEach { assertEquals(it, 1, count(it)) }
    }

    @Test
    fun `프로필은 제안 카드와 함께 지우고 대상이 없으면 아무것도 지우지 않는다`() {
        reset()
        tables.forEach { assertEquals(it, 1, count(it)) }

        reset(ResetTarget.PROFILE)
        assertEquals(0, count("profile"))
        assertEquals(0, count("profile_suggestion"))
        assertEquals(1, count("conversation"))
    }
}

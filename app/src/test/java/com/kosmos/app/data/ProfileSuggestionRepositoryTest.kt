package com.kosmos.app.data

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.repository.KnowledgeRepositoryImpl
import com.kosmos.app.data.local.repository.ProfileSuggestionRepositoryImpl
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.ProfileSuggestion
import com.kosmos.app.domain.model.ProfileSuggestionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ProfileSuggestionRepositoryTest]
 * 제안 저장·상태 전이·중복 검사와 지식 노트 source 왕복을 실제 SQLite 로 검증합니다 (C′2 M1).
 *
 * [WHY] `exists` 가 상태를 가리지 않는 것이 계약이다 — 거절한 사실을 다시 묻지 않는 장치가
 * 여기서 무너지면 자동 추출이 잔소리가 된다. 지식 source 는 매퍼 왕복에서 조용히 소실되는
 * 결함(EpisodeRepositoryTest 의 thinkingProcess 전례)을 막는다.
 */
@RunWith(RobolectricTestRunner::class)
class ProfileSuggestionRepositoryTest {

    // [WHY] DB 수명(열기·닫기)은 공용 Rule 이 맡는다 — 설정만 옮겼고 단언은 그대로다.
    @get:org.junit.Rule
    val dbRule = com.kosmos.app.testing.InMemoryKosmosDbRule()

    private lateinit var db: KosmosDatabase
    private lateinit var suggestions: ProfileSuggestionRepositoryImpl
    private lateinit var knowledge: KnowledgeRepositoryImpl

    @Before
    fun setUp() {
        db = dbRule.db
        suggestions = ProfileSuggestionRepositoryImpl(db.profileSuggestionDao())
        knowledge = KnowledgeRepositoryImpl(db.knowledgeDao())
    }


    private fun suggestion(
        id: String,
        key: String = "이름",
        value: String = "진우",
        status: ProfileSuggestionStatus = ProfileSuggestionStatus.PENDING,
        createdAt: Long = 100
    ) = ProfileSuggestion(
        id = id, key = key, value = value, episodeId = "e1",
        status = status, createdAt = createdAt, updatedAt = createdAt
    )

    @Test
    fun `PENDING 만 오래된 것부터 관찰된다`() = runBlocking {
        suggestions.insert(suggestion("s2", key = "직업", value = "개발자", createdAt = 200))
        suggestions.insert(suggestion("s1", createdAt = 100))
        suggestions.insert(suggestion("s3", key = "거주지", value = "부산", status = ProfileSuggestionStatus.REJECTED))

        val pending = suggestions.observePending().first()

        assertEquals(listOf("s1", "s2"), pending.map { it.id })
    }

    @Test
    fun `상태 전이 후에는 대기 목록에서 빠진다`() = runBlocking {
        suggestions.insert(suggestion("s1"))

        val result = suggestions.updateStatus("s1", ProfileSuggestionStatus.ACCEPTED, updatedAt = 999)

        assertTrue(result is AppResult.Success)
        assertTrue(suggestions.observePending().first().isEmpty())
    }

    @Test
    fun `exists 는 상태를 가리지 않고 같은 키-값을 찾는다`() = runBlocking {
        suggestions.insert(suggestion("s1", status = ProfileSuggestionStatus.REJECTED))

        assertTrue(suggestions.exists("이름", "진우"))
        assertFalse("값이 다르면 새 제안이다", suggestions.exists("이름", "진우님"))
        assertFalse(suggestions.exists("호칭", "진우"))
    }

    @Test
    fun `지식 노트 source 는 저장-재조회 왕복에서 보존된다`() = runBlocking {
        knowledge.save(
            KnowledgeNote(
                id = "k-auto", content = "회식은 마지막 금요일", tags = listOf("회식"),
                createdAt = 1, updatedAt = 1, source = KnowledgeNote.SOURCE_AUTO
            )
        )
        knowledge.save(
            KnowledgeNote(id = "k-manual", content = "와이파이 kosmos123", createdAt = 2, updatedAt = 2)
        )

        val notes = (knowledge.getNotes(offset = 0, limit = 10) as AppResult.Success).data.associateBy { it.id }

        assertEquals(KnowledgeNote.SOURCE_AUTO, notes.getValue("k-auto").source)
        assertEquals(KnowledgeNote.SOURCE_MANUAL, notes.getValue("k-manual").source)
    }
}

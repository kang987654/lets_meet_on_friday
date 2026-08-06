package com.kosmos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.repository.KnowledgeRepositoryImpl
import com.kosmos.app.domain.model.KnowledgeNote
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [KnowledgeTagRoundTripTest]
 * 콤마가 든 태그가 저장·조회 왕복에서 쪼개지지 않는지 검증합니다.
 *
 * [WHY] 순수 함수 테스트(`TagsNormalizeTest`)만으로는 부족하다 — 이 불변식은
 * `joinToString(",")`(저장)과 `split(",")`(조회)이 **실제로 왕복해야** 확인된다.
 * 정규화를 저장 경로에서 빼먹으면 순수 함수 테스트는 그대로 통과하면서 데이터가 깨진다.
 */
@RunWith(RobolectricTestRunner::class)
class KnowledgeTagRoundTripTest {

    private lateinit var db: KosmosDatabase
    private lateinit var repository: KnowledgeRepositoryImpl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KosmosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KnowledgeRepositoryImpl(db.knowledgeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun save(vararg tags: String) = runBlocking {
        val result = repository.save(
            KnowledgeNote(
                id = "n1",
                content = "노트",
                tags = tags.toList(),
                embedding = null,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        assertTrue("저장이 실패했다: $result", result is AppResult.Success)
    }

    private fun readTags(): List<String> = runBlocking {
        val result = repository.searchRecent(10)
        assertTrue("조회가 실패했다: $result", result is AppResult.Success)
        (result as AppResult.Success).data.single().tags
    }

    private fun searchTag(tag: String): Int = runBlocking {
        val result = repository.searchByTags(listOf(tag), 10)
        assertTrue("태그 검색이 실패했다: $result", result is AppResult.Success)
        (result as AppResult.Success).data.size
    }

    @Test
    fun `콤마가 든 태그가 두 개로 쪼개지지 않는다`() {
        save("밥, 국")

        // [WHY] 정규화 전에는 "밥, 국" 하나를 저장하면 ["밥", "국"] 두 개로 읽혔다.
        assertEquals(listOf("밥 국"), readTags())
    }

    @Test
    fun `콤마를 치환한 태그로 검색이 된다`() {
        save("밥, 국")

        // 정규화가 저장·검색 양쪽에 같이 적용되므로 원래 입력으로도 찾을 수 있다.
        assertEquals(1, searchTag("밥, 국"))
        assertEquals(1, searchTag("밥 국"))
    }

    @Test
    fun `콤마 없는 태그는 그대로 왕복한다`() {
        save("work", "urgent")

        assertEquals(listOf("work", "urgent"), readTags())
        assertEquals(1, searchTag("work"))
        // 0.7.1 의 정확 매칭이 유지되는지도 함께 확인한다.
        assertEquals(0, searchTag("wor"))
    }

    @Test
    fun `정규화로 중복이 된 태그는 하나만 남는다`() {
        save("a,b", "a b")

        assertEquals(listOf("a b"), readTags())
    }

    @Test
    fun `콤마만인 태그는 저장되지 않는다`() {
        save("work", ",,,")

        assertEquals(listOf("work"), readTags())
    }
}

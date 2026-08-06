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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [KnowledgeEmbeddingBlobTest]
 * 임베딩 BLOB 이 실제 Room 왕복에서 보존되고 벡터 검색이 동작하는지 검증합니다.
 *
 * [WHY] 순수 함수 테스트(`FloatBytesTest`)만으로는 부족하다 — Room 이 `ByteArray` 를 BLOB 으로
 * 처리하고 `SELECT *` 매핑이 되는지는 실제 DB 를 태워야 확인된다.
 */
@RunWith(RobolectricTestRunner::class)
class KnowledgeEmbeddingBlobTest {

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

    private fun save(id: String, embedding: FloatArray?, tags: List<String> = emptyList()) = runBlocking {
        val result = repository.save(
            KnowledgeNote(
                id = id,
                content = "내용-$id",
                tags = tags,
                embedding = embedding,
                createdAt = id.hashCode().toLong(),
                updatedAt = 0L
            )
        )
        assertTrue("저장이 실패했다: $result", result is AppResult.Success)
    }

    private fun <T> unwrap(result: AppResult<T>): T {
        assertTrue("실패했다: $result", result is AppResult.Success)
        return (result as AppResult.Success).data
    }

    @Test
    fun `임베딩이 BLOB 왕복에서 값을 유지한다`() {
        val embedding = floatArrayOf(0.5f, -0.25f, 1.75f)
        save("n1", embedding)

        val note = unwrap(runBlocking { repository.searchRecent(10) }).single()

        assertArrayEquals(embedding, note.embedding!!, 0f)
    }

    @Test
    fun `벡터 검색이 가장 가까운 노트를 먼저 준다`() {
        save("near", floatArrayOf(1f, 0f, 0f))
        save("far", floatArrayOf(0f, 1f, 0f))

        val results = unwrap(runBlocking { repository.searchByVector(floatArrayOf(1f, 0f, 0f), 2) })

        assertEquals("내용-near", results.first().content)
    }

    @Test
    fun `임베딩이 없는 노트는 벡터 검색에서 제외되고 최근 목록에는 남는다`() {
        save("withVector", floatArrayOf(1f, 0f))
        save("noVector", null)

        val vectorResults = unwrap(runBlocking { repository.searchByVector(floatArrayOf(1f, 0f), 10) })
        val recent = unwrap(runBlocking { repository.searchRecent(10) })

        assertEquals(listOf("내용-withVector"), vectorResults.map { it.content })
        assertEquals(2, recent.size)
        assertNull(recent.single { it.content == "내용-noVector" }.embedding)
    }

    @Test
    fun `길이가 다른 임베딩은 벡터 검색에서 건너뛴다`() {
        save("wrongSize", floatArrayOf(1f, 0f, 0f))

        val results = unwrap(runBlocking { repository.searchByVector(floatArrayOf(1f, 0f), 10) })

        assertEquals(emptyList<String>(), results.map { it.content })
    }

    @Test
    fun `여러 태그로 검색해도 같은 노트가 중복되지 않는다`() {
        // [WHY] KnowledgeEntity 는 ByteArray 필드를 가지므로 data class 의 equals 가 참조
        // 비교가 된다. 중복 제거를 Set 으로 하면 같은 노트가 태그마다 중복 반환된다 —
        // 문자열 임베딩 시절에는 값 비교라 우연히 동작했다.
        save("n1", floatArrayOf(1f, 2f), tags = listOf("work", "urgent"))

        val results = unwrap(runBlocking { repository.searchByTags(listOf("work", "urgent"), 10) })

        assertEquals(1, results.size)
    }

    @Test
    fun `offset limit 조회가 페이지 경계를 지킨다`() {
        // [WHY] getPagedData() 의 PagingSource 를 offset/limit 계약으로 바꿨으므로
        // 경계 동작을 여기서 고정한다.
        (1..5).forEach { save("n$it", null) }

        val firstPage = unwrap(runBlocking { repository.getNotes(0, 2) })
        val secondPage = unwrap(runBlocking { repository.getNotes(2, 2) })

        assertEquals(2, firstPage.size)
        assertEquals(2, secondPage.size)
        assertTrue("페이지가 겹치면 안 된다", firstPage.map { it.id }.intersect(secondPage.map { it.id }.toSet()).isEmpty())
    }
}

package com.kosmos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.db.entity.KnowledgeEntity
import com.kosmos.app.data.local.repository.KnowledgeRepositoryImpl
import com.kosmos.app.core.common.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [KnowledgeSearchEscapeTest]
 * 실제 SQLite 에서 LIKE 와일드카드 이스케이프와 태그 정확 매칭이 동작하는지 검증합니다.
 *
 * [WHY] 이스케이프는 순수 함수 테스트만으로는 부족하다 — DAO 쿼리에 `ESCAPE '\'` 절이
 * 실제로 붙어 있어야 효과가 있고, 그 둘이 어긋나면 조용히 예전 동작으로 돌아간다.
 * 인메모리 Room 으로 실제 쿼리를 태워 회귀를 막는다.
 */
@RunWith(RobolectricTestRunner::class)
class KnowledgeSearchEscapeTest {

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

    private fun note(id: String, content: String, tags: String = "") = KnowledgeEntity(
        id = id,
        content = content,
        sourceSessionId = null,
        tags = tags,
        embedding = null,
        createdAt = id.hashCode().toLong(),
        updatedAt = 0L
    )

    private fun insert(vararg entities: KnowledgeEntity) = runBlocking {
        entities.forEach { db.knowledgeDao().insert(it) }
    }

    private fun search(query: String): List<String> = runBlocking {
        val result = repository.search(query, 100)
        assertTrue("검색이 실패했다: $result", result is AppResult.Success)
        (result as AppResult.Success).data.map { it.content }
    }

    private fun searchTag(tag: String): List<String> = runBlocking {
        val result = repository.searchByTags(listOf(tag), 100)
        assertTrue("태그 검색이 실패했다: $result", result is AppResult.Success)
        (result as AppResult.Success).data.map { it.content }
    }

    @Test
    fun `퍼센트를 포함한 검색어는 리터럴로 매칭된다`() {
        insert(
            note("1", "할인율 100% 달성"),
            note("2", "예산 1000원 집행")
        )

        // [WHY] 이스케이프 전에는 패턴이 %100%% 가 되어 "1000원"도 매칭됐다.
        assertEquals(listOf("할인율 100% 달성"), search("100%"))
    }

    @Test
    fun `언더스코어는 임의 한 글자가 아니라 리터럴이다`() {
        insert(
            note("1", "snake_case 규칙"),
            note("2", "snakeXcase 오타")
        )

        assertEquals(listOf("snake_case 규칙"), search("snake_case"))
    }

    @Test
    fun `퍼센트 한 글자 검색이 전체를 매칭하지 않는다`() {
        insert(note("1", "첫 번째"), note("2", "두 번째"))

        // [WHY] 이스케이프 전에는 노트 100건이 전부 매칭돼 RAG 프롬프트 예산을 터뜨렸다.
        assertEquals(emptyList<String>(), search("%"))
    }

    @Test
    fun `언더스코어 한 글자 검색이 전체를 매칭하지 않는다`() {
        insert(note("1", "첫 번째"), note("2", "두 번째"))

        assertEquals(emptyList<String>(), search("_"))
    }

    @Test
    fun `빈 검색어는 전체가 아니라 빈 결과다`() {
        insert(note("1", "첫 번째"), note("2", "두 번째"))

        assertEquals(emptyList<String>(), search(""))
        assertEquals(emptyList<String>(), search("   "))
    }

    @Test
    fun `일반 검색은 그대로 동작한다`() {
        insert(note("1", "회의 준비 자료"), note("2", "장보기 목록"))

        assertEquals(listOf("회의 준비 자료"), search("회의"))
    }

    @Test
    fun `태그는 정확히 한 개 단위로 매칭된다`() {
        insert(
            note("1", "업무 노트", tags = "work,urgent"),
            note("2", "워크플로 노트", tags = "workflow")
        )

        // [WHY] 부분 일치였을 때는 "work" 검색이 "workflow" 노트까지 끌어왔다.
        assertEquals(listOf("업무 노트"), searchTag("work"))
        assertEquals(listOf("워크플로 노트"), searchTag("workflow"))
    }

    @Test
    fun `콤마로 이어진 태그의 중간과 끝도 매칭된다`() {
        insert(note("1", "노트", tags = "alpha,beta,gamma"))

        assertEquals(listOf("노트"), searchTag("alpha"))
        assertEquals(listOf("노트"), searchTag("beta"))
        assertEquals(listOf("노트"), searchTag("gamma"))
    }

    @Test
    fun `공백만인 태그는 아무것도 매칭하지 않는다`() {
        insert(note("1", "노트", tags = "work"))

        assertEquals(emptyList<String>(), searchTag("  "))
    }

    @Test
    fun `태그 검색의 와일드카드도 리터럴이다`() {
        insert(note("1", "노트", tags = "work"))

        assertEquals(emptyList<String>(), searchTag("%"))
        assertEquals(emptyList<String>(), searchTag("_"))
    }
}

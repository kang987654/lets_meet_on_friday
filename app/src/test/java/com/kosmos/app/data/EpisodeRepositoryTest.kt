package com.kosmos.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.repository.ConversationRepositoryImpl
import com.kosmos.app.data.local.repository.EpisodeRepositoryImpl
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.InputType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [EpisodeRepositoryTest]
 * 에피소드 저장·검색과 대화 매퍼의 왕복을 실제 SQLite 로 검증합니다.
 *
 * [WHY] 매퍼 왕복이 핵심이다 — `thinkingProcess` 는 도메인 모델에는 있는데 Entity 칼럼이 없어
 * **재로드 시 조용히 소실**됐다. episodeId·recallEpisodeIds 는 그 결함이 재현되지 않도록
 * 저장→재조회 전체 경로를 못박는다.
 */
@RunWith(RobolectricTestRunner::class)
class EpisodeRepositoryTest {

    private lateinit var db: KosmosDatabase
    private lateinit var episodes: EpisodeRepositoryImpl
    private lateinit var conversations: ConversationRepositoryImpl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KosmosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        episodes = EpisodeRepositoryImpl(db.episodeDao())
        conversations = ConversationRepositoryImpl(db.conversationDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun episode(
        id: String,
        status: EpisodeStatus = EpisodeStatus.SUMMARIZED,
        title: String? = "제목-$id",
        summary: String? = "요약-$id",
        tags: List<String> = listOf("태그"),
        createdAt: Long = id.hashCode().toLong()
    ) = Episode(
        id = id, sessionId = "s1", status = status, title = title, summary = summary,
        tags = tags, startAt = 100, endAt = 200, messageCount = 4, retryCount = 0,
        createdAt = createdAt, updatedAt = createdAt
    )

    private fun message(
        id: String,
        createdAt: Long,
        episodeId: String? = null,
        recall: List<String> = emptyList()
    ) = ChatMessage(
        id = id, sessionId = "s1", role = ChatMessage.Role.ASSISTANT, content = "내용-$id",
        inputType = InputType.TEXT, createdAt = createdAt,
        episodeId = episodeId, recallEpisodeIds = recall
    )

    private fun <T> AppResult<T>.get(): T = (this as AppResult.Success).data

    // --- 에피소드 왕복·검색 ---

    @Test
    fun `에피소드가 상태·태그를 잃지 않고 왕복한다`() = runBlocking {
        episodes.insert(episode("e1", tags = listOf("자전거", "비밀번호"))).get()

        val loaded = episodes.getById("e1").get()!!

        assertEquals(EpisodeStatus.SUMMARIZED, loaded.status)
        assertEquals(listOf("자전거", "비밀번호"), loaded.tags)
        assertEquals("제목-e1", loaded.title)
    }

    @Test
    fun `검색은 SUMMARIZED 만 대상이다`() = runBlocking {
        // [WHY] OPEN/CLOSED 는 아직 요약이 없고(title NULL), FAILED 는 아카이브 미노출 계약이다.
        episodes.insert(episode("open", status = EpisodeStatus.OPEN, title = null, summary = "자전거 얘기")).get()
        episodes.insert(episode("done", summary = "자전거 비밀번호를 저장했다")).get()

        val hits = episodes.search("자전거").get()

        assertEquals(listOf("done"), hits.map { it.id })
    }

    @Test
    fun `검색어의 LIKE 와일드카드가 이스케이프된다`() = runBlocking {
        episodes.insert(episode("e1", summary = "100% 완료")).get()
        episodes.insert(episode("e2", summary = "무관한 내용")).get()

        val hits = episodes.search("100%").get()

        // [WHY] 이스케이프가 없으면 '%' 가 와일드카드라 e2 도 걸린다.
        assertEquals(listOf("e1"), hits.map { it.id })
    }

    @Test
    fun `태그는 토큰 경계로 정확히 매칭된다`() = runBlocking {
        episodes.insert(episode("e1", tags = listOf("work"))).get()
        episodes.insert(episode("e2", tags = listOf("workflow"))).get()

        val hits = episodes.searchByTags("work").get()

        assertEquals(listOf("e1"), hits.map { it.id })
    }

    // --- 대화 매퍼 왕복 (thinkingProcess 소실 전례 회귀) ---

    @Test
    fun `episodeId 와 recallEpisodeIds 가 저장-재조회에서 소실되지 않는다`() = runBlocking {
        conversations.save(message("m1", createdAt = 100, episodeId = "e1", recall = listOf("e1", "e2"))).get()

        val loaded = conversations.getByEpisode("e1").get().single()

        assertEquals("e1", loaded.episodeId)
        assertEquals(listOf("e1", "e2"), loaded.recallEpisodeIds)
    }

    @Test
    fun `회수 없는 메시지는 빈 목록으로 돌아온다`() = runBlocking {
        conversations.save(message("m1", createdAt = 100)).get()

        val loaded = conversations.getUnassigned().get().single()

        assertTrue(loaded.recallEpisodeIds.isEmpty())
        assertEquals(null, loaded.episodeId)
    }

    // --- 타임라인 쿼리 (M2-3·M2-5 의 데이터 계약) ---

    @Test
    fun `getPagedAll 은 앵커 이전만 최신순으로 준다`() = runBlocking {
        (1..5).forEach { conversations.save(message("m$it", createdAt = it * 100L)).get() }

        val page = conversations.getPagedAll(beforeTs = 400, offset = 0, limit = 10).get()

        // 400 미만 = m1(100), m2(200), m3(300) — DESC
        assertEquals(listOf("m3", "m2", "m1"), page.map { it.id })
    }

    @Test
    fun `countNewerThan 은 경계를 포함한다`() = runBlocking {
        (1..5).forEach { conversations.save(message("m$it", createdAt = it * 100L)).get() }

        assertEquals(3, conversations.countNewerThan(300).get())
    }

    @Test
    fun `assignEpisode 는 해당 행만 갱신한다`() = runBlocking {
        conversations.save(message("m1", createdAt = 100)).get()
        conversations.save(message("m2", createdAt = 200)).get()

        conversations.assignEpisode("m1", "e9").get()

        assertEquals(listOf("m1"), conversations.getByEpisode("e9").get().map { it.id })
        assertEquals(listOf("m2"), conversations.getUnassigned().get().map { it.id })
    }
}

package com.kosmos.app.data

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.repository.DataResetRepositoryImpl
import com.kosmos.app.data.local.repository.EpisodeRepositoryImpl
import com.kosmos.app.data.local.repository.KnowledgeRepositoryImpl
import com.kosmos.app.data.local.repository.MemoryVectorRepositoryImpl
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.memory.MemoryVectorKey
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.EpisodeStatus
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.testing.InMemoryKosmosDbRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MemoryVectorRepositoryTest]
 * 기억 뜻 벡터(0.41.3 M2) — 실제 Room 스키마 위에서 대상 세기·만들 것 고르기·저장·모델 교체, 그리고 메모·대화가 바뀔 때의 무효화.
 */
@RunWith(RobolectricTestRunner::class)
class MemoryVectorRepositoryTest {

    @get:Rule
    val dbRule = InMemoryKosmosDbRule()

    private lateinit var vectors: MemoryVectorRepositoryImpl
    private lateinit var notes: KnowledgeRepositoryImpl
    private lateinit var episodes: EpisodeRepositoryImpl

    private fun <T> AppResult<T>.ok(): T {
        assertTrue(toString(), this is AppResult.Success)
        return (this as AppResult.Success).data
    }

    private fun note(id: String, content: String, tags: List<String> = emptyList(), at: Long = 1) =
        KnowledgeNote(id = id, content = content, tags = tags, createdAt = at, updatedAt = at)

    private fun episode(id: String, status: EpisodeStatus, summary: String? = "요약 $id") = Episode(
        id = id, sessionId = "s", status = status, title = "제목 $id", summary = summary, tags = listOf("태그"),
        startAt = 0, endAt = 1, messageCount = 2, retryCount = 0, createdAt = 1, updatedAt = 1
    )

    private val v = Int8Vector.quantize(floatArrayOf(1f, 0f))

    /** 지금 만들 것 전부에 벡터를 넣는다 — 만든 개수. */
    private fun embedAll(model: String = "m1") = runBlocking {
        val pending = vectors.pending(model, 100).ok()
        vectors.save(model, pending.map { it.key to v }).ok()
        pending.size
    }

    @Before
    fun setUp() = runBlocking {
        val db = dbRule.db
        vectors = MemoryVectorRepositoryImpl(db.memoryVectorDao())
        notes = KnowledgeRepositoryImpl(db.knowledgeDao(), db.memoryVectorDao())
        episodes = EpisodeRepositoryImpl(db.episodeDao(), db.memoryVectorDao())
        notes.save(note("k1", "자전거 비밀번호 4821", listOf("자전거", "비밀번호"))).ok()
        notes.save(note("k2", "땅콩 알레르기", at = 2)).ok()
        episodes.insert(episode("e1", EpisodeStatus.SUMMARIZED)).ok()
        episodes.insert(episode("e2", EpisodeStatus.OPEN, summary = null)).ok()
        Unit
    }

    @Test
    fun `대상은 메모 전부와 요약된 대화 - 입력 글자는 태그를 제목 자리에`() = runBlocking {
        val status = vectors.status("m1").ok()
        assertEquals(3, status.targets)
        assertEquals(0, status.embedded)
        assertFalse(status.ready)

        val pending = vectors.pending("m1", 10).ok()
        assertEquals(listOf(MemoryVectorKey.note("k2"), MemoryVectorKey.note("k1"), MemoryVectorKey.episode("e1")), pending.map { it.key })
        assertEquals("title: 자전거 비밀번호 | text: 자전거 비밀번호 4821", pending.single { it.key.refId == "k1" }.input)
        assertEquals("title: 제목 e1 태그 | text: 요약 e1", pending.single { it.key.refId == "e1" }.input)
    }

    @Test
    fun `다 만들면 준비 - 모델이 바뀌면 옛 벡터는 지운다`() = runBlocking {
        assertEquals(3, embedAll())
        assertTrue(vectors.status("m1").ok().ready)
        assertEquals(3, vectors.vectors("m1").ok().size)

        assertEquals(3, embedAll("m2"))
        assertEquals(0, vectors.vectors("m1").ok().size)
        assertTrue(vectors.status("m2").ok().ready)
    }

    @Test
    fun `메모를 고치거나 지우면 그 메모의 벡터만 사라진다`() = runBlocking {
        embedAll()
        notes.save(note("k1", "자전거 비밀번호 1234", listOf("자전거"))).ok()
        assertEquals(listOf(MemoryVectorKey.note("k1")), vectors.pending("m1", 10).ok().map { it.key })

        embedAll()
        notes.delete("k2").ok()
        val status = vectors.status("m1").ok()
        assertEquals(2, status.targets)
        assertTrue("지운 메모는 대상에서도 빠져 준비는 그대로", status.ready)
        assertFalse(MemoryVectorKey.note("k2") in vectors.vectors("m1").ok())
    }

    @Test
    fun `대화를 고치거나 합치거나 지우면 벡터가 사라지고 요약된 것만 대상이다`() = runBlocking {
        embedAll()
        episodes.update(episode("e2", EpisodeStatus.SUMMARIZED)).ok()
        assertEquals(listOf(MemoryVectorKey.episode("e2")), vectors.pending("m1", 10).ok().map { it.key })

        embedAll()
        episodes.mergeInto("e2", episode("e1", EpisodeStatus.SUMMARIZED, summary = "합친 요약")).ok()
        assertEquals(listOf(MemoryVectorKey.episode("e1")), vectors.pending("m1", 10).ok().map { it.key })

        embedAll()
        episodes.delete("e1").ok()
        assertEquals(2, vectors.status("m1").ok().targets)
        assertTrue(vectors.status("m1").ok().ready)
    }

    @Test
    fun `데이터 초기화는 고른 대상의 벡터만 지운다`() = runBlocking {
        embedAll()
        DataResetRepositoryImpl(dbRule.db).reset(setOf(ResetTarget.KNOWLEDGE)).ok()

        val left = vectors.vectors("m1").ok().keys
        assertEquals(setOf(MemoryVectorKey.episode("e1")), left)
    }

    @Test
    fun `모델을 지우면 전부 지운다`() = runBlocking {
        embedAll()
        vectors.clear().ok()
        assertEquals(0, vectors.status("m1").ok().embedded)
    }

    @Test
    fun `후보 키의 벡터만 읽고 SQLite 변수 상한을 넘는 키 목록도 묶어 읽는다(0·41·6 M4)`() = runBlocking {
        embedAll()
        val picked = vectors.vectorsFor("m1", listOf(MemoryVectorKey.note("k1"), MemoryVectorKey.episode("e1"))).ok()
        assertEquals(setOf(MemoryVectorKey.note("k1"), MemoryVectorKey.episode("e1")), picked.keys)

        // 없는 키 1,200개를 섞어도(변수 상한 999 초과) 있는 것만 돌아온다.
        val many = (1..1_200).map { MemoryVectorKey.note("missing$it") } + MemoryVectorKey.note("k2")
        assertEquals(setOf(MemoryVectorKey.note("k2")), vectors.vectorsFor("m1", many).ok().keys)
        assertTrue("다른 모델 벡터는 읽지 않는다", vectors.vectorsFor("m2", listOf(MemoryVectorKey.note("k1"))).ok().isEmpty())
    }
}

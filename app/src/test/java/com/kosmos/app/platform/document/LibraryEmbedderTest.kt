package com.kosmos.app.platform.document

import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.LibraryVectorInput
import com.kosmos.app.domain.library.TextEmbedder
import com.kosmos.app.testing.FakeLibraryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LibraryEmbedderTest]
 * 뜻 벡터 만들기(0.41.2 M3) — 사용자가 시작한 일만: 버튼은 전부, 등록은 그 문서만, 모델이 없으면 아무것도, 채팅 중 멈춤·이어 가기.
 */
class LibraryEmbedderTest {

    private val repo = FakeLibraryRepository(
        listOf(
            LibraryDocument("d1", "content://1", "민법", null, 1, LibraryStatus.READY, chunkCount = 20),
            LibraryDocument("d2", "content://2", "형법", null, 1, LibraryStatus.READY, chunkCount = 3)
        )
    ).apply {
        pendingInputs += (1L..20L).map { LibraryVectorInput(it, "d1", "title: 민법 | text: $it") }
        pendingInputs += (21L..23L).map { LibraryVectorInput(it, "d2", "title: 형법 | text: $it") }
    }

    private class FakeEmbedder : TextEmbedder {
        override val modelId = "fake"
        var calls = 0
        /** null 이 아니면 그 묶음에서 멈춘다 — 채팅 시작을 흉내 낸다. */
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun embedQuery(query: String) = floatArrayOf(1f, 0f)
        override suspend fun embedDocuments(inputs: List<String>): List<FloatArray> {
            calls++
            gate?.await()
            return inputs.map { floatArrayOf(1f, 0f) }
        }
    }

    private suspend fun LibraryEmbedder.awaitIdle() = withTimeout(5_000) { state.first { it == LibraryEmbedder.State.Idle } }

    @Test
    fun `버튼은 벡터 없는 조각 전부를 묶음으로 만들고 끝나면 쉰다`() = runBlocking {
        val fake = FakeEmbedder()
        val embedder = LibraryEmbedder(repo) { fake }

        embedder.prepareAll()
        withTimeout(5_000) { while (repo.vectors.size < 23) kotlinx.coroutines.delay(10) }
        embedder.awaitIdle()

        assertEquals(23, repo.vectors.size)
        assertTrue(repo.vectors.values.all { it.first == "fake" })
        assertEquals("16개씩 두 묶음", 2, fake.calls)
    }

    @Test
    fun `등록에 이어서는 그 문서만 만든다`() = runBlocking {
        val embedder = LibraryEmbedder(repo) { FakeEmbedder() }

        embedder.prepare("d2")
        withTimeout(5_000) { while (repo.vectors.size < 3) kotlinx.coroutines.delay(10) }
        embedder.awaitIdle()

        assertEquals(setOf(21L, 22L, 23L), repo.vectors.keys)
    }

    @Test
    fun `모델이 없으면 아무것도 하지 않는다`() = runBlocking {
        val embedder = LibraryEmbedder(repo) { null }

        embedder.prepareAll()
        embedder.prepare("d1")

        assertEquals(LibraryEmbedder.State.Idle, embedder.state.value)
        assertTrue(repo.vectors.isEmpty())
    }

    @Test
    fun `채팅이 시작되면 멈추고 답이 끝나면 남은 조각부터 이어 간다`() = runBlocking {
        val fake = FakeEmbedder().apply { gate = CompletableDeferred() }
        val embedder = LibraryEmbedder(repo) { fake }

        embedder.prepareAll()
        withTimeout(5_000) { embedder.state.first { it is LibraryEmbedder.State.Running } }
        embedder.onUserActivity()
        assertTrue(embedder.state.value is LibraryEmbedder.State.Paused)
        assertTrue("멈춘 묶음은 저장되지 않는다", repo.vectors.isEmpty())

        fake.gate = null
        embedder.onTurnFinished()
        withTimeout(5_000) { while (repo.vectors.size < 23) kotlinx.coroutines.delay(10) }
        embedder.awaitIdle()
        assertEquals(23, repo.vectors.size)
    }

    @Test
    fun `임베딩이 실패하면 실패 상태로 남고 다시 누르면 남은 것부터`() = runBlocking {
        var broken = true
        val fake = object : TextEmbedder {
            override val modelId = "fake"
            override suspend fun embedQuery(query: String) = null
            override suspend fun embedDocuments(inputs: List<String>) = if (broken) null else inputs.map { floatArrayOf(1f) }
        }
        val embedder = LibraryEmbedder(repo) { fake }

        embedder.prepareAll()
        withTimeout(5_000) { embedder.state.first { it == LibraryEmbedder.State.Failed } }

        broken = false
        embedder.prepareAll()
        withTimeout(5_000) { while (repo.vectors.size < 23) kotlinx.coroutines.delay(10) }
        embedder.awaitIdle()
        assertEquals(23, repo.vectors.size)
    }
}

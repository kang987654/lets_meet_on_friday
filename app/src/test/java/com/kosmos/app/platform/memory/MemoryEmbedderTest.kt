package com.kosmos.app.platform.memory

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.library.Int8Vector
import com.kosmos.app.domain.library.TextEmbedder
import com.kosmos.app.domain.memory.MemorySemanticStatus
import com.kosmos.app.domain.memory.MemoryVectorInput
import com.kosmos.app.domain.memory.MemoryVectorKey
import com.kosmos.app.domain.memory.MemoryVectorRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kosmos.app.runtime.metrics.RuntimeMetricsCollector
import io.mockk.every
import io.mockk.mockk

/**
 * [MemoryEmbedderTest]
 * 기억 뜻 벡터 만들기(0.41.3 M2) — 처음은 버튼, 한 번 준비한 뒤로는 새 기억을 조용할 때 자동, 채팅·백그라운드에서는 멈춤.
 */
class MemoryEmbedderTest {

    private class FakeRepo : MemoryVectorRepository {
        val targets = linkedMapOf<MemoryVectorKey, String>()
        val vectors = mutableMapOf<MemoryVectorKey, String>()

        fun add(id: String) {
            targets[MemoryVectorKey.note(id)] = "title: none | text: $id"
        }

        override suspend fun status(modelId: String?) = AppResult.Success(
            MemorySemanticStatus(modelId, targets.size, targets.keys.count { vectors[it] == modelId })
        )

        override suspend fun pending(modelId: String, limit: Int) = AppResult.Success(
            targets.filterKeys { vectors[it] != modelId }.map { (k, input) -> MemoryVectorInput(k, input) }.take(limit)
        )

        override suspend fun save(modelId: String, vectors: List<Pair<MemoryVectorKey, Int8Vector>>): AppResult<Unit> {
            vectors.forEach { (k, _) -> this.vectors[k] = modelId }
            return AppResult.Success(Unit)
        }

        override suspend fun vectors(modelId: String) = AppResult.Success(emptyMap<MemoryVectorKey, Int8Vector>())
        override suspend fun clear(): AppResult<Unit> = AppResult.Success(Unit).also { vectors.clear() }
    }

    private class FakeEmbedder : TextEmbedder {
        override val modelId = "m"
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun embedQuery(query: String) = floatArrayOf(1f)
        override suspend fun embedDocuments(inputs: List<String>): List<FloatArray> {
            gate?.await()
            return inputs.map { floatArrayOf(1f) }
        }
    }

    private suspend fun waitUntil(cond: () -> Boolean) = withTimeout(5_000) { while (!cond()) delay(10) }

    private fun setUp(installed: Boolean = true): Triple<FakeRepo, FakeEmbedder, MemoryEmbedder> {
        val repo = FakeRepo().apply { (1..20).forEach { add("n$it") } }
        val fake = FakeEmbedder()
        val embedder = MemoryEmbedder(repo) { if (installed) fake else null }.apply { resumeDelayMs = 0 }
        return Triple(repo, fake, embedder)
    }

    @Test
    fun `버튼은 모자란 기억 전부를 만든다`() = runBlocking {
        val (repo, _, embedder) = setUp()

        embedder.prepareAll()
        waitUntil { repo.vectors.size == 20 }
        withTimeout(5_000) { embedder.state.first { it == MemoryEmbedder.State.Idle } }
        assertEquals(20, repo.vectors.size)
    }

    @Test
    fun `한 번도 준비하지 않았으면 새 기억이 생겨도 자동으로 만들지 않는다`() = runBlocking {
        val (repo, _, embedder) = setUp()
        embedder.onForeground()

        repo.add("new")
        embedder.onTurnFinished()
        delay(300)
        assertTrue(repo.vectors.isEmpty())
    }

    @Test
    fun `한 번 준비한 뒤로는 새 기억을 자동으로 만든다`() = runBlocking {
        val (repo, _, embedder) = setUp()
        embedder.onForeground()
        embedder.prepareAll()
        waitUntil { repo.vectors.size == 20 }
        withTimeout(5_000) { embedder.state.first { it == MemoryEmbedder.State.Idle } }

        repo.add("new")
        embedder.onTurnFinished()
        waitUntil { repo.vectors.size == 21 }
    }

    @Test
    fun `채팅 중과 백그라운드에서는 자동으로 만들지 않고 답이 끝나면 만든다`() = runBlocking {
        val (repo, _, embedder) = setUp()
        embedder.onForeground()
        embedder.prepareAll()
        waitUntil { repo.vectors.size == 20 }
        withTimeout(5_000) { embedder.state.first { it == MemoryEmbedder.State.Idle } }

        embedder.onUserActivity()
        repo.add("during-chat")
        delay(200)
        assertEquals("채팅 중에는 그대로", 20, repo.vectors.size)

        embedder.onTurnFinished()
        waitUntil { repo.vectors.size == 21 }

        embedder.onBackground()
        repo.add("in-background")
        delay(200)
        assertEquals("백그라운드에서는 그대로", 21, repo.vectors.size)
        embedder.onForeground()
        waitUntil { repo.vectors.size == 22 }
    }

    @Test
    fun `채팅이 시작되면 하던 묶음을 저장하지 않고 멈춘다`() = runBlocking {
        val (repo, fake, embedder) = setUp()
        fake.gate = CompletableDeferred()

        embedder.prepareAll()
        withTimeout(5_000) { embedder.state.first { it is MemoryEmbedder.State.Running } }
        embedder.onUserActivity()
        fake.gate = null
        delay(200)
        assertTrue(repo.vectors.isEmpty())
        assertEquals(MemoryEmbedder.State.Idle, embedder.state.value)
    }

    @Test
    fun `모델이 없으면 아무것도 하지 않는다`() = runBlocking {
        val (repo, _, embedder) = setUp(installed = false)
        embedder.onForeground()
        embedder.prepareAll()
        embedder.onTurnFinished()
        delay(200)
        assertTrue(repo.vectors.isEmpty())
    }

    @Test
    fun `발열이면 묶음 사이에서 쉬고 식으면 이어 간다(0·41·6 M3)`() = runBlocking {
        val repo = FakeRepo().apply { (1..20).forEach { add("n$it") } }
        val metrics = mockk<RuntimeMetricsCollector> { every { getCurrentTemp() } returnsMany listOf(99f, 99f) andThen 20f }
        val embedder = MemoryEmbedder(repo, metrics) { FakeEmbedder() }.apply { resumeDelayMs = 0; thermalRecheckMs = 200 }

        embedder.prepareAll()
        delay(100)
        assertTrue("뜨거운 동안은 저장하지 않는다", repo.vectors.isEmpty())
        waitUntil { repo.vectors.size == 20 }
    }
}

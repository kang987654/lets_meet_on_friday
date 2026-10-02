package com.kosmos.app.feature.document

import android.net.Uri
import com.kosmos.app.data.local.prefs.RecentDocumentsStore
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.platform.document.DocumentAccess
import com.kosmos.app.testing.InMemoryPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [DocumentHomeViewModelTest]
 * 문서 홈 — 고른 파일은 영구 권한을 받아 목록에 올리고 연다, 권한을 못 받아도 이번 한 번은 연다, 권한이 사라진 최근 문서는
 * 목록에서 지우고 알린다, 목록에서 밀려나거나 지운 문서는 권한을 돌려준다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentHomeViewModelTest {

    private lateinit var store: RecentDocumentsStore
    private lateinit var viewModel: DocumentHomeViewModel

    private val granted = mutableSetOf<String>()
    private val released = mutableListOf<String>()
    private var persistFails = false

    private val access = object : DocumentAccess {
        override suspend fun persist(uri: Uri, now: Long): RecentDocument? {
            if (persistFails) return null
            granted += uri.toString()
            return RecentDocument(uri.toString(), "${uri}.pdf", "application/pdf", now)
        }
        override fun hasAccess(uri: String) = uri in granted
        override fun release(uri: String) { released += uri; granted -= uri }
    }

    private fun uri(s: String): Uri = mockk { every { this@mockk.toString() } returns s }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        store = RecentDocumentsStore(InMemoryPreferences())
        viewModel = DocumentHomeViewModel(store, access)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun nextEvent() = runBlocking { withTimeout(5_000) { viewModel.events.first() } }
    private fun recent() = runBlocking { store.recentFlow.first() }

    @Test
    fun `고른 파일은 목록 맨 위에 올리고 연다`() {
        viewModel.onPicked(uri("content://a"), now = 10)
        assertEquals(DocumentHomeViewModel.Event.Open("content://a", "application/pdf"), nextEvent())
        viewModel.onPicked(uri("content://b"), now = 20)
        nextEvent()
        assertEquals(listOf("content://b", "content://a"), recent().map { it.uri })
    }

    @Test
    fun `영구 권한을 못 받아도 이번 한 번은 열고 목록에는 남기지 않는다`() {
        persistFails = true
        viewModel.onPicked(uri("content://x"))
        assertEquals(DocumentHomeViewModel.Event.Open("content://x", null), nextEvent())
        assertEquals(emptyList<RecentDocument>(), recent())
    }

    @Test
    fun `권한이 사라진 최근 문서는 목록에서 지우고 알린다`() {
        viewModel.onPicked(uri("content://a"), now = 10)
        nextEvent()
        granted.clear()

        viewModel.open(recent().single(), now = 30)
        assertEquals(DocumentHomeViewModel.Event.AccessLost("content://a.pdf"), nextEvent())
        assertEquals(emptyList<RecentDocument>(), recent())
    }

    @Test
    fun `최근 문서를 열면 열람 시각을 갱신한다`() {
        viewModel.onPicked(uri("content://a"), now = 10)
        nextEvent()
        viewModel.open(recent().single(), now = 30)
        nextEvent()
        assertEquals(30L, recent().single().openedAt)
    }

    @Test
    fun `밀려나거나 지운 문서는 권한을 돌려준다`() {
        (1..21).forEach { n ->
            viewModel.onPicked(uri("content://$n"), now = n.toLong())
            nextEvent()
        }
        assertEquals(listOf("content://1"), released)

        viewModel.remove(recent().first())
        runBlocking { withTimeout(5_000) { while (released.size < 2) kotlinx.coroutines.delay(10) } }
        assertEquals("content://21", released.last())
    }
}

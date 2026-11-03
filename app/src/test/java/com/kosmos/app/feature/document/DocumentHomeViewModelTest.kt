package com.kosmos.app.feature.document

import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.domain.library.LibraryDocument
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import com.kosmos.app.platform.document.LibraryIndexer
import com.kosmos.app.testing.FakeLibraryRepository
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

    private fun uri(s: String): Uri = mockk {
        every { this@mockk.toString() } returns s
        every { lastPathSegment } returns s.substringAfterLast('/')
    }

    private val library = FakeLibraryRepository()
    private val added = mutableListOf<Pair<String, String>>()
    private val indexer = mockk<LibraryIndexer>(relaxed = true) {
        every { progress } returns MutableStateFlow(emptyMap())
        every { add(any(), any(), any(), any()) } answers { added += firstArg<Uri>().toString() to secondArg<String>(); Job() }
        every { remove(any()) } answers { runBlocking { library.remove(firstArg()) }; Job().apply { complete() } }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        store = RecentDocumentsStore(InMemoryPreferences())
        viewModel = DocumentHomeViewModel(store, access, library, indexer)
    }

    @After
    fun tearDown() {
        // [WHY] 보관함 목록 stateIn(WhileSubscribed) 이 구독이 끝난 뒤 메인 디스패처에 정지 타이머를 건다 — resetMain 뒤에 터지지 않게 먼저 닫는다.
        viewModel.viewModelScope.cancel()
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

    @Test
    fun `보관함에 고른 파일은 영구 권한을 받아 색인을 띄우고 권한을 못 받으면 넣지 않고 알린다`() {
        viewModel.onLibraryPicked(uri("content://law"), now = 5)
        runBlocking { withTimeout(5_000) { while (added.isEmpty()) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf("content://law" to "content://law.pdf"), added)

        persistFails = true
        viewModel.onLibraryPicked(uri("content://x/scan"))
        assertEquals(DocumentHomeViewModel.Event.LibraryAccessFailed("scan"), nextEvent())
        assertEquals(1, added.size)
    }

    @Test
    fun `보관함에 있는 문서는 최근 목록에서 빠져도 권한을 쥐고 보관함에서 빼면 그때 돌려준다`() {
        viewModel.onPicked(uri("content://law"), now = 1)
        nextEvent()
        runBlocking {
            library.upsert(LibraryDocument("l1", "content://law", "민법", null, 1, LibraryStatus.READY))
        }

        viewModel.remove(recent().single())
        runBlocking { withTimeout(5_000) { while (recent().isNotEmpty()) kotlinx.coroutines.delay(10) } }
        Thread.sleep(100)
        assertTrue("보관함 문서의 권한은 돌려주지 않는다", released.isEmpty())

        viewModel.removeLibrary(DocumentHomeViewModel.LibraryItem(library.documents.value.single(), null))
        runBlocking { withTimeout(5_000) { while (released.isEmpty()) kotlinx.coroutines.delay(10) } }
        assertEquals(listOf("content://law"), released)
        assertTrue(library.documents.value.isEmpty())
    }

    @Test
    fun `실패한 보관함 문서를 누르면 다시 색인하고 숨긴 문서는 목록에서 빠졌다가 되돌리면 돌아온다`() {
        val failed = LibraryDocument("l2", "content://f", "스캔", null, 1, LibraryStatus.FAILED, error = "NO_TEXT")
        runBlocking { library.upsert(failed) }
        val item = DocumentHomeViewModel.LibraryItem(failed, null)

        viewModel.openLibrary(item)
        io.mockk.verify { indexer.reindex(failed) }

        val items = mutableListOf<List<DocumentHomeViewModel.LibraryItem>>()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined).launch { viewModel.libraryItems.collect { items += it } }
        viewModel.hideLibrary(item)
        assertTrue(items.last().isEmpty())
        viewModel.restoreLibrary(item)
        assertEquals(listOf("l2"), items.last().map { it.document.id })
        job.cancel()
    }
}

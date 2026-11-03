package com.kosmos.app.feature.document

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.data.local.prefs.RecentDocumentsStore
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.domain.library.LibraryDocument
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.library.LibraryStatus
import com.kosmos.app.platform.document.DocumentAccess
import com.kosmos.app.platform.document.LibraryIndexer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [DocumentHomeViewModel]
 * 드로어 "문서" 화면 — 파일 고르기, 최근 문서 (0.34.0), 문서 보관함 (0.41.0).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Document)
 * - **Dependencies**: [RecentDocumentsStore](DataStore), [DocumentAccess](영구 읽기 권한), [LibraryRepository]·[LibraryIndexer](보관함)
 *
 * ### Key Flow
 * 1. [onPicked]: 고른 파일의 영구 권한을 받아 목록 맨 위에 올리고 뷰어를 연다. 권한을 못 받아도 이번 한 번은 연다(목록에는 없음).
 * 2. [open]: 최근 문서를 연다 — 권한이 사라졌으면(파일 삭제·앱 재설치) 목록에서 지우고 다시 고르라고 알린다.
 * 3. 목록에서 밀려나거나 지운 문서는 권한도 돌려준다 — 단 보관함에 있는 문서는 쥐고 있는다(보관함 원문 열기·다시 색인에 필요).
 * 4. [onLibraryPicked]: 영구 권한을 받아 보관함에 넣고 색인을 띄운다. 권한을 못 받으면 넣지 않는다(다시 색인할 수 없다).
 * 5. 보관함에서 빼기는 휴지통+되돌리기 — [hideLibrary] 로 목록에서 숨기고, 되돌리기가 끝나면 [removeLibrary] 가 색인을 지운다.
 */
@HiltViewModel
class DocumentHomeViewModel @Inject constructor(
    private val store: RecentDocumentsStore,
    private val access: DocumentAccess,
    private val library: LibraryRepository,
    private val indexer: LibraryIndexer
) : ViewModel() {

    sealed interface Event {
        data class Open(val uri: String, val mimeType: String?) : Event
        data class AccessLost(val name: String) : Event
        data class LibraryAccessFailed(val name: String) : Event
    }

    /** 보관함 목록 한 줄 — 색인 중이면 진행률. */
    data class LibraryItem(val document: LibraryDocument, val progress: LibraryIndexer.Progress?)

    val recent: StateFlow<List<RecentDocument>> = store.recentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val hiddenLibrary = MutableStateFlow<Set<String>>(emptySet())

    val libraryItems: StateFlow<List<LibraryItem>> =
        combine(library.observeDocuments(), indexer.progress, hiddenLibrary) { documents, progress, hidden ->
            documents.filter { it.id !in hidden }.map { LibraryItem(it, progress[it.id]) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        // [WHY] 색인 중에 멈춘 문서를 실패로 돌리는 일은 보관함을 처음 보여 줄 때 한다 — KosmosApp.onCreate 에서 하면 Robolectric 테스트마다
        // 실제 DB 에 쓰기가 떠 테스트가 DB 를 닫은 뒤 터졌다(0.41.0 M3). 멱등이라 화면을 다시 열어도 한 번만 돈다.
        indexer.start()
    }

    fun onPicked(uri: Uri, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val document = access.persist(uri, now)
            if (document != null) remember(document)
            _events.send(Event.Open(uri.toString(), document?.mimeType))
        }
    }

    fun open(document: RecentDocument, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            if (!access.hasAccess(document.uri)) {
                store.remove(document.uri)
                _events.send(Event.AccessLost(document.name))
                return@launch
            }
            remember(document.copy(openedAt = now))
            _events.send(Event.Open(document.uri, document.mimeType))
        }
    }

    /**
     * 휴지통 — 목록에서 바로 빼되 읽기 권한은 쥐고 있는다. 되돌리기가 끝나면 [restore] 또는 [remove] 가 마무리한다.
     *
     * [WHY] 권한을 먼저 돌려주면 되돌려도 그 문서를 다시 열 수 없다 — 파일 고르기를 거친 권한은 다시 받을 수 없다(0.38.3 M4).
     */
    fun hide(document: RecentDocument) {
        viewModelScope.launch { store.remove(document.uri) }
    }

    /** 되돌리기 — 원래 자리로. */
    fun restore(document: RecentDocument) {
        viewModelScope.launch { store.restore(document) }
    }

    /** 목록에서 빼고 읽기 권한을 돌려준다 — 되돌리기 시간이 지난 뒤의 마무리이기도 하다. */
    fun remove(document: RecentDocument) {
        viewModelScope.launch {
            store.remove(document.uri)
            releaseUnlessInLibrary(document.uri)
        }
    }

    /** 보관함에 넣을 파일을 골랐다 — 영구 권한을 받아야 넣는다. */
    fun onLibraryPicked(uri: Uri, now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val document = access.persist(uri, now)
            if (document == null) {
                _events.send(Event.LibraryAccessFailed(uri.lastPathSegment.orEmpty()))
                return@launch
            }
            // [WHY] 이름이 비면(제공자가 이름을 안 주면) 조각 머리말이 " > 제1조…"로 시작한다 — URI 끝 조각으로 메운다.
            val name = document.name.ifBlank { uri.lastPathSegment.orEmpty() }
            indexer.add(uri, name, document.mimeType, now)
        }
    }

    /** 보관함 문서를 누름 — 준비됐으면 원문을 열고, 실패했으면 다시 색인한다. 색인 중이면 아무것도 하지 않는다. */
    fun openLibrary(item: LibraryItem) {
        val document = item.document
        when (document.status) {
            LibraryStatus.READY -> viewModelScope.launch {
                if (access.hasAccess(document.uri)) _events.send(Event.Open(document.uri, document.mimeType))
                else _events.send(Event.AccessLost(document.name))
            }
            LibraryStatus.FAILED -> indexer.reindex(document)
            LibraryStatus.INDEXING -> Unit
        }
    }

    fun hideLibrary(item: LibraryItem) {
        hiddenLibrary.update { it + item.document.id }
    }

    fun restoreLibrary(item: LibraryItem) {
        hiddenLibrary.update { it - item.document.id }
    }

    /** 보관함에서 빼고 색인을 지운다 — 최근 문서에도 없으면 읽기 권한도 돌려준다. */
    fun removeLibrary(item: LibraryItem) {
        viewModelScope.launch {
            indexer.remove(item.document.id).join()
            hiddenLibrary.update { it - item.document.id }
            if (store.recentFlow.first().none { it.uri == item.document.uri }) access.release(item.document.uri)
        }
    }

    private suspend fun remember(document: RecentDocument) {
        store.add(document).forEach { releaseUnlessInLibrary(it) }
    }

    // [WHY] 최근 목록과 보관함이 같은 영구 권한을 나눠 쓴다 — 최근 목록에서 빠졌다고 돌려주면 보관함의 원문 열기·다시 색인이 깨진다(0.41.0).
    private suspend fun releaseUnlessInLibrary(uri: String) {
        if (library.observeDocuments().first().none { it.uri == uri }) access.release(uri)
    }
}

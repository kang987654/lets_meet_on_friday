package com.kosmos.app.feature.document

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.data.local.prefs.RecentDocumentsStore
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.platform.document.DocumentAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [DocumentHomeViewModel]
 * 드로어 "문서" 화면 — 파일 고르기와 최근 문서 (0.34.0).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Document)
 * - **Dependencies**: [RecentDocumentsStore](DataStore), [DocumentAccess](영구 읽기 권한)
 *
 * ### Key Flow
 * 1. [onPicked]: 고른 파일의 영구 권한을 받아 목록 맨 위에 올리고 뷰어를 연다. 권한을 못 받아도 이번 한 번은 연다(목록에는 없음).
 * 2. [open]: 최근 문서를 연다 — 권한이 사라졌으면(파일 삭제·앱 재설치) 목록에서 지우고 다시 고르라고 알린다.
 * 3. 목록에서 밀려나거나 지운 문서는 권한도 돌려준다.
 */
@HiltViewModel
class DocumentHomeViewModel @Inject constructor(
    private val store: RecentDocumentsStore,
    private val access: DocumentAccess
) : ViewModel() {

    sealed interface Event {
        data class Open(val uri: String, val mimeType: String?) : Event
        data class AccessLost(val name: String) : Event
    }

    val recent: StateFlow<List<RecentDocument>> = store.recentFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

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

    fun remove(document: RecentDocument) {
        viewModelScope.launch {
            store.remove(document.uri)
            access.release(document.uri)
        }
    }

    private suspend fun remember(document: RecentDocument) {
        val before = store.recentFlow.first().map { it.uri }.toSet()
        store.add(document)
        val after = store.recentFlow.first().map { it.uri }.toSet()
        (before - after).forEach(access::release)
    }
}

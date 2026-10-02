package com.kosmos.app.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.domain.document.RecentDocuments
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecentDocumentsStore]
 * 문서 홈의 최근 문서 목록 저장소 (0.34.0) — 설정 DataStore 의 키 하나.
 *
 * ### Architecture Context
 * - **Layer**: Data (Prefs)
 * - **Dependencies**: DataStore<Preferences>, [RecentDocuments](목록 규칙·저장 형식)
 *
 * [WHY] Room 이 아니라 DataStore — 문서 기능은 DB 를 열지 않는다(뷰어 경량화, 계획서 D3). 앱 안에서 고른(영구 권한을 받은)
 * 문서만 기록한다 — 다른 앱의 "연결 프로그램" 진입은 임시 권한이라 나중에 다시 열 수 없다.
 */
@Singleton
class RecentDocumentsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val recentFlow: Flow<List<RecentDocument>> = dataStore.data.map { RecentDocuments.decode(it[KEY]) }

    /** 맨 위에 올린다. @return 상한([RecentDocuments.MAX])에 밀려 목록에서 빠진 문서의 URI — 호출자가 권한을 돌려준다. */
    suspend fun add(document: RecentDocument): List<String> {
        var dropped = emptyList<String>()
        dataStore.edit { prefs ->
            val before = RecentDocuments.decode(prefs[KEY])
            val after = RecentDocuments.add(before, document)
            dropped = (before.map { it.uri } - after.map { it.uri }.toSet())
            prefs[KEY] = RecentDocuments.encode(after)
        }
        return dropped
    }

    suspend fun remove(uri: String) {
        dataStore.edit { prefs -> prefs[KEY] = RecentDocuments.encode(RecentDocuments.remove(RecentDocuments.decode(prefs[KEY]), uri)) }
    }

    private companion object {
        val KEY = stringPreferencesKey("recent_documents")
    }
}

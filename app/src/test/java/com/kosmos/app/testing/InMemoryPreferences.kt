package com.kosmos.app.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [WHY] 파일 DataStore 를 쓰지 않는다 — Windows JVM 에서는 두 번째 쓰기의 임시 파일 rename 이 대상 파일이 있으면 실패한다
 * ("Unable to rename …"). 안드로이드에서는 생기지 않는 문제라, 저장 규칙만 검증하는 이 테스트는 메모리 저장소를 쓴다.
 */
class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        lock.withLock { transform(state.value).also { state.value = it } }
}

package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.ui.paging.DefaultPagingSource
import com.kosmos.app.ui.paging.unwrapForPaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class AuditViewModel @Inject constructor(
    private val auditRepository: AuditRepository
) : ViewModel() {

    // [WHY] Pager 생성이 여기 있는 이유 — 리포지토리가 Flow<PagingData>를 반환하면
    // Pure Kotlin JVM 모듈인 :domain 이 androidx 타입을 공개 계약에 노출한다. 페이징은
    // UI 관심사이므로 offset/limit 계약만 받아 여기서 조립한다.
    val auditLogPagingData: Flow<PagingData<AuditEvent>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            DefaultPagingSource { offset, limit ->
                auditRepository.getEvents(offset, limit).unwrapForPaging()
            }
        }.flow.cachedIn(viewModelScope)
}

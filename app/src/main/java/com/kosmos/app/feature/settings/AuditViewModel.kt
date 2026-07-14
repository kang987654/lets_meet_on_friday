package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class AuditViewModel @Inject constructor(
    private val auditRepository: AuditRepository
) : ViewModel() {

    val auditLogPagingData: Flow<PagingData<AuditEvent>> =
        auditRepository.getPaged().cachedIn(viewModelScope)
}

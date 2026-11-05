package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditHashChain
import com.kosmos.app.domain.memory.AuditRepository
import com.kosmos.app.domain.model.AuditEvent
import com.kosmos.app.ui.paging.DefaultPagingSource
import com.kosmos.app.ui.paging.unwrapForPaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 활동 기록 체인 검증 상태(0.41.4) — 화면은 [Broken] 일 때만 경고 띠를, 끝에 [Ok.head] 를 보인다. */
sealed interface AuditChainUi {
    data object Checking : AuditChainUi

    /** @param head 마지막 체인 행의 번호와 지문 — 체인 행이 없으면 null */
    data class Ok(val verified: Int, val legacy: Int, val head: Pair<Long, String>?) : AuditChainUi

    data class Broken(val chainSeq: Long, val reason: AuditHashChain.Reason) : AuditChainUi

    /** 검증을 못 했다(DB 읽기 실패) — 경고가 아니라 아무것도 보이지 않는다. */
    data object Unknown : AuditChainUi
}

/**
 * [AuditViewModel]
 * 활동 기록 목록(페이징)과 해시 체인 검증 상태(0.41.4).
 *
 * ### Key Flow
 * 1. 목록은 offset/limit 저장소 계약을 여기서 Pager 로 조립한다.
 * 2. 화면에 들어오면 체인을 쪽 단위로 검증한다(IO — 화면을 막지 않는다) → [chain].
 */
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

    private val _chain = MutableStateFlow<AuditChainUi>(AuditChainUi.Checking)
    val chain: StateFlow<AuditChainUi> = _chain.asStateFlow()

    init {
        // [WHY] 화면 단위 뷰모델이라 init 에서 시작해도 된다(앱 수명 싱글턴의 init 구독 금지 — AGENTS §2-④ — 와 다르다). 기록이 수천 건이면 수백 ms.
        viewModelScope.launch(Dispatchers.IO) {
            _chain.value = when (val result = auditRepository.verifyChain()) {
                is AppResult.Failure -> AuditChainUi.Unknown
                is AppResult.Success -> when (val r = result.data) {
                    is AuditHashChain.Result.Valid -> AuditChainUi.Ok(r.verified, r.legacy, r.headSeq?.let { seq -> r.headHash?.let { seq to it } })
                    is AuditHashChain.Result.Tampered -> AuditChainUi.Broken(r.chainSeq, r.reason)
                }
            }
        }
    }
}

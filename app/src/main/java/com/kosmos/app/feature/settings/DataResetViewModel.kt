package com.kosmos.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.memory.DataResetRepository
import com.kosmos.app.domain.memory.ResetTarget
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.platform.alarm.ReminderAlarmScheduler
import com.kosmos.app.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 데이터 초기화 진행 상태. */
sealed interface DataResetState {
    data object Idle : DataResetState
    data object Running : DataResetState

    /** [needsRestart] — 대화 기록을 지웠으면 앱을 다시 시작해야 한다(아래 [DataResetViewModel] [WHY]). */
    data class Done(val needsRestart: Boolean) : DataResetState
    data class Failed(val message: String) : DataResetState
}

/**
 * [DataResetViewModel]
 * 설정의 "데이터 초기화"(0.37.1) — 고른 대상을 지우고 지운 것에 딸린 플랫폼 상태를 정리합니다.
 *
 * ### Architecture Context
 * - **Layer**: Presentation
 * - **Dependencies**: [DataResetRepository], [TaskRepository](알람 대상 조회), [ReminderAlarmScheduler], [WidgetRefresher], [SessionStore]
 *
 * ### Key Flow
 * 1. 할 일을 지우면 **지우기 전에** 예약된 리마인더 알람을 취소한다 — 지운 뒤에는 어떤 알람이 있었는지 알 수 없다.
 * 2. 저장소가 한 트랜잭션으로 지운다.
 * 3. 할 일이면 위젯을 갱신하고, 대화 기록이면 활성 세션을 지우고 재시작을 요구한다.
 *
 * [WHY] 대화 기록은 재시작이 필요하다 — 엔진의 대화 KV·채팅 화면 상태·에피소드 스케줄러가 지운 대화를 메모리에 들고 있다.
 * 백업 복원과 같은 출구(재시작 대화상자)를 쓴다.
 */
@HiltViewModel
class DataResetViewModel @Inject constructor(
    private val dataResetRepository: DataResetRepository,
    private val taskRepository: TaskRepository,
    private val reminderAlarmScheduler: ReminderAlarmScheduler,
    private val widgetRefresher: WidgetRefresher,
    private val sessionStore: SessionStore
) : ViewModel() {

    private val _state = MutableStateFlow<DataResetState>(DataResetState.Idle)
    val state: StateFlow<DataResetState> = _state.asStateFlow()

    fun reset(targets: Set<ResetTarget>) {
        if (targets.isEmpty() || _state.value == DataResetState.Running) return
        _state.value = DataResetState.Running
        viewModelScope.launch {
            if (ResetTarget.TASKS in targets) {
                (taskRepository.getActiveReminders() as? AppResult.Success)?.data
                    ?.forEach { reminderAlarmScheduler.cancel(it.id) }
            }
            when (val result = dataResetRepository.reset(targets)) {
                is AppResult.Success -> {
                    if (ResetTarget.TASKS in targets) widgetRefresher.refresh()
                    val conversations = ResetTarget.CONVERSATIONS in targets
                    if (conversations) sessionStore.clearActiveSessionId()
                    _state.value = DataResetState.Done(needsRestart = conversations)
                }
                is AppResult.Failure -> _state.value = DataResetState.Failed(ErrorMessages.userMessage(result.error))
            }
        }
    }

    /** 완료·실패 안내를 닫는다. 재시작이 필요한 완료는 닫을 수 없다(화면이 재시작만 제공). */
    fun dismiss() {
        val current = _state.value
        if (current is DataResetState.Done && current.needsRestart) return
        _state.value = DataResetState.Idle
    }
}

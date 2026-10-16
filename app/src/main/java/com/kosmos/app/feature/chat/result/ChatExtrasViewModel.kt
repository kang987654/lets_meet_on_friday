package com.kosmos.app.feature.chat.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.domain.util.IsoDateTimeParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * [ChatExtrasViewModel]
 * 채팅 화면의 결과 카드 상태와 상단 요약 타일(0.38.2 M3).
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Chat)
 * - **Dependencies**: [TaskRepository], [KnowledgeRepository], [GetTodayScheduleUseCase]
 *
 * ### Key Flow
 * 1. 화면이 결과가 있는 메시지를 그릴 때 [ensure] — 대상을 한 번만 구독해 [results] 에 현재 상태를 넣는다(리마인더·일정은 흐름이라 미룸·취소가 바로 반영).
 * 2. 화면이 열리고 대화가 늘 때 [refreshSummary] — 다음 알림·오늘 일정 수·남은 할 일 수.
 *
 * [WHY] `ChatViewModel` 과 나눈 이유 — 그 생성자를 늘리면 E2E 들이 손으로 만드는 곳을 모두 고쳐야 하고, `ChatScreen` 은 기본 인자만으로
 * 단독 compose 돼야 한다(AGENTS §2-② 계약). 이 ViewModel 이 없으면 카드·타일 없이 글만 보인다.
 */
@HiltViewModel
class ChatExtrasViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val getTodaySchedule: GetTodayScheduleUseCase
) : ViewModel() {

    private val _results = MutableStateFlow<Map<String, ResultCardState>>(emptyMap())
    val results: StateFlow<Map<String, ResultCardState>> = _results.asStateFlow()

    private val watching = mutableSetOf<String>()

    private val _summary = MutableStateFlow<ChatSummary?>(null)
    val summary: StateFlow<ChatSummary?> = _summary.asStateFlow()

    /** [result] 의 대상을 구독한다 — 같은 키는 한 번만. */
    fun ensure(result: MessageResult, today: LocalDate = LocalDate.now()) {
        val key = keyOf(result)
        if (!watching.add(key)) return
        viewModelScope.launch {
            stateFlowOf(result, today).collect { state -> _results.update { it + (key to state) } }
        }
    }

    private fun stateFlowOf(result: MessageResult, today: LocalDate): Flow<ResultCardState> = when (result.kind) {
        MessageResult.Kind.REMINDER -> taskRepository.observeById(result.ref).map { ResultCardMapper.reminder(it, today) }
        MessageResult.Kind.SCHEDULE -> taskRepository.observeById(result.ref).map { ResultCardMapper.schedule(it, today) }
        MessageResult.Kind.MEMORY_NOTE -> flow { emit(ResultCardMapper.note(noteOf(result.ref))) }
        MessageResult.Kind.MEMORY_RECALL -> flow { emit(ResultCardMapper.recall(result.ref.takeIf { it.isNotEmpty() }?.let { noteOf(it) }, result.meta)) }
    }

    private suspend fun noteOf(id: String) = (knowledgeRepository.getById(id) as? AppResult.Success)?.data

    /** 요약 타일을 다시 센다. 시각 판정은 [now] 기준(AGENTS §2-④). */
    fun refreshSummary(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        viewModelScope.launch {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val next = (taskRepository.getActiveReminders() as? AppResult.Success)?.data.orEmpty()
                .mapNotNull { task -> task.remindAtIso?.let { iso -> IsoDateTimeParser.toEpochMillis(iso, zone)?.let { it to iso } } }
                .filter { (at, _) -> at >= now }
                .minByOrNull { it.first }
                ?.second
            val nextLabel = next?.let { iso ->
                if (IsoDateTimeParser.toLocalDate(iso, zone) == today) IsoDateTimeParser.toDisplayTimeKorean(iso, zone)
                else IsoDateTimeParser.toDisplayKorean(iso, zone)
            }
            val schedules = (getTodaySchedule(ScheduleData.RangeType.TODAY, now, zone) as? AppResult.Success)?.data?.events?.size ?: 0
            val pending = (taskRepository.getCounts() as? AppResult.Success)?.data?.pending ?: 0
            _summary.value = ChatSummary(nextReminder = nextLabel, todaySchedules = schedules, pendingTasks = pending)
        }
    }

    companion object {
        /** 같은 대상이라도 근거(meta)가 다르면 다른 카드다 — 기억 답마다 충돌 줄이 다를 수 있다. */
        fun keyOf(result: MessageResult): String = "${result.kind}:${result.ref}:${result.meta.orEmpty()}"
    }
}

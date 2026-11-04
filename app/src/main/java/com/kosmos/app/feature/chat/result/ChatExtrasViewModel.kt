package com.kosmos.app.feature.chat.result

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.R
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.core.common.CurrentLanguage
import com.kosmos.app.domain.model.ChatMessage

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
    private val getTodaySchedule: GetTodayScheduleUseCase,
    private val reminderEditor: ReminderEditor,
    private val currentLanguage: CurrentLanguage = CurrentLanguage.KO
) : ViewModel() {

    private val currentLang: AppLanguage
        get() = currentLanguage.current

    private val _results = MutableStateFlow<Map<String, ResultCardState>>(emptyMap())
    val results: StateFlow<Map<String, ResultCardState>> = _results.asStateFlow()

    private val watching = mutableSetOf<String>()
    private val watchedResults = mutableMapOf<String, Pair<Context, MessageResult>>()
    private val watchJobs = mutableListOf<kotlinx.coroutines.Job>()
    private var today: LocalDate = LocalDate.now()

    init {
        // [WHY] 언어가 바뀌어 화면이 재생성돼도 ViewModel 은 살아남는다 — 옛 언어로 매핑된 카드 캐시를 비워
        // 재구성된 화면이 새 Context 로케일로 다시 구독·조립하게 한다(0.40.1 M2).
        viewModelScope.launch {
            var last = currentLanguage.current
            currentLanguage.languageFlow.collectLatest { lang ->
                if (lang != last) {
                    last = lang
                    watchJobs.forEach { it.cancel() }
                    watchJobs.clear()
                    watching.clear()
                    watchedResults.clear()
                    _results.value = emptyMap()
                    _editError.value = null
                }
            }
        }
    }

    /** 알림 시각(epoch ms) — 수정 시트의 시간 고르기 처음 값. 대상 흐름이 채운다. */
    private val reminderTimes = mutableMapOf<String, Long>()

    /** [taskId] 알림의 지금 시각(시·분). 모르면 지금 시각. */
    fun currentTime(taskId: String, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
        val at = Instant.ofEpochMilli(reminderTimes[taskId] ?: System.currentTimeMillis()).atZone(zone)
        return at.hour to at.minute
    }

    private val _summary = MutableStateFlow<ChatSummary?>(null)
    val summary: StateFlow<ChatSummary?> = _summary.asStateFlow()

    /**
     * 오늘 날짜([date])를 갱신합니다. 날짜가 바뀌었으면 이미 지켜보던 결과들을 새 날짜 기준으로 다시 구독합니다 (0.40.2 M3-10).
     *
     * [WHY] 자정이 지나도 기존 구독은 처음 `ensure` 때의 `today` 를 붙들고 있어 "오늘" 표기가 그대로 남았다.
     * `_results` 를 비우지 않고 덮어써서 화면 깜빡임 없이 날짜 표기만 바꾼다.
     */
    fun setToday(date: LocalDate) {
        if (date == this.today) return
        this.today = date
        val snapshot = watchedResults.values.toList()
        watchJobs.forEach { it.cancel() }
        watchJobs.clear()
        watching.clear()
        snapshot.forEach { (appContext, result) ->
            ensure(appContext, result, date)
        }
    }

    /** [result] 의 대상을 구독한다 — 같은 키는 한 번만. */
    fun ensure(context: Context, result: MessageResult, today: LocalDate = this.today) {
        this.today = today
        val key = keyOf(result)
        if (!watching.add(key)) return
        // [WHY] 구독은 화면 재생성을 넘어 산다 — 화면(Activity) Context 를 쥐면 버려진 화면이 해제되지 않는다.
        // 언어가 바뀌면 캐시를 비우고 새로 구독하므로 애플리케이션 Context 로도 새 언어가 반영된다.
        val appContext = context.applicationContext
        watchedResults[key] = appContext to result
        val job = viewModelScope.launch {
            var previous: ResultCardState? = null
            stateFlowOf(appContext, result, today).collect { state ->
                _results.update { it + (key to state) }
                // [WHY] 알람이 울리거나 시트·다른 화면에서 바뀌어도 대화 수는 그대로라 요약이 지난 시각을 "다음"으로 보였다(0.38.2 실기기) —
                // 지켜보는 할 일의 상태가 바뀌면 요약도 다시 센다. 첫 값은 지금 상태일 뿐이라 세지 않는다.
                if (previous != null && previous != state) refreshSummary()
                previous = state
            }
        }
        watchJobs += job
    }

    private fun stateFlowOf(context: Context, result: MessageResult, today: LocalDate): Flow<ResultCardState> = when (result.kind) {
        MessageResult.Kind.REMINDER -> taskRepository.observeById(result.ref).map { task ->
            task?.remindAtIso?.let { iso -> IsoDateTimeParser.toEpochMillis(iso)?.let { reminderTimes[result.ref] = it } }
            ResultCardMapper.reminder(context, task, today, lang = currentLang)
        }
        MessageResult.Kind.SCHEDULE -> taskRepository.observeById(result.ref).map { ResultCardMapper.schedule(context, it, today, lang = currentLang) }
        MessageResult.Kind.MEMORY_NOTE -> flow { emit(ResultCardMapper.note(context, noteOf(result.ref))) }
        MessageResult.Kind.MEMORY_RECALL -> flow { emit(ResultCardMapper.recall(context, result.ref.takeIf { it.isNotEmpty() }?.let { noteOf(it) }, result.meta, lang = currentLang)) }
        // [WHY] 출처는 찾은 순간의 기록이다 — 문서가 지워졌는지는 누를 때 DocSourceViewModel 이 확인한다.
        MessageResult.Kind.DOC_SOURCE -> flow { emit(ResultCardMapper.docSource(context, result.meta)) }
    }

    private suspend fun noteOf(id: String) = (knowledgeRepository.getById(id) as? AppResult.Success)?.data

    /** 요약 타일을 다시 센다. 시각 판정은 [now] 기준(AGENTS §2-④). */
    fun refreshSummary(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
        viewModelScope.launch {
            val lang = currentLang
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val next = (taskRepository.getActiveReminders() as? AppResult.Success)?.data.orEmpty()
                .mapNotNull { task -> task.remindAtIso?.let { iso -> IsoDateTimeParser.toEpochMillis(iso, zone)?.let { it to iso } } }
                .filter { (at, _) -> at >= now }
                .minByOrNull { it.first }
                ?.second
            val nextLabel = next?.let { iso ->
                if (IsoDateTimeParser.toLocalDate(iso, zone) == today) IsoDateTimeParser.toDisplayTimeKorean(iso, zone, lang)
                else IsoDateTimeParser.toDisplayKorean(iso, zone, lang)
            }
            val schedules = (getTodaySchedule(ScheduleData.RangeType.TODAY, now, zone) as? AppResult.Success)?.data?.events?.size ?: 0
            val pending = (taskRepository.getCounts() as? AppResult.Success)?.data?.pending ?: 0
            _summary.value = ChatSummary(nextReminder = nextLabel, todaySchedules = schedules, pendingTasks = pending)
        }
    }

    // ── 알림 수정 시트(M4) ──

    private val _editingReminder = MutableStateFlow<String?>(null)
    /** 수정 시트를 연 알림의 할 일 id — null 이면 닫힘. */
    val editingReminder: StateFlow<String?> = _editingReminder.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    fun openReminder(context: Context, taskId: String) {
        ensure(context, MessageResult(MessageResult.Kind.REMINDER, taskId))
        _editError.value = null
        _editingReminder.value = taskId
    }

    fun closeReminder() {
        _editingReminder.value = null
        _editError.value = null
    }

    /** 수정 결과로 저장된 비서 한 줄(D8)을 [onSaved] 로 넘긴다 — 화면이 대화 끝에 붙인다. */
    fun snooze(context: Context, taskId: String, onSaved: (ChatMessage) -> Unit) =
        edit(context, onSaved) { reminderEditor.snooze(taskId) }

    fun reschedule(context: Context, taskId: String, hour: Int, minute: Int, onSaved: (ChatMessage) -> Unit) =
        edit(context, onSaved) { reminderEditor.reschedule(taskId, hour, minute) }

    fun cancelReminder(context: Context, taskId: String, onSaved: (ChatMessage) -> Unit) =
        edit(context, onSaved) { reminderEditor.cancel(taskId) }

    private fun edit(
        context: Context,
        onSaved: (ChatMessage) -> Unit,
        action: suspend () -> AppResult<ChatMessage>
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> {
                    onSaved(result.data)
                    closeReminder()
                    refreshSummary()
                }
                is AppResult.Failure -> _editError.value =
                    (result.error as? AppError.ValidationError)?.reason
                        ?: appContext.getString(R.string.chat_reminder_update_failed)
            }
        }
    }

    companion object {
        /** 같은 대상이라도 근거(meta)가 다르면 다른 카드다 — 기억 답마다 충돌 줄이 다를 수 있다. */
        fun keyOf(result: MessageResult): String = "${result.kind}:${result.ref}:${result.meta.orEmpty()}"
    }
}

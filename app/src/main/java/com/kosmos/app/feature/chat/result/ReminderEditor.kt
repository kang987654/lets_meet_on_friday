package com.kosmos.app.feature.chat.result

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SessionStore
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.ConversationRepository
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.platform.alarm.ReminderAlarmScheduler
import com.kosmos.app.widget.WidgetRefresher
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * [ReminderEditor]
 * 채팅 알림 카드의 수정 시트(0.38.2 M4) — 10분 미루기·시간 바꾸기·알림 취소.
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Chat 결과 카드)
 * - **Dependencies**: [TaskRepository], [ReminderAlarmScheduler], [WidgetRefresher], [AuditTrailService], [ConversationRepository], [SessionStore], [ModelRunner]
 *
 * ### Key Flow
 * 1. 할 일을 고친다 — 시각 변경은 `remindedAtMs` 를 비워 다시 울릴 대상으로, 취소는 `cancelledAt`(행은 남는다, 결정 1).
 * 2. 알람을 다시 예약하거나 끈다 → 위젯 갱신 → 감사 기록.
 * 3. 바뀐 결과를 비서 한 줄로 **지금 세션**에 저장한다(D8, 문장은 코드가 만든다 — 추론 없음). 결과 칸이 같은 할 일을 가리켜 카드가 붙는다.
 * 4. 엔진 대화를 다음 턴에 새로 만들게 한다 — 살아 있는 대화 문맥에는 옛 시각만 있어서, 바로 물으면 옛 시각으로 답했다(사용자 결정 2026-10-19: 다음 턴 재구성).
 *
 * 시각 판정은 [now] 인자로 받는다(AGENTS §2-④).
 */
class ReminderEditor @Inject constructor(
    private val taskRepository: TaskRepository,
    private val alarmScheduler: ReminderAlarmScheduler,
    private val widgetRefresher: WidgetRefresher,
    private val auditTrailService: AuditTrailService,
    private val conversationRepository: ConversationRepository,
    private val sessionStore: SessionStore,
    private val modelRunner: ModelRunner
) {

    /** 지금 시각 또는 원래 시각 중 늦은 쪽에서 [minutes] 분 뒤로. */
    suspend fun snooze(taskId: String, minutes: Long = 10, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): AppResult<ChatMessage> {
        val task = load(taskId) ?: return notFound()
        val base = maxOf(task.remindAtIso?.let { IsoDateTimeParser.toEpochMillis(it, zone) } ?: now, now)
        return moveTo(task, base + minutes * 60_000, now, zone, verb = "미뤘어요")
    }

    /** 원래 날짜(지났으면 오늘)의 [hour]:[minute] 로. 지난 시각이면 [AppError.ValidationError]. */
    suspend fun reschedule(taskId: String, hour: Int, minute: Int, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): AppResult<ChatMessage> {
        val task = load(taskId) ?: return notFound()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = task.remindAtIso?.let { IsoDateTimeParser.toLocalDate(it, zone) }?.takeIf { !it.isBefore(today) } ?: today
        val target = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        if (target <= now) return AppResult.Failure(AppError.ValidationError("time", "이미 지난 시각이에요."))
        return moveTo(task, target, now, zone, verb = "바꿨어요")
    }

    suspend fun cancel(taskId: String, now: Long = System.currentTimeMillis()): AppResult<ChatMessage> {
        val task = load(taskId) ?: return notFound()
        val saved = taskRepository.save(task.copy(cancelledAt = now))
        if (saved is AppResult.Failure) return saved
        alarmScheduler.cancel(taskId)
        return finish(task, "'${task.title}' 알림을 취소했어요.", now)
    }

    private suspend fun moveTo(task: TaskItem, triggerAtMs: Long, now: Long, zone: ZoneId, verb: String): AppResult<ChatMessage> {
        val iso = LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAtMs), zone).format(ISO_SECONDS)
        val saved = taskRepository.save(task.copy(remindAtIso = iso, remindedAtMs = null))
        if (saved is AppResult.Failure) return saved
        alarmScheduler.schedule(task.id, triggerAtMs)
        val display = IsoDateTimeParser.toDisplayTimeKorean(iso, zone)
        val particle = if (endsWithConsonant(display)) "으로" else "로"
        return finish(task, "'${task.title}' 알림을 $display$particle $verb.", now)
    }

    private suspend fun finish(task: TaskItem, line: String, now: Long): AppResult<ChatMessage> {
        widgetRefresher.refresh()
        val sessionId = sessionStore.activeSessionIdFlow.first() ?: task.id
        auditTrailService.logToolCall(sessionId, toolName = "ReminderEdit(화면)", resultJson = line)
        val message = ChatMessage(
            id = UUID.randomUUID().toString(), sessionId = sessionId, role = ChatMessage.Role.ASSISTANT, content = line,
            inputType = InputType.TEXT, createdAt = now, result = MessageResult(MessageResult.Kind.REMINDER, task.id)
        )
        val written = conversationRepository.save(message)
        if (written is AppResult.Failure) return written
        modelRunner.invalidateConversation()
        return AppResult.Success(message)
    }

    private suspend fun load(taskId: String): TaskItem? = (taskRepository.getById(taskId) as? AppResult.Success)?.data

    private fun notFound(): AppResult<ChatMessage> = AppResult.Failure(AppError.ValidationError("task", "지운 알림이에요."))

    private companion object {
        val ISO_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        /** "오후 2:06" 의 마지막 글자(숫자) 받침 — 0·1·3·6·7·8 은 받침이 있다(영·일·삼·육·칠·팔). */
        fun endsWithConsonant(text: String?): Boolean = text?.lastOrNull()?.let { it in "013678" } ?: false
    }
}

package com.kosmos.app.feature.chat.result

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import com.kosmos.app.R
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.MessageResult
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.ui.component.IconSize
import com.kosmos.app.ui.component.KosmosIcon
import android.content.Context
import com.kosmos.app.ui.theme.KosmosShapes
import com.kosmos.app.ui.theme.KosmosTheme
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import com.kosmos.app.core.common.AppLanguage

/**
 * 채팅 결과 행 한 줄의 표시 상태(0.38.2) — [MessageResult] 의 대상을 **지금** 조회한 결과로 만든다(D6 ④).
 *
 * @property muted 취소·지움 — 흐리게, [struck] 이면 이름·값에 취소선(사용자 결정: 혼합안처럼 취소선이 직관적).
 * @property evidence 기억 답의 근거 줄(카드 안 구분선 아래).
 */
data class ResultCardState(
    val kind: MessageResult.Kind,
    @param:DrawableRes val icon: Int,
    val label: String,
    val status: String? = null,
    val title: String,
    val value: String? = null,
    val sub: String? = null,
    val muted: Boolean = false,
    val struck: Boolean = false,
    val evidence: List<Evidence> = emptyList()
)

/**
 * 기억 답의 근거 한 줄 — [kind] 가 아이콘과 강조(충돌은 주의 면)를 정한다.
 */
data class Evidence(val kind: Kind, val text: String) {
    enum class Kind { NOTE, CONVERSATION, CONFLICT }
}

/** 채팅 상단 요약 타일(0.38.2) — 다음 알림·오늘 일정 수·남은 할 일 수. */
data class ChatSummary(
    val nextReminder: String?,
    val todaySchedules: Int,
    val pendingTasks: Int
)

/**
 * [ResultCardState] 를 만드는 순수 함수들 — 오늘 판정은 인자로 받는다(AGENTS §2-④).
 */
object ResultCardMapper {

    fun reminder(
        context: Context,
        task: TaskItem?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): ResultCardState {
        val label = context.getString(R.string.result_reminder_label)
        if (task == null) {
            val deleted = context.getString(R.string.result_reminder_deleted)
            return ResultCardState(MessageResult.Kind.REMINDER, R.drawable.ic_ph_bell_duotone, label, deleted, deleted, muted = true)
        }
        val iso = task.remindAtIso
        val status = when {
            task.cancelledAt != null -> context.getString(R.string.result_reminder_status_cancelled)
            task.isCompleted -> context.getString(R.string.result_reminder_status_done)
            task.remindedAtMs != null -> context.getString(R.string.result_reminder_status_fired)
            else -> context.getString(R.string.result_reminder_status_upcoming)
        }
        return ResultCardState(
            kind = MessageResult.Kind.REMINDER,
            icon = R.drawable.ic_ph_bell_duotone,
            label = label,
            status = status,
            title = task.title,
            value = iso?.let { IsoDateTimeParser.toDisplayTimeKorean(it, zone, lang) },
            sub = iso?.let { dayOf(context, it, today, zone, lang) },
            muted = task.cancelledAt != null || task.isCompleted,
            struck = task.cancelledAt != null
        )
    }

    fun schedule(
        context: Context,
        task: TaskItem?,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): ResultCardState {
        val label = context.getString(R.string.result_schedule_label)
        if (task == null) {
            val deleted = context.getString(R.string.result_schedule_deleted)
            return ResultCardState(MessageResult.Kind.SCHEDULE, R.drawable.ic_ph_calendar_blank_duotone, label, deleted, deleted, muted = true)
        }
        val start = task.dueDateIso
        return ResultCardState(
            kind = MessageResult.Kind.SCHEDULE,
            icon = R.drawable.ic_ph_calendar_blank_duotone,
            label = label,
            title = task.title,
            value = start?.let { IsoDateTimeParser.toDisplayTimeKorean(it, zone, lang) },
            sub = start?.let { dayOf(context, it, today, zone, lang) }
        )
    }

    fun note(
        context: Context,
        note: KnowledgeNote?
    ): ResultCardState {
        val label = context.getString(R.string.result_note_label)
        return if (note == null) {
            val deleted = context.getString(R.string.result_note_deleted)
            ResultCardState(MessageResult.Kind.MEMORY_NOTE, R.drawable.ic_ph_clock_counter_clockwise_duotone, label, deleted, deleted, muted = true)
        } else {
            val saved = context.getString(R.string.result_note_status_saved)
            ResultCardState(MessageResult.Kind.MEMORY_NOTE, R.drawable.ic_ph_clock_counter_clockwise_duotone, label, saved, note.content)
        }
    }

    /**
     * 기억 답 — 근거 메모가 있으면 그 내용, 없으면 과거 대화만이 근거.
     * 근거 줄은 모델에게 간 문구와 같은 판정(RecallConflict)을 화면 말로 옮긴다(사용자 결정: 근거를 명시적으로).
     */
    fun recall(
        context: Context,
        note: KnowledgeNote?,
        meta: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        lang: AppLanguage = AppLanguage.KO
    ): ResultCardState {
        val json = meta?.let { runCatching { JSONObject(it) }.getOrNull() }
        val evidence = buildList {
            // [WHY] 가운뎃점으로 이은 정보 줄 대신 문장으로 — taste "AI 티" 목록(middle-dot strings), 0.38.2 M7b.
            if (note != null) {
                val at = json?.optLong("noteAt", note.createdAt) ?: note.createdAt
                val dateStr = IsoDateTimeParser.monthDayKorean(at, zone, lang)
                add(Evidence(Evidence.Kind.NOTE, context.getString(R.string.result_memory_note_saved_on, dateStr)))
            } else {
                add(Evidence(Evidence.Kind.CONVERSATION, context.getString(R.string.result_memory_found_in_chats)))
            }
            val conflicts = json?.optInt("conflictCount", 0) ?: 0
            if (conflicts > 0) {
                val values = json?.optJSONArray("otherValues")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }.orEmpty()
                val joined = values.joinToString(", ")
                val text = if (conflicts > 1) {
                    context.getString(R.string.result_memory_conflict_multi, conflicts, joined)
                } else {
                    context.getString(R.string.result_memory_conflict_single, joined)
                }
                add(Evidence(Evidence.Kind.CONFLICT, text))
            }
        }
        return ResultCardState(
            kind = MessageResult.Kind.MEMORY_RECALL,
            icon = R.drawable.ic_ph_clock_counter_clockwise_duotone,
            label = context.getString(R.string.result_memory_label),
            // [WHY] 큰 값을 따로 두지 않는다 — 메모 문장에 값이 이미 있어 "…는 134 / 134"로 겹쳐 보였다(0.38.2 M3 렌더).
            title = note?.content ?: context.getString(R.string.result_memory_found_in_chats),
            evidence = evidence
        )
    }

    private fun dayOf(context: Context, iso: String, today: LocalDate, zone: ZoneId, lang: AppLanguage): String? {
        val date = IsoDateTimeParser.toLocalDate(iso, zone) ?: return null
        return if (date == today) {
            context.getString(R.string.common_today)
        } else {
            IsoDateTimeParser.toDisplayDateKorean(iso, zone, lang)
        }
    }
}

/**
 * 비서 카드 안의 결과 행 — 가라앉은 면(`surfaceSunk`), Duotone 아이콘, 종류·상태 / 이름 / 값, ›.
 * **보기 전용**(D6 ②) — 누르면 시트가 열리고, 행 안에 바로 실행되는 버튼은 없다.
 */
@Composable
fun ResultRow(state: ResultCardState, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = KosmosTheme.colors
    val accent = if (state.muted) colors.textMuted else colors.brandText
    val strike = if (state.struck) TextDecoration.LineThrough else TextDecoration.None
    val description = listOfNotNull(state.label, state.status, state.title, state.value, state.sub).joinToString(", ")
    val clickable = if (onClick != null) {
        Modifier.semantics { contentDescription = description }.clickable(role = Role.Button, onClick = onClick)
    } else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KosmosShapes.md)
            .background(colors.surfaceSunk)
            // [WHY] 다크에서는 가라앉은 면과 카드 면의 밝기 차가 거의 없어 결과 행이 사라졌다 — 다크에서만 hairline(impeccable 점검 P2).
            .then(if (colors.surface.luminance() < 0.5f) Modifier.border(1.dp, colors.border, KosmosShapes.md) else Modifier)
            .then(clickable)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KosmosIcon(state.icon, contentDescription = null, tint = accent)
            Column(Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(state.label, state.status).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    maxLines = 1
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.muted) colors.textMuted else colors.textPrimary,
                    textDecoration = strike,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                state.sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1) }
            }
            state.value?.let { value ->
                // [WHY] "오후 1:56" 또는 "1:56 PM" 을 통째로 크게 쓰면 폭을 먹어 왼쪽 글이 꺾였다 — 오전/오후(AM/PM)는 작게, 숫자만 크게(0.38.2 M3 렌더).
                val periodKo = PERIOD.find(value)
                val periodEn = PERIOD_EN.find(value)
                val smallPeriod = periodKo?.groupValues?.get(1) ?: periodEn?.groupValues?.get(2)
                val mainTime = periodKo?.groupValues?.get(2) ?: periodEn?.groupValues?.get(1) ?: value
                Column(horizontalAlignment = Alignment.End) {
                    if (smallPeriod != null) {
                        Text(smallPeriod, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, textDecoration = strike)
                    }
                    Text(
                        text = mainTime,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (state.muted) colors.textMuted else colors.textPrimary,
                        textDecoration = strike,
                        maxLines = 1
                    )
                }
            }
            if (onClick != null) KosmosIcon(R.drawable.ic_ph_caret_right, contentDescription = null, size = IconSize.inline, tint = colors.textMuted)
        }
    }
}

private val PERIOD = Regex("^(오전|오후) (.+)$")
private val PERIOD_EN = Regex("^(.+) (AM|PM)$")

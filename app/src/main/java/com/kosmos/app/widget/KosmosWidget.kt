package com.kosmos.app.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.runtime.Composable
import com.kosmos.app.MainActivity
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.ui.theme.DarkColors
import com.kosmos.app.ui.theme.LightColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * [KosmosWidget]
 * 홈 화면 위젯 — 오늘 일정(최대 3건) + 남은 할 일 + 앱/음성 진입 버튼 (A3).
 *
 * ### Architecture Context
 * - **Layer**: App (Widget)
 * - **Dependencies**: [GetTodayScheduleUseCase], [TaskRepository] — EntryPoint 로 획득
 *
 * [WHY] 추론 0, DB 읽기만 — 브리핑 정시 알림(BriefingNotificationWorker)과 같은 재료 계약.
 * 백그라운드 모델 로드는 이 코드베이스가 기각한 방향이므로 위젯은 요약을 만들지 않는다.
 *
 * [WHY] 색은 `ColorProvider(day, night)` 에 KosmosColors Light/Dark 값을 복제한다 —
 * 위젯은 Compose 테마 트리(LocalKosmosColors) 밖에서 프레임워크가 그리므로, 알림의
 * notification_accent 복제와 같은 프레임워크 경계 예외다.
 */
class KosmosWidget : GlanceAppWidget() {

    // [WHY] GlanceAppWidget 은 프레임워크가 인스턴스화해 @AndroidEntryPoint 를 못 쓴다 —
    // 프로젝트 첫 @EntryPoint. 남용 방지를 위해 위젯 전용 인터페이스로 좁게 연다.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun getTodayScheduleUseCase(): GetTodayScheduleUseCase
        fun taskRepository(): TaskRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val schedule = (entryPoint.getTodayScheduleUseCase()
            .invoke(ScheduleData.RangeType.TODAY) as? AppResult.Success)?.data
        val tasks = (entryPoint.taskRepository()
            .getPendingTasksData(0, 100) as? AppResult.Success)?.data.orEmpty()
        val snapshot = buildWidgetSnapshot(schedule, tasks, System.currentTimeMillis())

        provideContent { WidgetContent(snapshot) }
    }
}

class KosmosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KosmosWidget()
}

// --- 팔레트: KosmosColors Light/Dark 복제 (프레임워크 경계 예외 — 파일 상단 [WHY]) ---
private object WidgetPalette {
    val bg = ColorProvider(day = LightColors.surface, night = DarkColors.surface)
    val accent = ColorProvider(day = LightColors.accent, night = DarkColors.accent)
    val accentDim = ColorProvider(day = LightColors.accentDim, night = DarkColors.accentDim)
    val textPrimary = ColorProvider(day = LightColors.textPrimary, night = DarkColors.textPrimary)
    val textSecondary = ColorProvider(day = LightColors.textSecondary, night = DarkColors.textSecondary)
    val textMuted = ColorProvider(day = LightColors.textMuted, night = DarkColors.textMuted)
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetPalette.bg)
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "오늘",
                style = TextStyle(color = WidgetPalette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = snapshot.dateLabel,
                style = TextStyle(color = WidgetPalette.textMuted, fontSize = 12.sp)
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))

        if (snapshot.eventLines.isEmpty()) {
            Text(
                text = "오늘 일정 없음",
                style = TextStyle(color = WidgetPalette.textSecondary, fontSize = 13.sp)
            )
        } else {
            snapshot.eventLines.forEach { line ->
                Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = line.time,
                        style = TextStyle(color = WidgetPalette.accent, fontSize = 13.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = line.title,
                        style = TextStyle(color = WidgetPalette.textPrimary, fontSize = 13.sp),
                        maxLines = 1
                    )
                }
            }
            if (snapshot.overflowCount > 0) {
                Text(
                    text = "+${snapshot.overflowCount}개 더",
                    style = TextStyle(color = WidgetPalette.textMuted, fontSize = 12.sp)
                )
            }
        }
        if (snapshot.deviceCalendarFailed) {
            Text(
                text = "기기 캘린더는 확인하지 못했어요",
                style = TextStyle(color = WidgetPalette.textMuted, fontSize = 11.sp)
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "할 일 ${snapshot.pendingTaskCount}건",
                style = TextStyle(color = WidgetPalette.textSecondary, fontSize = 12.sp)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            WidgetButton(text = "열기", action = actionStartActivity<MainActivity>())
            Spacer(modifier = GlanceModifier.width(8.dp))
            // [WHY] 🎤 은 VOICE_INPUT 액션 — 채팅 도착 즉시 녹음 시작(A3, 사용자 결정).
            WidgetButton(
                text = "🎤",
                // [WHY] Intent 오버로드는 core 가 아니라 appwidget 패키지에 있다 — core 의
                // actionStartActivity 와 이름이 같아 FQN 으로 구분한다.
                action = androidx.glance.appwidget.action.actionStartActivity(
                    com.kosmos.app.platform.tile.voiceLaunchIntent(androidx.glance.LocalContext.current)
                )
            )
        }
    }
}

@Composable
private fun WidgetButton(text: String, action: androidx.glance.action.Action) {
    Text(
        text = text,
        style = TextStyle(color = WidgetPalette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier
            .background(WidgetPalette.accentDim)
            .cornerRadius(12.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(action)
    )
}

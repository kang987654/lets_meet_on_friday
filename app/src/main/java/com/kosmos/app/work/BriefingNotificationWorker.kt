package com.kosmos.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.platform.notification.BriefingNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * [BriefingNotificationWorker]
 * 아침 정시에 브리핑 미리보기 알림을 게시하고 다음날 자기 자신을 재예약합니다 (A4 하이브리드).
 *
 * ### Architecture Context
 * - **Layer**: App (Work)
 * - **Dependencies**: [GetTodayScheduleUseCase], [TaskRepository], [BriefingNotifier]
 *
 * [WHY] **추론이 없다** — 로컬 DB 숫자("일정 N건 · 할 일 M건")만 읽는다. 브리핑 본문은
 * 앱이 열려 엔진 Ready 가 됐을 때 [MorningBriefingGenerator] 가 생성한다. 백그라운드
 * 모델 로드는 수명주기(onStop close)와 충돌해 기각된 방향이다. 추론이 없으므로 전경
 * 승격(setForeground)도 불필요하다.
 */
@HiltWorker
class BriefingNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsDataStore: SettingsDataStore,
    private val getTodaySchedule: GetTodayScheduleUseCase,
    private val taskRepository: TaskRepository,
    private val notifier: BriefingNotifier,
    private val scheduler: BriefingNotificationScheduler
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // [WHY] 예약 후 설정이 꺼졌을 수 있다 — 꺼져 있으면 알림도 재예약도 없이 조용히 끝낸다
        // (재예약을 남기면 매일 빈 워커가 돈다; 다시 켜는 쪽이 reschedule 을 부른다).
        if (!settingsDataStore.briefingEnabledFlow.first()) return Result.success()

        val schedule = (getTodaySchedule(ScheduleData.RangeType.TODAY) as? AppResult.Success)?.data
        val taskCount = (taskRepository.getPendingTasksData(0, MAX_TASK_COUNT) as? AppResult.Success)
            ?.data.orEmpty().count { !it.isCompleted }

        notifier.notifyPreview(
            eventCount = schedule?.events?.size ?: 0,
            taskCount = taskCount,
            // [WHY] 조회 자체가 실패해도 알림은 나간다 — 기기 캘린더 미확인으로 표기 (EC4).
            deviceCalendarFailed = schedule?.deviceCalendarFailed ?: true
        )

        scheduler.rescheduleFromWorker(settingsDataStore.briefingTimeMinutesFlow.first())
        return Result.success()
    }

    private companion object {
        const val MAX_TASK_COUNT = 100
    }
}

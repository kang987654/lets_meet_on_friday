package com.kosmos.app.platform.alarm

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.platform.notification.ReminderNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReminderFireHandler]
 * 알람이 울렸을 때(또는 재부팅 복원에서 이미 지난 리마인더를 발견했을 때) 실제 발화를 수행합니다.
 *
 * ### Architecture Context
 * - **Layer**: App (Platform)
 * - **Dependencies**: [TaskRepository], [ReminderNotifier]
 *
 * ### Key Flow
 * 1. DB 재확인 — 완료·기발화·리마인더 아님이면 조용히 종료 (알람 취소 누락 경합의 방어선)
 * 2. 알림 게시 → 발화 시각 기록(markReminded)
 *
 * [WHY] 리시버가 아니라 별도 @Singleton 인 이유: BroadcastReceiver 는 프레임워크가
 * 인스턴스화해서 단위 테스트에서 주입이 번거롭다 — 로직을 여기로 빼면 mockk 로 끝난다.
 */
@Singleton
class ReminderFireHandler @Inject constructor(
    private val taskRepository: TaskRepository,
    private val notifier: ReminderNotifier
) {
    suspend fun fire(taskId: String, now: Long = System.currentTimeMillis()) {
        val task = when (val result = taskRepository.getById(taskId)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> {
                AppLogger.w(TAG, "리마인더 조회 실패 — 발화 생략: $taskId")
                return
            }
        } ?: return
        val remindAtIso = task.remindAtIso
        if (remindAtIso == null || task.isCompleted || task.remindedAtMs != null) return

        // [WHY] 알림 먼저, 기록은 뒤 — 순서를 뒤집으면 기록 후 알림 실패 시 리마인더가 조용히
        // 사라진다. 이 순서에서는 기록 실패 시 재부팅 복원이 한 번 더 울릴 수 있을 뿐이다.
        notifier.notifyReminder(task.id, task.title, IsoDateTimeParser.toDisplayKorean(remindAtIso))
        taskRepository.markReminded(task.id, now)
    }

    private companion object {
        const val TAG = "ReminderFireHandler"
    }
}

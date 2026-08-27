package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.util.IsoDateTimeParser
import java.util.UUID
import javax.inject.Inject

/**
 * [AddReminderUseCase]
 * 지정 시각에 알림을 울릴 리마인더를 할 일(Task) 저장소에 등록하는 유스케이스입니다 (B1).
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [TaskRepository]
 *
 * ### Key Flow
 * 1. remindAt(ISO 8601) 파싱·과거 시각 검증 — 실패 시 [AppError.ValidationError]
 * 2. `remindAtIso` 가 채워진 [TaskItem] 저장 (리마인더 = remindAtIso 있는 할 일, 스키마 v7)
 * 3. 저장된 [TaskItem] 반환 — 알람 예약(epoch 시각 필요)은 호출측(app 계층) 몫
 */
class AddReminderUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    /** [WHY] now 를 인자로 받는다 — 과거 시각 판정을 벽시계에 묶으면 테스트가 실행 시각에 갈린다. */
    suspend operator fun invoke(
        content: String,
        remindAtIso: String,
        now: Long = System.currentTimeMillis()
    ): AppResult<TaskItem> {
        val remindAtMs = IsoDateTimeParser.toEpochMillis(remindAtIso)
            ?: return AppResult.Failure(AppError.ValidationError("time", "ISO 8601 형식이 아닙니다"))
        if (remindAtMs <= now) {
            return AppResult.Failure(AppError.ValidationError("time", "이미 지난 시각입니다"))
        }
        val taskItem = TaskItem(
            id = UUID.randomUUID().toString(),
            title = content,
            isCompleted = false,
            createdAt = now,
            remindAtIso = remindAtIso
        )
        return when (val result = taskRepository.save(taskItem)) {
            is AppResult.Success -> AppResult.Success(taskItem)
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }
}

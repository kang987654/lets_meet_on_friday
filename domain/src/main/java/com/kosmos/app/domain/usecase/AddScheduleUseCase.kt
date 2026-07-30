package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.tool.CalendarTool
import java.util.UUID
import javax.inject.Inject

/**
 * [AddScheduleUseCase]
 * 사용자 입력이나 에이전트의 결정에 따라 새로운 일정(Task)을 로컬 DB에 추가하고,
 * 기기 시스템 캘린더에도 동기화(best-effort)하는 유스케이스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [TaskRepository], [CalendarTool]
 *
 * ### Key Flow
 * 1. 전달받은 일정 제목, 시작/종료 시간, 설명을 기반으로 [TaskItem] 도메인 모델 생성 후 Room DB에 저장 (Source of Truth)
 * 2. [CalendarTool]로 기기 시스템 캘린더에 이벤트 삽입 시도 — 권한 미보유 등 실패 시
 *    로컬 저장은 유지한 채 경고 로깅만 수행 (Graceful Degradation, ADR-004)
 * 3. 저장 성공 시 생성된 Task ID를 [AppResult]로 반환
 */
class AddScheduleUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val calendarTool: CalendarTool
) {
    /**
     * 일정을 저장하고 생성된 Task ID를 반환합니다.
     * [WHY] endTime/description을 별도 필드로 보존한다 — 과거에는 endTime이 무성 폐기되고
     * description이 title에 뭉개졌으며, 하드코딩된 가짜 ID(1L)를 반환했다.
     */
    suspend operator fun invoke(title: String, startTime: String, endTime: String, description: String?): AppResult<String> {
        val taskItem = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title,
            isCompleted = false,
            dueDateIso = startTime,
            endDateIso = endTime.takeIf { it.isNotBlank() },
            description = description?.takeIf { it.isNotBlank() },
            createdAt = System.currentTimeMillis()
        )
        return when (val result = taskRepository.save(taskItem)) {
            is AppResult.Success -> {
                syncToDeviceCalendar(taskItem)
                AppResult.Success(taskItem.id)
            }
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }

    // [WHY] 로컬 DB가 단일 진실 원천이므로 기기 캘린더 동기화 실패(권한 미보유, 계정 없음 등)가
    // 일정 등록 자체를 실패시키지 않는다. 실패는 경고로만 남긴다. (ADR-004)
    private suspend fun syncToDeviceCalendar(task: TaskItem) {
        val draft = CalendarDraft(
            title = task.title,
            startIso = task.dueDateIso ?: return,
            endIso = task.endDateIso,
            note = task.description,
            confidence = 1.0f
        )
        when (val result = calendarTool.insert(draft)) {
            is AppResult.Success -> Unit
            is AppResult.Failure ->
                AppLogger.w(TAG, "Device calendar sync failed (local save kept): ${result.error}")
        }
    }

    private companion object {
        const val TAG = "AddScheduleUseCase"
    }
}

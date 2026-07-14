package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import java.util.UUID
import javax.inject.Inject

class AddScheduleUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(title: String, startTime: String, endTime: String, description: String?): AppResult<Long> {
        val taskItem = TaskItem(
            id = UUID.randomUUID().toString(),
            title = title + (description?.let { " - $it" } ?: ""),
            isCompleted = false,
            dueDateIso = startTime,
            createdAt = System.currentTimeMillis()
        )
        return when (val result = taskRepository.save(taskItem)) {
            is AppResult.Success -> AppResult.Success(1L)
            is AppResult.Failure -> AppResult.Failure(result.error)
        }
    }
}

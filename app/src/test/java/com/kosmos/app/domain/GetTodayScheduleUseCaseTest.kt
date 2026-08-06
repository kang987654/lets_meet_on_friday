package com.kosmos.app.domain

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.modelrunner.ModelTurn
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * [GetTodayScheduleUseCaseTest]
 * ISO 파싱(오프셋/UTC/로컬/날짜-only), WEEK 7일 경계, epoch 기준 정렬을 검증합니다.
 * (2026-07-31 감사에서 발견된 "+09:00 이벤트 무성 드롭" 결함의 회귀 방지 테스트)
 */
class GetTodayScheduleUseCaseTest {

    private class FakeTaskRepository(private val tasks: List<TaskItem>) : TaskRepository {
        override suspend fun save(task: TaskItem): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun updateCompletion(taskId: String, isCompleted: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun getPendingTasksData(offset: Int, limit: Int): AppResult<List<TaskItem>> =
            AppResult.Success(tasks)
    }

    private val fakeModelRunner = object : ModelRunner {
        override val loadState: StateFlow<ModelLoadState> =
            MutableStateFlow(ModelLoadState.Ready(ModelInfo("mock", "mock", "1.0", "Q4", 0L)))
        override suspend fun warmUp() {}
        override suspend fun generate(prompt: ChatPrompt, onToken: ((String) -> Unit)?): AppResult<ModelTurn> =
            AppResult.Success(ModelTurn("요약"))
        override suspend fun generateWithImage(
            prompt: ChatPrompt, imageBytes: ByteArray, imageTokenBudget: Int, onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> = AppResult.Success(ModelTurn("요약"))
        override suspend fun generateWithAudio(
            prompt: ChatPrompt, audioPath: String, onToken: ((String) -> Unit)?
        ): AppResult<ModelTurn> = AppResult.Success(ModelTurn("요약"))
        override suspend fun cancel() {}
        override fun close() {}
    }

    private val fakeCalendarTool = object : com.kosmos.app.domain.tool.CalendarTool {
        override suspend fun readEvents(startMs: Long, endMs: Long): AppResult<List<com.kosmos.app.domain.model.CalendarEvent>> =
            AppResult.Success(emptyList())
        override suspend fun insert(draft: com.kosmos.app.domain.model.CalendarDraft): AppResult<Long> =
            AppResult.Success(1L)
    }

    private fun task(id: String, dueIso: String) = TaskItem(
        id = id, title = id, isCompleted = false, dueDateIso = dueIso, createdAt = 0L
    )

    private fun todayAt(hour: Int): LocalDateTime =
        LocalDate.now(ZoneId.systemDefault()).atTime(hour, 0)

    @Test
    fun `offset datetime is parsed and included in today's schedule`() = runBlocking {
        val zone = ZoneId.systemDefault()
        val offsetIso = todayAt(15).atZone(zone).toOffsetDateTime().toString() // 예: 2026-07-31T15:00+09:00
        val useCase = GetTodayScheduleUseCase(FakeTaskRepository(listOf(task("offset", offsetIso))), fakeModelRunner, fakeCalendarTool)

        val result = useCase(ScheduleData.RangeType.TODAY)

        assertTrue(result is AppResult.Success)
        assertEquals(1, (result as AppResult.Success).data.events.size)
    }

    @Test
    fun `mixed iso formats are all parsed and sorted by actual time`() = runBlocking {
        val zone = ZoneId.systemDefault()
        val localIso = todayAt(18).toString()                                        // 로컬 일시
        val offsetIso = todayAt(9).atZone(zone).toOffsetDateTime().toString()        // 오프셋 포함
        val utcIso = todayAt(12).atZone(zone).toInstant().toString()                 // UTC(Z)
        val tasks = listOf(task("local18", localIso), task("offset09", offsetIso), task("utc12", utcIso))
        val useCase = GetTodayScheduleUseCase(FakeTaskRepository(tasks), fakeModelRunner, fakeCalendarTool)

        val result = useCase(ScheduleData.RangeType.TODAY)

        assertTrue(result is AppResult.Success)
        val events = (result as AppResult.Success).data.events
        assertEquals(listOf("offset09", "utc12", "local18"), events.map { it.id })
    }

    @Test
    fun `date-only iso is included and week range covers 7 days inclusive`() = runBlocking {
        val today = LocalDate.now(ZoneId.systemDefault())
        val dateOnly = task("d0", today.toString())
        val day6 = task("d6", today.plusDays(6).toString())
        val day7 = task("d7", today.plusDays(7).toString()) // 8일째 — WEEK 범위 밖
        val useCase = GetTodayScheduleUseCase(FakeTaskRepository(listOf(day7, day6, dateOnly)), fakeModelRunner, fakeCalendarTool)

        val result = useCase(ScheduleData.RangeType.WEEK)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("d0", "d6"), (result as AppResult.Success).data.events.map { it.id })
    }

    @Test
    fun `unparseable due date is skipped without error`() = runBlocking {
        val useCase = GetTodayScheduleUseCase(
            FakeTaskRepository(listOf(task("bad", "내일 오후"), task("ok", todayAt(10).toString()))),
            fakeModelRunner,
            fakeCalendarTool
        )

        val result = useCase(ScheduleData.RangeType.TODAY)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf("ok"), (result as AppResult.Success).data.events.map { it.id })
    }
}

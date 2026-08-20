package com.kosmos.app.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.ScheduleData
import com.kosmos.app.domain.model.TaskItem
import com.kosmos.app.domain.usecase.GetTodayScheduleUseCase
import com.kosmos.app.platform.notification.BriefingNotifier
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [BriefingNotificationWorkerTest]
 * 미리보기 알림 워커의 조건 분기(설정 off·EC4)와 자기 재예약을 검증합니다.
 *
 * [WHY] [BriefingNotifier]·[BriefingNotificationScheduler] 를 주입으로 떼어 두었으므로
 * 알림·WorkManager 없이 검증할 수 있다 (ModelDownloadWorkerTest 전례).
 */
@RunWith(RobolectricTestRunner::class)
class BriefingNotificationWorkerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var getTodaySchedule: GetTodayScheduleUseCase
    private lateinit var taskRepository: TaskRepository
    private lateinit var notifier: BriefingNotifier
    private lateinit var scheduler: BriefingNotificationScheduler

    @Before
    fun setUp() {
        settingsDataStore = mockk()
        getTodaySchedule = mockk()
        taskRepository = mockk()
        notifier = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)

        every { settingsDataStore.briefingEnabledFlow } returns flowOf(true)
        every { settingsDataStore.briefingTimeMinutesFlow } returns flowOf(557)
        coEvery { getTodaySchedule(ScheduleData.RangeType.TODAY) } returns AppResult.Success(
            ScheduleData(events = persistentListOf(), summary = null, rangeType = ScheduleData.RangeType.TODAY)
        )
        coEvery { taskRepository.getPendingTasksData(any(), any()) } returns AppResult.Success(
            listOf(TaskItem(id = "t1", title = "특강 준비", isCompleted = false, createdAt = 0L))
        )
    }

    private fun buildWorker(): BriefingNotificationWorker =
        TestListenableWorkerBuilder<BriefingNotificationWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: androidx.work.WorkerParameters
                    ) = BriefingNotificationWorker(
                        appContext, workerParameters,
                        settingsDataStore, getTodaySchedule, taskRepository, notifier, scheduler
                    )
                }
            )
            .build()

    @Test
    fun `정상 경로 - 알림 1회와 다음날 재예약 1회`() = runBlocking {
        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 1) { notifier.notifyPreview(0, 1, false) }
        // [WHY] 자기 재예약은 APPEND_OR_REPLACE 경로여야 한다 — REPLACE 는 실행 중인
        // 자기 자신을 취소할 수 있다.
        verify(exactly = 1) { scheduler.rescheduleFromWorker(557) }
    }

    @Test
    fun `설정이 꺼져 있으면 알림도 재예약도 없다`() = runBlocking {
        every { settingsDataStore.briefingEnabledFlow } returns flowOf(false)

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 0) { notifier.notifyPreview(any(), any(), any()) }
        verify(exactly = 0) { scheduler.rescheduleFromWorker(any()) }
    }

    @Test
    fun `일정 조회 실패도 알림은 나가되 기기 캘린더 미확인으로 표기한다`() = runBlocking {
        // [WHY] 조회 실패로 알림 자체를 삼키면 "매일 아침 온다"는 계약이 조용히 깨진다 (EC4).
        coEvery { getTodaySchedule(any()) } returns
            AppResult.Failure(com.kosmos.app.core.common.AppError.CalendarReadError("x"))

        buildWorker().doWork()

        verify(exactly = 1) { notifier.notifyPreview(0, 1, true) }
    }
}

package com.kosmos.app.platform.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.TaskItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * [ReminderAlarmSchedulerTest]
 * AlarmManager 예약·취소·재부팅 복원 분기를 ShadowAlarmManager 로 검증합니다.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderAlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private val repository: TaskRepository = mockk()
    private val fireHandler: ReminderFireHandler = mockk(relaxed = true)
    private lateinit var scheduler: ReminderAlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = requireNotNull(context.getSystemService<AlarmManager>())
        scheduler = ReminderAlarmScheduler(context, repository, fireHandler)
    }

    @Test
    fun `정확 알람 권한이 있으면 지정 시각 정각으로 예약한다`() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.schedule("t1", triggerAtMs = 12345L)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(12345L, alarms.first().triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarms.first().type)
    }

    @Test
    fun `정확 알람 권한이 없으면 창 모드로 강등하되 예약은 유지한다`() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)

        scheduler.schedule("t1", triggerAtMs = 12345L)

        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(1, alarms.size)
        assertEquals(12345L, alarms.first().triggerAtMs)
    }

    @Test
    fun `취소하면 같은 항목의 알람이 사라진다`() {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler.schedule("t1", triggerAtMs = 12345L)

        scheduler.cancel("t1")

        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `복원 시 미래 리마인더는 재예약하고 과거는 즉시 발화한다`() = runTest {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val future = TaskItem(
            id = "future", title = "미래", isCompleted = false, createdAt = 0L,
            remindAtIso = "2099-01-01T09:00:00"
        )
        val past = TaskItem(
            id = "past", title = "과거", isCompleted = false, createdAt = 0L,
            remindAtIso = "2000-01-01T09:00:00"
        )
        coEvery { repository.getActiveReminders() } returns AppResult.Success(listOf(future, past))

        scheduler.restoreAll(now = 1_000_000_000_000L) // 2001-09-09 근처 — past 와 future 사이

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
        coVerify(exactly = 1) { fireHandler.fire("past", 1_000_000_000_000L) }
        coVerify(exactly = 0) { fireHandler.fire("future", any()) }
    }

    @Test
    fun `복원 조회 실패 시 아무것도 하지 않는다`() = runTest {
        coEvery { repository.getActiveReminders() } returns
            AppResult.Failure(com.kosmos.app.core.common.AppError.DbReadError("task_item"))

        scheduler.restoreAll(now = 0L)

        assertEquals(0, shadowOf(alarmManager).scheduledAlarms.size)
        coVerify(exactly = 0) { fireHandler.fire(any(), any()) }
    }

    @Test
    fun `파생 ID 는 결정적이고 고정 상수 영역과 겹치지 않는다`() {
        val id = reminderStableId("3f2a9c1e-0000-4000-8000-123456789abc")

        assertEquals(id, reminderStableId("3f2a9c1e-0000-4000-8000-123456789abc"))
        assertTrue("고정 상수 영역(0~1003)과 겹친다", id >= 0x40000000)
        assertNotEquals(reminderStableId("다른-아이디"), id)
    }
}

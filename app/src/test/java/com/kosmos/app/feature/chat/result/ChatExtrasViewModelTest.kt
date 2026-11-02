package com.kosmos.app.feature.chat.result

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.domain.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ChatExtrasViewModelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 15)
    private val reminder = TaskItem(id = "t1", title = "물 마시기", isCompleted = false, createdAt = 0, remindAtIso = "2026-10-15T13:56:00")

    @Test
    fun `ChatExtrasViewModel — setToday 로 날짜가 바뀌면 같은 키의 카드가 새 날짜 기준으로 다시 매핑된다`() {
        val taskRepo: com.kosmos.app.domain.memory.TaskRepository = io.mockk.mockk(relaxed = true)
        io.mockk.every { taskRepo.observeById("t1") } returns kotlinx.coroutines.flow.flowOf(reminder)
        val vm = ChatExtrasViewModel(
            taskRepository = taskRepo,
            knowledgeRepository = io.mockk.mockk(relaxed = true),
            getTodaySchedule = io.mockk.mockk(relaxed = true),
            reminderEditor = io.mockk.mockk(relaxed = true)
        )
        val msgResult = com.kosmos.app.domain.model.MessageResult(com.kosmos.app.domain.model.MessageResult.Kind.REMINDER, "t1")
        val key = ChatExtrasViewModel.keyOf(msgResult)

        vm.ensure(context, msgResult, today)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("오늘", vm.results.value[key]?.sub)

        vm.setToday(today.plusDays(1))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("10월 15일 (목)", vm.results.value[key]?.sub)
    }
}

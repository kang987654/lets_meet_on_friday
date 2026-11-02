package com.kosmos.app.data

import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.data.local.db.entity.TaskEntity
import com.kosmos.app.testing.InMemoryKosmosDbRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [TaskDaoTest]
 * 인메모리 Room 에서 [com.kosmos.app.data.local.db.dao.TaskDao] 쿼리 계약을 검증합니다.
 */
@RunWith(RobolectricTestRunner::class)
class TaskDaoTest {

    @get:Rule
    val dbRule = InMemoryKosmosDbRule()

    private lateinit var db: KosmosDatabase

    @Before
    fun setUp() {
        db = dbRule.db
    }

    @Test
    fun `deleteCompleted 는 완료된 일정이나 취소된 알림을 빼고 완료된 할 일만 지운다`() = runBlocking {
        val dao = db.taskDao()
        val completedTodo = TaskEntity(
            id = "todo1", title = "완료 할 일", isCompleted = true, completedAt = 100L, createdAt = 1L
        )
        val completedSchedule = TaskEntity(
            id = "sched1", title = "완료 일정", dueDateIso = "2026-11-02T10:00:00", isCompleted = true, completedAt = 100L, createdAt = 2L
        )
        val cancelledReminder = TaskEntity(
            id = "rem1", title = "취소된 알림", remindAtIso = "2026-11-02T12:00:00", isCompleted = true, completedAt = 100L, cancelledAt = 50L, createdAt = 3L
        )
        dao.insert(completedTodo)
        dao.insert(completedSchedule)
        dao.insert(cancelledReminder)

        assertEquals(1, dao.countCompleted())
        val deleted = dao.deleteCompleted()

        assertEquals("countCompleted 와 똑같이 할 일 1건만 지워져야 한다", 1, deleted)
        assertEquals(null, dao.getById("todo1"))
        assertEquals("완료된 일정은 남아야 한다", "완료 일정", dao.getById("sched1")?.title)
        assertEquals("취소된 알림은 남아야 한다", "취소된 알림", dao.getById("rem1")?.title)
    }
}

package com.kosmos.app.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 0.41.14 D2 — 복원 직후에는 표시만 남기고, 다음 시작 때 한 번 그린 뒤 표시를 지운다. */
@RunWith(AndroidJUnit4::class)
class WidgetRefreshPendingTest {

    @Test
    fun `표시가 있으면 한 번 그리고 지우고 없으면 아무것도 안 한다`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val marker = File(context.noBackupFilesDir, "widget_refresh_pending")
        var drawn = 0
        val refresher = object : GlanceWidgetRefresher(context) {
            override suspend fun refresh() { drawn++ }
        }

        refresher.refreshIfPending()
        assertTrue("표시가 없으면 그리지 않는다", drawn == 0)

        refresher.refreshOnNextStart()
        assertTrue(marker.exists())
        refresher.refreshIfPending()
        assertTrue(drawn == 1)
        assertFalse("한 번 그린 뒤 지운다", marker.exists())
        refresher.refreshIfPending()
        assertTrue(drawn == 1)
    }
}

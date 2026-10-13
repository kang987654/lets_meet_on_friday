package com.kosmos.app.platform.notification

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * [NotificationAccessTest]
 * 알림 권한 판정 — 모델을 직접 넣은 기기처럼 한 번도 허용되지 않은 상태가 "뜰 수 없음"으로 읽혀야 화면이 요청한다.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationAccessTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `API 33 이상에서는 알림 권한이 요청 대상이다`() {
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, NotificationAccess.runtimePermission)
    }

    @Test
    fun `허용 전에는 알림을 띄울 수 없고 허용하면 띄울 수 있다`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(NotificationAccess.canPost(app))

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(NotificationAccess.canPost(app))
    }
}

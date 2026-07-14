package com.kosmos.app.core.security

object PermissionPolicy {
    const val CALENDAR_READ = "android.permission.READ_CALENDAR"
    const val CALENDAR_WRITE = "android.permission.WRITE_CALENDAR"
    const val MICROPHONE = "android.permission.RECORD_AUDIO"

    val AUTO_ALLOWED = setOf(CALENDAR_READ)
    val REQUIRES_PERMISSION = setOf(CALENDAR_READ, CALENDAR_WRITE, MICROPHONE)
}

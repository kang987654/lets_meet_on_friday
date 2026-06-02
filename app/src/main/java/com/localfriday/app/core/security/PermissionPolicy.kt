package com.localfriday.app.core.security

import android.Manifest

object PermissionPolicy {
    const val CALENDAR_READ = Manifest.permission.READ_CALENDAR
    const val CALENDAR_WRITE = Manifest.permission.WRITE_CALENDAR
    const val MICROPHONE = Manifest.permission.RECORD_AUDIO

    val AUTO_ALLOWED = setOf(CALENDAR_READ)
    val REQUIRES_PERMISSION = setOf(CALENDAR_READ, CALENDAR_WRITE, MICROPHONE)
}

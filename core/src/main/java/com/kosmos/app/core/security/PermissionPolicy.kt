package com.kosmos.app.core.security

/**
 * [PermissionPolicy]
 * 안드로이드 시스템 권한(READ_CALENDAR, WRITE_CALENDAR, RECORD_AUDIO) 및 권한 허용 정책 상수를 정의하는 보안 객체입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Security)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 툴 실행 전 런타임 권한 검증 및 에러 코드 매핑 시 권한 키를 제공합니다.
 */
object PermissionPolicy {
    const val CALENDAR_READ = "android.permission.READ_CALENDAR"
    const val CALENDAR_WRITE = "android.permission.WRITE_CALENDAR"
    const val MICROPHONE = "android.permission.RECORD_AUDIO"

    val AUTO_ALLOWED = setOf(CALENDAR_READ)
    val REQUIRES_PERMISSION = setOf(CALENDAR_READ, CALENDAR_WRITE, MICROPHONE)
}

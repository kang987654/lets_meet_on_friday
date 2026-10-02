package com.kosmos.app.platform.document

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 콘텐츠 URI 의 표시 이름(파일 이름) — 제공자가 모르거나 조회가 실패하면 null.
 *
 * [WHY] 다른 앱 제공자로 가는 IPC 라 실패할 수 있다(권한 만료·제공자 종료) — 이름은 부가 정보라 예외를 삼킨다.
 */
internal fun ContentResolver.displayName(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx != -1) cursor.getString(idx) else null
    }
}.getOrNull()

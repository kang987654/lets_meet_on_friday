package com.kosmos.app.platform.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.document.RecentDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [DocumentAccess]
 * 문서 홈이 고른 파일의 영구 읽기 권한과 이름을 다룹니다 (0.34.0) — 화면 테스트가 가짜로 바꿀 수 있게 인터페이스로 둔다.
 */
interface DocumentAccess {
    /** 영구 읽기 권한을 받고 최근 문서 항목을 만든다. 권한을 못 받으면 null(그래도 지금 한 번은 열 수 있다). */
    suspend fun persist(uri: Uri, now: Long = System.currentTimeMillis()): RecentDocument?

    /** 예전에 받은 영구 권한이 아직 있는가. */
    fun hasAccess(uri: String): Boolean

    /** 목록에서 빠진 문서의 영구 권한을 놓는다. */
    fun release(uri: String)
}

/**
 * [AndroidDocumentAccess]
 * [DocumentAccess] 구현 — `ACTION_OPEN_DOCUMENT` 로 받은 URI 에 `takePersistableUriPermission`.
 *
 * [WHY] 영구 권한 수에는 앱당 상한이 있다(안드로이드 버전마다 128~512) — 목록에서 밀려나거나 지운 문서의 권한은 [release] 로 돌려준다.
 */
class AndroidDocumentAccess @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DocumentAccess {

    override suspend fun persist(uri: Uri, now: Long): RecentDocument? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val persisted = runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { AppLogger.w(TAG, "영구 권한 실패: ${it.message}") }.isSuccess
        if (!persisted) return@withContext null
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx != -1) cursor.getString(idx) else null
            }
        }.getOrNull() ?: "문서"
        RecentDocument(uri.toString(), name, runCatching { resolver.getType(uri) }.getOrNull(), now)
    }

    override fun hasAccess(uri: String): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri.toString() == uri && it.isReadPermission }

    override fun release(uri: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private companion object {
        const val TAG = "DocumentAccess"
    }
}

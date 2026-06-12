package com.localfriday.app.platform.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class SharedInput {
    data class Text(val content: String) : SharedInput()
    data class Image(val uri: Uri, val sizeBytes: Long) : SharedInput()
    data class Document(val uri: Uri, val fileName: String, val textContent: String) : SharedInput()
}

@Singleton
class ShareIntentHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _sharedInputFlow = MutableSharedFlow<AppResult<SharedInput>>(extraBufferCapacity = 1)
    val sharedInputFlow = _sharedInputFlow.asSharedFlow()

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return

        val type = intent.type ?: return

        when {
            type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    _sharedInputFlow.tryEmit(AppResult.Success(SharedInput.Text(text)))
                } else {
                    _sharedInputFlow.tryEmit(AppResult.Failure(AppError.ValidationError("Share", "Empty text")))
                }
            }
            type.startsWith("image/") -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    processImageUri(uri, type)
                } else {
                    _sharedInputFlow.tryEmit(AppResult.Failure(AppError.ValidationError("Share", "Empty URI")))
                }
            }
            else -> {
                // Unsupported MIME type
                _sharedInputFlow.tryEmit(AppResult.Failure(AppError.UnsupportedImageFormat(type)))
            }
        }
    }

    private fun processImageUri(uri: Uri, mimeType: String) {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var sizeBytes = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = it.getLong(sizeIndex)
                    }
                }
            }

            // 10MB limit (TASK-066)
            if (sizeBytes > 10 * 1024 * 1024) {
                _sharedInputFlow.tryEmit(AppResult.Failure(AppError.ImageTooLarge(sizeBytes)))
                return
            }

            _sharedInputFlow.tryEmit(AppResult.Success(SharedInput.Image(uri, sizeBytes)))
        } catch (e: Exception) {
            e.printStackTrace()
            // 권한 오류나 기타 파일 시스템 에러 시 크래시 방지 및 에러 반환
            _sharedInputFlow.tryEmit(AppResult.Failure(AppError.UnsupportedImageFormat(mimeType)))
        }
    }
}

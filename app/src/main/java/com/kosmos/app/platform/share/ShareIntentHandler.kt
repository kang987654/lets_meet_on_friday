package com.kosmos.app.platform.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
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
    // [WHY] replay=1이 없으면 콜드 스타트 시(구독자인 ChatViewModel이 생기기 전) 공유 인텐트가 유실된다.
    // 늦은 구독자도 마지막 공유를 수신하며, 소비 후 clearConsumed()로 재전달을 막는다.
    private val _sharedInputFlow = MutableSharedFlow<AppResult<SharedInput>>(replay = 1, extraBufferCapacity = 1)
    val sharedInputFlow = _sharedInputFlow.asSharedFlow()

    /** 공유 입력을 소비한 뒤 호출 — replay 캐시를 비워 재구독 시 중복 처리를 방지합니다. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clearConsumed() {
        _sharedInputFlow.resetReplayCache()
    }

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
                    _sharedInputFlow.tryEmit(AppResult.Failure(AppError.ValidationError(com.kosmos.app.core.common.ValidationField.CONTENT, com.kosmos.app.core.common.ValidationReason.BLANK)))
                }
            }
            type.startsWith("image/") -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    processImageUri(uri, type)
                } else {
                    _sharedInputFlow.tryEmit(AppResult.Failure(AppError.ValidationError(com.kosmos.app.core.common.ValidationField.CONTENT, com.kosmos.app.core.common.ValidationReason.BLANK)))
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

            // [WHY] 매직 넘버였던 것을 core 상수로 교체했다 — 같은 한도가
            // `ImageInputAdapter`(단일 관문)에도 있으므로 두 값이 갈리면 안 된다. 여기 검사는
            // 인테이크 단계에서 사용자에게 먼저 알려 주기 위한 것이고, 실제 방어는 관문에 있다.
            if (sizeBytes > Constants.MAX_IMAGE_SIZE_BYTES) {
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

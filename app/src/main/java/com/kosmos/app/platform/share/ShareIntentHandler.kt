package com.kosmos.app.platform.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.core.common.ValidationField
import com.kosmos.app.core.common.ValidationReason
import com.kosmos.app.core.logging.AppLogger
import com.kosmos.app.domain.document.DocumentResult
import com.kosmos.app.platform.document.DocumentTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class SharedInput {
    data class Text(val content: String) : SharedInput()
    data class Image(val uri: Uri, val sizeBytes: Long) : SharedInput()
    /** @property truncated 문서가 길어 앞부분만 담았다(입력바 안내용, 0.35.0). */
    data class Document(val uri: Uri, val fileName: String, val textContent: String, val truncated: Boolean = false) : SharedInput()
}

@Singleton
class ShareIntentHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val documentExtractor: DocumentTextExtractor
) {
    // [WHY] replay=1이 없으면 콜드 스타트 시(구독자인 ChatViewModel이 생기기 전) 공유 인텐트가 유실된다.
    // 늦은 구독자도 마지막 공유를 수신하며, 소비 후 clearConsumed()로 재전달을 막는다.
    private val _sharedInputFlow = MutableSharedFlow<AppResult<SharedInput>>(replay = 1, extraBufferCapacity = 1)

    // [WHY] handleIntent 는 MainActivity.onCreate/onNewIntent(메인 스레드)에서 불린다. 이미지 크기
    // 조회(contentResolver.query)는 다른 앱 프로바이더로 가는 IPC 라 메인에서 돌리면 ANR 후보다.
    // 결과는 원래도 replay Flow 로 비동기 전달되므로 IO 로 옮겨도 소비 계약이 같다.
    private val ioScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )
    val sharedInputFlow = _sharedInputFlow.asSharedFlow()

    /**
     * 앱 안에서 만든 첨부를 넘긴다 — 문서 뷰어의 "채팅으로 보내기"(0.35.0). 같은 replay 계약이라 채팅 화면이 늦게 떠도 받는다.
     */
    fun offer(input: SharedInput) {
        _sharedInputFlow.tryEmit(AppResult.Success(input))
    }

    private val _extracting = MutableStateFlow(false)

    /** 입력바에서 고른 문서 파일을 읽는 중 — 입력바가 "읽는 중…"을 보인다. */
    val extracting: StateFlow<Boolean> = _extracting

    /**
     * 입력바에서 고른 문서 파일(xlsx·docx·hwpx·PDF)을 IO 에서 글자로 바꿔 첨부로 넘긴다 (0.35.0 M4).
     *
     * [WHY] 텍스트 파일 첨부는 E2E waitForIdle 경합 때문에 동기다(AttachmentReader KDoc). 문서 파일은 압축을 풀고 파싱해야 해 메인에서
     * 할 수 없다 — 이 경로만 비동기로 두고 결과는 같은 replay 흐름으로 보낸다. 실패는 "지원하지 않는 파일 형식" 안내로.
     */
    fun offerDocumentFile(uri: Uri, mimeType: String?) {
        _extracting.value = true
        ioScope.launch {
            val result = documentExtractor.extract(uri, mimeType)
            _extracting.value = false
            when (result) {
                is DocumentResult.Ok -> {
                    val (name, text) = result.value
                    _sharedInputFlow.tryEmit(AppResult.Success(SharedInput.Document(uri, name, text.text, text.truncated)))
                }
                is DocumentResult.Fail ->
                    _sharedInputFlow.tryEmit(AppResult.Failure(AppError.UnsupportedImageFormat("문서 첨부 실패: ${result.error}")))
            }
        }
    }

    /** 공유 입력을 소비한 뒤 호출 — replay 캐시를 비워 재구독 시 중복 처리를 방지합니다. */
    @OptIn(ExperimentalCoroutinesApi::class)
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
                    _sharedInputFlow.tryEmit(AppResult.Failure(AppError.ValidationError(ValidationField.CONTENT, ValidationReason.BLANK)))
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
                    ioScope.launch { processImageUri(uri, type) }
                } else {
                    _sharedInputFlow.tryEmit(AppResult.Failure(AppError.ValidationError(ValidationField.CONTENT, ValidationReason.BLANK)))
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
            AppLogger.e("ShareIntentHandler", "공유 이미지 조회 실패", e)
            // 권한 오류나 기타 파일 시스템 에러 시 크래시 방지 및 에러 반환
            _sharedInputFlow.tryEmit(AppResult.Failure(AppError.UnsupportedImageFormat(mimeType)))
        }
    }
}

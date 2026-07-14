package com.kosmos.app.data.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ModelDownloadService]
 * 핵심 역할: 외부 서버(URL)로부터 대용량 LLM 모델 파일을 기기 내부 저장소에 다운로드합니다.
 * Architecture Context: Data Layer (Network). OkHttp를 사용하여 HTTP 스트리밍 통신을 전담하며, Domain(UseCase)에 결과를 전달합니다.
 * Key Flow:
 * 1. OkHttp Request 생성 및 스트리밍 응답 대기.
 * 2. okio.sink를 통해 context.filesDir/models 하위에 버퍼링 청크 단위로 파일 쓰기 수행.
 * 3. 누적 바이트를 계산하여 0~100 사이의 진행률을 Flow로 방출.
 */
import com.kosmos.app.domain.tool.ModelDownloader

@Singleton
class ModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelDownloader {
    private val okHttpClient = OkHttpClient.Builder()
        .build()

    /**
     * 지정된 URL에서 파일을 스트리밍으로 다운로드하고, 진행률(0~100)을 Flow로 반환합니다.
     * 
     * @param url 다운로드할 모델의 URL
     * @param fileName 저장할 파일명 (기본값: url의 마지막 path segment)
     * @return 진행률(Int) Flow
     */
    override fun downloadModel(url: String, fileName: String?): Flow<Int> = flow {
        val request = Request.Builder().url(url).build()
        val actualFileName = fileName ?: url.substringAfterLast("/")
        val targetDir = File(context.filesDir, "models")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, actualFileName)

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to download file: ${response.code}")
        }

        val body = response.body ?: throw Exception("Response body is null")
        val contentLength = body.contentLength()
        val source = body.source()
        val sink = targetFile.sink().buffer()

        var totalBytesRead = 0L
        var lastProgress = 0

        try {
            emit(0)
            while (true) {
                val readCount = source.read(sink.buffer, 8192)
                if (readCount == -1L) break
                totalBytesRead += readCount
                sink.emit()

                if (contentLength > 0) {
                    val progress = ((totalBytesRead.toDouble() / contentLength.toDouble()) * 100).toInt()
                    if (progress > lastProgress) {
                        lastProgress = progress
                        emit(progress)
                    }
                }
            }
            sink.flush()
            emit(100) // Ensure 100% is emitted
        } finally {
            sink.close()
            source.close()
        }
    }.flowOn(Dispatchers.IO)
}

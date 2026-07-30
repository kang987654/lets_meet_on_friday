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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

import com.kosmos.app.domain.tool.ModelDownloader

/**
 * [ModelDownloadService]
 * 핵심 역할: 외부 서버(URL)로부터 대용량 LLM 모델 파일을 기기 내부 저장소에 다운로드합니다.
 * Architecture Context: Data Layer (Network). 공유 OkHttpClient(DI)를 사용하여 HTTP 스트리밍 통신을 전담하며, Domain(UseCase)에 결과를 전달합니다.
 * Key Flow:
 * 1. OkHttp Request 생성 및 스트리밍 응답 대기.
 * 2. `<파일명>.part` 임시 파일에 청크 단위로 기록하고, 완료 시에만 최종 파일명으로 원자적 rename.
 * 3. 실패/취소 시 임시 파일을 삭제하여 부분 다운로드가 유효 모델로 오인되는 것을 방지.
 * 4. 누적 바이트를 계산하여 0~100 사이의 진행률을 Flow로 방출.
 */
@Singleton
class ModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) : ModelDownloader {

    /**
     * 지정된 URL에서 파일을 스트리밍으로 다운로드하고, 진행률(0~100)을 Flow로 반환합니다.
     *
     * @param url 다운로드할 모델의 URL
     * @param fileName 저장할 파일명 (기본값: url의 마지막 path segment, 쿼리스트링 제외)
     * @return 진행률(Int) Flow
     */
    override fun downloadModel(url: String, fileName: String?): Flow<Int> = flow {
        val request = Request.Builder().url(url).build()
        val actualFileName = fileName
            ?: url.substringAfterLast("/").substringBefore("?").substringBefore("#")
        val targetDir = File(context.filesDir, "models")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, actualFileName)
        // [WHY] 최종 경로에 직접 쓰면 실패/취소 시 부분 파일이 유효 모델로 선택될 수 있어
        // 임시 .part 파일에 기록 후 성공 시에만 rename 한다.
        val partFile = File(targetDir, "$actualFileName.part")

        var completed = false
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to download file: HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Response body is null")
                val contentLength = body.contentLength()

                body.source().use { source ->
                    partFile.sink().buffer().use { sink ->
                        var totalBytesRead = 0L
                        var lastProgress = 0

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
                    }
                }
            }

            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Failed to replace existing model file: ${targetFile.name}")
            }
            if (!partFile.renameTo(targetFile)) {
                throw IOException("Failed to finalize downloaded model file: ${targetFile.name}")
            }
            completed = true
            emit(100) // Ensure 100% is emitted
        } finally {
            if (!completed && partFile.exists()) {
                partFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO)
}

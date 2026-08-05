package com.kosmos.app.data.network

import android.content.Context
import com.kosmos.app.core.common.Constants
import com.kosmos.app.data.di.DownloadClient
import com.kosmos.app.domain.tool.DownloadProbe
import com.kosmos.app.domain.tool.DownloadProgress
import com.kosmos.app.domain.tool.ModelDownloadException
import com.kosmos.app.domain.tool.ModelDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.appendingSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PartMeta]
 * 부분 다운로드(`.part`)가 어느 URL·어느 리소스 버전에서 왔는지 기록하는 사이드카입니다.
 *
 * [WHY] 프로세스가 죽어도 이어받기가 가능해야 하므로 재개 판정 정보를 메모리에 두면 안 된다.
 * 서버 파일이 교체되었는데 이어받으면 두 버전이 섞인 손상 파일이 만들어지므로 ETag를 함께 남긴다.
 */
@Serializable
internal data class PartMeta(
    val url: String,
    val entityTag: String?,
    val totalBytes: Long
) {
    fun matches(url: String, entityTag: String?): Boolean =
        this.url == url && this.entityTag == entityTag && entityTag != null

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 손상되었거나 없는 메타는 null로 취급해 "이어받을 수 없음"으로 안전하게 수렴시킵니다. */
        fun read(file: File): PartMeta? =
            runCatching { json.decodeFromString<PartMeta>(file.readText()) }.getOrNull()

        /**
         * [WHY] 메타 기록 실패는 다운로드를 중단시킬 사유가 아니다 — 잃는 것은 "이어받기 가능성"
         * 뿐이고, 그건 이관 이전의 기존 동작과 같다. 그래서 실패를 삼키고 전송을 계속한다.
         */
        fun write(file: File, meta: PartMeta) {
            runCatching { file.writeText(json.encodeToString(meta)) }
        }
    }
}

/**
 * [ModelDownloadService]
 * 외부 서버로부터 대용량 LLM 모델 파일을 기기 내부 저장소에 내려받습니다.
 *
 * ### Architecture Context
 * - **Layer**: Data (Network) — 스케줄링은 `:app`의 WorkManager가 담당하고 여기서는 바이트 전송만
 * - **Dependencies**: [OkHttpClient] (`@DownloadClient`), Android [Context]
 *
 * ### Key Flow
 * 1. [probe]로 총 크기·ETag·Range 지원 여부를 확인하고 저장 공간을 사전 점검합니다.
 * 2. `.part`가 남아 있고 ETag가 일치하면 `Range` 헤더로 이어받고, 아니면 처음부터 받습니다.
 * 3. 완료 시 기존 모델을 `.bak`으로 옮긴 뒤 rename 하고, 실패하면 기존 모델을 되돌립니다.
 * 4. 일시적 실패·취소에서는 `.part`를 남겨 다음 시도가 이어받을 수 있게 합니다.
 *
 * [WHY] 이전 구현은 (a) Range를 쓰지 않아 재시도가 매번 0바이트부터 3.6GB를 다시 받았고,
 * (b) `finally`에서 `.part`를 무조건 지워 이어받기가 원천적으로 불가능했으며,
 * (c) 기존 모델을 먼저 delete 한 뒤 rename 해 rename 실패 시 동작하던 모델까지 잃었다. (ADR-006)
 */
@Singleton
class ModelDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    @DownloadClient private val okHttpClient: OkHttpClient
) : ModelDownloader {

    override suspend fun probe(url: String): DownloadProbe = withContext(Dispatchers.IO) {
        // [WHY] HEAD를 거부하는 CDN이 있어, 실패하면 Range 0-0 GET으로 헤더만 확인한다.
        val head = runCatching { execute(Request.Builder().url(url).head().build()) }.getOrNull()
        val usable = head?.takeIf { it.isSuccessful }
            ?: execute(Request.Builder().url(url).header("Range", "bytes=0-0").build())

        usable.use { response ->
            if (!response.isSuccessful) throw response.toException()
            val entityTag = response.header("ETag") ?: response.header("Last-Modified")
            val supportsRange = response.code == 206 ||
                response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            // 206 응답의 Content-Length는 1바이트이므로 Content-Range의 전체 크기를 우선한다.
            val total = response.header("Content-Range")
                ?.substringAfter('/', "")
                ?.toLongOrNull()
                ?: response.header("Content-Length")?.toLongOrNull()
                ?: -1L
            DownloadProbe(totalBytes = total, entityTag = entityTag, supportsRange = supportsRange)
        }
    }

    override fun downloadModel(url: String, fileName: String?): Flow<DownloadProgress> = flow {
        val target = targetFile(url, fileName)
        target.parentFile?.mkdirs()
        val part = partFile(target)
        val metaFile = metaFile(target)

        val probe = probe(url)
        val existingMeta = PartMeta.read(metaFile)
        val resumeFrom = if (part.exists() && probe.supportsRange &&
            existingMeta?.matches(url, probe.entityTag) == true
        ) {
            part.length()
        } else {
            0L
        }
        // [WHY] 이어받을 수 없는 부분 파일은 남겨두면 다음 시도에서 잘못된 오프셋으로 오해되므로 지운다.
        if (resumeFrom == 0L && part.exists()) part.delete()

        val expectedTotal = probe.totalBytes.takeIf { it > 0 } ?: Constants.EXPECTED_MODEL_SIZE_BYTES
        ensureFreeSpace(expectedTotal - resumeFrom + Constants.MODEL_DOWNLOAD_SPACE_SLACK_BYTES)

        // [WHY] 첫 바이트를 쓰기 전에 메타를 기록해야 스트리밍 중 프로세스가 죽어도 재개할 수 있다.
        PartMeta.write(metaFile, PartMeta(url, probe.entityTag, probe.totalBytes))

        try {
            val request = Request.Builder().url(url).apply {
                if (resumeFrom > 0) {
                    header("Range", "bytes=$resumeFrom-")
                    // [WHY] 서버 파일이 교체되면 206 대신 200이 오므로, 아래에서 처음부터 다시 받는다.
                    probe.entityTag?.let { header("If-Range", it) }
                }
            }.build()

            var downloaded: Long
            execute(request).use { response ->
                when {
                    response.code == 416 -> {
                        // 요청한 범위가 무효 — 부분 파일을 버리고 다음 시도에서 처음부터 받는다.
                        part.delete()
                        throw ModelDownloadException.Transient("Range not satisfiable; restarting")
                    }
                    !response.isSuccessful -> throw response.toException()
                }

                val isPartial = response.code == 206
                downloaded = if (isPartial) resumeFrom else 0L
                val body = response.body ?: throw ModelDownloadException.Transient("Empty body")
                val total = if (isPartial) {
                    response.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                        ?: probe.totalBytes
                } else {
                    body.contentLength().takeIf { it > 0 } ?: probe.totalBytes
                }

                emit(DownloadProgress(downloaded, total))
                body.source().use { source ->
                    val sink = if (isPartial) part.appendingSink() else part.sink()
                    sink.buffer().use { out ->
                        // [WHY] 64KB 청크마다 방출하면 3.6GB 다운로드에 6만 번 넘게 emit 된다.
                        // 1MB 단위로 묶어야 상위(Worker)의 스로틀 이전에 불필요한 부하가 생기지 않는다.
                        var lastReported = downloaded
                        while (true) {
                            val read = source.read(out.buffer, STREAM_CHUNK_BYTES)
                            if (read == -1L) break
                            downloaded += read
                            out.emit()
                            if (downloaded - lastReported >= PROGRESS_REPORT_INTERVAL_BYTES) {
                                lastReported = downloaded
                                emit(DownloadProgress(downloaded, total))
                            }
                        }
                        out.flush()
                    }
                }
            }

            verifyAndFinalize(part, target, expected = probe.totalBytes)
            metaFile.delete()
            emit(DownloadProgress(target.length(), target.length()))
        } catch (e: ModelDownloadException.Permanent) {
            // [WHY] 영구 실패는 이어받아도 같은 결과이므로 부분 파일을 정리해 저장 공간을 되돌려준다.
            part.delete()
            metaFile.delete()
            throw e
        } catch (e: IOException) {
            // 일시적 실패: .part 와 .part.meta 를 그대로 남겨 다음 시도가 Range 로 이어받는다.
            throw ModelDownloadException.Transient(e.message ?: "Network failure", e)
        }
        // CancellationException 도 잡지 않는다 → 사용자 취소 시에도 부분 파일이 보존된다.
    }.flowOn(Dispatchers.IO)

    override fun clearPartial(url: String, fileName: String?) {
        val target = targetFile(url, fileName)
        partFile(target).delete()
        metaFile(target).delete()
    }

    override fun partialBytes(url: String, fileName: String?): Long {
        val part = partFile(targetFile(url, fileName))
        return if (part.exists()) part.length() else 0L
    }

    /**
     * 부분 파일을 최종 모델 파일로 확정합니다.
     *
     * [WHY] 기존 구현은 target 을 먼저 delete 한 뒤 rename 했다 — rename 이 실패하면 사용자는
     * 새 모델도 없고 동작하던 모델도 없는 상태가 된다. 기존 파일을 `.bak`으로 옮겨두고
     * 실패 시 되돌린다.
     */
    private fun verifyAndFinalize(part: File, target: File, expected: Long) {
        if (expected > 0 && part.length() != expected) {
            throw ModelDownloadException.Permanent(
                "Size mismatch: expected $expected bytes, got ${part.length()}"
            )
        }
        val backup = File(target.parentFile, target.name + Constants.MODEL_BACKUP_SUFFIX)
        backup.delete()
        val staged = target.exists()
        if (staged && !target.renameTo(backup)) {
            throw ModelDownloadException.Permanent("Cannot stage existing model: ${target.name}")
        }
        if (!part.renameTo(target)) {
            if (staged) backup.renameTo(target)
            throw ModelDownloadException.Permanent("Cannot finalize model file: ${target.name}")
        }
        backup.delete()
    }

    private fun ensureFreeSpace(required: Long) {
        val available = context.filesDir.usableSpace
        if (available < required) {
            throw ModelDownloadException.InsufficientStorage(required, available)
        }
    }

    private fun execute(request: Request): Response =
        try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw ModelDownloadException.Transient(e.message ?: "Connection failed", e)
        }

    /**
     * HTTP 응답 코드를 재시도 가능 여부로 번역합니다.
     * [WHY] 5xx·408·429는 서버 측 일시 상태이므로 재시도 가치가 있으나, 그 외 4xx(404, 403 등)는
     * 몇 번을 다시 요청해도 같은 응답이 온다.
     */
    private fun Response.toException(): ModelDownloadException =
        if (code >= 500 || code == 408 || code == 429) {
            ModelDownloadException.Transient("HTTP $code")
        } else {
            ModelDownloadException.Permanent("HTTP $code")
        }

    private fun targetFile(url: String, fileName: String?): File {
        val actual = fileName
            ?: url.substringAfterLast("/").substringBefore("?").substringBefore("#")
        return File(File(context.filesDir, Constants.MODEL_DIR_NAME), actual)
    }

    private fun partFile(target: File) =
        File(target.parentFile, target.name + Constants.MODEL_PART_SUFFIX)

    private fun metaFile(target: File) =
        File(target.parentFile, target.name + Constants.MODEL_PART_META_SUFFIX)

    private companion object {
        const val STREAM_CHUNK_BYTES = 64L * 1024
        const val PROGRESS_REPORT_INTERVAL_BYTES = 1024L * 1024
    }
}

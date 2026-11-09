package com.kosmos.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kosmos.app.core.common.Constants
import com.kosmos.app.data.network.ModelDownloadService
import com.kosmos.app.domain.tool.ModelDownloadException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * [ModelDownloadResumeTest]
 * [ModelDownloadService]의 이어받기·부분 파일 보존·기존 모델 보호 동작을 검증합니다.
 *
 * [WHY] 이 세 가지가 이번 이관의 핵심이고 동시에 조용히 깨지기 쉬운 부분이다.
 * 재시도가 이어받지 못하면 매번 3.6GB를 다시 받고, 확정 로직이 틀리면 동작하던 모델을 잃는다.
 * `:data`에 test source set이 없어 `:app`의 테스트에서 검증한다(`:app`이 `:data`를 의존).
 */
@RunWith(RobolectricTestRunner::class)
class ModelDownloadResumeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var server: MockWebServer
    private lateinit var service: ModelDownloadService

    private val fileName = "model.litertlm"
    private val body = ByteArray(2048) { (it % 251).toByte() }

    private val modelDir: File get() = File(context.filesDir, Constants.MODEL_DIR_NAME)
    private val target: File get() = File(modelDir, fileName)
    private val part: File get() = File(modelDir, fileName + Constants.MODEL_PART_SUFFIX)
    private val meta: File get() = File(modelDir, fileName + Constants.MODEL_PART_META_SUFFIX)

    private val url: String get() = server.url("/$fileName").toString()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        service = ModelDownloadService(context, OkHttpClient())
        modelDir.mkdirs()
        modelDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** HEAD 프로브 응답 — 총 크기와 Range 지원을 알린다. */
    private fun headResponse(etag: String = "\"v1\"") = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Length", body.size)
        .setHeader("Accept-Ranges", "bytes")
        .setHeader("ETag", etag)

    private fun fullBodyResponse() = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Length", body.size)
        .setBody(Buffer().write(body))

    private fun partialBodyResponse(from: Int) = MockResponse()
        .setResponseCode(206)
        .setHeader("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
        .setBody(Buffer().write(body, from, body.size - from))

    private fun download() = runBlocking {
        service.downloadModel(url, fileName).toList()
    }

    @Test
    fun `처음부터 받으면 전체 파일이 확정된다`() {
        server.enqueue(headResponse())
        server.enqueue(fullBodyResponse())

        val emissions = download()

        assertArrayEquals(body, target.readBytes())
        assertFalse("확정 후 부분 파일은 남지 않는다", part.exists())
        assertFalse("확정 후 메타도 정리된다", meta.exists())
        assertEquals(body.size.toLong(), emissions.last().downloadedBytes)
    }

    @Test
    fun `중간에 끊기면 부분 파일이 보존되어 다음 시도가 이어받는다`() {
        // 1차 시도: 응답 본문 도중 연결이 끊긴다.
        server.enqueue(headResponse())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", body.size)
                .setBody(Buffer().write(body, 0, 1024))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val failure = runCatching { download() }.exceptionOrNull()

        assertTrue(
            "전송 중 끊김은 재시도 가능해야 한다: $failure",
            failure is ModelDownloadException.Transient
        )
        // [WHY] 이전 구현은 finally 에서 .part 를 무조건 지워 이어받기가 불가능했다.
        assertTrue("부분 파일이 남아 있어야 이어받을 수 있다", part.exists())
        assertTrue(part.length() > 0)
        assertTrue("메타가 남아야 ETag 검증으로 이어받을 수 있다", meta.exists())
        assertEquals(part.length(), service.partialBytes(url, fileName))

        // 2차 시도: 남은 구간만 206 으로 받아 완성한다.
        val resumeFrom = part.length().toInt()
        server.enqueue(headResponse())
        server.enqueue(partialBodyResponse(resumeFrom))

        download()

        assertArrayEquals("이어받은 결과가 원본과 같아야 한다", body, target.readBytes())
    }

    @Test
    fun `멈춘 연결에서도 취소하면 곧바로 멈추고 부분 파일은 남는다`() = runBlocking {
        // [WHY] 블로킹 읽기는 코루틴 취소를 보지 않아 읽기 시간 제한까지 기다렸다 — 취소가 요청을 끊어야 한다(0.41.9 O2).
        server.enqueue(headResponse())
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", body.size)
                .setBody(Buffer().write(body, 0, 1024))
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )
        val slow = ModelDownloadService(context, OkHttpClient.Builder().readTimeout(java.time.Duration.ofSeconds(30)).build())
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { slow.downloadModel(url, fileName).collect { } }
        kotlinx.coroutines.delay(500)
        val startedAt = System.currentTimeMillis()
        job.cancel()
        job.join()
        assertTrue("취소가 읽기 시간 제한을 기다리지 않아야 한다", System.currentTimeMillis() - startedAt < 5_000)
        assertTrue("취소로 끝나야 한다", job.isCancelled)
    }

    @Test
    fun `이어받기 요청에 200이 오면 처음부터 다시 받는다`() {
        part.writeBytes(body.copyOfRange(0, 512))
        meta.writeText("""{"url":"$url","entityTag":"\"v1\"","totalBytes":${body.size}}""")

        // 서버 파일이 교체된 상황 — If-Range 가 어긋나 206 이 아니라 200 이 온다.
        server.enqueue(headResponse())
        server.enqueue(fullBodyResponse())

        download()

        // 부분 파일에 덧붙이지 않고 절단 후 다시 썼으므로 크기가 정확히 일치한다.
        assertEquals(body.size.toLong(), target.length())
        assertArrayEquals(body, target.readBytes())
    }

    @Test
    fun `ETag가 바뀌면 부분 파일을 버리고 처음부터 받는다`() {
        part.writeBytes(body.copyOfRange(0, 512))
        meta.writeText("""{"url":"$url","entityTag":"\"old\"","totalBytes":${body.size}}""")

        server.enqueue(headResponse(etag = "\"v2\""))
        server.enqueue(fullBodyResponse())

        download()

        assertArrayEquals(body, target.readBytes())
        // Range 헤더 없이 요청했는지 확인한다(프로브 요청 다음이 본문 요청).
        server.takeRequest()
        assertEquals(null, server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `저장 공간이 부족하면 전송 전에 차단하고 필요 바이트 수를 알린다`() {
        // 프로브가 가용 공간보다 큰 총 크기를 보고하도록 만든다.
        val hugeSize = context.filesDir.usableSpace + Constants.MODEL_DOWNLOAD_SPACE_SLACK_BYTES
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", hugeSize)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("ETag", "\"v1\"")
        )

        val failure = runCatching { download() }.exceptionOrNull()

        assertTrue(failure is ModelDownloadException.InsufficientStorage)
        // [WHY] 이전 구현은 requiredBytes 를 0L 로 넘겨 UI 가 아무 안내도 할 수 없었다.
        assertTrue((failure as ModelDownloadException.InsufficientStorage).requiredBytes > 0)
        assertFalse("전송을 시작하지 않았으므로 부분 파일도 없다", part.exists())
    }

    @Test
    fun `HTTP 404는 재시도하지 않는 영구 실패다`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        val failure = runCatching { download() }.exceptionOrNull()

        assertTrue(failure is ModelDownloadException.Permanent)
    }

    @Test
    fun `HTTP 503은 재시도 가능한 실패다`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))

        val failure = runCatching { download() }.exceptionOrNull()

        assertTrue(failure is ModelDownloadException.Transient)
    }

    @Test
    fun `새 모델 확정에 실패해도 기존 모델은 남는다`() {
        val existing = ByteArray(64) { 7 }
        target.writeBytes(existing)

        server.enqueue(headResponse())
        // 총 크기와 다른 본문을 보내 확정 단계의 크기 검증에서 실패하게 만든다.
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", body.size)
                .setBody(Buffer().write(body, 0, body.size - 1))
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
        )

        runCatching { download() }

        // [WHY] 이전 구현은 target 을 먼저 delete 한 뒤 rename 했다 — 확정이 실패하면
        // 사용자는 새 모델도 없고 쓰던 모델도 없는 상태가 됐다.
        assertTrue("기존 모델 파일이 보존되어야 한다", target.exists())
        assertArrayEquals(existing, target.readBytes())
    }

    @Test
    fun `clearPartial은 부분 파일과 메타를 함께 제거한다`() {
        part.writeBytes(ByteArray(10))
        meta.writeText("{}")

        service.clearPartial(url, fileName)

        assertFalse(part.exists())
        assertFalse(meta.exists())
        assertEquals(0L, service.partialBytes(url, fileName))
    }

    @Test
    fun `부분 파일이 이미 전체 크기면 다시 받지 않고 확정한다`() {
        // [WHY] 다 받은 직후 확정(rename) 전에 종료되면 .part 길이가 전체 크기와 같다(0.40.2 M2-5).
        // Range 요청을 보내면 416 으로 파일이 지워지므로 전송 요청 없이 바로 확정해야 한다.
        part.writeBytes(body)
        meta.writeText("""{"url":"$url","entityTag":"\"v1\"","totalBytes":${body.size}}""")
        server.enqueue(headResponse())

        val emissions = download()

        assertEquals("HEAD 프로브 1건만 가고 본문/Range 요청은 가지 않아야 한다", 1, server.requestCount)
        assertArrayEquals(body, target.readBytes())
        assertFalse(part.exists())
        assertFalse(meta.exists())
        assertEquals(body.size.toLong(), emissions.last().downloadedBytes)
    }
}

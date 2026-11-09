package com.kosmos.app.data

import com.kosmos.app.data.local.file.ExportManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExportManifestJsonTest]
 * 백업 manifest 의 직렬화 설정([ExportManifest.JSON])을 검증합니다 (0.41.9 S1).
 *
 * [WHY] 기본 `Json` 은 기본값 필드(`version` 등)를 파일에 쓰지 않아, 읽는 쪽 기본값이 바뀌면 옛 백업이 새 버전으로 읽힌다.
 */
class ExportManifestJsonTest {

    @Test
    fun `버전처럼 기본값인 필드도 파일에 쓴다`() {
        val text = ExportManifest.JSON.encodeToString(ExportManifest.serializer(), ExportManifest(appVersion = "0.41.9", exportDate = 1L))
        assertTrue("version 이 파일에 있어야 한다: $text", text.contains("\"version\":\"1.0\""))
        assertTrue(text.contains("\"encrypted\":false"))
    }

    @Test
    fun `모르는 필드가 있어도 읽는다`() {
        val text = """{"version":"1.0","appVersion":"9.9.9","exportDate":1,"futureField":true}"""
        val manifest = ExportManifest.JSON.decodeFromString(ExportManifest.serializer(), text)
        assertEquals("1.0", manifest.version)
        assertEquals("9.9.9", manifest.appVersion)
    }
}

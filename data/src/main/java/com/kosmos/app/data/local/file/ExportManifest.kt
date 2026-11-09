package com.kosmos.app.data.local.file

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 백업 zip 의 `manifest.json` — 읽고 쓸 때는 [JSON] 을 쓴다. */
@Serializable
data class ExportManifest(
    val version: String = "1.0",
    val encrypted: Boolean = false,
    val exportDate: Long = System.currentTimeMillis(),
    val appVersion: String,
    val modelId: String? = null,
    val sessionCount: Int = 0
) {
    companion object {
        /**
         * [WHY] 기본 `Json` 은 기본값 필드를 쓰지 않아 `version` 이 파일에 없었다 — 읽는 쪽 기본값이 바뀌면 옛 백업이 새 버전으로 읽힌다.
         * 모르는 필드도 넘긴다 — 새 앱이 필드를 더한 백업을 옛 앱이 "가져오기 실패"로 막지 않게(0.41.9 외부 분석 S1).
         */
        val JSON: Json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}

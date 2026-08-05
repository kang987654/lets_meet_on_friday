package com.kosmos.app.domain.tool

/**
 * [DownloadProgress]
 * 다운로드 진행 상황을 바이트 단위로 표현합니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Tool 모델)
 * - **Dependencies**: 없음
 *
 * [WHY] 이전에는 `Flow<Int>`(퍼센트)만 흘렸으나, 알림 본문("1.2GB / 3.6GB"),
 * 저장 공간 사전 점검, 이어받기 오프셋 계산에 모두 실제 바이트 수가 필요하다.
 *
 * @param totalBytes 총 크기. 서버가 Content-Length를 주지 않으면 -1(미상).
 */
data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    /** 0~100 퍼센트. 총 크기를 알 수 없으면 -1(진행률 표시 불가). */
    val percent: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
        } else {
            -1
        }
}

/**
 * [DownloadProbe]
 * 본문 전송 전에 확인한 원격 리소스의 메타데이터입니다.
 *
 * @param totalBytes 총 크기. 알 수 없으면 -1.
 * @param entityTag ETag 또는 Last-Modified — 이어받기 시 `If-Range` 검증에 사용합니다.
 * @param supportsRange 서버가 부분 요청(`Accept-Ranges: bytes`)을 지원하는지 여부.
 */
data class DownloadProbe(
    val totalBytes: Long,
    val entityTag: String?,
    val supportsRange: Boolean
)

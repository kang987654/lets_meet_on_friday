package com.kosmos.app.domain.tool

import kotlinx.coroutines.flow.Flow

/**
 * [ModelDownloader]
 * 모델 파일의 실제 바이트 전송을 담당하는 저수준 계약입니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Tool 인터페이스) — 구현은 `:data`의 `ModelDownloadService`
 * - **Dependencies**: 없음 (Pure Kotlin)
 *
 * ### Key Flow
 * 1. [probe]로 총 크기·ETag·Range 지원 여부를 먼저 확인합니다.
 * 2. [downloadModel]이 부분 파일(`.part`)을 이어받아 진행률을 스트리밍합니다.
 * 3. 실패·취소 시 `.part`는 보존되고, [clearPartial]로만 명시적으로 폐기합니다.
 *
 * [WHY] 스케줄링(WorkManager)과 바이트 전송을 분리해 이 계약이 Android 의존성 없이
 * 유지되도록 한다. 재시도 정책은 상위(Worker)가 결정하고 여기서는 예외 종류만 구분한다.
 */
interface ModelDownloader {

    /** 다운로드를 시작하기 전에 총 크기와 Range 지원 여부를 조회합니다. */
    suspend fun probe(url: String): DownloadProbe

    /**
     * 모델을 내려받으며 진행 상황을 방출합니다.
     * 실패 시 [ModelDownloadException]의 하위 타입을 던져 재시도 가능 여부를 알립니다.
     */
    fun downloadModel(url: String, fileName: String? = null): Flow<DownloadProgress>

    /** 부분 다운로드 파일과 메타데이터를 제거합니다. */
    fun clearPartial(url: String, fileName: String? = null)

    /** 이어받을 수 있는 부분 파일의 크기(바이트)를 반환합니다. 없으면 0입니다. */
    fun partialBytes(url: String, fileName: String? = null): Long
}

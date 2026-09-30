package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult
import java.io.File

/**
 * 기억 백업(zip) 내보내기·가져오기 계약입니다 (PRD F8).
 */
interface MemoryBackupManager {
    /** 캐시 디렉터리에 백업 zip 을 만든다. 저장 위치로 옮기는 것은 호출자 몫(SAF). */
    suspend fun createExportZip(appVersion: String): AppResult<File>

    /**
     * 백업 zip 으로 DB 를 교체한다. 성공 후에는 프로세스 재시작이 전제다.
     *
     * @param zipUriString SAF 가 돌려준 **`content://` URI 의 문자열 표현**(`Uri.toString()`).
     *   [WHY] domain 은 Android `Uri` 타입을 모르므로 문자열로 받는다 — 파일 경로가 아니다.
     *   더 새 스키마의 백업은 `ImportSchemaMismatch` 로 거부된다(다운그레이드 파괴 마이그레이션 방지).
     */
    suspend fun restoreFromZip(zipUriString: String): AppResult<Unit>
}

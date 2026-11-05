package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.audit.AuditTrailService
import com.kosmos.app.domain.memory.MemoryBackupManager
import javax.inject.Inject

/**
 * [ImportMemoryUseCase]
 * 백업 ZIP 으로 DB 를 바꾸고, 실패만 활동 기록에 남깁니다.
 *
 * [WHY] **성공하면 기록하지 않는다**(0.41.4) — 복원은 DB 파일을 바꿔치기하고 앱을 다시 시작하는 전제라 열린 Room 연결을 닫지 않는다.
 * 그 연결로 쓰면 SQLite 가 보장하지 않는 쓰기다(바뀐 파일의 WAL 과 엉키거나 사라진다) — 예전에는 여기서 "복원 성공"을 썼다.
 * 복원된 체인은 백업을 만든 기기의 체인 그대로이고(마지막 줄 = 그 백업의 "내보내기 시작"), 재시작 뒤 첫 기록이 그 뒤에 이어진다.
 */
class ImportMemoryUseCase @Inject constructor(
    private val memoryBackupManager: MemoryBackupManager,
    private val auditTrailService: AuditTrailService
) {
    suspend operator fun invoke(zipUriString: String): AppResult<Unit> {
        val result = memoryBackupManager.restoreFromZip(zipUriString)
        if (result is AppResult.Failure) auditTrailService.logBackupEvent("system_import", "import", "FAILED")
        return result
    }
}

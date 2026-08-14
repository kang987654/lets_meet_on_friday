package com.kosmos.app.feature.memory

import java.io.File

enum class MemoryFilterType {
    TASK,
    KNOWLEDGE
}

/**
 * [BackupState]
 * 내보내기/가져오기 진행 상태입니다.
 *
 * [WHY] 이전에는 결과를 콜백 람다로 돌려줬다. 람다가 Activity `context`를 캡처하고
 * `viewModelScope` 코루틴이 그것을 붙잡기 때문에, DB를 재작성하는 가져오기 도중 화면을
 * 회전하면 파괴된 Activity를 향해 `startActivity`가 실행됐다. 반대로 컴포지션을 벗어나면
 * 새 람다가 생겨 진행 중 코루틴은 옛 람다를 들고 있어 완료 통지가 유실되고, `UiState`에
 * 읽을 필드가 없어 복구도 못 했다. 결과를 상태로 들고 있으면 둘 다 사라진다.
 */
sealed interface BackupState {
    object Idle : BackupState

    object Exporting : BackupState

    /**
     * zip 생성이 끝났고 사용자가 저장 위치를 고르는 중이다.
     *
     * [WHY] zip 은 `cacheDir` 에 만들어져 사용자가 직접 접근할 수 없고 시스템이 언제든
     * 비울 수 있다. SAF 왕복 동안 파일을 붙들 곳이 필요하다.
     */
    data class ReadyToSave(val file: File, val suggestedName: String) : BackupState

    object Importing : BackupState

    /**
     * DB 가 이미 교체된 상태다. 프로세스를 재시작해야 한다.
     *
     * [WHY] 재시작을 ViewModel 이 수행하지 않는다 — Activity 를 다루는 일이고,
     * 사용자가 확인 버튼을 누른 시점에만 일어나야 한다.
     */
    object ImportSucceeded : BackupState

    data class Failed(val message: String) : BackupState
}

data class MemoryUiState(
    // [WHY] 기존 기본값 ALL은 두 탭(Memory/Tasks) 중 어느 것도 선택되지 않은 상태로 렌더링돼
    // 첫 진입 시 탭이 모두 비활성처럼 보였다. 기본 탭을 KNOWLEDGE로 고정한다.
    val selectedFilter: MemoryFilterType = MemoryFilterType.KNOWLEDGE,
    val backup: BackupState = BackupState.Idle,
    // [WHY] prd.md F8 정책 — 백업 파일에 개인정보가 포함됨을 UI에서 명시해야 한다.
    // v1은 암호화를 의도적으로 넣지 않았으므로 이 경고가 유일한 보호막이다.
    val showExportNotice: Boolean = false,
    val showImportWarning: Boolean = false,
    /**
     * 목록 조작(Task 완료 등) 실패 시 사용자에게 보여줄 문구입니다. 소비 후 null 로 되돌립니다.
     *
     * [WHY] 예전에는 Task 완료 토글의 저장 실패가 무시됐다 — 체크했는데 항목이 안 지워지면
     * 사용자는 탭이 씹혔다고 본다(같은 뷰모델의 백업 경로만 실패를 노출했다).
     */
    val actionError: String? = null
)

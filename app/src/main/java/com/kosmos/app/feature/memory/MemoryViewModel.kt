package com.kosmos.app.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.model.KnowledgeNote
import com.kosmos.app.domain.model.TaskItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.net.Uri
import com.kosmos.app.domain.usecase.ExportMemoryUseCase
import com.kosmos.app.domain.usecase.ImportMemoryUseCase
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.platform.file.BackupFileWriter
import com.kosmos.app.ui.paging.DefaultPagingSource
import com.kosmos.app.ui.paging.unwrapForPaging

/**
 * [MemoryViewModel]
 * 지식 노트(Knowledge Notes) 및 할 일(Task) 목록의 페이징 데이터 표시와 데이터 백업/복원(Export/Import)을 제어하는 뷰모델입니다.
 *
 * ### Architecture Context
 * - **Layer**: Feature / Presentation (Memory)
 * - **Dependencies**: [KnowledgeRepository], [TaskRepository], [ExportMemoryUseCase], [ImportMemoryUseCase]
 *
 * ### Key Flow
 * 1. Paging3 및 Flow를 통해 지식 노트 및 Task 데이터를 UI에 관찰 가능한 스트림으로 서빙합니다.
 * 2. 탭 필터 변경 및 Task 완료 처리 UI 액션을 도메인/리포지토리로 전달합니다.
 * 3. [ExportMemoryUseCase] 및 [ImportMemoryUseCase]를 호출하고, 결과를 콜백이 아니라
 *    [BackupState]로 노출합니다([BackupState] KDoc의 `[WHY]` 참조).
 */
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val taskRepository: TaskRepository,
    private val exportMemoryUseCase: ExportMemoryUseCase,
    private val importMemoryUseCase: ImportMemoryUseCase,
    private val backupFileWriter: BackupFileWriter
) : ViewModel() {


    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    // Paging Data Flows
    // [WHY] Pager 생성이 여기 있는 이유 — 리포지토리가 Flow<PagingData>를 반환하면
    // Pure Kotlin JVM 모듈인 :domain 이 androidx 타입을 공개 계약에 노출한다. 페이징은
    // UI 관심사이므로 offset/limit 계약만 받아 여기서 조립한다.
    val knowledgePagingData: Flow<PagingData<KnowledgeNote>> =
        androidx.paging.Pager(androidx.paging.PagingConfig(pageSize = 20)) {
            DefaultPagingSource { offset, limit ->
                knowledgeRepository.getNotes(offset, limit).unwrapForPaging()
            }
        }.flow.cachedIn(viewModelScope)

    val taskPagingData: Flow<PagingData<TaskItem>> =
        androidx.paging.Pager(androidx.paging.PagingConfig(pageSize = 20)) {
            DefaultPagingSource { offset, limit ->
                taskRepository.getPendingTasksData(offset, limit).unwrapForPaging()
            }
        }.flow.cachedIn(viewModelScope)

    fun onFilterSelected(filterType: MemoryFilterType) {
        _uiState.update { it.copy(selectedFilter = filterType) }
    }

    /**
     * 할 일을 직접 추가합니다.
     * [WHY] "Add new task" 는 원래 빈 스텁(`clickable { }`)이었다 — 버튼만 있고 기능이 없어
     * 탭이 무음으로 씹혔다 (2026-08-15 사용자 피드백). 저장 실패는 completeTask 와 같은
     * 경로(actionError 토스트)로 알린다.
     */
    fun addTask(title: String, onSaved: () -> Unit = {}) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val result = taskRepository.save(
                TaskItem(
                    id = java.util.UUID.randomUUID().toString(),
                    title = trimmed,
                    isCompleted = false,
                    createdAt = System.currentTimeMillis()
                )
            )
            when (result) {
                // [WHY] 저장이 끝난 뒤에 알린다 — 호출부가 여기서 페이징 refresh 를 걸므로,
                // 저장 전에 refresh 가 돌면 방금 추가한 항목이 목록에 안 보인다.
                is AppResult.Success -> onSaved()
                is AppResult.Failure ->
                    _uiState.update { it.copy(actionError = ErrorMessages.userMessage(result.error)) }
            }
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            val result = taskRepository.updateCompletion(taskId, true)
            if (result is AppResult.Failure) {
                _uiState.update { it.copy(actionError = ErrorMessages.userMessage(result.error)) }
            }
        }
    }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    // --- 백업 (내보내기 / 가져오기) ---

    /** 내보내기 전 개인정보 포함 경고를 띄운다. */
    fun requestExport() {
        if (isBackupBusy()) return
        _uiState.update { it.copy(showExportNotice = true) }
    }

    /** 가져오기 전 기존 데이터 덮어쓰기 경고를 띄운다. */
    fun requestImport() {
        if (isBackupBusy()) return
        _uiState.update { it.copy(showImportWarning = true) }
    }

    fun confirmExport() {
        if (isBackupBusy()) return
        _uiState.update { it.copy(showExportNotice = false, backup = BackupState.Exporting) }
        viewModelScope.launch {
            val next = when (val result = exportMemoryUseCase()) {
                is AppResult.Success -> BackupState.ReadyToSave(result.data, result.data.name)
                is AppResult.Failure -> BackupState.Failed(ErrorMessages.userMessage(result.error))
            }
            _uiState.update { it.copy(backup = next) }
        }
    }

    /**
     * 사용자가 고른 위치로 zip 을 복사한다.
     *
     * [WHY] 복사를 컴포저블이 아니라 여기서 한다 — SAF 결과가 돌아온 직후 화면이 재구성되거나
     * 사라져도 복사가 끝까지 진행되어야 한다.
     */
    fun saveExportTo(destination: Uri) {
        val ready = _uiState.value.backup as? BackupState.ReadyToSave ?: return
        viewModelScope.launch {
            val next = when (val result = backupFileWriter.copyTo(ready.file, destination)) {
                is AppResult.Success -> BackupState.Idle
                is AppResult.Failure -> BackupState.Failed(ErrorMessages.userMessage(result.error))
            }
            ready.file.delete()
            _uiState.update { it.copy(backup = next) }
        }
    }

    /** 사용자가 저장 위치 선택을 취소했다. */
    fun onExportCancelled() {
        discardExportFile()
        _uiState.update { it.copy(backup = BackupState.Idle) }
    }

    fun importData(uri: Uri) {
        // [WHY] 가져오기는 DB 파일을 통째로 교체하므로 재진입이 곧 데이터 파괴다.
        // 진행 중 재호출은 조용히 무시한다.
        if (isBackupBusy()) return
        _uiState.update { it.copy(showImportWarning = false, backup = BackupState.Importing) }
        viewModelScope.launch {
            val next = when (val result = importMemoryUseCase(uri.toString())) {
                is AppResult.Success -> BackupState.ImportSucceeded
                is AppResult.Failure -> BackupState.Failed(ErrorMessages.userMessage(result.error))
            }
            _uiState.update { it.copy(backup = next) }
        }
    }

    fun dismissBackupState() {
        // [WHY] ImportSucceeded 는 닫을 수 없다 — DB 가 이미 교체된 상태로 앱을 계속 쓰면
        // 화면에 남은 페이징 캐시와 실제 데이터가 어긋난다. 재시작만이 유효한 출구다.
        if (_uiState.value.backup is BackupState.ImportSucceeded) return
        _uiState.update {
            it.copy(backup = BackupState.Idle, showExportNotice = false, showImportWarning = false)
        }
    }

    private fun isBackupBusy(): Boolean = when (_uiState.value.backup) {
        is BackupState.Exporting, is BackupState.Importing,
        is BackupState.ReadyToSave, is BackupState.ImportSucceeded -> true
        else -> false
    }

    /** [WHY] cacheDir 사본을 남겨두면 3.6GB 모델과 함께 캐시 용량을 잡아먹는다. */
    private fun discardExportFile() {
        (_uiState.value.backup as? BackupState.ReadyToSave)?.file?.delete()
    }
}

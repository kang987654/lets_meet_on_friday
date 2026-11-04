package com.kosmos.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kosmos.app.R
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.tool.ModelDownloadStatus
import com.kosmos.app.runtime.embedding.EmbeddingModelManager
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.component.KosmosListGroup
import com.kosmos.app.ui.component.SectionHeader
import com.kosmos.app.ui.theme.KosmosTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 설정 > 모델 관리의 뜻 검색 모델 항목 상태 (0.41.2). */
sealed interface EmbeddingModelUi {
    data object NotInstalled : EmbeddingModelUi
    data object Waiting : EmbeddingModelUi
    data class Downloading(val percent: Int) : EmbeddingModelUi
    data object Failed : EmbeddingModelUi
    data object Installed : EmbeddingModelUi
}

/**
 * [EmbeddingModelViewModel]
 * 뜻 검색 모델(EmbeddingGemma 2, 선택 설치)의 받기·지우기 — 설정 > 모델 관리 (0.41.2 M3).
 *
 * ### Architecture Context
 * - **Layer**: Feature (Settings) — [EmbeddingModelManager](다운로드·파일·벡터 지우기).
 *
 * ### Key Flow
 * 1. 설치 여부 + 다운로드 상태 → [EmbeddingModelUi]. 다운로드가 끝나면 설치 여부를 다시 읽고 종료 상태를 소비한다.
 */
@HiltViewModel
class EmbeddingModelViewModel @Inject constructor(
    private val manager: EmbeddingModelManager
) : ViewModel() {

    val ui: StateFlow<EmbeddingModelUi> = combine(manager.installed, manager.downloadStatus) { installed, status ->
        when (status) {
            is ModelDownloadStatus.Queued -> EmbeddingModelUi.Waiting
            is ModelDownloadStatus.Running -> EmbeddingModelUi.Downloading(status.progress.percent.coerceAtLeast(0))
            is ModelDownloadStatus.Failed -> EmbeddingModelUi.Failed
            ModelDownloadStatus.Succeeded -> {
                manager.refresh()
                manager.acknowledgeDownload()
                EmbeddingModelUi.Installed
            }
            ModelDownloadStatus.Idle, ModelDownloadStatus.Cancelled ->
                if (installed) EmbeddingModelUi.Installed else EmbeddingModelUi.NotInstalled
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmbeddingModelUi.NotInstalled)

    fun refresh() = manager.refresh()

    fun download() = manager.download()

    fun cancel() = manager.cancelDownload()

    fun retry() {
        manager.acknowledgeDownload()
        manager.download()
    }

    fun delete() {
        viewModelScope.launch { manager.delete() }
    }
}

/** 화면 진입점 — Hilt 로 상태를 받아 [EmbeddingModelSection] 을 그린다. */
@Composable
fun EmbeddingModelSectionRoute(viewModel: EmbeddingModelViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // [WHY] adb 로 넣거나 다른 화면에서 지운 경우도 화면에 들어올 때 맞춘다.
    LaunchedEffect(Unit) { viewModel.refresh() }
    EmbeddingModelSection(ui, viewModel::download, viewModel::cancel, viewModel::retry, viewModel::delete)
}

/**
 * 뜻 검색 모델 항목 — 이름·설명, 상태에 따라 받기 / 진행·취소 / 다시 시도 / 설치됨·지우기(확인 창).
 */
@Composable
fun EmbeddingModelSection(
    ui: EmbeddingModelUi,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = KosmosTheme.colors
    SectionHeader(stringResource(R.string.model_mgmt_embedding_section), Modifier.padding(top = 16.dp))
    KosmosListGroup {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("EmbeddingGemma 2", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                stringResource(R.string.model_mgmt_embedding_desc, Constants.EMBEDDING_MODEL_SIZE_MB),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            val status = when (ui) {
                EmbeddingModelUi.Installed -> stringResource(R.string.model_mgmt_embedding_installed)
                EmbeddingModelUi.Waiting -> stringResource(R.string.model_mgmt_embedding_waiting)
                is EmbeddingModelUi.Downloading -> stringResource(R.string.model_mgmt_embedding_downloading, ui.percent)
                EmbeddingModelUi.Failed -> stringResource(R.string.model_mgmt_embedding_failed)
                EmbeddingModelUi.NotInstalled -> null
            }
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui == EmbeddingModelUi.Failed) colors.dangerText else colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                when (ui) {
                    EmbeddingModelUi.NotInstalled ->
                        KosmosButton(stringResource(R.string.common_download), onDownload, Modifier.fillMaxWidth(), accent = true)
                    EmbeddingModelUi.Waiting, is EmbeddingModelUi.Downloading ->
                        KosmosButton(stringResource(R.string.common_cancel), onCancel, Modifier.fillMaxWidth())
                    EmbeddingModelUi.Failed ->
                        KosmosButton(stringResource(R.string.common_retry), onRetry, Modifier.fillMaxWidth(), accent = true)
                    EmbeddingModelUi.Installed ->
                        KosmosButton(stringResource(R.string.model_mgmt_embedding_delete), { confirmDelete = true }, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.model_mgmt_embedding_delete_title)) },
            text = { Text(stringResource(R.string.model_mgmt_embedding_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.model_mgmt_embedding_delete), color = colors.dangerText)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textMuted)
                }
            }
        )
    }
}

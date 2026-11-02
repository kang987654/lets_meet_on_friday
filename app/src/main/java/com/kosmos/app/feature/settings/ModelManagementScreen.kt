package com.kosmos.app.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.R
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.core.common.Constants
import com.kosmos.app.ui.component.KosmosListGroup
import com.kosmos.app.ui.component.KosmosTopBar
import com.kosmos.app.ui.component.SectionHeader
import com.kosmos.app.ui.component.kosmosTextFieldColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: ModelManagementViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    var customUrl by remember { mutableStateOf("") }

    val defaultModelUrl = Constants.DEFAULT_MODEL_DOWNLOAD_URL
    // 실패 후 "재시도"가 같은 대상을 다시 요청할 수 있도록 마지막 요청 URL을 기억한다.
    var lastRequestedUrl by remember { mutableStateOf(defaultModelUrl) }

    // [WHY] 알림 권한이 거부되면 진행률 알림만 사라지고 다운로드는 그대로 동작한다.
    // 따라서 권한 결과를 기다리지 않고, 거부되어도 다운로드를 막지 않는다.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 결과와 무관하게 진행한다 */ }

    val startDownload: (String) -> Unit = { url ->
        lastRequestedUrl = url
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.downloadModel(url)
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            KosmosTopBar(title = stringResource(R.string.model_mgmt_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // [WHY] 진행 카드 + 두 섹션 + URL 입력이 큰 글자·키보드에서 잘렸다(0.38.3 M6 점검 P1).
                .imePadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // [WHY] 수 GB 다운로드를 모달 다이얼로그로 가두면 사용자가 앱을 쓸 수 없다.
            // 화면 내 진행 카드로 내려 취소·잔여 저장 공간을 함께 노출한다 (B-3 1단계).
            when (val state = downloadState) {
                is DownloadState.Downloading -> DownloadProgressCard(
                    progress = state.progress,
                    downloadedBytes = state.downloadedBytes,
                    totalBytes = state.totalBytes,
                    isRetrying = state.isRetrying,
                    onCancel = { viewModel.cancelDownload() },
                    onDiscardPartial = { viewModel.discardPartial() }
                )
                // [WHY] Wi-Fi 전용 제약(ADR-006) 때문에 Wi-Fi 가 없으면 작업이 대기 상태로 멈춘다.
                // 이 카드가 없으면 사용자에게는 "다운로드 버튼이 먹지 않는" 것으로 보인다.
                is DownloadState.Queued -> DownloadQueuedCard(
                    onCancel = { viewModel.cancelDownload() }
                )
                else -> Unit
            }

            // [WHY] 섹션 제목·묶음·버튼을 공통 부품으로(0.38.3 M3) — URL 버튼만 손으로 만든 상자라 다른 버튼과 모양·비활성 표현이 달랐다.
            SectionHeader(stringResource(R.string.model_mgmt_default_section))
            KosmosListGroup {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Gemma 4 E4B", style = MaterialTheme.typography.titleMedium, color = KosmosTheme.colors.textPrimary)
                    Text(
                        stringResource(R.string.model_mgmt_default_spec),
                        style = MaterialTheme.typography.bodySmall,
                        color = KosmosTheme.colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    KosmosButton(stringResource(R.string.common_download), { startDownload(defaultModelUrl) }, Modifier.fillMaxWidth(), accent = true)
                }
            }

            SectionHeader(stringResource(R.string.model_mgmt_custom_url_section), Modifier.padding(top = 16.dp))
            KosmosListGroup {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text(stringResource(R.string.model_mgmt_custom_url_label), color = KosmosTheme.colors.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = kosmosTextFieldColors(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    KosmosButton(stringResource(R.string.model_mgmt_download_custom_url), { startDownload(customUrl) }, Modifier.fillMaxWidth(), enabled = customUrl.isNotBlank())
                }
            }
        }
    }

    when (val state = downloadState) {
        is DownloadState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text(stringResource(R.string.model_mgmt_done_title)) },
                text = { Text(stringResource(R.string.model_mgmt_done_body)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text(stringResource(R.string.common_confirm), color = KosmosTheme.colors.brandText)
                    }
                },
                containerColor = KosmosTheme.colors.surface,
                titleContentColor = KosmosTheme.colors.textPrimary,
                textContentColor = KosmosTheme.colors.textSecondary
            )
        }
        is DownloadState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text(stringResource(R.string.model_mgmt_failed_title), color = KosmosTheme.colors.dangerText) },
                text = {
                    // 이어받을 부분 파일이 있으면 재시도가 저렴하다는 사실을 함께 알린다.
                    val resumeNote = if (state.resumableBytes > 0) {
                        stringResource(R.string.model_mgmt_resume_note, state.resumableBytes.toGigabytes())
                    } else {
                        ""
                    }
                    Text(state.message + resumeNote)
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetState()
                        startDownload(lastRequestedUrl)
                    }) {
                        Text(stringResource(R.string.common_retry), color = KosmosTheme.colors.brandText)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text(stringResource(R.string.common_close), color = KosmosTheme.colors.textMuted)
                    }
                },
                containerColor = KosmosTheme.colors.surface,
                titleContentColor = KosmosTheme.colors.textPrimary,
                textContentColor = KosmosTheme.colors.textSecondary
            )
        }
        else -> {}
    }
}

private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0

private fun Long.toGigabytes(): Double = this / BYTES_PER_GB

/** 카드의 공통 껍데기 — 진행/대기 카드가 같은 시각 언어를 쓰도록 묶습니다. */
@Composable
private fun DownloadCardShell(content: @Composable ColumnScope.() -> Unit) {
    KosmosListGroup(Modifier.padding(top = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * 다운로드 진행 카드 — 진행률, 받은 용량, 잔여 저장 공간, 취소/폐기 버튼을 표시합니다.
 *
 * [WHY] 다운로드가 WorkManager 로 이관되어 앱을 닫아도 계속되므로, "앱을 종료하면 중단됩니다"
 * 라는 기존 안내는 사실과 반대가 되었다. (ADR-006)
 */
// [WHY] lint(UsableSpace)는 allocateBytes 대체를 권하지만 여기는 **표시 전용**이라
// usableSpace 가 의미상 정답이다 — 사용자에게 보여줄 값은 "지금 남은 공간"이지
// "시스템이 타 앱 캐시를 지워서 만들 수 있는 공간"이 아니다.
@SuppressLint("UsableSpace")
@Composable
private fun DownloadProgressCard(
    progress: Int,
    downloadedBytes: Long,
    totalBytes: Long,
    isRetrying: Boolean,
    onCancel: () -> Unit,
    onDiscardPartial: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // [WHY] usableSpace 는 디스크 stat 이므로 컴포지션마다 읽으면 안 된다. 진행률 10% 단위로만
    // 다시 조회해 표시 정확도와 비용을 맞바꾼다.
    val freeSpaceGb = remember(progress / 10) {
        context.filesDir.usableSpace.toGigabytes()
    }
    val indeterminate = progress < 0

    DownloadCardShell {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isRetrying) stringResource(R.string.model_mgmt_retrying)
                else stringResource(R.string.model_mgmt_downloading),
                style = MaterialTheme.typography.titleSmall,
                color = KosmosTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            if (!indeterminate) {
                Text("$progress%", color = KosmosTheme.colors.brandText, fontWeight = FontWeight.Bold)
            }
        }
        if (indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = KosmosTheme.colors.brand,
                trackColor = KosmosTheme.colors.surface
            )
        } else {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = KosmosTheme.colors.brand,
                trackColor = KosmosTheme.colors.surface
            )
        }
        val sizeText = if (totalBytes > 0) {
            "%.1fGB / %.1fGB".format(downloadedBytes.toGigabytes(), totalBytes.toGigabytes())
        } else {
            stringResource(R.string.model_mgmt_downloaded_gb, downloadedBytes.toGigabytes())
        }
        Text(
            stringResource(R.string.model_mgmt_free_space, sizeText, freeSpaceGb),
            style = MaterialTheme.typography.bodySmall,
            color = KosmosTheme.colors.textMuted
        )
        Text(
            stringResource(R.string.model_mgmt_bg_note),
            style = MaterialTheme.typography.bodySmall,
            color = KosmosTheme.colors.textMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.model_mgmt_pause), color = KosmosTheme.colors.textSecondary)
            }
            // [WHY] 취소는 이어받기용 부분 파일을 남긴다. 저장 공간을 즉시 회수하고 싶은
            // 사용자를 위해 폐기를 별도 동작으로 노출한다.
            TextButton(onClick = onDiscardPartial) {
                Text(stringResource(R.string.model_mgmt_cancel_delete), color = KosmosTheme.colors.dangerText)
            }
        }
    }
}

/** Wi-Fi 연결이나 재시도 백오프를 기다리는 동안 표시하는 대기 카드입니다. */
@Composable
private fun DownloadQueuedCard(onCancel: () -> Unit) {
    DownloadCardShell {
        Text(
            stringResource(R.string.model_mgmt_waiting_wifi),
            style = MaterialTheme.typography.titleSmall,
            color = KosmosTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = KosmosTheme.colors.brand,
            trackColor = KosmosTheme.colors.surface
        )
        Text(
            stringResource(R.string.model_mgmt_waiting_wifi_body),
            style = MaterialTheme.typography.bodySmall,
            color = KosmosTheme.colors.textMuted
        )
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.model_mgmt_cancel_queue), color = KosmosTheme.colors.dangerText)
        }
    }
}

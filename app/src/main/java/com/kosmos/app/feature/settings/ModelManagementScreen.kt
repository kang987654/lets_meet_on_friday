package com.kosmos.app.feature.settings

import com.kosmos.app.ui.theme.KosmosTheme
import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.ui.component.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: ModelManagementViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    var customUrl by remember { mutableStateOf("") }

    val defaultModelUrl = com.kosmos.app.core.common.Constants.DEFAULT_MODEL_DOWNLOAD_URL
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
            TopAppBar(
                title = { Text("모델 관리", color = KosmosTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("뒤로", color = KosmosTheme.colors.accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
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

            Text(
                text = "기본 모델",
                style = MaterialTheme.typography.titleMedium,
                color = KosmosTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(
                        backgroundColor = KosmosTheme.colors.glass,
                        borderColor = KosmosTheme.colors.borderHigh,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Gemma 4 E4B", style = MaterialTheme.typography.titleMedium, color = KosmosTheme.colors.accentAlt, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "LiteRT-LM 최적화 · 크기 약 3.6GB",
                        style = MaterialTheme.typography.bodySmall,
                        color = KosmosTheme.colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(
                                backgroundColor = KosmosTheme.colors.accent.copy(alpha = 0.15f),
                                borderColor = KosmosTheme.colors.accent.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .clickable { startDownload(defaultModelUrl) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("내려받기", color = KosmosTheme.colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "직접 URL 로 내려받기",
                style = MaterialTheme.typography.titleMedium,
                color = KosmosTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(
                        backgroundColor = KosmosTheme.colors.glass,
                        borderColor = KosmosTheme.colors.borderHigh,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("URL (.litertlm 파일)", color = KosmosTheme.colors.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KosmosTheme.colors.accent,
                            unfocusedBorderColor = KosmosTheme.colors.border,
                            focusedTextColor = KosmosTheme.colors.textPrimary,
                            unfocusedTextColor = KosmosTheme.colors.textPrimary
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    
                    val canDownload = customUrl.isNotBlank()
                    val btnBg = if (canDownload) KosmosTheme.colors.accent else KosmosTheme.colors.glass
                    val btnText = if (canDownload) KosmosTheme.colors.onAccent else KosmosTheme.colors.textMuted
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(
                                backgroundColor = btnBg,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = canDownload) {
                                if (canDownload) startDownload(customUrl)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("이 URL 로 내려받기", color = btnText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    when (val state = downloadState) {
        is DownloadState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("내려받기 완료") },
                text = { Text("모델을 받아서 적용했어요. 이제 대화를 시작할 수 있어요.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("확인", color = KosmosTheme.colors.accent)
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
                title = { Text("내려받기 실패", color = KosmosTheme.colors.danger) },
                text = {
                    // 이어받을 부분 파일이 있으면 재시도가 저렴하다는 사실을 함께 알린다.
                    val resumeNote = if (state.resumableBytes > 0) {
                        "\n\n%.1fGB까지 받아둔 상태라 재시도하면 이어서 받습니다."
                            .format(state.resumableBytes.toGigabytes())
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
                        Text("재시도", color = KosmosTheme.colors.accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("닫기", color = KosmosTheme.colors.textMuted)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(
                backgroundColor = KosmosTheme.colors.glassMid,
                borderColor = KosmosTheme.colors.accent.copy(alpha = 0.4f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
                if (isRetrying) "모델 다운로드 재시도 중" else "모델 다운로드 중",
                style = MaterialTheme.typography.titleSmall,
                color = KosmosTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            if (!indeterminate) {
                Text("$progress%", color = KosmosTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        }
        if (indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = KosmosTheme.colors.accent,
                trackColor = KosmosTheme.colors.glass
            )
        } else {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = KosmosTheme.colors.accent,
                trackColor = KosmosTheme.colors.glass
            )
        }
        val sizeText = if (totalBytes > 0) {
            "%.1fGB / %.1fGB".format(downloadedBytes.toGigabytes(), totalBytes.toGigabytes())
        } else {
            "%.1fGB 받았어요".format(downloadedBytes.toGigabytes())
        }
        Text(
            "$sizeText · 남은 저장 공간 %.1fGB".format(freeSpaceGb),
            style = MaterialTheme.typography.bodySmall,
            color = KosmosTheme.colors.textMuted
        )
        Text(
            "앱을 닫아도 백그라운드에서 계속 진행됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = KosmosTheme.colors.textMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onCancel) {
                Text("일시 중지", color = KosmosTheme.colors.textSecondary)
            }
            // [WHY] 취소는 이어받기용 부분 파일을 남긴다. 저장 공간을 즉시 회수하고 싶은
            // 사용자를 위해 폐기를 별도 동작으로 노출한다.
            TextButton(onClick = onDiscardPartial) {
                Text("취소 후 삭제", color = KosmosTheme.colors.danger)
            }
        }
    }
}

/** Wi-Fi 연결이나 재시도 백오프를 기다리는 동안 표시하는 대기 카드입니다. */
@Composable
private fun DownloadQueuedCard(onCancel: () -> Unit) {
    DownloadCardShell {
        Text(
            "Wi-Fi 연결을 기다리는 중",
            style = MaterialTheme.typography.titleSmall,
            color = KosmosTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = KosmosTheme.colors.accent,
            trackColor = KosmosTheme.colors.glass
        )
        Text(
            "데이터 요금을 아끼기 위해 Wi-Fi에 연결되면 자동으로 시작합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = KosmosTheme.colors.textMuted
        )
        TextButton(onClick = onCancel) {
            Text("대기 취소", color = KosmosTheme.colors.danger)
        }
    }
}

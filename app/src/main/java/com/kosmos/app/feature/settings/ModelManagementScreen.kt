package com.kosmos.app.feature.settings

import com.kosmos.app.ui.theme.KosmosTheme
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
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

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Model Management", color = KosmosTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = KosmosTheme.colors.accent)
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
            (downloadState as? DownloadState.Downloading)?.let { downloading ->
                DownloadProgressCard(
                    progress = downloading.progress,
                    onCancel = { viewModel.cancelDownload() }
                )
            }

            Text(
                text = "Predefined Models",
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
                        "Optimized for LiteRT-LM. Size: ~3.6GB",
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
                            .clickable { viewModel.downloadModel(defaultModelUrl) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Download", color = KosmosTheme.colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "Custom Download URL",
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
                        label = { Text("URL (.litertlm file)", color = KosmosTheme.colors.textMuted) },
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
                                if (canDownload) viewModel.downloadModel(customUrl) 
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Download Custom Model", color = btnText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    when (val state = downloadState) {
        is DownloadState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("Download Complete") },
                text = { Text("The model has been successfully downloaded and applied.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("OK", color = KosmosTheme.colors.accent)
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
                title = { Text("Download Failed", color = KosmosTheme.colors.danger) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("OK", color = KosmosTheme.colors.danger)
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

/** 다운로드 진행 카드 — 진행률, 잔여 저장 공간, 취소 버튼을 화면 내에 표시합니다. */
@Composable
private fun DownloadProgressCard(progress: Int, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val freeSpaceGb = remember {
        context.filesDir.usableSpace / (1024.0 * 1024.0 * 1024.0)
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "모델 다운로드 중",
                    style = MaterialTheme.typography.titleSmall,
                    color = KosmosTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text("$progress%", color = KosmosTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = KosmosTheme.colors.accent,
                trackColor = KosmosTheme.colors.glass
            )
            Text(
                "남은 저장 공간 %.1fGB · 앱을 종료하면 다운로드가 중단됩니다".format(freeSpaceGb),
                style = MaterialTheme.typography.bodySmall,
                color = KosmosTheme.colors.textMuted
            )
            TextButton(onClick = onCancel) {
                Text("다운로드 취소", color = KosmosTheme.colors.danger)
            }
        }
    }
}

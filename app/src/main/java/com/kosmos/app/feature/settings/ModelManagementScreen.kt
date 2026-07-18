package com.kosmos.app.feature.settings

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
import com.kosmos.app.ui.component.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagementScreen(
    viewModel: ModelManagementViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val downloadState by viewModel.downloadState.collectAsState()
    var customUrl by remember { mutableStateOf("") }
    
    val defaultModelUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Model Management", color = com.kosmos.app.ui.theme.TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = com.kosmos.app.ui.theme.Cyan)
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
            Text(
                text = "Predefined Models",
                style = MaterialTheme.typography.titleMedium,
                color = com.kosmos.app.ui.theme.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(
                        backgroundColor = com.kosmos.app.ui.theme.GlassColor,
                        borderColor = com.kosmos.app.ui.theme.BorderHighColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Gemma 4 E4B", style = MaterialTheme.typography.titleMedium, color = com.kosmos.app.ui.theme.Violet, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Optimized for LiteRT-LM. Size: ~3.6GB",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.kosmos.app.ui.theme.TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(
                                backgroundColor = com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.15f),
                                borderColor = com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.downloadModel(defaultModelUrl) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Download", color = com.kosmos.app.ui.theme.Cyan, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "Custom Download URL",
                style = MaterialTheme.typography.titleMedium,
                color = com.kosmos.app.ui.theme.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(
                        backgroundColor = com.kosmos.app.ui.theme.GlassColor,
                        borderColor = com.kosmos.app.ui.theme.BorderHighColor,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("URL (.litertlm file)", color = com.kosmos.app.ui.theme.TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.kosmos.app.ui.theme.Cyan,
                            unfocusedBorderColor = com.kosmos.app.ui.theme.BorderColor,
                            focusedTextColor = com.kosmos.app.ui.theme.TextPrimary,
                            unfocusedTextColor = com.kosmos.app.ui.theme.TextPrimary
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    
                    val canDownload = customUrl.isNotBlank()
                    val btnBg = if (canDownload) com.kosmos.app.ui.theme.Cyan else com.kosmos.app.ui.theme.GlassColor
                    val btnText = if (canDownload) com.kosmos.app.ui.theme.BgColor else com.kosmos.app.ui.theme.TextMuted
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

    // Foreground Progress Dialog (Uncancellable)
    when (val state = downloadState) {
        is DownloadState.Downloading -> {
            Dialog(
                onDismissRequest = { /* Cannot dismiss */ },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(
                            backgroundColor = com.kosmos.app.ui.theme.GlassMidColor,
                            borderColor = com.kosmos.app.ui.theme.BorderColor,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Downloading Model...", style = MaterialTheme.typography.titleMedium, color = com.kosmos.app.ui.theme.TextPrimary, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = com.kosmos.app.ui.theme.Cyan,
                            trackColor = com.kosmos.app.ui.theme.GlassColor
                        )
                        Text("${state.progress}%", color = com.kosmos.app.ui.theme.TextSecondary)
                    }
                }
            }
        }
        is DownloadState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("Download Complete") },
                text = { Text("The model has been successfully downloaded and applied.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("OK", color = com.kosmos.app.ui.theme.Cyan)
                    }
                },
                containerColor = com.kosmos.app.ui.theme.SurfaceColor,
                titleContentColor = com.kosmos.app.ui.theme.TextPrimary,
                textContentColor = com.kosmos.app.ui.theme.TextSecondary
            )
        }
        is DownloadState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("Download Failed", color = com.kosmos.app.ui.theme.Danger) },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("OK", color = com.kosmos.app.ui.theme.Danger)
                    }
                },
                containerColor = com.kosmos.app.ui.theme.SurfaceColor,
                titleContentColor = com.kosmos.app.ui.theme.TextPrimary,
                textContentColor = com.kosmos.app.ui.theme.TextSecondary
            )
        }
        else -> {}
    }
}

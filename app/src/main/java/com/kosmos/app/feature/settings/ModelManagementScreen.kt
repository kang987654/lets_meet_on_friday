package com.kosmos.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel

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
        topBar = {
            TopAppBar(
                title = { Text("Model Management") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Predefined Models",
                style = MaterialTheme.typography.titleMedium
            )
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Gemma 4 E4B", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Optimized for LiteRT-LM. Size: ~3.6GB",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.downloadModel(defaultModelUrl) }
                    ) {
                        Text("Download")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Custom Download URL",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text("URL (.litertlm file)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { 
                    if (customUrl.isNotBlank()) {
                        viewModel.downloadModel(customUrl)
                    }
                },
                enabled = customUrl.isNotBlank()
            ) {
                Text("Download Custom Model")
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
                Card(modifier = Modifier.padding(16.dp)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Downloading Model...", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${state.progress}%")
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
                        Text("OK")
                    }
                }
            )
        }
        is DownloadState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetState() },
                title = { Text("Download Failed") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.resetState() }) {
                        Text("OK")
                    }
                }
            )
        }
        else -> {}
    }
}

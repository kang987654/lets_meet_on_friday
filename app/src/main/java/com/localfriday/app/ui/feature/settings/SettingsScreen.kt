package com.localfriday.app.ui.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localfriday.app.domain.modelrunner.ModelLoadState

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToAudit: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Model Status Section
        SectionCard(title = "Local AI Model (Gemma)") {
            when (val state = uiState.modelLoadState) {
                is ModelLoadState.Loading -> {
                    CircularProgressIndicator()
                    Text("Checking model state...", modifier = Modifier.padding(top = 8.dp))
                }
                is ModelLoadState.Ready -> {
                    Text(
                        text = "Status: Ready",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Model ID: ${state.modelInfo.modelId}", style = MaterialTheme.typography.bodyMedium)
                    Text("Version: ${state.modelInfo.modelVersion}", style = MaterialTheme.typography.bodyMedium)
                    Text("Quantization: ${state.modelInfo.quantization}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Path: ${state.modelInfo.modelPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                is ModelLoadState.NotFound -> {
                    Text(
                        text = "Status: Not Found",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The model file could not be found at the required path. Please download the Gemma 2B IT model and place it in the application's external 'models' directory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Expected Path: ${state.expectedPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refreshModelState() }) {
                        Text("Refresh Model State")
                    }
                }
                is ModelLoadState.Error -> {
                    Text(
                        text = "Status: Error",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "An error occurred: ${state.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // 2. Response Style Section
        SectionCard(title = "Response Style") {
            val styles = listOf("DEFAULT", "CONCISE", "DETAILED")
            styles.forEach { style ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onResponseStyleChanged(style) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (uiState.responseStyle == style),
                        onClick = { viewModel.onResponseStyleChanged(style) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = style, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // 3. Security & Audit Section
        SectionCard(title = "Security & Logs") {
            Text(
                text = "View the history of model executions and API calls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onNavigateToAudit, modifier = Modifier.fillMaxWidth()) {
                Text("View Audit Logs")
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

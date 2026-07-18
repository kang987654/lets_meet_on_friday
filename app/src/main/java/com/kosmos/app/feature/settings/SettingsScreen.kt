package com.kosmos.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalContext
import com.kosmos.app.ui.component.glassEffect
import com.kosmos.app.domain.modelrunner.ModelLoadState

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToAudit: () -> Unit = {},
    onNavigateToModelManagement: () -> Unit = {}
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
            color = com.kosmos.app.ui.theme.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 1. Model Status Section
        SectionCard(title = "Local AI Model (Gemma)") {
            when (val state = uiState.modelLoadState) {
                is ModelLoadState.Loading -> {
                    CircularProgressIndicator(color = com.kosmos.app.ui.theme.Cyan)
                    Text("Checking model state...", color = com.kosmos.app.ui.theme.TextMuted, modifier = Modifier.padding(top = 8.dp))
                }
                is ModelLoadState.FileFound -> {
                    CircularProgressIndicator(color = com.kosmos.app.ui.theme.Cyan)
                    Text("Model file found, preparing engine...", color = com.kosmos.app.ui.theme.TextMuted, modifier = Modifier.padding(top = 8.dp))
                }
                is ModelLoadState.InitializingEngine -> {
                    CircularProgressIndicator(color = com.kosmos.app.ui.theme.Cyan)
                    Text("Initializing AI Engine...", color = com.kosmos.app.ui.theme.TextMuted, modifier = Modifier.padding(top = 8.dp))
                }
                is ModelLoadState.Ready -> {
                    Text(
                        text = "Status: Ready",
                        color = com.kosmos.app.ui.theme.Success,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Model ID: ${state.modelInfo.modelId}", color = com.kosmos.app.ui.theme.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("Version: ${state.modelInfo.modelVersion}", color = com.kosmos.app.ui.theme.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("Quantization: ${state.modelInfo.quantization}", color = com.kosmos.app.ui.theme.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Path: ${state.modelInfo.modelPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.kosmos.app.ui.theme.TextMuted,
                        modifier = Modifier.padding(top = 4.dp)
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
                            .clickable { onNavigateToModelManagement() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Manage Models", color = com.kosmos.app.ui.theme.Cyan, fontWeight = FontWeight.Bold)
                    }
                }
                is ModelLoadState.NotFound -> {
                    Text(
                        text = "Status: Not Found",
                        color = com.kosmos.app.ui.theme.Danger,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The model file could not be found at the required path.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.kosmos.app.ui.theme.Danger
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .glassEffect(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .clickable { viewModel.refreshModelState() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Refresh", color = com.kosmos.app.ui.theme.TextPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .glassEffect(
                                    backgroundColor = com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.2f),
                                    borderColor = com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.5f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                )
                                .clickable { onNavigateToModelManagement() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Download", color = com.kosmos.app.ui.theme.Cyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is ModelLoadState.Error -> {
                    Text(
                        text = "Status: Error",
                        color = com.kosmos.app.ui.theme.Danger,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "An error occurred: ${state.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = com.kosmos.app.ui.theme.Danger,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        // 2. Response Style Section
        SectionCard(title = "Response Style") {
            val styles = listOf("CONCISE", "DEFAULT", "DETAILED")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                styles.forEach { style ->
                    val isSelected = uiState.responseStyle == style
                    val bgColor = if (isSelected) com.kosmos.app.ui.theme.Cyan.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent
                    val textColor = if (isSelected) com.kosmos.app.ui.theme.Cyan else com.kosmos.app.ui.theme.TextMuted
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(bgColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            .clickable { viewModel.onResponseStyleChanged(style) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style.lowercase().replaceFirstChar { it.uppercase() }, 
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        
        // 3. Context Window Section
        SectionCard(title = "Context Window (Max Tokens)") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Token Limit", color = com.kosmos.app.ui.theme.TextPrimary)
                    Text("${uiState.maxTokens} Tokens", color = com.kosmos.app.ui.theme.Cyan, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.maxTokens.toFloat(),
                    onValueChange = { viewModel.onMaxTokensChanged(it.toInt()) },
                    valueRange = 1000f..8000f,
                    steps = 6, // 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000 -> 7 intervals, 6 steps
                    colors = SliderDefaults.colors(
                        thumbColor = com.kosmos.app.ui.theme.Cyan,
                        activeTrackColor = com.kosmos.app.ui.theme.Cyan,
                        inactiveTrackColor = com.kosmos.app.ui.theme.GlassColor
                    )
                )
                Text(
                    text = "A larger context window allows the AI to remember more recent conversation history but uses more device memory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.kosmos.app.ui.theme.TextMuted
                )
            }
        }

        // 4. Security & Audit Section
        SectionCard(title = "Security & Logs") {
            Text(
                text = "View the history of model executions and API calls.",
                style = MaterialTheme.typography.bodyMedium,
                color = com.kosmos.app.ui.theme.TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        borderColor = com.kosmos.app.ui.theme.BorderColor
                    )
                    .clickable { onNavigateToAudit() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("View Audit Logs", color = com.kosmos.app.ui.theme.TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp)) // padding for bottom nav
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(
                backgroundColor = com.kosmos.app.ui.theme.GlassColor,
                borderColor = com.kosmos.app.ui.theme.BorderHighColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = com.kosmos.app.ui.theme.TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

package com.kosmos.app.ui.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
// No commented import needed
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kosmos.app.ui.theme.Ink
import com.kosmos.app.ui.theme.MutedText
import com.kosmos.app.ui.theme.SemanticDanger
import com.kosmos.app.ui.theme.SkyBlue
import com.kosmos.app.ui.theme.SurfaceCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceOverlay(
    sessionId: String,
    onDismiss: () -> Unit,
    // Callback to let the ChatScreen know a message was sent, so it can do optimistic append
    // since VoiceViewModel's processVoiceInputUseCase doesn't automatically update ChatScreen UI
    onMessageSent: (String) -> Unit,
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        viewModel.startListening()
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = uiState) {
                is VoiceUiState.Idle, is VoiceUiState.Listening -> {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.voice_listening),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(SkyBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(40.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Dummy mic icon shape
                        Box(modifier = Modifier.size(24.dp).background(SkyBlue, shape = RoundedCornerShape(12.dp)))
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.stopListening() },
                        colors = ButtonDefaults.buttonColors(containerColor = Ink)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.done))
                    }
                }
                is VoiceUiState.Transcribing -> {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.voice_transcribing),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(color = SkyBlue)
                    Spacer(modifier = Modifier.height(32.dp))
                }
                is VoiceUiState.Success -> {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.voice_confirm_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background, shape = RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = state.transcript,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TextButton(
                            onClick = { 
                                viewModel.reset()
                                onDismiss() 
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.cancel), color = MutedText)
                        }
                        Button(
                            onClick = {
                                viewModel.sendTranscript(sessionId, state.transcript)
                                onMessageSent(state.transcript)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.send))
                        }
                    }
                }
                is VoiceUiState.Error -> {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.voice_error_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = SemanticDanger
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.reset()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Ink)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.kosmos.app.R.string.close))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

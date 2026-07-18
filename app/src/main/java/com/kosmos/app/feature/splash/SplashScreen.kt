package com.kosmos.app.feature.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.ui.component.AuroraBackground
import com.kosmos.app.ui.component.OrbPulse

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onInitializationComplete: () -> Unit
) {
    val loadState by viewModel.loadState.collectAsState()

    LaunchedEffect(loadState) {
        if (loadState is ModelLoadState.Ready) {
            onInitializationComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AuroraBackground()
        
        if (loadState is ModelLoadState.Error || loadState is ModelLoadState.NotFound) {
            val errorMessage = when (val state = loadState) {
                is ModelLoadState.Error -> state.error.toString()
                is ModelLoadState.NotFound -> "Model not found at: ${state.expectedPath}"
                else -> ""
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "Failed to load models:\n$errorMessage",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.retry() }) {
                    Text(text = "Retry")
                }
            }
        } else {
            OrbPulse()
        }
    }
}

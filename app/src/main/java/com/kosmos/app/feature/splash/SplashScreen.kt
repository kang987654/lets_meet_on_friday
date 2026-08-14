package com.kosmos.app.feature.splash

import com.kosmos.app.ui.theme.KosmosTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.ui.component.AuroraBackground
import com.kosmos.app.ui.component.OrbPulse

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onInitializationComplete: () -> Unit,
    // [WHY] AC8 은 "모델 파일 없음 → 설정 화면 이동" 을 요구하는데, 안내 문구는 "설정 > 모델
    // 관리에서 내려받아 주세요" 라고 말하면서 이동 수단은 Retry 버튼뿐이었다 — 하단 탭 우회로가
    // 있어 갇히지는 않지만 안내와 동선이 어긋났다(MVP 감사 ac5-8).
    onNavigateToModelManagement: () -> Unit = {}
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()

    LaunchedEffect(loadState) {
        if (loadState is ModelLoadState.Ready) {
            onInitializationComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AuroraBackground()
        
        if (loadState is ModelLoadState.Error || loadState is ModelLoadState.NotFound) {
            val errorMessage = when (val state = loadState) {
                is ModelLoadState.Error -> com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error)
                is ModelLoadState.NotFound ->
                    "AI 모델 파일이 없어요. 설정 > 모델 관리에서 내려받아 주세요.\n(경로: ${state.expectedPath})"
                else -> ""
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = KosmosTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (loadState is ModelLoadState.NotFound) {
                    Button(onClick = onNavigateToModelManagement) {
                        Text(text = "모델 내려받기")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(onClick = { viewModel.retry() }) {
                    Text(text = "다시 시도")
                }
            }
        } else {
            OrbPulse()
        }
    }
}

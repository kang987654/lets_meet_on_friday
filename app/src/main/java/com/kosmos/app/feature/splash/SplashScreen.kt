package com.kosmos.app.feature.splash

import androidx.compose.foundation.layout.fillMaxWidth
import com.kosmos.app.ui.theme.KosmosTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.ui.theme.SplashStyle
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis

@Composable
fun SplashScreen(
    viewModel: SplashViewModel = hiltViewModel(),
    onInitializationComplete: () -> Unit,
    // [WHY] AC8 은 "모델 파일 없음 → 설정 화면 이동" 을 요구하는데, 안내 문구는 "설정 > 모델
    // 관리에서 내려받아 주세요" 라고 말하면서 이동 수단은 Retry 버튼뿐이었다 — 하단 탭 우회로가
    // 있어 갇히지는 않지만 안내와 동선이 어긋났다(MVP 감사 ac5-8).
    onNavigateToModelManagement: () -> Unit = {},
    // 설정의 시작 화면 장면 — null 이면 저장값을 아직 읽지 못한 것이라 장면을 그리지 않고 기다린다(기본 장면이 한 번 비치지 않게).
    splashStyle: SplashStyle? = SplashStyle.PLANET,
    // 장면을 이 시각(ms)에 멈춰 그린다 — 카탈로그 캡처용. null 이면 움직인다.
    previewTimeMs: Long? = null
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val handoff = LocalSplashHandoff.current
    val reduced = com.kosmos.app.ui.component.rememberReducedMotion()
    // [WHY] 앱을 열 때 이미 Ready(잠깐 나갔다 온 경우)면 장면 없이 바로 채팅(사용자 결정) — 매번 2초 넘게 기다리지 않게.
    val readyAtStart = remember { viewModel.loadState.value is ModelLoadState.Ready }
    val loading = loadState !is ModelLoadState.Error && loadState !is ModelLoadState.NotFound
    val animate = previewTimeMs == null && !reduced && splashStyle != null
    val clock = remember { SceneClock() }
    var started by remember { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }
    // Ready 순간에는 안내 문구가 비므로 마지막 문구를 덮개에 넘긴다(그리기용 기억이라 상태로 두지 않는다).
    val last = remember { LastStatus() }

    // [WHY] 장면은 엔진을 기다리는 동안만 돈다(DESIGN Motion) — 위치는 프레임 수가 아니라 흐른 시간으로 정해 프레임이 밀려도 속도가 같고,
    // 몇 초씩 멈춘 뒤에는 건너뛰지 않는다([SceneClock]).
    LaunchedEffect(animate, loading) {
        if (!animate || !loading) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                frame = clock.tick(it)
                started = true
            }
        }
    }

    LaunchedEffect(loadState) {
        if (loadState is ModelLoadState.Ready) {
            val style = splashStyle
            if (handoff != null && animate && style != null && !readyAtStart && started) {
                handoff.begin(SplashExit(style, clock, clock.time, last.text))
            }
            onInitializationComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        
        if (loadState is ModelLoadState.Error || loadState is ModelLoadState.NotFound) {
            val errorMessage = when (val state = loadState) {
                is ModelLoadState.Error -> com.kosmos.app.core.mapper.ErrorMessages.userMessage(state.error)
                is ModelLoadState.NotFound ->
                    "AI 모델 파일이 없어요. 모델 관리에서 내려받아 주세요."
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
                // [WHY] 로드 실패(파일 손상 추정)도 해법이 "다시 내려받기"라 같은 버튼을 준다.
                val canRedownload = loadState is ModelLoadState.NotFound ||
                    (loadState as? ModelLoadState.Error)?.error is com.kosmos.app.core.common.AppError.ModelLoadFailed
                if (canRedownload) {
                    com.kosmos.app.ui.component.KosmosButton("모델 내려받기", onNavigateToModelManagement, Modifier.fillMaxWidth(), accent = true)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                com.kosmos.app.ui.component.KosmosButton("다시 시도", { viewModel.retry() }, Modifier.fillMaxWidth())
            }
        } else {
            // [WHY] 장면만 있으면 "빈 화면"으로 오인된다(2026-08-15 실기기 문의) — 워드마크와 지금 무엇을 하는 중인지를 함께 보여
            // 살아 있음을 드러낸다. 엔진 재초기화는 실측 9~12초라(0.16.2) 그 시간 동안의 침묵이 가장 큰 불안 요소다.
            val statusText = when (loadState) {
                is ModelLoadState.Loading -> "모델 상태를 확인하고 있어요…"
                is ModelLoadState.FileFound -> "모델 파일을 찾았어요. 엔진을 준비하고 있어요…"
                is ModelLoadState.InitializingEngine -> "AI 엔진을 깨우는 중이에요 (10초 정도)…"
                else -> ""
            }
            if (statusText.isNotEmpty()) last.text = statusText
            SplashStage(
                style = splashStyle,
                time = { previewTimeMs ?: if (!animate) STATIC_SCENE_MS else frame },
                status = statusText.ifEmpty { last.text }
            )
        }
    }
}

private class LastStatus {
    var text: String = ""
}

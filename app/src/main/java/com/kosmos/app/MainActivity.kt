package com.kosmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

import android.content.Intent
import com.kosmos.app.platform.share.ShareIntentHandler
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var shareIntentHandler: ShareIntentHandler

    @Inject
    lateinit var voiceLaunchHandler: com.kosmos.app.platform.launch.VoiceLaunchHandler

    @Inject
    lateinit var modelRunner: com.kosmos.app.domain.modelrunner.ModelRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [WHY] 시스템 바를 투명하게 두고 앱 바탕이 비치게 한다 — 인셋은 각 화면의 상단 바·입력바가 진다(DESIGN.md "시스템 바").
        enableEdgeToEdge()
        // [WHY] 모델은 채팅 화면이 보일 때만 데운다 — 문서 뷰어는 모델 없이 열린다(0.34.0, ModelWarmUpObserver KDoc).
        lifecycle.addObserver(com.kosmos.app.platform.launch.ModelWarmUpObserver(modelRunner))

        // [WHY] 첫 실행 시 일괄 권한 요청(deprecated API, 결과 미처리)은 제거한다.
        // 권한은 각 기능 진입 시점에 컨텍스트와 함께 요청한다 (예: ChatScreen 마이크 요청 플로우).

        // Handle intent on cold start
        shareIntentHandler.handleIntent(intent)
        voiceLaunchHandler.handleIntent(intent)

        setContent {
            // [WHY] 저장된 테마 모드를 앱 루트에서 구독해 전체 트리에 적용한다 (ADR-005).
            val themeViewModel: com.kosmos.app.ui.theme.ThemeViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val fontMode by themeViewModel.fontMode.collectAsStateWithLifecycle()
            // [WHY] 창 바탕(앱 시작 순간)도 앱 테마를 따르게 — 저장값을 읽은 뒤 시스템의 앱별 야간 모드에 알린다.
            val persistedThemeMode by themeViewModel.persistedThemeMode.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(persistedThemeMode) {
                persistedThemeMode?.let { com.kosmos.app.ui.theme.syncApplicationNightMode(context, it) }
            }

            com.kosmos.app.ui.theme.KosmosTheme(themeMode = themeMode, fontMode = fontMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Set the new intent so getIntent() returns it
        setIntent(intent)
        // Handle intent when app is already running
        shareIntentHandler.handleIntent(intent)
        voiceLaunchHandler.handleIntent(intent)
    }
}

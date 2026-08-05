package com.kosmos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [WHY] 첫 실행 시 일괄 권한 요청(deprecated API, 결과 미처리)은 제거한다.
        // 권한은 각 기능 진입 시점에 컨텍스트와 함께 요청한다 (예: ChatScreen 마이크 요청 플로우).

        // Handle intent on cold start
        shareIntentHandler.handleIntent(intent)

        setContent {
            // [WHY] 저장된 테마 모드를 앱 루트에서 구독해 전체 트리에 적용한다 (ADR-005).
            val themeViewModel: com.kosmos.app.ui.theme.ThemeViewModel =
                androidx.lifecycle.viewmodel.compose.viewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

            com.kosmos.app.ui.theme.KosmosTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.kosmos.app.ui.component.AuroraBackground {
                        MainScreen()
                    }
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
    }
}

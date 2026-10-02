package com.kosmos.app.feature.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * [DocumentViewerActivity]
 * 문서 뷰어 진입점 (0.34.0) — 다른 앱의 "연결 프로그램"(ACTION_VIEW)과 앱 안 문서 홈이 같은 화면을 띄운다.
 *
 * ### Architecture Context
 * - **Layer**: Feature (Document)
 * - **Dependencies**: [DocumentViewerViewModel], [ThemeViewModel](설정 DataStore 만)
 *
 * ### Key Flow
 * 1. 인텐트 data 의 URI 를 [DocumentViewerViewModel.load].
 * 2. 닫기 → finish. 문서마다 최근 앱 목록에 따로 뜬다(`documentLaunchMode="intoExisting"`) — 같은 파일을 다시 열면 그 창으로.
 *
 * [WHY] MainActivity 와 분리한 이유 — 스플래시(모델 대기)·NavHost·Room 을 거치지 않고 바로 그리기 위해서다. 모델 준비는
 * MainActivity 에만 붙어 있으므로(ModelWarmUpObserver) 이 화면만 열면 모델이 올라가지 않는다. **여기에 ModelRunner 나 DB 를
 * 주입하지 말 것** — DocumentViewerActivityTest 가 막는다.
 */
@AndroidEntryPoint
class DocumentViewerActivity : ComponentActivity() {

    private val viewModel: DocumentViewerViewModel by viewModels()
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val textScaleStep by viewModel.textScaleStep.collectAsStateWithLifecycle()
            val selection by viewModel.selection.collectAsStateWithLifecycle()
            LaunchedEffect(uri) { viewModel.load(uri, intent?.type) }
            KosmosTheme(themeMode = themeMode) {
                DocumentViewerScreen(
                    state = state,
                    onSelectSheet = viewModel::selectSheet,
                    onClose = ::finish,
                    textScaleStep = textScaleStep,
                    onTextScale = viewModel::changeTextScale,
                    selection = selection,
                    selectionActions = SelectionActions(
                        start = viewModel::startSelection,
                        cancel = viewModel::cancelSelection,
                        row = viewModel::tapRow,
                        block = viewModel::tapBlock,
                        pdfPage = viewModel::onPdfPage,
                        send = { if (viewModel.sendToChat()) openChat() }
                    )
                )
            }
        }
    }

    /**
     * 채팅 화면을 앞으로 가져온다 — 이때 MainActivity 의 onStart 가 모델을 데운다(ADR-027).
     *
     * [WHY] NEW_TASK 는 채팅 태스크를 찾아 앞으로 가져오고, 없으면 새로 만든다. 뷰어 태스크는 친화도가 비어 있어
     * (`taskAffinity=""`) 채팅 화면이 뷰어 태스크 안에 하나 더 생기지 않는다. 뷰어는 닫지 않는다 — 다른 범위를 또 보낼 수 있다.
     */
    private fun openChat() {
        startActivity(Intent(this, com.kosmos.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object {
        /** 앱 안에서 문서를 연다(문서 홈). 읽기 권한을 함께 넘긴다. */
        fun intent(context: Context, uri: Uri, mimeType: String? = null): Intent =
            Intent(context, DocumentViewerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}

package com.kosmos.app.feature.document

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.domain.document.DocumentError
import com.kosmos.app.ui.theme.KosmosTheme
import kotlinx.coroutines.launch

/**
 * 문서 뷰어 화면 (0.34.0) — 상태별로 표([SheetView])·PDF([PdfView])·오류를 그린다. 상태를 받기만 하는 무상태 화면이다.
 *
 * [WHY] 상단은 TopAppBar(인셋 내장), 시트 탭은 bottomBar 에 `navigationBarsPadding` 을 직접 단다 — 평범한 Row 는
 * 제스처 바에 깔린다(AGENTS §2-③, 0.19.1 실기기 결함).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    state: DocumentViewerState,
    onSelectSheet: (Int) -> Unit,
    onClose: () -> Unit,
    textScaleStep: Int = 1,
    onTextScale: (delta: Int) -> Unit = {},
    selection: ChatSelection? = null,
    selectionActions: SelectionActions = SelectionActions()
) {
    val colors = KosmosTheme.colors
    var zoom by rememberSaveable { mutableFloatStateOf(MIN_ZOOM) }
    var detailText by remember { mutableStateOf<String?>(null) }

    val title = state.fileName ?: "문서"

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { TextButton(onClick = onClose) { Text("닫기", color = colors.accent) } },
                actions = {
                    val ready = state is DocumentViewerState.Pdf || state is DocumentViewerState.Flow ||
                        (state is DocumentViewerState.Spreadsheet && state.sheet != null)
                    if (selection == null && ready) {
                        TextButton(onClick = selectionActions.start) { Text("채팅으로", color = colors.accent) }
                    }
                    if (state is DocumentViewerState.Flow) {
                        TextButton(onClick = { onTextScale(-1) }, enabled = textScaleStep > 0) { Text("가−", color = colors.accent, fontSize = 13.sp) }
                        TextButton(onClick = { onTextScale(1) }, enabled = textScaleStep < 2) { Text("가+", color = colors.accent, fontSize = 17.sp) }
                    }
                    if (state is DocumentViewerState.Pdf) {
                        // 버튼은 1배 → 1.5배 → 2배 → 1배로 돈다(두 손가락 확대와 같은 값을 쓴다).
                        TextButton(onClick = { zoom = nextZoom(zoom) }) {
                            Text("${(zoom * 100).toInt()}%", color = colors.accent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg)
            )
        },
        bottomBar = {
            when {
                selection != null -> SelectionBar(selection, selectionActions)
                state is DocumentViewerState.Spreadsheet && state.sheetNames.size > 1 -> SheetTabs(state.sheetNames, state.selected, onSelectSheet)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                DocumentViewerState.Loading -> Centered { Loading("여는 중…") }
                is DocumentViewerState.Failed -> Centered { Message(documentErrorMessage(state.error)) }
                is DocumentViewerState.Pdf -> PdfView(state.pages, zoom, onZoomChange = { zoom = it }, onPageChange = selectionActions.pdfPage)
                is DocumentViewerState.Flow -> when {
                    state.document.blocks.isEmpty() -> Centered { Message("내용이 없는 문서예요") }
                    else -> FlowView(
                        state.document,
                        state.images,
                        textScaleOf(textScaleStep),
                        selectedBlocks = (selection as? ChatSelection.Blocks)?.indices,
                        onBlockClick = selectionActions.block
                    )
                }
                is DocumentViewerState.Spreadsheet -> when {
                    state.sheetError != null -> Centered { Message(documentErrorMessage(state.sheetError)) }
                    state.sheet == null -> Centered { Loading("시트를 읽는 중…") }
                    state.sheet.rows.isEmpty() -> Centered { Message("빈 시트예요") }
                    else -> SheetView(
                        state.sheet,
                        onCellLongPress = { detailText = it },
                        selectedRows = (selection as? ChatSelection.Rows)?.let { it.range ?: it.anchor?.let { a -> a..a } },
                        onRowNumberClick = if (selection is ChatSelection.Rows) selectionActions.row else null
                    )
                }
            }
        }
    }

    detailText?.let { text -> CellDetailDialog(text, onDismiss = { detailText = null }) }
}

/** "채팅으로 보내기" 화면 동작 — 기본값은 아무것도 하지 않는다(테스트·미리보기에서 화면만 그릴 때). */
data class SelectionActions(
    val start: () -> Unit = {},
    val cancel: () -> Unit = {},
    val row: (Int) -> Unit = {},
    val block: (Int) -> Unit = {},
    val pdfPage: (Int) -> Unit = {},
    val send: () -> Unit = {}
)

/** 고르는 중 하단 막대 — 안내 또는 "n / 300자", 취소·보내기. */
@Composable
private fun SelectionBar(selection: ChatSelection, actions: SelectionActions) {
    val colors = KosmosTheme.colors
    val preview = selection.preview
    val message = when {
        preview != null -> buildString {
            append("${preview.text.length} / ${CHAT_CAP}자")
            if (preview.truncated) append(" · 앞부분만 보내요(전체 ${preview.fullLength}자)")
        }
        selection is ChatSelection.Rows && selection.anchor == null -> "보낼 시작 행의 번호를 누르세요"
        selection is ChatSelection.Rows -> "끝 행의 번호를 누르세요"
        selection is ChatSelection.Blocks -> "보낼 문단·표를 누르세요"
        selection is ChatSelection.PdfPage && selection.unsupported -> "이 기기에서는 PDF 글자를 꺼낼 수 없어요(안드로이드 15 이상)"
        else -> "이 페이지에서 보낼 글자를 찾는 중이에요"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.glassMid)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(message, color = if (preview?.truncated == true) colors.warning else colors.textSecondary, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = actions.cancel) { Text("취소", color = colors.textSecondary) }
            TextButton(onClick = actions.send, enabled = preview != null) { Text("채팅으로 보내기", color = if (preview != null) colors.accent else colors.textMuted) }
        }
    }
}

@Composable
private fun SheetTabs(names: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = KosmosTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.glassMid)
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        names.forEachIndexed { index, name ->
            val isSelected = index == selected
            Text(
                text = name,
                color = if (isSelected) colors.onAccent else colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .background(if (isSelected) colors.accent else colors.glass, RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun CellDetailDialog(text: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = text,
                color = KosmosTheme.colors.textPrimary,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("셀 내용", text)))
                    onDismiss()
                }
            }) { Text("복사") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = KosmosTheme.colors.surface
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Loading(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = KosmosTheme.colors.accent)
        Spacer(Modifier.height(12.dp))
        Text(label, color = KosmosTheme.colors.textSecondary)
    }
}

@Composable
private fun Message(text: String) {
    Text(text, color = KosmosTheme.colors.textSecondary, textAlign = TextAlign.Center)
}

/** 읽기 실패 사유 → 사용자 안내. */
internal fun documentErrorMessage(error: DocumentError): String = when (error) {
    DocumentError.UNSUPPORTED -> "이 형식은 아직 열 수 없어요.\nPDF · 엑셀(xlsx) · CSV · 워드(docx) · 한글(hwpx)을 열 수 있어요."
    DocumentError.UNREADABLE -> "파일을 열 수 없어요.\n권한이 만료됐거나 파일이 옮겨졌을 수 있어요."
    DocumentError.TOO_LARGE -> "파일이 너무 커서 열 수 없어요."
    DocumentError.CORRUPT -> "파일이 손상됐거나 내용이 형식과 달라요."
    DocumentError.ENCRYPTED -> "암호가 걸린 문서이거나 지원하지 않는 옛 형식(xls · doc · hwp)이에요."
}

internal fun nextZoom(zoom: Float): Float = when {
    zoom < 1.5f -> 1.5f
    zoom < 2f -> 2f
    else -> MIN_ZOOM
}

private val CHAT_CAP = com.kosmos.app.core.common.Constants.MAX_ATTACHED_DOC_CHARS

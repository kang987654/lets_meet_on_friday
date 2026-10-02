package com.kosmos.app.feature.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.domain.document.DocumentType
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.ui.component.glassEffect
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 문서 홈 (0.34.0) — 드로어 "📄 문서"의 목적지. 파일 열기 + 최근 문서 목록. 문서는 [DocumentViewerActivity] 가 연다.
 *
 * [WHY] 상단은 TopAppBar(인셋 내장, AGENTS §2-③). 최근 문서를 길게 누르면 목록에서만 지운다(파일은 그대로) — 확인 문구로 그 차이를 알린다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentHomeScreen(
    viewModel: DocumentHomeViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val colors = KosmosTheme.colors
    val context = LocalContext.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var removing by remember { mutableStateOf<RecentDocument?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onPicked(it) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DocumentHomeViewModel.Event.Open ->
                    context.startActivity(DocumentViewerActivity.intent(context, event.uri.toUri(), event.mimeType))
                is DocumentHomeViewModel.Event.AccessLost ->
                    snackbar.showSnackbar("'${event.name}'을(를) 열 수 없어요. 파일 열기로 다시 골라 주세요.")
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("문서", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("뒤로", color = colors.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(shape = RoundedCornerShape(16.dp), backgroundColor = colors.accentDim, borderColor = colors.accent)
                        .combinedClickable(onClick = { picker.launch(DocumentType.VIEWABLE_MIME_TYPES.toTypedArray()) })
                        .padding(16.dp)
                ) {
                    Text("📂  파일 열기", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "PDF · 엑셀(xlsx) · CSV · 워드(docx) · 한글(hwpx) — AI 를 켜지 않고 가볍게 열어요",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                Text(
                    "최근 문서",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp)
                )
            }
            if (recent.isEmpty()) {
                item {
                    Text(
                        "여기서 연 문서가 쌓여요. 다른 앱에서 '연결 프로그램'으로 연 문서는 남지 않아요.",
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            items(recent, key = { it.uri }) { document ->
                RecentRow(
                    document = document,
                    onClick = { viewModel.open(document) },
                    onLongClick = { removing = document }
                )
            }
        }
    }

    removing?.let { document ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text("'${document.name}'을(를) 최근 목록에서 지울까요? 파일은 지워지지 않아요.", color = colors.textPrimary) },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(document); removing = null }) { Text("지우기", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("취소") } },
            containerColor = colors.surface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(document: RecentDocument, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = KosmosTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(documentIcon(document), fontSize = 20.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(document.name, color = colors.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${IsoDateTimeParser.monthDayKorean(document.openedAt)} · ${IsoDateTimeParser.timeKorean(document.openedAt)}",
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }
    }
}

internal fun documentIcon(document: RecentDocument): String = when (DocumentType.detect(document.mimeType, document.name)) {
    DocumentType.PDF -> "📕"
    DocumentType.XLSX -> "📊"
    DocumentType.CSV -> "📋"
    DocumentType.DOCX -> "📘"
    DocumentType.HWPX -> "📗"
    DocumentType.UNSUPPORTED -> "📄"
}

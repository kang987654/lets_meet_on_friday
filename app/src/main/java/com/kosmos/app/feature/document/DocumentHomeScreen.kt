package com.kosmos.app.feature.document

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kosmos.app.R
import com.kosmos.app.domain.document.DocumentType
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.domain.util.IsoDateTimeParser
import com.kosmos.app.ui.component.GroupPosition
import com.kosmos.app.ui.component.KosmosButton
import com.kosmos.app.ui.component.groupItem
import com.kosmos.app.ui.component.groupPosition
import com.kosmos.app.ui.component.showUndo
import com.kosmos.app.ui.theme.KosmosTheme
import com.kosmos.app.ui.theme.currentAppLanguage
import kotlinx.coroutines.launch
import com.kosmos.app.ui.component.DeleteIconButton
import com.kosmos.app.ui.component.EmptyState
import com.kosmos.app.ui.component.KosmosListRow
import com.kosmos.app.ui.component.KosmosTopBar
import com.kosmos.app.ui.component.SectionHeader
import com.kosmos.app.ui.component.rememberToday

/**
 * 문서 홈 (0.34.0) — 드로어 "문서"의 목적지. 파일 열기 + 최근 문서 목록. 문서는 [DocumentViewerActivity] 가 연다.
 *
 * [WHY] 최근 문서는 휴지통으로 바로 빼고 되돌리기를 준다(0.38.3 결정 2) — 길게 누르기는 보이지 않는 동작이었다. 목록에서만 빠지고 파일은 그대로라는 사실은 각주로 알린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentHomeScreen(
    viewModel: DocumentHomeViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val colors = KosmosTheme.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onPicked(it) }
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            when (event) {
                is DocumentHomeViewModel.Event.Open ->
                    context.startActivity(DocumentViewerActivity.intent(context, event.uri.toUri(), event.mimeType))
                is DocumentHomeViewModel.Event.AccessLost ->
                    snackbar.showSnackbar(
                        resources.getString(
                            R.string.doc_home_access_lost_snackbar,
                            event.name.ifBlank { resources.getString(R.string.doc_viewer_default_title) }
                        )
                    )
            }
        }
    }

    val hide: (RecentDocument) -> Unit = { document ->
        viewModel.hide(document)
        scope.launch {
            var undone = false
            // [WHY] 화면을 떠나 스낵바가 취소돼도 권한은 돌려준다 — finally 의 remove 는 viewModelScope 에서 돈다.
            try {
                snackbar.showUndo(
                    message = resources.getString(
                        R.string.doc_home_removed_snackbar,
                        document.name.ifBlank { resources.getString(R.string.doc_viewer_default_title) }
                    ),
                    actionLabel = resources.getString(R.string.common_undo)
                ) { undone = true; viewModel.restore(document) }
            } finally {
                if (!undone) viewModel.remove(document)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            KosmosTopBar(title = stringResource(R.string.doc_home_title), onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            item {
                // [WHY] 파일 열기는 이 화면의 주 동작이라 주 버튼 — 하늘 면 + 하늘 테두리 상자는 버튼인지 안내인지 모호했다(0.38.3 M4 시안 A).
                KosmosButton(stringResource(R.string.doc_home_open_file), { picker.launch(DocumentType.PICKER_MIME_TYPES.toTypedArray()) }, Modifier.fillMaxWidth(), accent = true)
                Text(
                    stringResource(R.string.doc_home_supported_formats),
                    color = colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
            if (recent.isEmpty()) {
                item {
                    EmptyState(
                        R.drawable.ic_ph_file,
                        stringResource(R.string.doc_home_empty_recent)
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.doc_home_section_recent), Modifier.padding(top = 16.dp)) }
                itemsIndexed(recent, key = { _, document -> document.uri }) { index, document ->
                    RecentRow(
                        document = document,
                        position = groupPosition(index, recent.size),
                        onClick = { viewModel.open(document) },
                        onRemove = { hide(document) }
                    )
                }
                item {
                    Text(
                        stringResource(R.string.doc_home_recent_footnote),
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(document: RecentDocument, position: GroupPosition, onClick: () -> Unit, onRemove: () -> Unit) {
    val lang = currentAppLanguage()
    val today = rememberToday()
    val zone = java.time.ZoneId.systemDefault()
    val openedDay = java.time.Instant.ofEpochMilli(document.openedAt).atZone(zone).toLocalDate()
    KosmosListRow(
        title = document.name.ifBlank { stringResource(R.string.doc_viewer_default_title) },
        supporting = "${IsoDateTimeParser.dayLabelKorean(openedDay, today, lang)} ${IsoDateTimeParser.timeKorean(document.openedAt, lang = lang)}",
        leadingIcon = documentIcon(document),
        titleMaxLines = 1,
        onClick = onClick,
        divider = position == GroupPosition.First || position == GroupPosition.Middle,
        modifier = Modifier.groupItem(position),
        trailing = { DeleteIconButton(onRemove, contentDescription = stringResource(R.string.doc_home_remove_recent_cd)) }
    )
}

@androidx.annotation.DrawableRes
internal fun documentIcon(document: RecentDocument): Int = when (DocumentType.detect(document.mimeType, document.name)) {
    DocumentType.PDF -> R.drawable.ic_ph_file_pdf
    DocumentType.XLSX -> R.drawable.ic_ph_file_xls
    DocumentType.CSV -> R.drawable.ic_ph_file_csv
    DocumentType.DOCX -> R.drawable.ic_ph_file_doc
    DocumentType.HWPX -> R.drawable.ic_ph_file_text
    DocumentType.UNSUPPORTED -> R.drawable.ic_ph_file
}

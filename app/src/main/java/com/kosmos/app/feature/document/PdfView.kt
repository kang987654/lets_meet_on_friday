package com.kosmos.app.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.platform.document.PdfPages
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * PDF 를 페이지 단위로 그립니다 (0.34.0) — 보이는 페이지만 렌더하고([PdfPages] 가 캐시), 두 손가락으로 1~3배 확대.
 *
 * [WHY] 확대는 비트맵을 다시 그리지 않고 늘린다 — 화면 폭 비트맵(페이지당 수 MB)을 배율만큼 크게 만들면 3배에서 페이지당
 * 수십 MB 가 되어 캐시가 바로 넘친다. 3배에서 글자가 흐려지는 것은 실기기 게이트에서 확인한다(계획서).
 * 확대 중 가로 이동은 바깥 가로 스크롤이 맡는다 — 목록 자체 폭을 배율만큼 넓힌다.
 *
 * @param zoom 현재 배율(화면이 상태를 가진다 — 상단 바 배율 버튼과 공유).
 */
@Composable
fun PdfView(
    pages: PdfPages,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KosmosTheme.colors
    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    // [WHY] pointerInput(Unit) 블록은 한 번만 만들어진다 — 배율은 최신 값을 따로 읽어야 버튼으로 바꾼 배율에서 이어 확대된다.
    val latestZoom by rememberUpdatedState(zoom)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .pointerInput(Unit) {
                // [WHY] Initial 단계에서 두 손가락 제스처만 가로챈다 — 한 손가락 끌기는 목록 스크롤에 그대로 넘긴다.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var current = latestZoom
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            val factor = event.calculateZoom()
                            if (factor != 1f) {
                                current = (current * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                onZoomChange(current)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val viewportWidth = maxWidth
        val renderWidthPx = with(LocalDensity.current) { viewportWidth.roundToPx() }
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(viewportWidth * zoom).fillMaxHeight().testTag(PDF_LIST_TAG),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(count = pages.pageCount, key = { it }) { index ->
                    PdfPage(pages, index, renderWidthPx, viewportWidth * zoom)
                }
            }
        }
        Text(
            text = "$currentPage / ${pages.pageCount}",
            color = colors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .background(colors.glassHigh, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PdfPage(pages: PdfPages, index: Int, renderWidthPx: Int, width: androidx.compose.ui.unit.Dp) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pages, index, renderWidthPx) {
        value = pages.render(index, renderWidthPx)
    }
    val height = width * pages.aspectRatio(index)
    Box(
        modifier = Modifier.fillMaxWidth().height(height).background(KosmosTheme.colors.glass),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image == null) {
            Text("${index + 1}쪽", color = KosmosTheme.colors.textMuted, fontSize = 12.sp)
        } else {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "${index + 1}쪽",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 3f
internal const val PDF_LIST_TAG = "pdf_pages"

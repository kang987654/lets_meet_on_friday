package com.kosmos.app.feature.document

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.domain.document.FlowBlock
import com.kosmos.app.domain.document.FlowDocument
import com.kosmos.app.domain.document.Span
import com.kosmos.app.domain.document.TableCell
import com.kosmos.app.domain.document.plainText
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 워드·한글 문서 읽기 모드 (0.35.0) — 블록을 위에서 아래로 다시 배치한다. 원본의 쪽·글꼴·정밀 배치는 재현하지 않는다(계획서 결정 1).
 *
 * ### Key Flow
 * 1. 블록 단위 `LazyColumn` — 큰 문서도 보이는 블록만 구성한다.
 * 2. 제목은 단계별 크기, 목록은 들여쓰기 + 글머리, 표는 가로 스크롤 격자, 이미지는 화면 폭으로 줄여 지연 디코드, 쪽 나눔은 구분선.
 * 3. 글자는 길게 눌러 선택·복사할 수 있다(`SelectionContainer`).
 *
 * @param scale 글자 배율(화면 상단 가−/가+ 단계).
 */
@Composable
fun FlowView(
    document: FlowDocument,
    images: suspend (entryName: String, widthPx: Int) -> Bitmap?,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val colors = KosmosTheme.colors
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface)) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(FLOW_LIST_TAG),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (document.truncated) {
                    item(key = "truncated") {
                        Text("문서가 길어서 앞부분만 보여요", color = colors.warning, fontSize = 12.sp)
                    }
                }
                itemsIndexed(document.blocks, key = { index, _ -> index }) { _, block ->
                    FlowBlockView(block, images, widthPx, scale)
                }
            }
        }
    }
}

@Composable
private fun FlowBlockView(block: FlowBlock, images: suspend (String, Int) -> Bitmap?, widthPx: Int, scale: Float) {
    val colors = KosmosTheme.colors
    when (block) {
        is FlowBlock.Heading -> Text(
            text = annotated(block.spans),
            color = colors.textPrimary,
            fontSize = (when (block.level) { 1 -> 22f; 2 -> 19f; else -> 17f } * scale).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )
        is FlowBlock.Paragraph -> Text(annotated(block.spans), color = colors.textPrimary, fontSize = body(scale), lineHeight = line(scale))
        is FlowBlock.ListItem -> Row(Modifier.padding(start = (block.level * 16).dp)) {
            Text(if (block.level == 0) "•  " else "◦  ", color = colors.textSecondary, fontSize = body(scale))
            Text(annotated(block.spans), color = colors.textPrimary, fontSize = body(scale), lineHeight = line(scale))
        }
        is FlowBlock.Table -> FlowTable(block, scale)
        is FlowBlock.Image -> FlowImage(block.entryName, images, widthPx)
        FlowBlock.PageBreak -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = colors.borderHigh)
    }
}

@Composable
private fun FlowTable(table: FlowBlock.Table, scale: Float) {
    val colors = KosmosTheme.colors
    // [WHY] 칸 폭은 열마다 고정(120dp × 가로 병합 수) — 칸 내용 길이로 맞추면 행마다 폭이 달라져 격자가 어긋난다.
    Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, colors.border)) {
        table.rows.forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                row.forEach { cell -> FlowTableCell(cell, scale) }
            }
        }
    }
}

@Composable
private fun FlowTableCell(cell: TableCell, scale: Float) {
    val colors = KosmosTheme.colors
    Box(
        Modifier
            .width(CELL_WIDTH * cell.colSpan)
            .fillMaxHeight()
            .border(0.5.dp, colors.border)
            .background(if (cell.merged) colors.glass else colors.surface)
            .padding(6.dp)
    ) {
        if (!cell.merged) {
            Text(
                text = cell.blocks.joinToString("\n") { it.plainText() },
                color = colors.textPrimary,
                fontSize = (13f * scale).sp
            )
        }
    }
}

@Composable
private fun FlowImage(entryName: String, images: suspend (String, Int) -> Bitmap?, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(initialValue = null, entryName, widthPx) { value = images(entryName, widthPx) }
    val image = bitmap
    if (image == null || image.width == 0) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(KosmosTheme.colors.glass))
    } else {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "문서 그림",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height)
        )
    }
}

private fun annotated(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        if (!span.bold && !span.italic && !span.underline) {
            append(span.text)
        } else {
            withStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (span.underline) TextDecoration.Underline else null
                )
            ) { append(span.text) }
        }
    }
}

private fun body(scale: Float): TextUnit = (15f * scale).sp
private fun line(scale: Float): TextUnit = (23f * scale).sp

/** 글자 크기 단계(0~2) → 배율. */
internal fun textScaleOf(step: Int): Float = when (step) {
    0 -> 0.85f
    2 -> 1.25f
    else -> 1f
}

private val CELL_WIDTH = 120.dp
internal const val FLOW_LIST_TAG = "flow_blocks"

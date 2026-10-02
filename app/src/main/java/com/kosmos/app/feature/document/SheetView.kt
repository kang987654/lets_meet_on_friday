package com.kosmos.app.feature.document

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosmos.app.domain.document.CellRange
import com.kosmos.app.domain.document.CellRef
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.domain.document.SheetRow
import com.kosmos.app.ui.theme.KosmosTheme

/**
 * 표 한 장을 그립니다 (0.34.0) — 열 머리(A, B, …)·행 번호·틀 고정·병합을 지원하는 보기 전용 격자.
 *
 * ### Key Flow
 * 1. 열 머리 + 틀 고정 행은 `stickyHeader` 로 위에 붙는다.
 * 2. 행 번호와 틀 고정 열은 가로로 움직이지 않고, 나머지 열은 **모든 행이 같은 [ScrollState] 를 공유**해 함께 움직인다.
 * 3. 병합 셀은 범위의 첫 칸이 넓이를 합쳐 차지하고 나머지 칸은 그리지 않는다(세로 병합의 아래 행은 빈 칸).
 * 4. 칸을 길게 누르면 [onCellLongPress] — 잘린 긴 글자를 전부 보고 복사한다.
 *
 * [WHY] 행 높이는 고정이다 — 가변 높이면 틀 고정 열과 스크롤 열이 행마다 높이를 맞춰야 해 측정이 두 번 돈다.
 * 칸 안 줄바꿈은 한 줄로 줄이고 전체는 길게 눌러 본다. 그리는 열은 [MAX_RENDER_COLUMNS] 까지(아주 넓은 시트의 행 구성 비용 상한).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetView(
    sheet: Sheet,
    modifier: Modifier = Modifier,
    onCellLongPress: (String) -> Unit = {}
) {
    val layout = remember(sheet) { SheetLayout.of(sheet) }
    val horizontal = rememberScrollState()
    val colors = KosmosTheme.colors

    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface)) {
        // [WHY] 틀 고정 열이 화면 절반을 넘으면 스크롤할 자리가 없다 — 그때는 고정을 풀어 엑셀 모바일과 같게 동작한다.
        val frozenColumns = layout.frozenColumns.takeIf { n ->
            ROW_NUMBER_WIDTH + (0 until n).fold(0.dp) { acc, c -> acc + layout.width(c) } <= maxWidth / 2
        } ?: 0
        val frozenRange = 0 until frozenColumns
        val scrollRange = frozenColumns until layout.columnCount
        val (frozenRows, bodyRows) = sheet.rows.partition { it.index < layout.frozenRows }

        Column(Modifier.fillMaxSize()) {
            if (!sheet.complete) {
                Text(
                    text = "나머지 행을 읽는 중…",
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            if (sheet.truncated) {
                Text(
                    text = "파일이 커서 앞부분(${sheet.rows.lastOrNull()?.index?.plus(1) ?: 0}행까지)만 보여요",
                    color = colors.warning,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize().testTag(SHEET_GRID_TAG)) {
                stickyHeader(key = "header") {
                    Column(Modifier.background(colors.surface)) {
                        HeaderRow(layout, frozenRange, scrollRange, horizontal)
                        frozenRows.forEach { row -> DataRow(row, layout, frozenRange, scrollRange, horizontal, onCellLongPress) }
                    }
                }
                items(bodyRows, key = { it.index }) { row ->
                    DataRow(row, layout, frozenRange, scrollRange, horizontal, onCellLongPress)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(layout: SheetLayout, frozen: IntRange, scroll: IntRange, horizontal: ScrollState) {
    val colors = KosmosTheme.colors
    Row(Modifier.background(colors.glassMid)) {
        GridCell("", ROW_NUMBER_WIDTH, header = true)
        frozen.forEach { c -> GridCell(CellRef.columnName(c), layout.width(c), header = true) }
        Row(Modifier.horizontalScroll(horizontal)) {
            scroll.forEach { c -> GridCell(CellRef.columnName(c), layout.width(c), header = true) }
        }
    }
}

@Composable
private fun DataRow(
    row: SheetRow,
    layout: SheetLayout,
    frozen: IntRange,
    scroll: IntRange,
    horizontal: ScrollState,
    onCellLongPress: (String) -> Unit
) {
    val texts = remember(row) { row.cells.associate { it.column to it.text } }
    val merges = layout.mergesAt(row.index)
    Row {
        GridCell((row.index + 1).toString(), ROW_NUMBER_WIDTH, header = true)
        Segment(row.index, texts, merges, frozen, layout, onCellLongPress)
        Row(Modifier.horizontalScroll(horizontal)) {
            Segment(row.index, texts, merges, scroll, layout, onCellLongPress)
        }
    }
}

@Composable
private fun Segment(
    rowIndex: Int,
    texts: Map<Int, String>,
    merges: List<CellRange>,
    range: IntRange,
    layout: SheetLayout,
    onCellLongPress: (String) -> Unit
) {
    var c = range.first
    while (c <= range.last) {
        val merge = merges.firstOrNull { c in it.firstColumn..it.lastColumn }
        if (merge == null) {
            GridCell(texts[c].orEmpty(), layout.width(c), onLongPress = onCellLongPress)
            c++
        } else {
            val end = minOf(merge.lastColumn, range.last)
            val width = (c..end).fold(0.dp) { acc, col -> acc + layout.width(col) }
            val text = if (rowIndex == merge.firstRow && c == merge.firstColumn) texts[c].orEmpty() else ""
            GridCell(text, width, onLongPress = onCellLongPress, centered = true)
            c = end + 1
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    text: String,
    width: Dp,
    header: Boolean = false,
    centered: Boolean = false,
    onLongPress: ((String) -> Unit)? = null
) {
    val colors = KosmosTheme.colors
    val line = colors.border
    val numeric = !header && NUMERIC.matches(text)
    Box(
        modifier = Modifier
            .width(width)
            .height(ROW_HEIGHT)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(line, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
                drawLine(line, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
            }
            .then(
                if (onLongPress != null && text.isNotEmpty()) {
                    Modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress(text) })
                } else Modifier
            )
            .padding(horizontal = 6.dp),
        contentAlignment = when {
            header || centered -> Alignment.Center
            numeric -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        }
    ) {
        Text(
            text = text.replace('\n', ' '),
            color = if (header) colors.textMuted else colors.textPrimary,
            fontSize = if (header) 11.sp else 13.sp,
            fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (numeric) TextAlign.End else TextAlign.Start
        )
    }
}

/**
 * 너비 정보가 없는 열(csv 전부, 너비를 안 정한 xlsx 열)을 내용 길이로 맞춘다 — 기본 72dp 면 전화번호·날짜가 잘렸다(0.34.0 M4 에뮬레이터).
 *
 * [WHY] 앞 [FIT_SAMPLE_ROWS] 행만 본다 — 큰 시트에서 전부 재면 그리기 전에 지연이 생기고, 앞부분을 먼저 그린 뒤(부분 시트) 끝까지
 * 읽은 시트로 바뀔 때 같은 표본이어야 열 너비가 튀지 않는다. 글자 폭은 13sp 기준 근사(ASCII 7.5dp, 그 외 13dp).
 */
private fun fittedWidths(sheet: Sheet, columnCount: Int): List<Dp> {
    val longest = FloatArray(columnCount)
    // 여러 열에 걸친 병합 칸은 넓이를 합쳐 쓰므로 한 열의 너비를 정하는 데 넣지 않는다.
    val spanning = sheet.merges.filter { it.lastColumn > it.firstColumn }.map { it.firstRow to it.firstColumn }.toSet()
    sheet.rows.take(FIT_SAMPLE_ROWS).forEach { row ->
        row.cells.forEach { cell ->
            if (cell.column < columnCount && (row.index to cell.column) !in spanning) {
                val width = cell.text.substringBefore('\n').sumOf { ch -> if (ch.code < 128) 75 else 130 }.toFloat() / 10f
                if (width > longest[cell.column]) longest[cell.column] = width
            }
        }
    }
    return longest.map { w -> if (w == 0f) DEFAULT_COLUMN_WIDTH else (w + 14f).dp.coerceIn(48.dp, 220.dp) }
}

/** 시트에서 한 번만 계산해 두는 그리기 정보. */
private class SheetLayout(
    val columnCount: Int,
    val frozenRows: Int,
    val frozenColumns: Int,
    private val widths: List<Dp>,
    private val mergesByRow: Map<Int, List<CellRange>>
) {
    fun width(column: Int): Dp = widths.getOrElse(column) { DEFAULT_COLUMN_WIDTH }
    fun mergesAt(row: Int): List<CellRange> = mergesByRow[row].orEmpty()

    companion object {
        fun of(sheet: Sheet): SheetLayout {
            val columnCount = sheet.columnCount.coerceIn(1, MAX_RENDER_COLUMNS)
            val fitted = fittedWidths(sheet, columnCount)
            val widths = List(columnCount) { c ->
                // 엑셀 너비는 "기본 글꼴 숫자 몇 개" 단위다 — 대략 7dp/자 + 여백으로 환산하고 극단값은 자른다.
                sheet.columnWidths[c]?.let { (it * 7f + 10f).dp.coerceIn(36.dp, 320.dp) } ?: fitted[c]
            }
            val mergesByRow = mutableMapOf<Int, MutableList<CellRange>>()
            sheet.merges
                .filter { it.lastRow - it.firstRow <= MAX_MERGE_ROWS }
                .forEach { m -> (m.firstRow..m.lastRow).forEach { r -> mergesByRow.getOrPut(r) { mutableListOf() } += m } }
            return SheetLayout(
                columnCount = columnCount,
                frozenRows = sheet.frozenRows.takeIf { it <= MAX_FROZEN_ROWS } ?: 0,
                frozenColumns = sheet.frozenColumns.coerceAtMost(columnCount),
                widths = widths,
                mergesByRow = mergesByRow
            )
        }
    }
}

private val NUMERIC = Regex("""^[-+]?[\d,]*\.?\d+%?$""")
private val ROW_HEIGHT = 32.dp
private val ROW_NUMBER_WIDTH = 44.dp
private val DEFAULT_COLUMN_WIDTH = 72.dp
private const val MAX_RENDER_COLUMNS = 200
private const val MAX_FROZEN_ROWS = 5
private const val MAX_MERGE_ROWS = 1000
private const val FIT_SAMPLE_ROWS = 200

/** 테스트가 격자를 찾는 태그. */
internal const val SHEET_GRID_TAG = "sheet_grid"


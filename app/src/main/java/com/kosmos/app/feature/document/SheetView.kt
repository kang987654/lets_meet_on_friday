package com.kosmos.app.feature.document

import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
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
    onCellLongPress: (String) -> Unit = {},
    selectedRows: IntRange? = null,
    onRowNumberClick: ((Int) -> Unit)? = null
) {
    // [WHY] 열 너비는 실제 글꼴로 잰다 — 글자 수 × 고정 폭으로 어림하면 HadaRound 숫자가 더 넓어 금액이 "100…"으로 잘렸다(0.38.5 impeccable P1).
    val measurer = rememberTextMeasurer()
    val cellStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val density = LocalDensity.current
    val layout = remember(sheet, cellStyle, density) {
        SheetLayout.of(sheet) { text -> with(density) { measurer.measure(text, cellStyle, maxLines = 1, softWrap = false).size.width.toDp() } }
    }
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
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            if (sheet.truncated) {
                Text(
                    text = "파일이 커서 앞부분(${sheet.rows.lastOrNull()?.index?.plus(1) ?: 0}행까지)만 보여요",
                    color = colors.warningText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            LazyColumn(Modifier.fillMaxSize().testTag(SHEET_GRID_TAG)) {
                stickyHeader(key = "header") {
                    Column(Modifier.background(colors.surface)) {
                        HeaderRow(layout, frozenRange, scrollRange, horizontal)
                        frozenRows.forEach { row ->
                            DataRow(row, layout, frozenRange, scrollRange, horizontal, onCellLongPress, selectedRows?.contains(row.index) == true, onRowNumberClick)
                        }
                    }
                }
                items(bodyRows, key = { it.index }) { row ->
                    DataRow(row, layout, frozenRange, scrollRange, horizontal, onCellLongPress, selectedRows?.contains(row.index) == true, onRowNumberClick)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(layout: SheetLayout, frozen: IntRange, scroll: IntRange, horizontal: ScrollState) {
    val colors = KosmosTheme.colors
    Row(Modifier.background(colors.surfaceSunk)) {
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
    onCellLongPress: (String) -> Unit,
    selected: Boolean,
    onRowNumberClick: ((Int) -> Unit)?
) {
    val texts = remember(row) { row.cells.associate { it.column to it.text } }
    val merges = layout.mergesAt(row.index)
    // [WHY] 고른 행은 accentGlow — accentDim 은 다크 모드에서 옅어 고른 범위가 잘 안 보였다(0.36.0 실기기). FlowView 도 같다.
    Row(if (selected) Modifier.background(KosmosTheme.colors.brandContainer) else Modifier) {
        GridCell(
            (row.index + 1).toString(),
            ROW_NUMBER_WIDTH,
            header = true,
            onClick = onRowNumberClick?.let { click -> { click(row.index) } }
        )
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
            // [WHY] 병합 칸도 왼쪽부터 — 엑셀 기본 정렬이고, 넓게 병합한 제목(A1:T1)을 가운데에 두면 휴대폰 화면 밖에 그려져 빈 행으로 보였다(0.38.3 M4 실기기 제보).
            GridCell(text, width, onLongPress = onCellLongPress)
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
    onLongPress: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null
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
                when {
                    onClick != null -> Modifier.combinedClickable(onClick = onClick)
                    onLongPress != null && text.isNotEmpty() -> Modifier.combinedClickable(onClick = {}, onLongClick = { onLongPress(text) })
                    else -> Modifier
                }
            )
            .padding(horizontal = 6.dp),
        contentAlignment = when {
            header -> Alignment.Center
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
 * 열마다 내용에 맞는 너비 — 모든 칸([Fit.all])과 숫자 칸만([Fit.numeric]). 내용이 없으면 0.
 *
 * [WHY] 앞 [FIT_SAMPLE_ROWS] 행만 본다 — 큰 시트에서 전부 재면 그리기 전에 지연이 생기고, 앞부분을 먼저 그린 뒤(부분 시트) 끝까지
 * 읽은 시트로 바뀔 때 같은 표본이어야 열 너비가 튀지 않는다. 글자 수로 어림해 열마다 긴 후보 [FIT_CANDIDATES] 개만 실제로 잰다.
 */
private class Fit(val all: List<Dp>, val numeric: List<Dp>)

private fun fittedWidths(sheet: Sheet, columnCount: Int, measure: (String) -> Dp): Fit {
    val all = List(columnCount) { mutableListOf<String>() }
    val numeric = List(columnCount) { mutableListOf<String>() }
    // 여러 열에 걸친 병합 칸은 넓이를 합쳐 쓰므로 한 열의 너비를 정하는 데 넣지 않는다.
    val spanning = sheet.merges.filter { it.lastColumn > it.firstColumn }.map { it.firstRow to it.firstColumn }.toSet()
    sheet.rows.take(FIT_SAMPLE_ROWS).forEach { row ->
        row.cells.forEach { cell ->
            val text = cell.text.substringBefore('\n')
            if (cell.column < columnCount && text.isNotEmpty() && (row.index to cell.column) !in spanning) {
                all[cell.column] += text
                if (NUMERIC.matches(text)) numeric[cell.column] += text
            }
        }
    }
    fun widest(texts: List<String>): Dp = texts
        .sortedByDescending { t -> t.sumOf { ch -> if (ch.code < 128) 3 else 5 } }
        .take(FIT_CANDIDATES)
        .maxOfOrNull { measure(it) + CELL_CHROME } ?: 0.dp
    return Fit(all.map(::widest), numeric.map(::widest))
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
        /** @param measure 칸 글자 한 줄의 실제 너비. */
        fun of(sheet: Sheet, measure: (String) -> Dp): SheetLayout {
            val columnCount = sheet.columnCount.coerceIn(1, MAX_RENDER_COLUMNS)
            val fit = fittedWidths(sheet, columnCount, measure)
            // 엑셀 너비는 "기본 글꼴 숫자 몇 개" 단위다 — 우리 글꼴의 숫자 폭으로 환산한다(고정 7dp 는 HadaRound 숫자보다 좁았다).
            val digit = measure("0")
            val widths = List(columnCount) { c ->
                val excel = sheet.columnWidths[c]
                when {
                    // [WHY] 엑셀은 숫자를 자르지 않는다(###) — 정한 너비가 좁아도 숫자 칸은 다 보이게 넓힌다.
                    excel != null -> maxOf(digit * excel + CELL_CHROME, fit.numeric[c]).coerceIn(36.dp, 320.dp)
                    fit.all[c] == 0.dp -> DEFAULT_COLUMN_WIDTH
                    else -> fit.all[c].coerceIn(48.dp, 220.dp)
                }
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
private const val FIT_CANDIDATES = 3

/** 칸 좌우 안쪽 여백(6 + 6) + 오른쪽 선 + 여유. */
private val CELL_CHROME = 16.dp

/** 테스트가 격자를 찾는 태그. */
internal const val SHEET_GRID_TAG = "sheet_grid"


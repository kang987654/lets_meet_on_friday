package com.kosmos.app.domain.document

/**
 * [FlowDocument]
 * 워드(docx)·한글(hwpx) 문서를 **읽기 모드**로 다시 배치하기 위한 블록 목록 (0.35.0).
 *
 * 쪽 나눔 위치·글꼴·정밀 배치는 담지 않는다(계획서 사용자 결정 1) — 문단·제목·목록·표·이미지의 순서와 굵게/기울임/밑줄만.
 *
 * @property truncated 블록 상한([DocumentLimits.maxBlocks])에 닿아 앞부분만 담았다.
 */
data class FlowDocument(val blocks: List<FlowBlock>, val truncated: Boolean = false)

/** 문단 안의 같은 서식 글자 묶음. */
data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false)

sealed interface FlowBlock {
    /** @property level 1부터. */
    data class Heading(val level: Int, val spans: List<Span>) : FlowBlock

    data class Paragraph(val spans: List<Span>) : FlowBlock

    /** @property level 0부터(들여쓰기 단계). */
    data class ListItem(val level: Int, val spans: List<Span>) : FlowBlock

    data class Table(val rows: List<List<TableCell>>) : FlowBlock

    /** @property entryName zip 안의 이미지 경로 — 화면이 필요할 때 읽는다. */
    data class Image(val entryName: String) : FlowBlock

    data object PageBreak : FlowBlock
}

/**
 * 표의 칸 하나.
 *
 * @property colSpan 가로 병합 칸 수.
 * @property merged 세로 병합의 이어지는 칸(위 칸에 합쳐져 비어 보인다).
 */
data class TableCell(val blocks: List<FlowBlock>, val colSpan: Int = 1, val merged: Boolean = false)

/** 문단·표 칸의 순수 글자 — 채팅 보내기·테스트용. */
fun List<Span>.plainText(): String = joinToString("") { it.text }

/** 블록의 순수 글자(표는 칸을 탭, 행을 줄바꿈으로). */
fun FlowBlock.plainText(): String = when (this) {
    is FlowBlock.Heading -> spans.plainText()
    is FlowBlock.Paragraph -> spans.plainText()
    is FlowBlock.ListItem -> spans.plainText()
    is FlowBlock.Table -> rows.joinToString("\n") { row -> row.joinToString("\t") { cell -> cell.blocks.joinToString(" ") { it.plainText() } } }
    is FlowBlock.Image, FlowBlock.PageBreak -> ""
}

/**
 * 문단 하나를 만드는 동안 글자를 모은다 — 같은 서식이 이어지면 한 [Span] 으로 합친다.
 */
internal class SpanCollector {
    private val spans = mutableListOf<Span>()

    fun add(text: String, bold: Boolean, italic: Boolean, underline: Boolean) {
        if (text.isEmpty()) return
        val last = spans.lastOrNull()
        if (last != null && last.bold == bold && last.italic == italic && last.underline == underline) {
            spans[spans.lastIndex] = last.copy(text = last.text + text)
        } else {
            spans += Span(text, bold, italic, underline)
        }
    }

    fun isBlank(): Boolean = spans.all { it.text.isBlank() }

    fun take(): List<Span> = spans.toList().also { spans.clear() }
}

/**
 * 블록을 받는 자리 — 본문 또는 표 칸. 표는 칸 안에 표가 또 들어갈 수 있어 스택으로 쌓는다.
 */
internal class BlockSink(private val limits: DocumentLimits) {
    private val root = mutableListOf<FlowBlock>()
    private val tables = ArrayDeque<TableBuilder>()
    var truncated = false
        private set
    private var count = 0

    fun add(block: FlowBlock) {
        if (++count > limits.maxBlocks) {
            truncated = true
            throw StopParsing()
        }
        (tables.lastOrNull()?.currentCell ?: root) += block
    }

    fun beginTable() = tables.addLast(TableBuilder())
    fun beginRow() = tables.lastOrNull()?.beginRow()
    fun beginCell(colSpan: Int, merged: Boolean) = tables.lastOrNull()?.beginCell(colSpan, merged)
    fun endCell() = tables.lastOrNull()?.endCell()

    /** 칸 속성은 칸 내용보다 먼저 오지만 칸 시작 뒤에 읽힌다(docx tcPr) — 이미 연 칸의 병합 정보를 고친다. */
    fun updateCell(colSpan: Int, merged: Boolean, rowSpan: Int = 1) = tables.lastOrNull()?.updateCell(colSpan, merged, rowSpan)

    fun endTable() {
        val table = tables.removeLastOrNull() ?: return
        val rows = table.finish()
        if (rows.isNotEmpty()) add(FlowBlock.Table(rows))
    }

    fun finish(): FlowDocument {
        while (tables.isNotEmpty()) endTable() // 상한으로 중간에 끊긴 표도 읽은 데까지 보인다
        return FlowDocument(root.toList(), truncated)
    }

    private class TableBuilder {
        // 칸마다 세로 병합 수를 따로 둔다 — hwpx 는 세로로 합쳐진 아래 칸을 아예 생략하므로 끝에서 자리를 채운다.
        private val rows = mutableListOf<List<Pair<TableCell, Int>>>()
        private var row: MutableList<Pair<TableCell, Int>>? = null
        private var cellBlocks: MutableList<FlowBlock>? = null
        private var cellSpan = 1
        private var cellMerged = false
        private var cellRowSpan = 1

        val currentCell: MutableList<FlowBlock>?
            get() = cellBlocks

        fun beginRow() {
            row?.let { if (it.isNotEmpty()) rows += it.toList() }
            row = mutableListOf()
        }

        fun beginCell(colSpan: Int, merged: Boolean) {
            if (row == null) row = mutableListOf()
            cellBlocks = mutableListOf()
            updateCell(colSpan, merged)
        }

        fun updateCell(colSpan: Int, merged: Boolean, rowSpan: Int = 1) {
            cellSpan = colSpan.coerceIn(1, 64)
            cellMerged = merged
            cellRowSpan = rowSpan.coerceIn(1, 1000)
        }

        fun endCell() {
            val blocks = cellBlocks ?: return
            row?.add(TableCell(blocks.toList(), cellSpan, cellMerged) to cellRowSpan)
            cellBlocks = null
        }

        fun finish(): List<List<TableCell>> {
            endCell()
            row?.let { if (it.isNotEmpty()) rows += it.toList() }
            row = null
            if (rows.all { r -> r.all { it.second == 1 } }) return rows.map { r -> r.map { it.first } }
            // 위 행의 세로 병합이 덮는 자리: (행, 열) → 덮는 칸의 가로 폭
            val covered = mutableMapOf<Pair<Int, Int>, Int>()
            return rows.mapIndexed { r, cells ->
                val out = mutableListOf<TableCell>()
                var c = 0
                fun fillCovered() {
                    while (true) {
                        val span = covered[r to c] ?: break
                        out += TableCell(emptyList(), span, merged = true)
                        c += span
                    }
                }
                cells.forEach { (cell, rowSpan) ->
                    fillCovered()
                    out += cell
                    for (dr in 1 until rowSpan) covered[(r + dr) to c] = cell.colSpan
                    c += cell.colSpan
                }
                fillCovered()
                out.toList()
            }
        }
    }
}

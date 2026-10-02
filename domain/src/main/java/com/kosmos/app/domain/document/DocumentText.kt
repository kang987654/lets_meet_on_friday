package com.kosmos.app.domain.document

/**
 * 채팅에 보낼 문서 글자 — 상한 안으로 자른 결과.
 *
 * @property text 보낼 글자(상한 이하).
 * @property fullLength 자르기 전 길이 — 화면이 "n / 300자"와 "앞부분만 보내요"를 판단한다.
 */
data class ClippedText(val text: String, val fullLength: Int) {
    val truncated: Boolean get() = fullLength > text.length
}

/**
 * [DocumentText]
 * 문서의 고른 범위를 채팅 첨부 글자로 바꿉니다 (0.35.0) — 모델 입력이 되는 형식이라 exp46 실측으로 고른 형식만 쓴다.
 *
 * ### Key Flow
 * 1. 시트: 고른 행 앞에 머리 행(첫 행)을 붙이고([withHeader]) [TABLE_FORMAT] 으로 줄마다 만든다.
 * 2. 읽기 모드: 문단은 줄바꿈으로, 표는 같은 표 형식으로.
 * 3. [cap] 을 넘으면 **행·블록 경계에서** 자른다 — 행 중간을 자르면 칸 값이 잘려 틀린 숫자를 모델에 준다.
 *    첫 단위 하나가 이미 넘으면 그것만 글자 단위로 자른다(빈 첨부보다 낫다).
 */
object DocumentText {

    /** 표 형식 후보 — exp46 비교 대상. */
    enum class TableFormat { MARKDOWN, TSV, KEY_VALUE }

    /**
     * 채팅 첨부에 쓰는 표 형식 — exp46c(0.35.0 M0) 판정: "열이름: 값" 19/20, 탭 17/20, 마크다운 16/20(문서 턴 리마인더 조건, 표 5종×질문 4).
     *
     * [WHY] 300자에 드는 행은 1행 적지만(9.8 대 10.8) 칸마다 열 이름이 붙어 합계·세기 질문에서 어느 열인지 덜 헷갈렸다
     * (합계 3문항 중 2 대 0). 행이 적어 더할 수가 적은 몫도 섞여 있다 — 판정 규칙(정답 수 우선)대로 골랐다.
     */
    val TABLE_FORMAT = TableFormat.KEY_VALUE

    /**
     * 시트의 [rows] 범위(행 번호, 0부터)를 글자로. 머리 행이 범위 밖이면 앞에 붙인다 — 열 이름 없이는 "금액이 얼마야"를 답할 수 없다.
     */
    fun sheet(sheet: Sheet, rows: IntRange, cap: Int, format: TableFormat = TABLE_FORMAT): ClippedText {
        val picked = sheet.rows.filter { it.index in rows }
        val header = sheet.rows.firstOrNull()?.takeIf { it.index !in rows && withHeader(sheet) }
        val width = sheet.columnCount.coerceAtLeast(1)
        val grid = (listOfNotNull(header) + picked).map { row -> List(width) { c -> row.cells.firstOrNull { it.column == c }?.text.orEmpty() } }
        return clip(table(grid, format), cap)
    }

    /** 읽기 모드 블록들(고른 순서)을 글자로. */
    fun flow(blocks: List<FlowBlock>, cap: Int, format: TableFormat = TABLE_FORMAT): ClippedText {
        val units = blocks.flatMap { block ->
            when (block) {
                is FlowBlock.Table -> table(block.rows.map { row -> row.map { cell -> cell.blocks.joinToString(" ") { it.plainText() } } }, format)
                is FlowBlock.ListItem -> listOf("  ".repeat(block.level) + "- " + block.spans.plainText())
                is FlowBlock.Image, FlowBlock.PageBreak -> emptyList()
                else -> listOf(block.plainText())
            }
        }.filter { it.isNotBlank() }
        return clip(units, cap)
    }

    /** 글자 하나(PDF 페이지 글자 등)를 줄 단위로 자른다. */
    fun plain(text: String, cap: Int): ClippedText = clip(text.lines().filter { it.isNotBlank() }, cap)

    // 머리 행을 붙일지 — 틀 고정이 있으면 그 행이 머리이고, 없어도 첫 행을 머리로 본다(가계부·명단 대부분이 그렇다).
    private fun withHeader(sheet: Sheet): Boolean = sheet.rows.isNotEmpty()

    private fun table(grid: List<List<String>>, format: TableFormat): List<String> {
        if (grid.isEmpty()) return emptyList()
        val clean = grid.map { row -> row.map { it.replace('\n', ' ').replace('\t', ' ').replace("|", "/").trim() } }
        return when (format) {
            TableFormat.MARKDOWN -> {
                val head = clean.first()
                listOf("| " + head.joinToString(" | ") + " |", "|" + "---|".repeat(head.size)) +
                    clean.drop(1).map { "| " + it.joinToString(" | ") + " |" }
            }
            TableFormat.TSV -> clean.map { it.joinToString("\t") }
            TableFormat.KEY_VALUE -> {
                val head = clean.first()
                clean.drop(1).map { row -> row.indices.filter { row[it].isNotEmpty() }.joinToString(", ") { "${head.getOrElse(it) { "" }}: ${row[it]}" } }
            }
        }
    }

    /** 줄 단위로 상한까지 담는다. 마크다운 구분선 바로 뒤에서 끊겨 빈 표가 되는 것은 허용한다(머리만이라도 보인다). */
    private fun clip(lines: List<String>, cap: Int): ClippedText {
        val full = lines.joinToString("\n")
        if (full.length <= cap) return ClippedText(full, full.length)
        val out = StringBuilder()
        for (line in lines) {
            val next = if (out.isEmpty()) line else "\n$line"
            if (out.length + next.length > cap) break
            out.append(next)
        }
        if (out.isEmpty()) out.append(lines.firstOrNull().orEmpty().take(cap))
        return ClippedText(out.toString(), full.length)
    }
}

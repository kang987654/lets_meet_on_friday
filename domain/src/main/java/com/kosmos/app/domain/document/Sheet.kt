package com.kosmos.app.domain.document

/**
 * [Sheet]
 * 뷰어가 그리는 표 한 장 — xlsx 시트와 csv 가 같은 모양으로 나온다. 빈 칸은 담지 않는 희소 표현이다.
 *
 * @property rows 내용이 있는 행만, 행 번호 오름차순.
 * @property columnCount 가장 오른쪽 열 + 1(머리글 A, B, … 를 그릴 폭).
 * @property merges 병합 범위 — 화면은 첫 칸에 내용을 두고 나머지를 비운다.
 * @property frozenRows 틀 고정 행 수.
 * @property frozenColumns 틀 고정 열 수.
 * @property columnWidths 열 번호 → 너비(엑셀 문자 단위). 없으면 기본 너비.
 * @property truncated 상한에 닿아 앞부분만 담았다.
 * @property complete false 면 아직 읽는 중인 앞부분이다(큰 시트의 첫 화면용) — 끝까지 읽으면 같은 시트가 다시 온다.
 */
data class Sheet(
    val name: String,
    val rows: List<SheetRow>,
    val columnCount: Int,
    val merges: List<CellRange> = emptyList(),
    val frozenRows: Int = 0,
    val frozenColumns: Int = 0,
    val columnWidths: Map<Int, Float> = emptyMap(),
    val truncated: Boolean = false,
    val complete: Boolean = true
)

/** @property index 0부터 세는 행 번호(엑셀 표기는 +1). */
data class SheetRow(val index: Int, val cells: List<SheetCell>)

/** @property column 0부터 세는 열 번호. */
data class SheetCell(val column: Int, val text: String)

/** 0부터 세는 닫힌 범위. */
data class CellRange(val firstRow: Int, val firstColumn: Int, val lastRow: Int, val lastColumn: Int)

/** 엑셀 셀 참조 도우미. */
object CellRef {
    /** "AB12" → 열 27(0부터). 글자가 없으면 null. */
    fun column(ref: String): Int? {
        var column = 0
        var letters = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z' && ch !in 'a'..'z') break
            column = column * 26 + (ch.uppercaseChar() - 'A' + 1)
            letters++
        }
        return if (letters == 0) null else column - 1
    }

    /** "AB12" → 행 11(0부터). 숫자가 없으면 null. */
    fun row(ref: String): Int? = ref.dropWhile { it.isLetter() }.toIntOrNull()?.minus(1)

    /** 열 번호(0부터) → "A", "Z", "AA". */
    fun columnName(column: Int): String {
        val sb = StringBuilder()
        var n = column + 1
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** "A1:C3" → 범위. 형식이 틀리면 null. 한 칸("B2")도 받는다. */
    fun range(ref: String): CellRange? {
        val parts = ref.split(':')
        val start = parts.first()
        val end = parts.getOrElse(1) { start }
        val r1 = row(start) ?: return null
        val c1 = column(start) ?: return null
        val r2 = row(end) ?: return null
        val c2 = column(end) ?: return null
        return CellRange(minOf(r1, r2), minOf(c1, c2), maxOf(r1, r2), maxOf(c1, c2))
    }
}

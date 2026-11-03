package com.kosmos.app.domain.library

import com.kosmos.app.domain.document.DocumentText
import com.kosmos.app.domain.document.FlowBlock
import com.kosmos.app.domain.document.Sheet
import com.kosmos.app.domain.document.plainText

/**
 * 문서 보관함의 검색 단위 한 조각.
 *
 * @property index 문서 안 순서(0부터).
 * @property sectionPath 문서명부터 이 조각까지의 위치 — 예 `민법 > 제1편 총칙 > 제7장 소멸시효 > 제162조(채권, 재산권의 소멸시효)`.
 *   한 단위가 상한을 넘어 여러 조각이 되면 끝에 ` (2/3)` 가 붙는다.
 * @property article 조문 번호(`"162"`, `"5의2"`) — 조문이 아닌 조각은 null. 질문의 "제162조"를 색인이 아니라 이 열로 찾는다(exp74).
 * @property page PDF 쪽 번호(1부터) — 쪽이 없는 형식은 null.
 * @property text 본문(머리말 제외).
 */
data class LibraryChunk(
    val index: Int,
    val sectionPath: String,
    val article: String?,
    val page: Int?,
    val text: String
)

/**
 * 보관함 크기 상한 — 넘으면 등록 전에 거절한다(0.41.0, exp74: 64만 자 ≈ 색인 4.7MB + 조각 글자 1.8MB).
 *
 * [WHY] 문서당 200만 자는 두꺼운 전공서적 한 권(50만~100만 자)의 두 배 여유, 전체 1,000만 자는 DB 약 100MB 다 — 백업 ZIP·기기 저장 공간이
 * 감당할 선에서 끊는다. 색인 시간·질의 시간 실측(M4)에 따라 조정한다.
 */
object LibraryLimits {
    const val MAX_DOCUMENT_CHARS = 2_000_000
    const val MAX_TOTAL_CHARS = 10_000_000
}

/** 조각 나누기 입력 한 줄 — 파서 결과를 형식과 무관하게 이 세 가지로 바꾼 뒤 나눈다. */
sealed interface LibraryLine {
    /** 제목 — [level] 이 작을수록 상위(같거나 낮은 수준의 앞 제목을 대신한다). */
    data class Heading(val level: Int, val text: String) : LibraryLine

    data class Body(val text: String) : LibraryLine

    /** PDF 쪽 시작 — 조각은 쪽을 넘지 않는다(출처의 "몇 쪽"이 정확하게). */
    data class Page(val number: Int) : LibraryLine
}

/**
 * [LibraryChunker]
 * 문서 보관함에 등록한 문서를 검색 조각으로 나눕니다 (0.41.0 M1) — 모델을 쓰지 않는 순수 함수.
 *
 * ### Architecture Context
 * - **Layer**: Domain (Library) — 입력은 기존 파서 결과(`FlowDocument` 블록·`Sheet`·PDF 쪽 글자·평문), 출력 [LibraryChunk].
 * - **Consumers**: 앱의 색인기(`LibraryIndexer`)
 *
 * ### Key Flow
 * 1. 형식별 변환(`fromFlow`·`fromSheets`·`fromPages`·`fromPlain`)으로 [LibraryLine] 목록을 만든다.
 * 2. 제목·법령식 편장절관 줄은 위치 경로를 갱신하고, `제N조(제목)` 으로 시작하는 줄은 새 조문 단위를 연다.
 * 3. 단위(조문·제목 아래 본문·쪽) 본문을 [maxChars] 이하 조각으로 줄 경계에서 나눈다 — 한 줄이 넘으면 그 줄만 글자로 자른다.
 *
 * [WHY] 조(條) 하나를 한 단위로 둔다 — 출처를 "민법 제162조"로 정확히 보이고, 조문 번호 질문을 구조 열로 찾기 위해서다.
 * 상한 300/500/800 은 회수율 차이가 없었고 500 에서 갈리는 조가 4.7% 였다(exp74, 공개 법령 64만 자).
 */
object LibraryChunker {

    /** 조각 본문 상한(글자) — exp74 결정. */
    const val MAX_CHARS = 500

    private val LAW_HEADING = Regex("""^제\d+(편|장|절|관)\s""")
    private val ARTICLE = Regex("""^제(\d+)조(?:의(\d+))?\s*\(([^)]*)\)""")
    private val LAW_LEVEL = mapOf("편" to 1, "장" to 2, "절" to 3, "관" to 4)

    // [WHY] 개정 이력 표지(<개정 2011. 3. 7.>, [전문개정 …])는 검색어와 겹치는 숫자·낱말만 더하고 뜻이 없다 — 조각에서 뺀다.
    private val NOTE = Regex("""<(?:개정|신설|시행일)[^>]*>|\[(?:본조신설|전문개정|제목개정|종전|본조제목개정|시행일)[^\]]*]""")

    fun fromFlow(blocks: List<FlowBlock>): List<LibraryLine> = blocks.flatMap { block ->
        when (block) {
            is FlowBlock.Heading -> listOf(LibraryLine.Heading(block.level, block.spans.plainText()))
            is FlowBlock.ListItem -> listOf(LibraryLine.Body("  ".repeat(block.level) + "- " + block.spans.plainText()))
            is FlowBlock.Table -> DocumentText.tableLines(block).map { LibraryLine.Body(it) }
            is FlowBlock.Image, FlowBlock.PageBreak -> emptyList()
            else -> listOf(LibraryLine.Body(block.plainText()))
        }
    }

    /** 시트마다 `시트: 이름` 제목 + "열머리: 값" 행 — 행 중간을 자르지 않는다. */
    fun fromSheets(sheets: List<Sheet>): List<LibraryLine> = sheets.flatMap { sheet ->
        listOf(LibraryLine.Heading(1, "시트: ${sheet.name}")) + DocumentText.sheetLines(sheet).map { LibraryLine.Body(it) }
    }

    /** 쪽 글자 목록(0번이 1쪽) — 글자를 못 뽑은 쪽(null·빈칸)은 건너뛴다. */
    fun fromPages(pages: List<String?>): List<LibraryLine> = pages.flatMapIndexed { i, text ->
        val lines = text.orEmpty().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) emptyList() else listOf(LibraryLine.Page(i + 1)) + lines.map { LibraryLine.Body(it) }
    }

    fun fromPlain(text: String): List<LibraryLine> = text.lines().filter { it.isNotBlank() }.map { LibraryLine.Body(it) }

    fun chunk(documentName: String, lines: List<LibraryLine>, maxChars: Int = MAX_CHARS): List<LibraryChunk> {
        val out = mutableListOf<LibraryChunk>()
        val headings = ArrayDeque<Pair<Int, String>>()
        var page: Int? = null
        var unitTitle: String? = null
        var unitArticle: String? = null
        val parts = mutableListOf<String>()
        val buf = StringBuilder()

        fun cut() {
            if (buf.isNotEmpty()) parts += buf.toString()
            buf.setLength(0)
        }

        fun endUnit() {
            cut()
            val base = (listOf(documentName) + headings.map { it.second } + listOfNotNull(unitTitle)).joinToString(" > ")
            parts.forEachIndexed { i, text ->
                val path = if (parts.size > 1) "$base (${i + 1}/${parts.size})" else base
                out += LibraryChunk(out.size, path, unitArticle, page, text)
            }
            parts.clear()
            unitTitle = null
            unitArticle = null
        }

        fun heading(level: Int, text: String) {
            endUnit()
            while (headings.isNotEmpty() && headings.last().first >= level) headings.removeLast()
            headings.addLast(level to text)
        }

        fun body(raw: String) {
            val line = NOTE.replace(raw, "").trim()
            if (line.isEmpty()) return
            var rest = line
            while (rest.isNotEmpty()) {
                val joined = if (buf.isEmpty()) rest.length else buf.length + 1 + rest.length
                if (joined <= maxChars) {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(rest)
                    return
                }
                if (buf.isNotEmpty()) {
                    cut()
                    continue
                }
                // [WHY] 한 줄이 상한보다 길다 — 줄 경계가 없으니 글자 단위로 자른다(빈 조각보다 낫다).
                parts += rest.take(maxChars)
                rest = rest.drop(maxChars)
            }
        }

        for (line in lines) {
            when (line) {
                is LibraryLine.Page -> {
                    endUnit()
                    page = line.number
                }
                is LibraryLine.Heading -> heading(line.level, line.text.trim())
                is LibraryLine.Body -> {
                    val text = line.text.trim()
                    val law = LAW_HEADING.find(text)
                    val article = ARTICLE.find(text)
                    when {
                        law != null -> heading(LAW_LEVEL.getValue(law.groupValues[1]), text)
                        article != null -> {
                            endUnit()
                            unitTitle = article.value
                            unitArticle = article.groupValues[1] + article.groupValues[2].takeIf { it.isNotEmpty() }?.let { "의$it" }.orEmpty()
                            body(text)
                        }
                        else -> body(text)
                    }
                }
            }
        }
        endUnit()
        return out
    }
}

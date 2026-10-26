package com.kosmos.app.domain.document

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * [XlsxReader]
 * xlsx(Office Open XML 스프레드시트)를 보기 전용으로 읽습니다 — 외부 라이브러리 없이 zip + SAX (0.34.0).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Document) — 순수 JVM, 안드로이드 의존 없음(JVM 테스트가 그대로 돈다)
 * - **Dependencies**: [ZipSource], [XlsxNumberFormat]
 *
 * ### Key Flow
 * 1. [open]: `_rels/.rels` → 통합 문서 경로 → `workbook.xml`(시트 이름·순서·1904 기준) + 관계 파일(시트 경로) +
 *    `sharedStrings.xml` + `styles.xml`(셀 서식 → 숫자 서식·[CellStyle]) + 테마(`theme1.xml`, 테마 색).
 * 2. [readSheet]: 시트 XML 을 스트리밍해 내용 있는 칸만 담는다. 수식은 **저장된 마지막 계산값**만 보인다(재계산 없음).
 * 3. 행·셀 상한에 닿으면 앞부분만 담고 `truncated`.
 *
 * [WHY] 시트는 탭을 누를 때 하나씩 읽는다 — 첫 화면에 필요 없는 시트까지 읽으면 큰 통합 문서의 첫 표시가 늦어진다.
 * 차트·이미지·조건부 서식·숨김 행은 다루지 않는다(보기 전용 범위, 계획서 결정 3). 셀 서식은 배경·글자색·굵기·가로 정렬만(0.38.7 결정 1).
 */
class XlsxReader(zip: ZipSource, private val limits: DocumentLimits = DocumentLimits()) {

    private val pkg = XmlPackage(zip, limits)

    /** 열린 통합 문서 — 시트 이름 목록과, 시트를 읽는 데 필요한 공유 표들. */
    class Workbook internal constructor(
        val sheetNames: List<String>,
        internal val sheetPaths: List<String?>,
        internal val sharedStrings: List<String>,
        internal val cellFormats: List<XlsxNumberFormat.Spec>,
        internal val date1904: Boolean,
        internal val cellStyles: List<CellStyle> = emptyList()
    )

    fun open(): DocumentResult<Workbook> = readDocument {
        val workbookPath = pkg.mainPart(default = "xl/workbook.xml")
        val workbookDir = workbookPath.substringBeforeLast('/', missingDelimiterValue = "")
        val workbook = WorkbookHandler().also { pkg.parse(workbookPath, it, required = true) }
        val rels = pkg.relationshipsOf(workbookPath)
        val strings = SharedStringsHandler().also {
            pkg.parse(rels.byType("/sharedStrings") ?: resolvePartPath(workbookDir, "sharedStrings.xml"), it, required = false)
        }
        val styles = StylesHandler().also {
            pkg.parse(rels.byType("/styles") ?: resolvePartPath(workbookDir, "styles.xml"), it, required = false)
        }
        val theme = ThemeHandler().also {
            pkg.parse(rels.byType("/theme") ?: resolvePartPath(workbookDir, "theme/theme1.xml"), it, required = false)
        }
        if (workbook.sheets.isEmpty()) throw DocumentException(DocumentError.CORRUPT)
        Workbook(
            sheetNames = workbook.sheets.map { it.first },
            sheetPaths = workbook.sheets.map { (_, relId) -> rels.target(relId) },
            sharedStrings = strings.items,
            cellFormats = styles.cellFormatIds.map { id -> XlsxNumberFormat.spec(id, styles.customFormats[id]) },
            date1904 = workbook.date1904,
            cellStyles = styles.cellStyles(theme.colors())
        )
    }

    /**
     * @param onFirstRows 앞 [FIRST_BATCH_ROWS] 행을 읽은 순간 한 번 불린다(`complete = false` 인 부분 시트) — 큰 시트도 첫 화면을
     * 바로 그리게 한다. 시트가 그보다 짧으면 불리지 않는다.
     */
    fun readSheet(workbook: Workbook, index: Int, onFirstRows: (Sheet) -> Unit = {}): DocumentResult<Sheet> = readDocument {
        val name = workbook.sheetNames.getOrNull(index) ?: throw DocumentException(DocumentError.CORRUPT)
        val path = workbook.sheetPaths.getOrNull(index) ?: throw DocumentException(DocumentError.CORRUPT)
        val handler = SheetHandler(workbook, limits) { rows -> onFirstRows(rows.toSheet(name, complete = false)) }
        pkg.parse(path, handler, required = true)
        handler.toSheet(name)
    }

    internal companion object {
        /** 부분 시트를 먼저 내보내는 행 수 — 휴대폰 한 화면(20~30행)의 몇 배. */
        const val FIRST_BATCH_ROWS = 200

        /** 엑셀이 제어 문자를 `_x000D_` 처럼 이스케이프한 것을 되돌린다. */
        fun unescape(text: String): String {
            if (!text.contains("_x")) return text
            return Regex("_x([0-9A-Fa-f]{4})_").replace(text) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        }
    }

    // --- SAX 핸들러들 ---

    private class WorkbookHandler : DefaultHandler() {
        val sheets = mutableListOf<Pair<String, String>>() // 이름, 관계 id
        var date1904 = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "workbookPr" -> date1904 = attributes.getValue("date1904").let { it == "1" || it == "true" }
                "sheet" -> {
                    val name = attributes.getValue("name") ?: return
                    val relId = (0 until attributes.length)
                        .firstOrNull { attributes.getLocalName(it) == "id" }
                        ?.let { attributes.getValue(it) } ?: return
                    sheets += name to relId
                }
            }
        }
    }

    private class SharedStringsHandler : DefaultHandler() {
        val items = mutableListOf<String>()
        private val current = StringBuilder()
        private var inText = false
        private var phoneticDepth = 0

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "si" -> current.setLength(0)
                "rPh" -> phoneticDepth++ // 일본어 후리가나 등 — 본문이 아니다
                "t" -> inText = phoneticDepth == 0
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inText) current.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            when (localName) {
                "t" -> inText = false
                "rPh" -> phoneticDepth--
                "si" -> items += unescape(current.toString())
            }
        }
    }

    /** 테마의 색 구성(clrScheme) — 원소 이름 → 색. 시스템 색(sysClr)은 마지막 값(lastClr)을 쓴다. */
    private class ThemeHandler : DefaultHandler() {
        private val byName = mutableMapOf<String, Int>()
        private var inScheme = false
        private var current: String? = null

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when {
                localName == "clrScheme" -> inScheme = true
                inScheme && localName in XlsxColor.THEME_ORDER -> current = localName
                inScheme && localName == "srgbClr" -> current?.let { n -> XlsxColor.parseRgb(attributes.getValue("val"))?.let { byName[n] = it } }
                inScheme && localName == "sysClr" -> current?.let { n -> XlsxColor.parseRgb(attributes.getValue("lastClr"))?.let { byName[n] = it } }
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            if (localName == "clrScheme") inScheme = false
            if (localName == current) current = null
        }

        /** 테마 색 번호 순서([XlsxColor.THEME_ORDER]) — 없는 원소는 엑셀 기본(흰 바탕·검은 글자)으로 채운다. */
        fun colors(): List<Int> = XlsxColor.THEME_ORDER.mapIndexed { i, name ->
            byName[name] ?: if (i % 2 == 0) WHITE else BLACK
        }

        private companion object {
            const val WHITE = 0xFFFFFFFF.toInt()
            const val BLACK = 0xFF000000.toInt()
        }
    }

    private class StylesHandler : DefaultHandler() {
        val customFormats = mutableMapOf<Int, String>()
        val cellFormatIds = mutableListOf<Int>()

        /** 원본 색 표기(아직 테마로 풀지 않은 것). */
        private class ColorRef(val rgb: String?, val theme: Int?, val tint: Double?, val indexed: Int?)
        private class Font(var bold: Boolean = false, var color: ColorRef? = null)
        private class Xf(val fontId: Int, val fillId: Int, var horizontal: String? = null)

        private val fonts = mutableListOf<Font>()
        private val fills = mutableListOf<ColorRef?>()
        private val xfs = mutableListOf<Xf>()
        private var inCellXfs = false
        private var inFonts = false
        private var inFills = false
        private var solid = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "numFmt" -> {
                    val id = attributes.getValue("numFmtId")?.toIntOrNull() ?: return
                    attributes.getValue("formatCode")?.let { customFormats[id] = it }
                }
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) {
                    cellFormatIds += attributes.getValue("numFmtId")?.toIntOrNull() ?: 0
                    xfs += Xf(attributes.getValue("fontId")?.toIntOrNull() ?: 0, attributes.getValue("fillId")?.toIntOrNull() ?: 0)
                }
                "alignment" -> if (inCellXfs) xfs.lastOrNull()?.horizontal = attributes.getValue("horizontal")
                // [WHY] 차등 서식(dxfs)·표 스타일 안의 font/fill 은 셀 서식 번호와 무관하다 — fonts·fills 목록 안의 것만 센다.
                "fonts" -> inFonts = true
                "font" -> if (inFonts) fonts += Font()
                "b" -> if (inFonts) fonts.lastOrNull()?.bold = attributes.getValue("val").let { it == null || it == "1" || it == "true" }
                "color" -> if (inFonts) fonts.lastOrNull()?.color = refOf(attributes)
                "fills" -> inFills = true
                "fill" -> if (inFills) fills += null
                "patternFill" -> if (inFills) solid = attributes.getValue("patternType") == "solid"
                "fgColor" -> if (inFills && solid && fills.isNotEmpty()) fills[fills.lastIndex] = refOf(attributes)
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            when (localName) {
                "cellXfs" -> inCellXfs = false
                "fonts" -> inFonts = false
                "fills" -> inFills = false
                "patternFill" -> solid = false
            }
        }

        private fun refOf(a: Attributes): ColorRef? {
            if (a.getValue("auto").let { it == "1" || it == "true" }) return null
            return ColorRef(a.getValue("rgb"), a.getValue("theme")?.toIntOrNull(), a.getValue("tint")?.toDoubleOrNull(), a.getValue("indexed")?.toIntOrNull())
        }

        private fun ColorRef.resolve(theme: List<Int>): Int? = XlsxColor.resolve(rgb, this.theme, tint, indexed, theme)

        /**
         * 셀 서식 번호(cellXfs 순서) → [CellStyle].
         *
         * [WHY] 기본 글꼴(0번)의 색과 같은 글자색은 null 로 둔다 — 원본의 "검은 글자"를 그대로 칠하면 다크 모드에서 글자가 사라진다.
         * 다크 변환은 화면이 하지만, 서식 없는 글자는 처음부터 테마 글자색을 따르게 한다.
         */
        fun cellStyles(theme: List<Int>): List<CellStyle> {
            val defaultColor = fonts.firstOrNull()?.color?.resolve(theme)
            return xfs.map { xf ->
                val font = fonts.getOrNull(xf.fontId)
                CellStyle(
                    fill = fills.getOrNull(xf.fillId)?.resolve(theme),
                    fontColor = font?.color?.resolve(theme)?.takeIf { it != defaultColor },
                    bold = font?.bold == true,
                    align = when (xf.horizontal) {
                        "left", "start" -> CellStyle.Align.LEFT
                        "center", "centerContinuous" -> CellStyle.Align.CENTER
                        "right", "end" -> CellStyle.Align.RIGHT
                        else -> null
                    }
                )
            }
        }
    }

    private class SheetHandler(
        private val workbook: Workbook,
        private val limits: DocumentLimits,
        private val onFirstRows: (SheetHandler) -> Unit
    ) : DefaultHandler() {
        private val rows = mutableListOf<SheetRow>()
        private val merges = mutableListOf<CellRange>()
        private val widths = mutableMapOf<Int, Float>()
        private var frozenRows = 0
        private var frozenColumns = 0
        private var truncated = false
        private var maxColumn = -1
        private var cellCount = 0

        private var rowIndex = -1
        private var rowCells = mutableListOf<SheetCell>()
        private var column = -1
        private var type: String? = null
        private var style = 0
        private val value = StringBuilder()
        private val inline = StringBuilder()
        private var inValue = false
        private var inInlineText = false
        private var inInline = false
        private var phoneticDepth = 0

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "pane" -> {
                    val state = attributes.getValue("state")
                    if (state == "frozen" || state == "frozenSplit") {
                        frozenColumns = attributes.getValue("xSplit")?.toDoubleOrNull()?.toInt() ?: 0
                        frozenRows = attributes.getValue("ySplit")?.toDoubleOrNull()?.toInt() ?: 0
                    }
                }
                "col" -> {
                    val min = attributes.getValue("min")?.toIntOrNull() ?: return
                    val max = attributes.getValue("max")?.toIntOrNull() ?: return
                    val width = attributes.getValue("width")?.toFloatOrNull() ?: return
                    // [WHY] "A:XFD 전체 열" 같은 범위가 흔하다 — 화면에 쓸 만큼만 펼친다.
                    for (c in min..minOf(max, min + MAX_WIDTH_COLUMNS)) widths[c - 1] = width
                }
                "row" -> {
                    if (rows.size >= limits.maxRows) {
                        truncated = true
                        throw StopParsing()
                    }
                    rowIndex = attributes.getValue("r")?.toIntOrNull()?.minus(1) ?: (rowIndex + 1)
                    rowCells = mutableListOf()
                    column = -1
                }
                "c" -> {
                    column = attributes.getValue("r")?.let { CellRef.column(it) } ?: (column + 1)
                    type = attributes.getValue("t")
                    style = attributes.getValue("s")?.toIntOrNull() ?: 0
                    value.setLength(0)
                    inline.setLength(0)
                }
                "v" -> inValue = true
                "is" -> inInline = true
                "rPh" -> phoneticDepth++
                "t" -> inInlineText = inInline && phoneticDepth == 0
                "mergeCell" -> attributes.getValue("ref")?.let { CellRef.range(it) }?.let { merges += it }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            when {
                inValue -> value.append(ch, start, length)
                inInlineText -> inline.append(ch, start, length)
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            when (localName) {
                "v" -> inValue = false
                "t" -> inInlineText = false
                "is" -> inInline = false
                "rPh" -> phoneticDepth--
                "c" -> {
                    val text = cellText()
                    val cellStyle = workbook.cellStyles.getOrNull(style)
                    // [WHY] 빈 칸도 배경색이 있으면 담는다 — 칠한 머리 줄이 글자 있는 칸만 칠해져 끊겨 보였다(0.38.7 M0).
                    if (text.isNotEmpty() || cellStyle?.fill != null) {
                        if (++cellCount > limits.maxCells) {
                            truncated = true
                            flushRow()
                            throw StopParsing()
                        }
                        rowCells += SheetCell(column, text, if (cellStyle == null || cellStyle.isPlain) SheetCell.NO_STYLE else style)
                        if (text.isNotEmpty() && column > maxColumn) maxColumn = column
                    }
                }
                "row" -> flushRow()
            }
        }

        private fun flushRow() {
            if (rowCells.isNotEmpty()) {
                rows += SheetRow(rowIndex, rowCells.toList())
                if (rows.size == FIRST_BATCH_ROWS) onFirstRows(this)
            }
            rowCells = mutableListOf()
        }

        private fun cellText(): String {
            val raw = value.toString()
            return when (type) {
                "s" -> raw.trim().toIntOrNull()?.let { workbook.sharedStrings.getOrNull(it) }.orEmpty()
                "inlineStr" -> unescape(inline.toString())
                "b" -> if (raw.trim() == "1") "TRUE" else if (raw.isBlank()) "" else "FALSE"
                "e", "str" -> unescape(raw)
                "d" -> raw.substringBefore('T')
                else -> if (raw.isBlank()) "" else {
                    val spec = workbook.cellFormats.getOrNull(style) ?: XlsxNumberFormat.Spec(XlsxNumberFormat.Kind.GENERAL)
                    XlsxNumberFormat.format(raw, spec, workbook.date1904)
                }
            }
        }

        fun toSheet(name: String, complete: Boolean = true): Sheet {
            if (complete) flushRow()
            val mergeMax = merges.maxOfOrNull { it.lastColumn } ?: -1
            return Sheet(
                name = name,
                rows = if (complete) rows.sortedBy { it.index } else rows.toList(),
                columnCount = maxOf(maxColumn, mergeMax) + 1,
                merges = merges.toList(),
                frozenRows = frozenRows,
                frozenColumns = frozenColumns,
                columnWidths = widths.toMap(),
                truncated = truncated,
                complete = complete,
                styles = workbook.cellStyles
            )
        }

        private companion object {
            const val MAX_WIDTH_COLUMNS = 256
        }
    }
}

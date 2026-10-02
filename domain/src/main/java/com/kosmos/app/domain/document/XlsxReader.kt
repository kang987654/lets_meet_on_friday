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
 *    `sharedStrings.xml` + `styles.xml`(셀 서식 → 숫자 서식).
 * 2. [readSheet]: 시트 XML 을 스트리밍해 내용 있는 칸만 담는다. 수식은 **저장된 마지막 계산값**만 보인다(재계산 없음).
 * 3. 행·셀 상한에 닿으면 앞부분만 담고 `truncated`.
 *
 * [WHY] 시트는 탭을 누를 때 하나씩 읽는다 — 첫 화면에 필요 없는 시트까지 읽으면 큰 통합 문서의 첫 표시가 늦어진다.
 * 차트·이미지·조건부 서식·숨김 행은 다루지 않는다(보기 전용 범위, 계획서 결정 3).
 */
class XlsxReader(private val zip: ZipSource, private val limits: DocumentLimits = DocumentLimits()) {

    /** 열린 통합 문서 — 시트 이름 목록과, 시트를 읽는 데 필요한 공유 표들. */
    class Workbook internal constructor(
        val sheetNames: List<String>,
        internal val sheetPaths: List<String?>,
        internal val sharedStrings: List<String>,
        internal val cellFormats: List<XlsxNumberFormat.Spec>,
        internal val date1904: Boolean
    )

    fun open(): DocumentResult<Workbook> = guard {
        val workbookPath = officeDocumentPath()
        val workbookDir = workbookPath.substringBeforeLast('/', missingDelimiterValue = "")
        val relsPath = (if (workbookDir.isEmpty()) "" else "$workbookDir/") + "_rels/" + workbookPath.substringAfterLast('/') + ".rels"

        val workbook = WorkbookHandler().also { parse(workbookPath, it, required = true) }
        val rels = RelsHandler().also { parse(relsPath, it, required = false) }
        val strings = SharedStringsHandler().also { handler ->
            val target = rels.byType("/sharedStrings")?.let { resolve(workbookDir, it) } ?: resolve(workbookDir, "sharedStrings.xml")
            parse(target, handler, required = false)
        }
        val styles = StylesHandler().also { handler ->
            val target = rels.byType("/styles")?.let { resolve(workbookDir, it) } ?: resolve(workbookDir, "styles.xml")
            parse(target, handler, required = false)
        }
        if (workbook.sheets.isEmpty()) throw DocumentException(DocumentError.CORRUPT)
        Workbook(
            sheetNames = workbook.sheets.map { it.first },
            sheetPaths = workbook.sheets.map { (_, relId) -> rels.byId[relId]?.let { resolve(workbookDir, it) } },
            sharedStrings = strings.items,
            cellFormats = styles.cellFormatIds.map { id -> XlsxNumberFormat.spec(id, styles.customFormats[id]) },
            date1904 = workbook.date1904
        )
    }

    /**
     * @param onFirstRows 앞 [FIRST_BATCH_ROWS] 행을 읽은 순간 한 번 불린다(`complete = false` 인 부분 시트) — 큰 시트도 첫 화면을
     * 바로 그리게 한다. 시트가 그보다 짧으면 불리지 않는다.
     */
    fun readSheet(workbook: Workbook, index: Int, onFirstRows: (Sheet) -> Unit = {}): DocumentResult<Sheet> = guard {
        val name = workbook.sheetNames.getOrNull(index) ?: throw DocumentException(DocumentError.CORRUPT)
        val path = workbook.sheetPaths.getOrNull(index) ?: throw DocumentException(DocumentError.CORRUPT)
        val handler = SheetHandler(workbook, limits) { rows -> onFirstRows(rows.toSheet(name, complete = false)) }
        parse(path, handler, required = true)
        handler.toSheet(name)
    }

    private fun officeDocumentPath(): String {
        val root = RelsHandler().also { parse("_rels/.rels", it, required = false) }
        return root.byType("/officeDocument")?.let { resolve("", it) } ?: "xl/workbook.xml"
    }

    private fun parse(path: String, handler: DefaultHandler, required: Boolean) {
        val input = zip.open(path)
        if (input == null) {
            if (required) throw DocumentException(DocumentError.CORRUPT)
            return
        }
        LimitedInputStream(input, limits.maxEntryBytes).use { parseXml(it, handler) }
    }

    private inline fun <T> guard(block: () -> T): DocumentResult<T> = try {
        DocumentResult.Ok(block())
    } catch (e: DocumentException) {
        DocumentResult.Fail(e.error)
    } catch (e: java.io.IOException) {
        DocumentResult.Fail(DocumentError.CORRUPT)
    }

    internal companion object {
        /** 부분 시트를 먼저 내보내는 행 수 — 휴대폰 한 화면(20~30행)의 몇 배. */
        const val FIRST_BATCH_ROWS = 200

        /** 관계 파일의 Target 을 zip 엔트리 경로로 — 절대("/xl/…")와 상대("worksheets/…", "../…") 둘 다. */
        fun resolve(baseDir: String, target: String): String {
            if (target.startsWith("/")) return target.removePrefix("/")
            val parts = (if (baseDir.isEmpty()) emptyList() else baseDir.split('/')).toMutableList()
            target.split('/').forEach { part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(part)
                }
            }
            return parts.joinToString("/")
        }

        /** 엑셀이 제어 문자를 `_x000D_` 처럼 이스케이프한 것을 되돌린다. */
        fun unescape(text: String): String {
            if (!text.contains("_x")) return text
            return Regex("_x([0-9A-Fa-f]{4})_").replace(text) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        }
    }

    // --- SAX 핸들러들 ---

    private class RelsHandler : DefaultHandler() {
        val byId = mutableMapOf<String, String>()
        private val byTypeSuffix = mutableListOf<Pair<String, String>>()

        fun byType(suffix: String): String? = byTypeSuffix.firstOrNull { it.first.endsWith(suffix) }?.second

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            if (localName != "Relationship") return
            val target = attributes.getValue("Target") ?: return
            attributes.getValue("Id")?.let { byId[it] = target }
            attributes.getValue("Type")?.let { byTypeSuffix += it to target }
        }
    }

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

    private class StylesHandler : DefaultHandler() {
        val customFormats = mutableMapOf<Int, String>()
        val cellFormatIds = mutableListOf<Int>()
        private var inCellXfs = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "numFmt" -> {
                    val id = attributes.getValue("numFmtId")?.toIntOrNull() ?: return
                    attributes.getValue("formatCode")?.let { customFormats[id] = it }
                }
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) cellFormatIds += attributes.getValue("numFmtId")?.toIntOrNull() ?: 0
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            if (localName == "cellXfs") inCellXfs = false
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
                    if (text.isNotEmpty()) {
                        if (++cellCount > limits.maxCells) {
                            truncated = true
                            flushRow()
                            throw StopParsing()
                        }
                        rowCells += SheetCell(column, text)
                        if (column > maxColumn) maxColumn = column
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
                complete = complete
            )
        }

        private companion object {
            const val MAX_WIDTH_COLUMNS = 256
        }
    }
}

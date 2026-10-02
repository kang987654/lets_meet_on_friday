package com.kosmos.app.domain.document

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * [DocxReader]
 * 워드(docx, Office Open XML 문서)를 읽기 모드 [FlowDocument] 로 읽습니다 (0.35.0).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Document) — 순수 JVM, zip + SAX ([XlsxReader] 와 같은 방식)
 * - **Dependencies**: [ZipSource], [BlockSink]
 *
 * ### Key Flow
 * 1. `_rels/.rels` → 본문 경로(보통 `word/document.xml`), 본문 관계 파일 → 이미지 경로, `styles.xml` → 제목 스타일 판별.
 * 2. 본문을 스트리밍: `w:p`(문단; 제목·목록 판별) · `w:r`(서식 런) · `w:t`/`w:tab`/`w:br` · `w:tbl`(가로 병합 `gridSpan`, 세로 병합
 *    `vMerge`) · `w:drawing` 의 `a:blip r:embed`(이미지) · `w:br type="page"`(쪽 나눔).
 *
 * [WHY] 머리글·바닥글·각주·메모는 본문 밖 파트라 읽지 않는다 — 읽기 모드의 목적(내용 확인)에 비해 순서 배치가 어색해진다.
 * 변경 추적의 삭제 글자는 `w:delText` 라 자연히 빠진다. 제목은 스타일 id 가 아니라 **스타일 이름("heading N")·개요 수준**으로
 * 판별한다 — 한국어 워드는 제목 스타일 id 가 "1", "2" 같은 숫자다.
 */
class DocxReader(zip: ZipSource, private val limits: DocumentLimits = DocumentLimits()) {

    private val pkg = XmlPackage(zip, limits)

    fun read(): DocumentResult<FlowDocument> = readDocument {
        val documentPath = pkg.mainPart(default = "word/document.xml")
        val dir = documentPath.substringBeforeLast('/', missingDelimiterValue = "")
        val rels = pkg.relationshipsOf(documentPath)
        val styles = StylesHandler().also { pkg.parse(rels.byType("/styles") ?: resolvePartPath(dir, "styles.xml"), it, required = false) }
        val body = BodyHandler(limits, styles.headingLevels, styles.listStyles, rels::target)
        pkg.parse(documentPath, body, required = true)
        body.sink.finish()
    }

    /** 스타일 id → 제목 수준, 그리고 목록 스타일 id. */
    private class StylesHandler : DefaultHandler() {
        val headingLevels = mutableMapOf<String, Int>()
        val listStyles = mutableSetOf<String>()
        private var styleId: String? = null

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "style" -> styleId = attr(attributes, "styleId")
                "name" -> styleId?.let { id ->
                    val name = attr(attributes, "val")?.lowercase()?.trim() ?: return
                    HEADING.matchEntire(name)?.let { headingLevels[id] = it.groupValues[1].toInt().coerceIn(1, 6) }
                    if (name == "title") headingLevels[id] = 1
                    if (name.startsWith("list")) listStyles += id
                }
                "outlineLvl" -> styleId?.let { id ->
                    val level = attr(attributes, "val")?.toIntOrNull() ?: return
                    if (level in 0..5 && id !in headingLevels) headingLevels[id] = level + 1
                }
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            if (localName == "style") styleId = null
        }

        private companion object {
            val HEADING = Regex("""heading\s*(\d)""")
        }
    }

    private class BodyHandler(
        limits: DocumentLimits,
        private val headingLevels: Map<String, Int>,
        private val listStyles: Set<String>,
        private val imagePath: (String) -> String?
    ) : DefaultHandler() {
        val sink = BlockSink(limits)
        private val text = SpanCollector()
        private var paragraphDepth = 0
        private var styleId: String? = null
        private var listLevel: Int? = null
        private var inRunProps = false
        private var inParaProps = false
        private var bold = false
        private var italic = false
        private var underline = false
        private var inText = false
        private var cellSpan = 1
        private var cellMerged = false
        private var pendingPageBreak = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "p" -> {
                    paragraphDepth++
                    styleId = null
                    listLevel = null
                }
                "pPr" -> inParaProps = true
                "pStyle" -> styleId = attr(attributes, "val")
                "numPr" -> if (listLevel == null) listLevel = 0
                "ilvl" -> listLevel = attr(attributes, "val")?.toIntOrNull()?.coerceIn(0, 8) ?: 0
                "r" -> { bold = false; italic = false; underline = false }
                "rPr" -> inRunProps = true
                "b" -> if (inRunProps) bold = on(attributes)
                "i" -> if (inRunProps) italic = on(attributes)
                "u" -> if (inRunProps) underline = attr(attributes, "val").let { it == null || it != "none" }
                "t" -> inText = true
                // [WHY] pPr 안의 w:tabs/w:tab 은 탭 위치 정의다 — 글자 탭이 아니다.
                "tab" -> if (!inRunProps && !inParaProps && paragraphDepth > 0) text.add("\t", bold, italic, underline)
                "br" -> when (attr(attributes, "type")) {
                    "page" -> pendingPageBreak = true
                    else -> text.add("\n", bold, italic, underline)
                }
                "cr" -> text.add("\n", bold, italic, underline)
                "blip" -> attr(attributes, "embed")?.let(imagePath)?.let { flushParagraph(); sink.add(FlowBlock.Image(it)) }
                "imagedata" -> attr(attributes, "id")?.let(imagePath)?.let { flushParagraph(); sink.add(FlowBlock.Image(it)) }
                "tbl" -> { flushParagraph(); sink.beginTable() }
                "tr" -> sink.beginRow()
                "tc" -> { cellSpan = 1; cellMerged = false; sink.beginCell(1, false) }
                "gridSpan" -> cellSpan = attr(attributes, "val")?.toIntOrNull() ?: 1
                // vMerge 값이 없거나 "continue" 면 위 칸에 이어지는 칸, "restart" 면 병합의 시작 칸이다.
                "vMerge" -> cellMerged = attr(attributes, "val").let { it == null || it == "continue" }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inText) text.add(String(ch, start, length), bold, italic, underline)
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            when (localName) {
                "t" -> inText = false
                "rPr" -> inRunProps = false
                "tcPr" -> sink.updateCell(cellSpan, cellMerged)
                "pPr" -> inParaProps = false
                "p" -> {
                    flushParagraph()
                    paragraphDepth--
                }
                "tc" -> { flushParagraph(); sink.endCell() }
                "tbl" -> sink.endTable()
            }
        }

        private fun flushParagraph() {
            if (pendingPageBreak) {
                pendingPageBreak = false
                sink.add(FlowBlock.PageBreak)
            }
            if (text.isBlank()) {
                text.take()
                return
            }
            val spans = text.take()
            val heading = styleId?.let { headingLevels[it] }
            val list = listLevel ?: styleId?.takeIf { it in listStyles }?.let { 0 }
            sink.add(
                when {
                    heading != null -> FlowBlock.Heading(heading, spans)
                    list != null -> FlowBlock.ListItem(list, spans)
                    else -> FlowBlock.Paragraph(spans)
                }
            )
        }
    }

    private companion object {
        /** 네임스페이스 접두사와 무관하게 로컬 이름으로 속성을 찾는다(w:val, r:embed …). */
        fun attr(attributes: Attributes, local: String): String? =
            (0 until attributes.length).firstOrNull { attributes.getLocalName(it) == local }?.let { attributes.getValue(it) }

        /** `<w:b/>` 는 켜짐, `<w:b w:val="0|false"/>` 는 꺼짐. */
        fun on(attributes: Attributes): Boolean = attr(attributes, "val").let { it == null || (it != "0" && it != "false") }
    }
}

package com.kosmos.app.domain.document

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * [HwpxReader]
 * 한글 hwpx(OWPML, KS X 6101 — zip + XML)를 읽기 모드 [FlowDocument] 로 읽습니다 (0.35.0).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Document) — 순수 JVM, zip + SAX
 * - **Dependencies**: [ZipSource], [BlockSink]
 *
 * ### Key Flow
 * 1. `Contents/content.hpf`(패키지 목차): 매니페스트(id → 경로)와 spine 순서로 본문 구역(`section*.xml`)을 찾는다.
 *    목차가 없거나 깨졌으면 `Contents/section0.xml`, `section1.xml` … 을 차례로 연다.
 * 2. `Contents/header.xml`: 글자 모양(charPr — 굵게·기울임·밑줄)과 스타일 이름(개요 N → 목록, 제목 → 제목).
 * 3. 구역 스트리밍: `hp:p`(쪽 나눔 속성) · `hp:run`(글자 모양 참조) · `hp:t`(안의 `hp:tab`·`hp:lineBreak` 포함) ·
 *    `hp:tbl`(`hp:cellSpan` 의 가로·세로 병합) · `hp:pic` 의 `hc:img`(이미지 → 매니페스트 경로).
 *
 * [WHY] **실파일 다양성이 크다**(한글 버전·변환기마다 다르다) — 모르는 요소는 건너뛰고 글자만 건진다. 실패하지 않는 것이 우선이다.
 * 머리말·꼬리말·각주·미주는 본문 흐름이 아니라 건너뛴다(docx 와 같은 결정).
 */
class HwpxReader(zip: ZipSource, private val limits: DocumentLimits = DocumentLimits()) {

    private val pkg = XmlPackage(zip, limits)

    fun read(): DocumentResult<FlowDocument> = readDocument {
        val manifest = ManifestHandler().also { pkg.parse(CONTENT_HPF, it, required = false) }
        val header = HeaderHandler().also { pkg.parse(manifest.path("header") ?: "Contents/header.xml", it, required = false) }
        val sections = manifest.sectionPaths().ifEmpty { fallbackSections() }
        if (sections.isEmpty()) throw DocumentException(DocumentError.CORRUPT)
        val body = SectionHandler(limits, header) { id -> manifest.path(id) }
        sections.forEach { path ->
            if (!body.sink.truncated) pkg.parse(path, body, required = true)
        }
        body.sink.finish()
    }

    private fun fallbackSections(): List<String> =
        generateSequence(0) { it + 1 }.take(MAX_SECTIONS).map { "Contents/section$it.xml" }
            .takeWhile(pkg::exists)
            .toList()

    /** content.hpf — id → 경로, spine 순서. */
    private inner class ManifestHandler : DefaultHandler() {
        private val items = mutableMapOf<String, String>()
        private val spine = mutableListOf<String>()

        fun path(id: String): String? = items[id]?.let(::normalize)

        fun sectionPaths(): List<String> {
            val ordered = spine.mapNotNull { items[it] }.filter { SECTION.containsMatchIn(it) }
            val fromManifest = ordered.ifEmpty { items.values.filter { SECTION.containsMatchIn(it) }.sortedBy { sectionNumber(it) } }
            return fromManifest.map(::normalize)
        }

        // [WHY] href 는 보통 패키지 루트 기준("Contents/section0.xml")이지만 목차 기준 상대 경로("section0.xml")로 쓴 변환기도 있다.
        private fun normalize(href: String): String {
            val clean = href.removePrefix("/")
            if (pkg.exists(clean)) return clean
            val relative = resolvePartPath("Contents", clean)
            return if (pkg.exists(relative)) relative else clean
        }

        private fun sectionNumber(path: String) = SECTION.find(path)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "item" -> {
                    val id = attributes.getValue("id") ?: return
                    val href = attributes.getValue("href") ?: return
                    items[id] = href
                }
                "itemref" -> attributes.getValue("idref")?.let { spine += it }
            }
        }
    }

    /** header.xml — 글자 모양 id → (굵게, 기울임, 밑줄), 스타일 id → 이름. */
    private class HeaderHandler : DefaultHandler() {
        val charProps = mutableMapOf<String, Triple<Boolean, Boolean, Boolean>>()
        val styleNames = mutableMapOf<String, String>()
        private var charId: String? = null
        private var bold = false
        private var italic = false
        private var underline = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            when (localName) {
                "charPr" -> {
                    charId = attributes.getValue("id")
                    bold = false; italic = false; underline = false
                }
                "bold" -> if (charId != null) bold = true
                "italic" -> if (charId != null) italic = true
                "underline" -> if (charId != null) underline = attributes.getValue("type").let { it != null && it != "NONE" }
                "style" -> {
                    val id = attributes.getValue("id") ?: return
                    styleNames[id] = listOfNotNull(attributes.getValue("name"), attributes.getValue("engName")).joinToString("|")
                }
            }
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            if (localName == "charPr") {
                charId?.let { charProps[it] = Triple(bold, italic, underline) }
                charId = null
            }
        }
    }

    private class SectionHandler(
        limits: DocumentLimits,
        private val header: HeaderHandler,
        private val imagePath: (String) -> String?
    ) : DefaultHandler() {
        val sink = BlockSink(limits)
        private val text = SpanCollector()
        private var skipDepth = 0
        private var paragraphDepth = 0
        private val paragraphStyles = ArrayDeque<String?>()
        private var props = Triple(false, false, false)
        private var inText = false
        private var pendingPageBreak = false

        override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
            if (localName in SKIPPED) {
                skipDepth++
                return
            }
            if (skipDepth > 0) return
            when (localName) {
                "p" -> {
                    // 표 칸 안의 문단은 칸 안에서 끝난다 — 바깥 문단의 글자를 먼저 내보낸다.
                    if (paragraphDepth > 0) flushParagraph(paragraphStyles.lastOrNull())
                    paragraphDepth++
                    paragraphStyles.addLast(attributes.getValue("styleIDRef"))
                    if (attributes.getValue("pageBreak") == "1") pendingPageBreak = true
                }
                "run" -> props = header.charProps[attributes.getValue("charPrIDRef")] ?: Triple(false, false, false)
                "t" -> inText = true
                "tab" -> if (inText) add("\t")
                "lineBreak" -> if (inText) add("\n")
                "nbSpace", "fwSpace" -> if (inText) add(" ")
                "tbl" -> { flushParagraph(paragraphStyles.lastOrNull()); sink.beginTable() }
                "tr" -> sink.beginRow()
                "tc" -> sink.beginCell(1, false)
                "cellSpan" -> sink.updateCell(
                    colSpan = attributes.getValue("colSpan")?.toIntOrNull() ?: 1,
                    merged = false,
                    rowSpan = attributes.getValue("rowSpan")?.toIntOrNull() ?: 1
                )
                "img" -> attributes.getValue("binaryItemIDRef")?.let(imagePath)?.let {
                    flushParagraph(paragraphStyles.lastOrNull())
                    sink.add(FlowBlock.Image(it))
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inText && skipDepth == 0) add(String(ch, start, length))
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            if (localName in SKIPPED) {
                skipDepth--
                return
            }
            if (skipDepth > 0) return
            when (localName) {
                "t" -> inText = false
                "p" -> {
                    flushParagraph(paragraphStyles.removeLastOrNull())
                    paragraphDepth--
                }
                "tc" -> sink.endCell()
                "tbl" -> sink.endTable()
            }
        }

        private fun add(s: String) = text.add(s, props.first, props.second, props.third)

        private fun flushParagraph(styleId: String?) {
            if (pendingPageBreak) {
                pendingPageBreak = false
                sink.add(FlowBlock.PageBreak)
            }
            if (text.isBlank()) {
                text.take()
                return
            }
            val spans = text.take()
            val style = styleId?.let { header.styleNames[it] }.orEmpty()
            val outline = OUTLINE.find(style)?.groupValues?.get(1)?.toIntOrNull()
            sink.add(
                when {
                    style.contains("제목") || style.contains("Title") -> FlowBlock.Heading(1, spans)
                    outline != null -> FlowBlock.ListItem((outline - 1).coerceIn(0, 8), spans)
                    else -> FlowBlock.Paragraph(spans)
                }
            )
        }
    }

    private companion object {
        const val CONTENT_HPF = "Contents/content.hpf"
        const val MAX_SECTIONS = 200
        val SECTION = Regex("""section(\d+)\.xml$""")
        val OUTLINE = Regex("""(?:개요|Outline)\s*(\d+)""")

        /** 본문 흐름이 아닌 것 — 머리말·꼬리말·각주·미주·숨은 설명. */
        val SKIPPED = setOf("header", "footer", "footNote", "endNote", "hiddenComment")
    }
}

package com.kosmos.app.domain.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [FlowReadersTest]
 * docx·hwpx 읽기 모드 파서 — 테스트 안에서 최소 문서를 조립한다(개인 문서 픽스처 없음).
 * 제목·서식 런·목록·표 병합(가로·세로)·이미지 참조·쪽 나눔·머리말 제외·모르는 요소 무시·깨진 XML.
 */
class FlowReadersTest {

    private fun source(files: Map<String, String?>) = ZipSource { name -> files[name]?.toByteArray()?.inputStream() }

    private fun <T> DocumentResult<T>.ok(): T = when (this) {
        is DocumentResult.Ok -> value
        is DocumentResult.Fail -> { fail("실패: $error"); throw IllegalStateException() }
    }

    private fun texts(doc: FlowDocument) = doc.blocks.map { b -> b::class.simpleName + ":" + b.plainText() }

    // --- docx ---

    private val W = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
        "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\""

    private fun docx(body: String) = source(
        mapOf(
            "_rels/.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""",
            "word/_rels/document.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rIdS" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
<Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
<Relationship Id="rIdLink" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com" TargetMode="External"/>
</Relationships>""",
            // 한국어 워드는 제목 스타일 id 가 숫자다 — 이름으로 판별해야 한다
            "word/styles.xml" to """<w:styles $W><w:style w:styleId="1"><w:name w:val="heading 1"/></w:style>
<w:style w:styleId="Subtitle2"><w:name w:val="My Sub"/><w:pPr><w:outlineLvl w:val="1"/></w:pPr></w:style>
<w:style w:styleId="a"><w:name w:val="List Paragraph"/></w:style></w:styles>""",
            "word/document.xml" to """<w:document $W><w:body>$body</w:body></w:document>"""
        )
    )

    @Test
    fun `docx 제목 · 서식 런 · 목록 · 탭 · 줄바꿈`() {
        val doc = DocxReader(docx(
            """<w:p><w:pPr><w:pStyle w:val="1"/><w:tabs><w:tab w:val="left" w:pos="720"/></w:tabs></w:pPr><w:r><w:t>회의록</w:t></w:r></w:p>
<w:p><w:pPr><w:pStyle w:val="Subtitle2"/></w:pPr><w:r><w:t>안건</w:t></w:r></w:p>
<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>굵게</w:t></w:r><w:r><w:rPr><w:b w:val="0"/><w:i/></w:rPr><w:t xml:space="preserve"> 기울임</w:t></w:r><w:r><w:tab/><w:t>탭</w:t><w:br/><w:t>둘째 줄</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="1"/><w:numId w:val="3"/></w:numPr></w:pPr><w:r><w:t>목록 항목</w:t></w:r></w:p>
<w:p><w:pPr><w:pStyle w:val="a"/></w:pPr><w:r><w:t>스타일 목록</w:t></w:r></w:p>
<w:p><w:r><w:delText>지운 글</w:delText></w:r></w:p>
<w:p/>"""
        )).read().ok()

        assertEquals(
            listOf("Heading:회의록", "Heading:안건", "Paragraph:굵게 기울임\t탭\n둘째 줄", "ListItem:목록 항목", "ListItem:스타일 목록"),
            texts(doc)
        )
        assertEquals(1, (doc.blocks[0] as FlowBlock.Heading).level)
        assertEquals("개요 수준 1 → 제목 2", 2, (doc.blocks[1] as FlowBlock.Heading).level)
        val spans = (doc.blocks[2] as FlowBlock.Paragraph).spans
        assertEquals(listOf(true, false, false), spans.map { it.bold })
        assertEquals(listOf(false, true, false), spans.map { it.italic })
        assertEquals(1, (doc.blocks[3] as FlowBlock.ListItem).level)
    }

    @Test
    fun `docx 표 병합 · 이미지 · 쪽 나눔`() {
        val doc = DocxReader(docx(
            """<w:p><w:r><w:t>앞</w:t></w:r></w:p>
<w:tbl><w:tr><w:tc><w:tcPr><w:gridSpan w:val="2"/></w:tcPr><w:p><w:r><w:t>머리</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:tcPr><w:vMerge w:val="restart"/></w:tcPr><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr>
<w:tr><w:tc><w:tcPr><w:vMerge/></w:tcPr><w:p/></w:tc><w:tc><w:p><w:r><w:t>C</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:drawing><a:graphic><a:graphicData><a:blip r:embed="rIdImg"/></a:graphicData></a:graphic></w:drawing></w:r></w:p>
<w:p><w:r><w:br w:type="page"/></w:r><w:r><w:t>다음 쪽</w:t></w:r></w:p>"""
        )).read().ok()

        assertEquals(listOf("Paragraph", "Table", "Image", "PageBreak", "Paragraph"), doc.blocks.map { it::class.simpleName })
        val table = doc.blocks[1] as FlowBlock.Table
        assertEquals(listOf(2), table.rows[0].map { it.colSpan })
        assertEquals(listOf(false, false), table.rows[1].map { it.merged })
        assertEquals("세로 병합의 이어지는 칸", listOf(true, false), table.rows[2].map { it.merged })
        assertEquals("머리\nA\tB\n\tC", table.plainText())
        assertEquals(FlowBlock.Image("word/media/image1.png"), doc.blocks[2])
    }

    @Test
    fun `docx 깨진 본문과 빠진 본문은 손상됨, 블록 상한은 앞부분만`() {
        assertEquals(DocumentResult.Fail(DocumentError.CORRUPT), DocxReader(docx("<w:p><w:r>")).read())
        val missing = source(mapOf("_rels/.rels" to null))
        assertEquals(DocumentResult.Fail(DocumentError.CORRUPT), DocxReader(missing).read())

        val many = (1..10).joinToString("") { "<w:p><w:r><w:t>문단$it</w:t></w:r></w:p>" }
        val doc = DocxReader(docx(many), DocumentLimits(maxBlocks = 3)).read().ok()
        assertEquals(3, doc.blocks.size)
        assertTrue(doc.truncated)
    }

    // --- hwpx ---

    private val HP = "xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\" xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\" " +
        "xmlns:hc=\"http://www.hancom.co.kr/hwpml/2011/core\""

    private val hpf = """<opf:package xmlns:opf="http://www.idpf.org/2007/opf/"><opf:manifest>
<opf:item id="header" href="Contents/header.xml" media-type="application/xml"/>
<opf:item id="section1" href="Contents/section1.xml" media-type="application/xml"/>
<opf:item id="section0" href="Contents/section0.xml" media-type="application/xml"/>
<opf:item id="image1" href="BinData/image1.png" media-type="image/png"/>
</opf:manifest><opf:spine><opf:itemref idref="header" linear="yes"/><opf:itemref idref="section0"/><opf:itemref idref="section1"/></opf:spine></opf:package>"""

    private val header = """<hh:head xmlns:hh="http://www.hancom.co.kr/hwpml/2011/head"><hh:refList>
<hh:charProperties><hh:charPr id="0"/><hh:charPr id="7"><hh:bold/><hh:underline type="BOTTOM"/></hh:charPr><hh:charPr id="8"><hh:underline type="NONE"/><hh:italic/></hh:charPr></hh:charProperties>
<hh:styles><hh:style id="0" type="PARA" name="바탕글" engName="Normal"/><hh:style id="2" type="PARA" name="개요 2" engName="Outline 2"/><hh:style id="9" type="PARA" name="문서 제목" engName="Doc Title"/></hh:styles>
</hh:refList></hh:head>"""

    private fun hwpx(section0: String, section1: String = "<hs:sec $HP></hs:sec>", withHpf: Boolean = true) = source(
        mapOf(
            "Contents/content.hpf" to if (withHpf) hpf else null,
            "Contents/header.xml" to header,
            "Contents/section0.xml" to "<hs:sec $HP>$section0</hs:sec>",
            "Contents/section1.xml" to section1
        )
    )

    @Test
    fun `hwpx 문단 · 글자 모양 · 스타일 · 구역 순서 · 머리말 제외`() {
        val doc = HwpxReader(hwpx(
            """<hp:p styleIDRef="9"><hp:run charPrIDRef="0"><hp:t>보고서</hp:t></hp:run></hp:p>
<hp:p styleIDRef="0"><hp:run charPrIDRef="0"><hp:ctrl><hp:header><hp:subList><hp:p><hp:run><hp:t>머리말 글</hp:t></hp:run></hp:p></hp:subList></hp:header></hp:ctrl><hp:t>보통 </hp:t></hp:run><hp:run charPrIDRef="7"><hp:t>굵은밑줄</hp:t></hp:run><hp:run charPrIDRef="8"><hp:t>기울<hp:tab width="100"/>임<hp:lineBreak/>끝</hp:t></hp:run></hp:p>
<hp:p styleIDRef="2"><hp:run><hp:t>개요 항목</hp:t></hp:run><hp:unknownThing>무시</hp:unknownThing></hp:p>""",
            section1 = """<hs:sec $HP><hp:p pageBreak="1"><hp:run><hp:t>둘째 구역</hp:t></hp:run></hp:p></hs:sec>"""
        )).read().ok()

        assertEquals(
            listOf("Heading:보고서", "Paragraph:보통 굵은밑줄기울\t임\n끝", "ListItem:개요 항목", "PageBreak:", "Paragraph:둘째 구역"),
            texts(doc)
        )
        val spans = (doc.blocks[1] as FlowBlock.Paragraph).spans
        assertEquals(listOf(false, true, false), spans.map { it.bold })
        assertEquals(listOf(false, true, false), spans.map { it.underline })
        assertEquals(listOf(false, false, true), spans.map { it.italic })
        assertEquals("개요 2 → 들여쓰기 1", 1, (doc.blocks[2] as FlowBlock.ListItem).level)
    }

    @Test
    fun `hwpx 표 — 세로 병합으로 생략된 칸을 채운다 · 이미지`() {
        val cell = { text: String, col: Int, row: Int, cs: Int, rs: Int ->
            """<hp:tc><hp:subList><hp:p><hp:run><hp:t>$text</hp:t></hp:run></hp:p></hp:subList><hp:cellAddr colAddr="$col" rowAddr="$row"/><hp:cellSpan colSpan="$cs" rowSpan="$rs"/></hp:tc>"""
        }
        val doc = HwpxReader(hwpx(
            """<hp:p><hp:run><hp:t>표 앞</hp:t><hp:tbl rowCnt="3" colCnt="3">
<hp:tr>${cell("구분", 0, 0, 1, 2)}${cell("상반기", 1, 0, 2, 1)}</hp:tr>
<hp:tr>${cell("1분기", 1, 1, 1, 1)}${cell("2분기", 2, 1, 1, 1)}</hp:tr>
<hp:tr>${cell("매출", 0, 2, 1, 1)}${cell("10", 1, 2, 1, 1)}${cell("20", 2, 2, 1, 1)}</hp:tr>
</hp:tbl></hp:run></hp:p>
<hp:p><hp:run><hp:pic><hc:img binaryItemIDRef="image1"/></hp:pic></hp:run></hp:p>"""
        )).read().ok()

        assertEquals(listOf("Paragraph", "Table", "Image"), doc.blocks.map { it::class.simpleName })
        val table = doc.blocks[1] as FlowBlock.Table
        assertEquals("구분\t상반기\n\t1분기\t2분기\n매출\t10\t20", table.plainText())
        assertEquals("생략된 칸을 병합 자리로 채운다", listOf(true, false, false), table.rows[1].map { it.merged })
        assertEquals(listOf(1, 2), table.rows[0].map { it.colSpan })
        assertEquals(FlowBlock.Image("BinData/image1.png"), doc.blocks[2])
    }

    @Test
    fun `hwpx 목차가 없으면 구역 파일을 차례로 연다, 깨진 구역은 손상됨`() {
        val doc = HwpxReader(hwpx("<hp:p><hp:run><hp:t>목차 없음</hp:t></hp:run></hp:p>", withHpf = false)).read().ok()
        assertEquals(listOf("Paragraph:목차 없음"), texts(doc))

        assertEquals(DocumentResult.Fail(DocumentError.CORRUPT), HwpxReader(hwpx("<hp:p><hp:run>")).read())
        assertEquals(DocumentResult.Fail(DocumentError.CORRUPT), HwpxReader(source(emptyMap())).read())
    }
}

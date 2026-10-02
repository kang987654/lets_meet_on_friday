package com.kosmos.app.domain.document

import org.xml.sax.helpers.DefaultHandler
import java.io.FilterInputStream
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/**
 * 문서를 읽다 실패한 이유 — 뷰어가 사용자 안내 문구로 바꾼다.
 */
enum class DocumentError {
    /** 형식을 지원하지 않는다. */
    UNSUPPORTED,

    /** 파일을 열 수 없다(권한 만료·삭제). */
    UNREADABLE,

    /** 상한을 넘었다(압축 폭탄 포함). */
    TOO_LARGE,

    /** 구조가 깨졌다. */
    CORRUPT,

    /** 암호가 걸려 있다. */
    ENCRYPTED
}

/** 리더 내부에서 실패를 전달하는 예외 — 공개 API 는 [DocumentResult] 로 감싼다. */
class DocumentException(val error: DocumentError, cause: Throwable? = null) : Exception(error.name, cause)

/** 문서 읽기 결과. */
sealed interface DocumentResult<out T> {
    data class Ok<T>(val value: T) : DocumentResult<T>
    data class Fail(val error: DocumentError) : DocumentResult<Nothing>
}

/**
 * [DocumentLimits]
 * 문서 읽기 안전 상한 — 손상·악성 파일에서 메모리 폭주와 무한 대기를 막는다.
 *
 * @property maxEntryBytes zip 엔트리 하나의 압축 해제 상한(압축 폭탄 방지).
 * @property maxRows 시트 하나에서 읽는 행 상한 — 넘으면 앞부분만 보이고 `truncated`.
 * @property maxCells 시트 하나의 셀 상한(넓은 시트 대비).
 * @property maxCsvBytes csv 읽기 상한.
 * @property maxBlocks 읽기 모드 문서(docx·hwpx)의 블록(문단·표·이미지) 상한.
 */
data class DocumentLimits(
    val maxEntryBytes: Long = 50L * 1024 * 1024,
    val maxRows: Int = 20_000,
    val maxCells: Int = 400_000,
    val maxCsvBytes: Long = 20L * 1024 * 1024,
    val maxBlocks: Int = 20_000
)

/**
 * [ZipSource]
 * zip 엔트리를 이름으로 여는 최소 인터페이스 — 앱은 `java.util.zip.ZipFile`(무작위 접근), 테스트는 메모리 맵을 쓴다.
 *
 * [WHY] `ZipInputStream` 스트리밍(계획서 초안)이 아니라 무작위 접근이다 — 엑셀은 `sharedStrings.xml` 을 시트 **뒤에**
 * 저장하는 경우가 많아 순차 읽기로는 시트를 다 버퍼링해야 한다. 앱은 콘텐츠 URI 를 캐시 파일로 한 번 복사해 `ZipFile` 로 연다.
 */
fun interface ZipSource {
    /** 엔트리가 없으면 null. */
    fun open(name: String): InputStream?
}

/** 읽은 바이트가 [limit] 를 넘으면 [DocumentError.TOO_LARGE] 를 던지는 스트림. */
internal class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) add(n.toLong())
        return n
    }

    private fun add(n: Long) {
        count += n
        if (count > limit) throw DocumentException(DocumentError.TOO_LARGE)
    }
}

/** 상한(행·셀)에 닿아 파싱을 일찍 끝낼 때 던지는 내부 신호. */
internal class StopParsing : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

/**
 * SAX 로 파싱한다. 외부 엔티티는 끈다(XXE 방지). 기능 설정은 구현마다 지원이 달라(안드로이드 Expat 은 일부 거부) 개별로 시도한다.
 */
internal fun parseXml(input: InputStream, handler: DefaultHandler) {
    val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
    listOf(
        "http://xml.org/sax/features/external-general-entities",
        "http://xml.org/sax/features/external-parameter-entities"
    ).forEach { feature -> runCatching { factory.setFeature(feature, false) } }
    runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    try {
        factory.newSAXParser().parse(input, handler)
    } catch (_: StopParsing) {
        // 상한 도달 — 읽은 데까지가 결과다.
    } catch (e: DocumentException) {
        throw e
    } catch (e: Exception) {
        // [WHY] SAX 는 핸들러 예외를 SAXException 으로 감싸 다시 던지는 구현이 있다 — 감싼 원인을 풀어 본다.
        when (val cause = e.cause) {
            is StopParsing -> Unit
            is DocumentException -> throw cause
            else -> throw DocumentException(DocumentError.CORRUPT, e)
        }
    }
}

/**
 * 파일 앞 바이트로 실제 형식을 확인한다 — 확장자·MIME 이 맞아도 내용이 다른 경우를 안내하기 위해서다.
 *
 * [WHY] 암호가 걸린 xlsx 는 zip 이 아니라 OLE 복합 파일(옛 xls 와 같은 겉모양)로 저장된다 — zip 으로 열다 실패하면
 * "손상됨"이 아니라 "암호·옛 형식"으로 안내해야 사용자가 원인을 안다.
 */
object DocumentSniffer {
    private val OLE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    fun isZip(head: ByteArray): Boolean = head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()

    fun isOleCompound(head: ByteArray): Boolean = head.size >= OLE.size && OLE.indices.all { head[it] == OLE[it] }

    fun isPdf(head: ByteArray): Boolean = head.size >= 5 && String(head, 0, 5, Charsets.US_ASCII) == "%PDF-"
}

/** 리더 본문을 감싸 실패를 [DocumentResult] 로 바꾼다 — 상한·손상은 [DocumentException], 읽기 오류는 CORRUPT. */
internal inline fun <T> readDocument(block: () -> T): DocumentResult<T> = try {
    DocumentResult.Ok(block())
} catch (e: DocumentException) {
    DocumentResult.Fail(e.error)
} catch (e: java.io.IOException) {
    DocumentResult.Fail(DocumentError.CORRUPT)
}

/** 파트 경로 해석 — 절대("/xl/…")와 [baseDir] 기준 상대("worksheets/…", "../…") 둘 다 zip 엔트리 경로로. */
internal fun resolvePartPath(baseDir: String, target: String): String {
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

/**
 * [XmlPackage]
 * zip 안 XML 파트를 상한([DocumentLimits.maxEntryBytes]) 걸어 SAX 로 읽는 공통 부분 — xlsx·docx·hwpx 리더가 같이 쓴다.
 */
internal class XmlPackage(private val zip: ZipSource, private val limits: DocumentLimits) {

    /** 파트가 없으면 [required] 일 때만 CORRUPT. */
    fun parse(path: String, handler: DefaultHandler, required: Boolean) {
        val input = zip.open(path)
        if (input == null) {
            if (required) throw DocumentException(DocumentError.CORRUPT)
            return
        }
        LimitedInputStream(input, limits.maxEntryBytes).use { parseXml(it, handler) }
    }

    fun exists(path: String): Boolean = zip.open(path)?.also { it.close() } != null

    /** OOXML 본문 파트(`_rels/.rels` 의 officeDocument). 없으면 [default]. */
    fun mainPart(default: String): String = relationshipsOf("").byType("/officeDocument") ?: default

    /** 파트 하나의 관계 파일(`폴더/_rels/이름.rels`) — 대상 경로는 파트 폴더 기준으로 풀어 둔다. [partPath] 가 ""면 패키지 루트. */
    fun relationshipsOf(partPath: String): Relationships {
        val dir = partPath.substringBeforeLast('/', missingDelimiterValue = "")
        val relsPath = if (partPath.isEmpty()) "_rels/.rels" else resolvePartPath(dir, "_rels/" + partPath.substringAfterLast('/') + ".rels")
        return Relationships(dir).also { parse(relsPath, it, required = false) }
    }
}

/** OOXML 관계 파일(`*.rels`) — id·종류로 대상 파트 경로를 찾는다. 외부 대상(하이퍼링크 등)은 zip 안 파일이 아니라 뺀다. */
internal class Relationships(private val baseDir: String) : DefaultHandler() {
    private val byId = mutableMapOf<String, String>()
    private val types = mutableListOf<Pair<String, String>>()

    fun target(id: String): String? = byId[id]

    /** 종류 URI 가 [suffix] 로 끝나는 첫 관계의 대상. */
    fun byType(suffix: String): String? = types.firstOrNull { it.first.endsWith(suffix) }?.second

    override fun startElement(uri: String?, localName: String, qName: String?, attributes: org.xml.sax.Attributes) {
        if (localName != "Relationship" || attributes.getValue("TargetMode") == "External") return
        val target = attributes.getValue("Target")?.let { resolvePartPath(baseDir, it) } ?: return
        attributes.getValue("Id")?.let { byId[it] = target }
        attributes.getValue("Type")?.let { types += it to target }
    }
}

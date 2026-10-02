package com.kosmos.app.domain.document

/**
 * 문서 홈의 최근 문서 한 줄 (0.34.0).
 *
 * @property uri 영구 읽기 권한을 받은 콘텐츠 URI 문자열.
 * @property openedAt 마지막으로 연 시각(epoch ms).
 */
data class RecentDocument(val uri: String, val name: String, val mimeType: String?, val openedAt: Long)

/**
 * [RecentDocuments]
 * 최근 문서 목록 규칙 — 같은 URI 는 맨 위로 갱신, 최신순 [MAX] 개. 저장 형식(한 줄에 한 문서, 탭 구분)도 여기서 정한다.
 *
 * [WHY] JSON 이 아니라 탭 구분 줄이다 — domain 은 순수 JVM 이라 안드로이드 org.json 을 못 쓰고, 이 정도 구조에 직렬화 라이브러리를
 * 들이지 않는다. 이름의 탭·줄바꿈은 공백으로 바꿔 형식이 깨지지 않게 한다.
 */
object RecentDocuments {
    const val MAX = 20

    fun add(list: List<RecentDocument>, item: RecentDocument): List<RecentDocument> =
        (listOf(item) + list.filterNot { it.uri == item.uri }).take(MAX)

    fun remove(list: List<RecentDocument>, uri: String): List<RecentDocument> = list.filterNot { it.uri == uri }

    fun encode(list: List<RecentDocument>): String = list.joinToString("\n") { d ->
        listOf(d.uri, clean(d.name), d.mimeType.orEmpty(), d.openedAt.toString()).joinToString("\t")
    }

    fun decode(text: String?): List<RecentDocument> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4 || parts[0].isBlank()) return@mapNotNull null
        val openedAt = parts[3].toLongOrNull() ?: return@mapNotNull null
        RecentDocument(parts[0], parts[1], parts[2].ifEmpty { null }, openedAt)
    }

    private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}

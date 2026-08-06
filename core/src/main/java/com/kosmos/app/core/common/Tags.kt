package com.kosmos.app.core.common

/**
 * [Tags]
 * 지식 노트 태그를 저장 형식에 맞게 정규화합니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common) — Pure Kotlin, `:data`의 저장/검색 경로에서 사용
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 저장 시 [normalizeAll]로 다듬은 뒤 콤마로 이어 `tags` 칼럼에 넣습니다.
 * 2. 태그 검색 시에도 같은 정규화를 적용해 저장된 값과 형태를 맞춥니다.
 *
 * [WHY] `tags` 칼럼은 콤마 조인 문자열이고, `KnowledgeDao.searchByTags` 가
 * `',' || tags || ','` 패턴으로 **콤마를 순수 구분자로 하드 가정**한다. 따라서 태그 자체에
 * 콤마가 들어오면 저장은 되지만 읽을 때 두 개로 쪼개진다 — `["밥, 국"]` 이 `["밥", "국"]` 이
 * 되고, 0.7.1 의 태그 정확 매칭 이후로는 원래 태그로 검색이 아예 불가능해진다.
 *
 * [WHY] 저장을 거부하는 대신 공백으로 치환한다 (사용자 결정). 태그는 분류용 라벨이라
 * 콤마가 들어갈 일이 드물고, 그 드문 경우에 툴 호출을 실패시켜 대화를 끊는 것보다 살짝
 * 다듬어 넣는 편이 낫다. 구분자 자체를 바꾸는 근본 해결은 Room 마이그레이션과 기존 데이터
 * 변환이 필요해 별건으로 남겼다.
 */
object Tags {

    private const val DELIMITER = ","

    private val WHITESPACE = Regex("\\s+")

    /**
     * 태그 하나를 정규화합니다. 콤마를 공백으로 바꾸고 연속 공백을 하나로 접은 뒤 trim 합니다.
     */
    fun normalize(raw: String): String =
        raw.replace(DELIMITER, " ").replace(WHITESPACE, " ").trim()

    /**
     * 태그 목록을 정규화하고 빈 값과 중복을 제거합니다.
     *
     * [WHY] 중복 제거가 필요한 이유는 정규화가 **새로운 중복을 만들 수 있기** 때문이다 —
     * `["a,b", "a b"]` 는 서로 다른 입력이지만 정규화 후 둘 다 `"a b"` 가 된다.
     */
    fun normalizeAll(raw: List<String>): List<String> =
        raw.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
}

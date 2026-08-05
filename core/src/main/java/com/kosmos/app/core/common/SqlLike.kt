package com.kosmos.app.core.common

/**
 * [SqlLike]
 * SQL `LIKE` 패턴에 사용자 입력을 안전하게 끼워 넣기 위한 유틸리티입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common) — Pure Kotlin, `:data`의 DAO 호출부에서 사용
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 호출부가 [escape]로 사용자 입력의 와일드카드를 무력화합니다.
 * 2. DAO 쿼리는 `ESCAPE '\'` 절과 함께 이 결과를 바인딩합니다.
 *
 * [WHY] Room 의 파라미터 바인딩은 SQL 인젝션을 막지만, `%` 와 `_` 는 바인딩된 **값 안쪽**에
 * 있으므로 여전히 와일드카드로 해석된다. 그래서 사용자가 "100%" 를 검색하면 리터럴이 아니라
 * "100 을 포함하는 모든 것" 이 매칭되고, "%" 한 글자는 전체 테이블을 긁어와 RAG 프롬프트의
 * 컨텍스트 예산을 터뜨렸다.
 */
object SqlLike {

    const val ESCAPE_CHAR = "\\"

    /**
     * `LIKE` 패턴에서 특수 의미를 갖는 문자를 이스케이프합니다.
     *
     * [WHY] 백슬래시를 **가장 먼저** 치환해야 한다. 순서를 바꾸면 `%`/`_` 를 위해 새로 넣은
     * 백슬래시를 다시 이스케이프해서 패턴이 망가진다.
     *
     * [WHY] SQLite 는 기본 이스케이프 문자가 없어서 지금까지 `\` 가 리터럴로 무해했다.
     * `ESCAPE '\'` 를 선언하는 순간 기존 사용자 텍스트의 `\` 가 이스케이프 접두사로 해석되므로,
     * 이 함수와 DAO 의 `ESCAPE` 절은 반드시 함께 적용해야 한다.
     */
    fun escape(input: String): String = input
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}

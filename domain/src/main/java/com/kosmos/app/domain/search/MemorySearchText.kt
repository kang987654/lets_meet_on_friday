package com.kosmos.app.domain.search

import com.kosmos.app.domain.model.Episode
import com.kosmos.app.domain.model.KnowledgeNote

/**
 * 바이그램 점수를 매길 때 쓰는 문서 텍스트 — 지식 노트와 에피소드 문서의 **공통 정의**.
 *
 * [WHY] `SearchMemory` 툴과 드로어 검색이 같은 텍스트에 같은 점수를 매겨야 "비서는 못 찾는데
 * 나는 보이는" 불신이 생기지 않는다(ui_a_prime.md 동작 규칙). 표시용 문구("(과거 대화) …")는
 * 넣지 않는다 — 모든 에피소드에 같은 바이그램이 붙어 점수를 오염시킨다. exp33 도
 * `title tags summary` 로 잰다.
 */
fun KnowledgeNote.searchText(): String = "$content ${tags.joinToString(" ")}"

fun Episode.searchText(): String = "${title.orEmpty()} ${tags.joinToString(" ")} ${summary.orEmpty()}"

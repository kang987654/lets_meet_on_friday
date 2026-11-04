package com.kosmos.app.feature.document

import com.kosmos.app.domain.document.FlowBlock
import com.kosmos.app.domain.document.plainText

/**
 * 뷰어를 열 위치(0.41.2 출처 카드) — PDF 는 쪽, 흐름 문서는 찾을 머리말.
 *
 * @property page PDF 쪽(1부터).
 * @property find 보관함 조각의 마지막 머리말(예 `제162조(채권, 재산권의 소멸시효)`, 여러 조각이면 끝의 ` (2/3)` 포함).
 */
data class ViewerTarget(val page: Int? = null, val find: String? = null) {

    companion object {
        private val PART = Regex("""\s*\(\d+/\d+\)$""")
        private val SPACE = Regex("""\s+""")

        /**
         * [find] 머리말로 시작하는 첫 블록의 위치 — 없으면 null(맨 위에서 연다).
         *
         * [WHY] 조각 머리말은 개정 표지를 뺀 글이라 원문 블록과 끝이 다를 수 있다 — 통째로 같은 블록이 없으면 조문 번호 앞부분(`제162조(`)으로 다시 찾는다.
         */
        fun blockIndexOf(blocks: List<FlowBlock>, find: String?): Int? {
            val key = find?.replace(PART, "")?.let { norm(it) }?.takeIf { it.isNotEmpty() } ?: return null
            val texts = blocks.map { norm(it.plainText()) }
            texts.indexOfFirst { it.startsWith(key) }.takeIf { it >= 0 }?.let { return it }
            val head = key.substringBefore('(', "").takeIf { it.isNotEmpty() }?.let { "$it(" }
            if (head != null) texts.indexOfFirst { it.startsWith(head) }.takeIf { it >= 0 }?.let { return it }
            return texts.indexOfFirst { it.contains(key) }.takeIf { it >= 0 }
        }

        private fun norm(text: String) = text.replace(SPACE, " ").trim()
    }
}

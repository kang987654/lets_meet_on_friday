package com.kosmos.app.domain.cleanup

import com.kosmos.app.domain.model.KnowledgeNote

/**
 * 기억 병합 제안 — 미리보기에서 사용자가 체크해야 적용된다(체크 기본 해제, exp42 M0 결정).
 *
 * @property sources 합쳐질 원본 노트들(적용 시 삭제)
 * @property mergedContent 모델이 합친 한 문장(원문 숫자 보존 검사 통과분만 제안된다)
 */
data class MergeProposal(
    val sources: List<KnowledgeNote>,
    val mergedContent: String
) {
    /** 원본 id 묶음 — 미리보기 체크 상태의 키. */
    val key: String get() = sources.map { it.id }.sorted().joinToString("+")
}

package com.kosmos.app.domain.model

/**
 * 지식 노트 — 가끔-관련 기억(3층 기억 모델의 Knowledge 층). SearchMemory 가 회수한다.
 *
 * [source] 는 "manual"(사용자·툴 콜 저장) / "auto"(C′2 리셋 시점 자동 추출). 자동 항목은
 * 기억 화면에서 출처 배지로 구분되고 삭제할 수 있다 — G3(쓰기=승인) 예외의 통제 장치.
 *
 * [WHY] 벡터를 들고 있지 않다 — 옛 영어 전용 임베더 자리(`embedding`)는 0.41.3 에서 지웠고, 뜻 검색 벡터는 모델 id 와 함께
 * `memory_vector` 표에 따로 둔다(ADR-038). 모델을 바꾸거나 지울 때 메모를 건드리지 않기 위해서다.
 */
data class KnowledgeNote(
    val id: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val source: String = SOURCE_MANUAL
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_AUTO = "auto"
    }
}

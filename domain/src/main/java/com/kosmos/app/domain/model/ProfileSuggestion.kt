package com.kosmos.app.domain.model

/**
 * 프로필 제안 — 리셋 시점 자동 추출(C′2)이 뽑은 프로필 급 사실의 승인 대기 행.
 *
 * [WHY] 기존 [ApprovalCoordinator] 는 단일 슬롯·60초 자동 거절·채팅 화면 생존 전제라
 * 백그라운드 추출의 승인에 못 쓴다. 제안을 테이블에 영속하면 ① 화면이 없어도 잃지 않고
 * ② 거절 이력이 남아 같은 사실을 다시 묻지 않는다.
 */
data class ProfileSuggestion(
    val id: String,
    val key: String,
    val value: String,
    /** 추출 원본 에피소드 — 삭제된 에피소드일 수 있으므로 참조 무결성은 강제하지 않는다. */
    val episodeId: String?,
    val status: ProfileSuggestionStatus,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * PENDING → ACCEPTED(프로필에 source=auto 로 저장) | REJECTED(같은 키-값은 재제안 금지).
 */
enum class ProfileSuggestionStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

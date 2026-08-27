package com.kosmos.app.domain.model

/**
 * 프로필 키-값 항목 — 항상-관련 기억(3층 기억 모델의 Profile 층, C′1).
 * 예: "이름" → "진우", "말투" → "친근하게, 존댓말".
 */
data class ProfileEntry(
    val key: String,
    val value: String,
    val source: String = SOURCE_MANUAL,
    val updatedAt: Long = 0L
) {
    companion object {
        /** 사용자가 드로어 카드에서 직접 입력. */
        const val SOURCE_MANUAL = "manual"

        /** C′2 리셋 시점 자동 추출이 쓸 예약값 — 지금은 생산처 없음. */
        const val SOURCE_AUTO = "auto"
    }
}

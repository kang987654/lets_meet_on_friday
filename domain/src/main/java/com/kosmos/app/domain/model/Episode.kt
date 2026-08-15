package com.kosmos.app.domain.model

/**
 * [Episode]
 * 대화 타임라인에서 자동 분절된 하나의 주제 단위입니다 (ADR-022 — 세션은 저장 개념).
 *
 * ### Architecture Context
 * - **Layer**: Domain (Model)
 * - **Dependencies**: 없음
 *
 * [WHY] 사용자는 세션을 만들지 않는다 — 경계(무활동 30분 / 예산 리셋)가 자동으로 에피소드를
 * 닫고, oneShot 요약이 제목/태그/요약을 채워 검색 가능한 문서로 만든다. 원문 메시지는
 * `conversation.episodeId` 로 이 에피소드에 귀속된다.
 *
 * [WHY] 요약 전에는 [title]/[summary] 가 null 이다 — 상태 기계([EpisodeStatus])가 요약
 * 파이프라인의 진실이고, 인프로세스 큐는 그 캐시일 뿐이다(강제종료 후 catch-up 이 status 로
 * 복원한다).
 */
data class Episode(
    val id: String,
    val sessionId: String,
    val status: EpisodeStatus,
    val title: String?,
    val summary: String?,
    val tags: List<String>,
    val startAt: Long,
    val endAt: Long?,
    val messageCount: Int,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * 에피소드 수명주기.
 *
 * OPEN → (경계 감지) → CLOSED → (요약 성공) → SUMMARIZED
 *                              → (3회 실패) → FAILED
 *
 * [WHY] FAILED 는 아카이브에 노출하지 않되 원문 대화는 타임라인에 그대로 남는다 — 요약 실패가
 * 데이터 손실이 되지 않게 하는 계약이다.
 */
enum class EpisodeStatus {
    OPEN,
    CLOSED,
    SUMMARIZED,
    FAILED
}

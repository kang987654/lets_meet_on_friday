package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"]),
        Index(value = ["episodeId"])
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val inputType: String, // "TEXT", "VOICE", "IMAGE"
    val searchUsed: Boolean,
    val createdAt: Long,
    /**
     * 이 메시지가 속한 에피소드 (ADR-022). NULL = 미배정 — catch-up 이 소급 배정하는 대상.
     *
     * [WHY] 시간 범위 추정이 아니라 정확한 멤버십이어야 "원문 대화로 이동"과 messageCount 가
     * 어긋나지 않는다. 인덱스는 getByEpisode/getUnassigned 때문.
     */
    val episodeId: String? = null,
    /**
     * 이 답변이 참조한 에피소드 id 들 (CSV, 최대 3건 — SearchMemory 상위 3건).
     *
     * [WHY] 제목은 저장하지 않는다 — 에피소드를 수정하면 저장된 제목이 낡는 비정규화를 피하고,
     * 렌더 시 episode 테이블에서 id 로 해석한다. thinkingProcess 가 Entity 칼럼이 없어 재로드
     * 시 소실됐던 전례가 있으므로 칩 데이터는 처음부터 영속한다.
     */
    val recallEpisodeIds: String? = null
)

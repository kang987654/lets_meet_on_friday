package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [ProfileSuggestionEntity]
 * 자동 추출된 프로필 급 사실의 승인 대기 행 (C′2, 스키마 v9).
 *
 * [WHY] `profile` 테이블에 status 를 얹지 않는다 — `profile` 은 key PK 라 대기 중 제안이
 * 기존 항목을 덮어쓰고, 렌더 경로가 상태 필터를 알아야 해 바이트-안정 계약이 복잡해진다.
 * 별도 테이블이 값싸고 거절 이력(재제안 금지)도 자연스럽다.
 */
@Entity(
    tableName = "profile_suggestion",
    indices = [Index(value = ["status"])]
)
data class ProfileSuggestionEntity(
    @PrimaryKey
    val id: String,
    val key: String,
    val value: String,
    val episodeId: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

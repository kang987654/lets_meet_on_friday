package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episode",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
/**
 * [WHY] `knowledge_note` 에 kind 판별자로 편입하지 않고 별도 테이블이다 — 기존 소비처
 * (메모 목록·백업 내보내기)가 필터를 빠뜨리면 에피소드가 메모 UI 에 새는 무음 실패 모드가
 * 생기고, 상태 기계·시간 범위·재시도 카운트라는 형상 자체가 다르다 (ADR-022 구현 결정).
 *
 * [WHY] `status` 인덱스는 catch-up 이 앱 시작마다 `getByStatus(OPEN/CLOSED/FAILED)` 를 훑기
 * 때문이고, `createdAt` 인덱스는 아카이브 목록(최신순 페이징) 때문이다.
 */
data class EpisodeEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val status: String, // "OPEN" | "CLOSED" | "SUMMARIZED" | "FAILED"
    val title: String?, // 요약 전 NULL
    val summary: String?, // 요약 전 NULL
    val tags: String, // CSV (knowledge_note.tags 와 같은 형식·같은 정규화)
    val startAt: Long,
    val endAt: Long?, // OPEN 동안 NULL
    val messageCount: Int,
    val retryCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

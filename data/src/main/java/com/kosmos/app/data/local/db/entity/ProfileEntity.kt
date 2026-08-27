package com.kosmos.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [ProfileEntryEntity]
 * 프로필 키-값 항목 — 시스템 지시에 상시 주입되는 소량 기억입니다 (C′1, 스키마 v8).
 *
 * [WHY] v7 까지는 고정 컬럼 2개(name, style)의 단일 행이었고 호출처 0곳의 사문 스키마였다.
 * "키-값 강제" 스펙(expand.md Track C′)에 맞춰 행 단위로 재설계 — 데이터 0행이라 무손실.
 * source 는 "manual"(사용자 직접) / "auto"(C′2 자동 추출 예정) — 출처 표시는 관리 장치 스펙.
 */
@Entity(tableName = "profile")
data class ProfileEntryEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val source: String,
    val updatedAt: Long
)

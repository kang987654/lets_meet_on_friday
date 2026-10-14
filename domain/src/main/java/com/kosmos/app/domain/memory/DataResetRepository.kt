package com.kosmos.app.domain.memory

import com.kosmos.app.core.common.AppResult

/**
 * 사용자가 설정에서 고르는 초기화 대상 — 무엇을 지우는지가 곧 화면의 체크 항목이다.
 *
 * - [CONVERSATIONS]: 채팅 메시지(브리핑 카드 포함)·기억 요약(에피소드)·감사 기록·호칭 제안 카드 — 대화에서 파생된 것까지 함께 지운다.
 * - [TASKS]: 할 일 전체(완료 포함). 리마인더 알람 취소는 호출측 몫(플랫폼 관심사).
 * - [KNOWLEDGE]: 지식 노트 전체(직접·자동 저장).
 * - [PROFILE]: 프로필 항목과 호칭 제안 카드.
 */
enum class ResetTarget { CONVERSATIONS, TASKS, KNOWLEDGE, PROFILE }

/**
 * [DataResetRepository]
 * 고른 대상의 데이터를 한 번에 지웁니다(0.37.1). 설정·모델 파일·최근 문서 목록은 대상이 아니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain 계약 — 구현은 data 모듈이 DB 트랜잭션 하나로 한다(일부만 지워지는 상태를 남기지 않는다).
 */
interface DataResetRepository {
    suspend fun reset(targets: Set<ResetTarget>): AppResult<Unit>
}

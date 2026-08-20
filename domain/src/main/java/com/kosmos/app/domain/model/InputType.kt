package com.kosmos.app.domain.model

enum class InputType {
    TEXT,
    VOICE,
    IMAGE,

    /**
     * 비서가 스스로 시작한 아침 브리핑 발화 (A4).
     * [WHY] 본래 "사용자 입력 양식" 축이지만, 브리핑 카드를 구분 렌더할 유일한 무마이그레이션
     * 경로다 — DB 는 문자열 저장이고 미지 값은 읽기에서 TEXT 로 폴백하므로(구버전 안전)
     * 스키마 변경 없이 추가된다. 사용자 메시지에는 절대 쓰지 않는다.
     */
    BRIEFING
}

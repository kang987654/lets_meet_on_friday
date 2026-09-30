package com.kosmos.app.core.common

/**
 * 응답 스타일 설정값(DataStore 에 문자열로 저장)의 단일 출처입니다.
 *
 * [WHY] 같은 리터럴이 설정 저장소·설정 화면·컨텍스트 조립·프롬프트 조립 네 곳에 흩어져 있었다 —
 * 한 곳의 오타가 조용히 "기본 스타일"로 떨어진다. 저장된 값과 호환되도록 문자열 자체는 바꾸지 않는다.
 */
object ResponseStyle {
    const val DEFAULT = "DEFAULT"
    const val CONCISE = "CONCISE"
    const val DETAILED = "DETAILED"
}

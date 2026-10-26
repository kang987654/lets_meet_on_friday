package com.kosmos.app.domain.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 되묻기 판정(0.38.7 M5) — 되묻기에 불린 메모 저장을 앱이 막는 기준. */
class RepeatRequestTest {

    @Test
    fun `되묻기`() {
        listOf("송장번호 다시 알려줘", "사물함 번호 다시 말해 줘", "예약 번호 뭐였지?", "아까 뭐라고 했더라", "회의 시간 다시 얘기해 줄래?")
            .forEach { assertTrue(it, RepeatRequest.matches(it)) }
    }

    @Test
    fun `저장 요청이 함께 있거나 되묻기가 아니면 아니다`() {
        listOf("송장번호 다시 알려줄 테니 기억해 둬", "주차 위치 저장해줘", "내 자전거 비밀번호 4821이야", "잊지 마, 다시 말해 줄게", "점심 메뉴 추천해 줘")
            .forEach { assertFalse(it, RepeatRequest.matches(it)) }
    }
}

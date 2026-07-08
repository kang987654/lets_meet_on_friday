package com.localfriday.app

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import org.junit.Rule
import org.junit.Before

/**
 * 챗봇 파이프라인(의도 분석 -> 컨텍스트 조립 -> 추론 -> 파싱)이 
 * 한 번에 끊기지 않고 동작하는지 확인하는 통합 테스트 기반 클래스입니다.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class AssistantPipelineTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun `기본 챗봇 파이프라인 구조 검증 테스트`() {
        // TODO: 향후 챗봇 파이프라인의 핵심인 AssistantOrchestrator를 주입받아
        // 가짜(Mock) 입력을 넣고, 올바른 ActionCard가 생성되는지 테스트할 예정입니다.
    }
}

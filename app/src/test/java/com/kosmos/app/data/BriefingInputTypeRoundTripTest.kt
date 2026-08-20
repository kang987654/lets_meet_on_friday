package com.kosmos.app.data

import com.kosmos.app.data.local.db.entity.ConversationEntity
import com.kosmos.app.data.local.repository.toDomain
import com.kosmos.app.domain.model.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BriefingInputTypeRoundTripTest]
 * BRIEFING 입력 유형의 저장 왕복과 미지 값 폴백을 못박습니다.
 *
 * [WHY] BRIEFING 은 enum 추가만으로 스키마 변경 없이 저장된다 — 그 전제가 "미지 문자열은
 * 읽기에서 TEXT 로 폴백"이다. 이 폴백이 깨지면 구버전으로 내려간 DB 가 크래시한다.
 */
class BriefingInputTypeRoundTripTest {

    private fun entity(inputType: String) = ConversationEntity(
        id = "m1", sessionId = "s1", role = "ASSISTANT",
        content = "좋은 아침이에요", inputType = inputType,
        searchUsed = false, createdAt = 1L
    )

    @Test
    fun `BRIEFING 은 왕복에서 보존된다`() {
        assertEquals(InputType.BRIEFING, entity(InputType.BRIEFING.name).toDomain().inputType)
    }

    @Test
    fun `미지 입력 유형 문자열은 TEXT 로 폴백한다`() {
        assertEquals(InputType.TEXT, entity("HOLOGRAM").toDomain().inputType)
    }
}

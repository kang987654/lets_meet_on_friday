package com.kosmos.app.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RecallConflictTest]
 * 메모와 다른 값을 적은 요약 판정 — exp53 의 실기기 축약 자료를 그대로 쓴다.
 */
class RecallConflictTest {

    private val bike = listOf("자전거 비밀번호는 134")

    @Test
    fun `메모의 값이 없고 다른 값을 적은 요약은 충돌이다`() {
        assertTrue(RecallConflict.contradicts("(과거 대화) 자전거 비밀번호 변경: 처음에 1234로 설정했으나, 이후 4321로 변경했습니다.", bike))
        assertTrue("짧은 메모도 주제 겹침 없이 잡는다", RecallConflict.contradicts("(과거 대화) 사물함 정리: 번호가 112번이라고 말했습니다.", listOf("회사 사물함 번호는 58번")))
    }

    @Test
    fun `메모의 값을 적었거나 숫자가 없거나 메모에 숫자가 없으면 충돌이 아니다`() {
        assertFalse("메모 값이 들어 있으면 유지", RecallConflict.contradicts("(과거 대화) 비서는 처음 134라고 답했고 이후 4321 얘기도 나왔습니다.", bike))
        assertFalse("숫자 없는 요약", RecallConflict.contradicts("(과거 대화) 자전거 비밀번호를 물었습니다.", bike))
        assertFalse("숫자 없는 메모", RecallConflict.contradicts("(과거 대화) 와이파이 비밀번호를 kosmos77로 바꿨습니다.", listOf("와이파이는 거실 공유기")))
    }

    @Test
    fun `날짜와 시각 숫자는 값으로 보지 않는다`() {
        assertEquals(listOf("4936"), RecallConflict.valuesIn("10월 3일 오후 2시에 자물쇠를 사서 비밀번호를 4936으로 맞췄다"))
        assertFalse(RecallConflict.contradicts("(과거 대화) 10월 3일 자물쇠 교체: 10월 3일에 비밀번호를 4936으로 맞췄습니다.", listOf("자물쇠 비밀번호는 4936")))
        assertFalse("날짜만 있는 요약", RecallConflict.contradicts("(과거 대화) 10월 3일에 자물쇠를 바꿨습니다.", listOf("자물쇠 비밀번호는 4936")))
    }

    @Test
    fun `다른 값은 메모 값을 빼고 순서대로 중복 없이 모은다`() {
        val eps = listOf("1234로 설정했다가 4321로 바꿈", "4521에서 1234, 다시 4321", "**4321**이라고 답함")
        assertEquals(listOf("1234", "4321", "4521"), RecallConflict.otherValues(eps, bike))
        assertEquals(listOf("1234"), RecallConflict.otherValues(eps, bike, max = 1))
    }

    @Test
    fun `여러 주제가 섞인 요약이나 무관한 주제의 요약에서는 다른 주제의 숫자를 값으로 섞지 않는다`() {
        val multiTopicEpisode = "(과거 대화) 회의실 및 비밀번호 이야기: 사용자는 다음 주 금요일 회의실이 5층 C룸이고 사물함 번호는 214번, 택배 송장번호는 6043 1122 9087이라고 말했어요. 이후 자전거 비밀번호를 4321로 바꿨다고 했어요."
        val unrelatedEpisode = "(과거 대화) 사물함과 송장번호: 사물함 번호는 214번이고 택배 송장번호는 6043 1122 9087이라고 말했어요."

        assertTrue(RecallConflict.contradicts(multiTopicEpisode, bike))
        assertEquals(listOf("4321"), RecallConflict.otherValues(listOf(multiTopicEpisode), bike))

        assertFalse("자전거가 전혀 언급되지 않은 다른 주제 요약은 충돌이 아니다", RecallConflict.contradicts(unrelatedEpisode, bike))
        assertEquals(emptyList<String>(), RecallConflict.otherValues(listOf(unrelatedEpisode), bike))
    }
}

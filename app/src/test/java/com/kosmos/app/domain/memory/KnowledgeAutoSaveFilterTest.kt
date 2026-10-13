package com.kosmos.app.domain.memory

import com.kosmos.app.domain.memory.KnowledgeAutoSaveFilter.Skip
import com.kosmos.app.domain.model.KnowledgeNote
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [KnowledgeAutoSaveFilterTest]
 * 자동 저장 필터의 판정 표 — 실기기(2026-10-12)에서 새어 들어간 세 형태와, 막으면 안 되는 이웃 형태를 함께 못박는다.
 */
class KnowledgeAutoSaveFilterTest {

    private fun note(content: String) = KnowledgeNote(id = content, content = content, createdAt = 0, updatedAt = 0)

    @Test
    fun `저장하지 않을 이유 판정 표`() {
        val manual = listOf(note("자전거 비밀번호는 134"), note("와이파이 비번 kosmos123"))
        val rejected = listOf("저장할 내용: 커피 대신 녹차를 마신다.")
        val cases = listOf(
            "아침에 커피 대신 녹차를 마심" to Skip.REJECTED,
            "자전거 비밀번호: 4321" to Skip.CONFLICT,
            "3시 40분에 스트레칭하기 알림 설정 필요" to Skip.REMINDER_REQUEST,
            "오늘 7시에 물 마시라고 알려줘" to Skip.REMINDER_REQUEST,
            // 막으면 안 되는 이웃들
            "자전거 비밀번호는 134" to null, // 같은 숫자 — 중복 검사 몫이지 충돌이 아니다
            "현관 비밀번호 2580" to null, // 숫자가 다르지만 대상이 다르다
            "매일 아침 7시에 조깅 알림" to null, // 반복 습관
            "땅콩 알레르기가 있음" to null,
            "회사 주차는 B2 구역" to null
        )
        for ((fact, expected) in cases) {
            assertEquals(fact, expected, KnowledgeAutoSaveFilter.skipReason(fact, rejected, manual))
        }
    }

    @Test
    fun `태그는 노트 내용에 든 후보만 최대 개수까지`() {
        val episodeTags = listOf("알림", "자전거 비밀번호", "출시 준비", "녹차")

        assertEquals(listOf("녹차"), KnowledgeAutoSaveFilter.tagsFor("아침에 커피 대신 녹차를 마심", episodeTags, 3))
        assertEquals(listOf("자전거 비밀번호"), KnowledgeAutoSaveFilter.tagsFor("자전거비밀번호 134", episodeTags, 3))
        assertEquals(emptyList<String>(), KnowledgeAutoSaveFilter.tagsFor("땅콩 알레르기", episodeTags, 3))
        assertEquals(listOf("알림"), KnowledgeAutoSaveFilter.tagsFor("알림 녹차 출시 준비", episodeTags, 1))
    }
}

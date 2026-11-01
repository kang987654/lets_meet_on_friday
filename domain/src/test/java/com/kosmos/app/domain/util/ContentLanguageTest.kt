package com.kosmos.app.domain.util

import com.kosmos.app.core.common.AppLanguage
import com.kosmos.app.domain.model.ChatMessage
import com.kosmos.app.domain.model.InputType
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentLanguageTest {

    @Test
    fun `detect returns KO for Korean text even with short English acronyms`() {
        assertEquals(
            AppLanguage.KO,
            ContentLanguage.detect("내일 KTX 타고 부산 출장 가는데 와이파이 PIN은 0427이야", fallback = AppLanguage.EN)
        )
    }

    @Test
    fun `detect returns EN for English text`() {
        assertEquals(
            AppLanguage.EN,
            ContentLanguage.detect("My bike lock code is 134 and I prefer green tea in the morning.", fallback = AppLanguage.KO)
        )
    }

    @Test
    fun `detect returns fallback when text has only digits or symbols`() {
        assertEquals(AppLanguage.KO, ContentLanguage.detect("1234 - 5678!", fallback = AppLanguage.KO))
        assertEquals(AppLanguage.EN, ContentLanguage.detect("1234 - 5678!", fallback = AppLanguage.EN))
    }

    @Test
    fun `detectMessages prioritizes user messages over assistant replies`() {
        val messages = listOf(
            ChatMessage(
                id = "1",
                sessionId = "s",
                role = ChatMessage.Role.USER,
                content = "자전거 비밀번호는 134야",
                inputType = InputType.TEXT,
                createdAt = 1000L
            ),
            ChatMessage(
                id = "2",
                sessionId = "s",
                role = ChatMessage.Role.ASSISTANT,
                content = "I have noted your bike lock password as 134 for future reference in your memory.",
                inputType = InputType.TEXT,
                createdAt = 2000L
            )
        )
        assertEquals(AppLanguage.KO, ContentLanguage.detectMessages(messages, fallback = AppLanguage.EN))
    }
}

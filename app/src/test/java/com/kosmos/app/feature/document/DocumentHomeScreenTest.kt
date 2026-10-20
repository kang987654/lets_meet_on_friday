package com.kosmos.app.feature.document

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import com.kosmos.app.data.local.prefs.RecentDocumentsStore
import com.kosmos.app.domain.document.RecentDocument
import com.kosmos.app.platform.document.DocumentAccess
import com.kosmos.app.testing.InMemoryPreferences
import com.kosmos.app.ui.theme.KosmosTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [DocumentHomeScreenTest]
 * 문서 홈 — 빈 목록 안내, 최근 문서 표시, 휴지통으로 빼고 되돌리기 (0.34.0 M3, 0.38.3 M4 — 길게 누르기 확인 창을 바꿨다, 사용자 확인).
 */
@RunWith(RobolectricTestRunner::class)
class DocumentHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val store = RecentDocumentsStore(InMemoryPreferences())
    private val access = object : DocumentAccess {
        override suspend fun persist(uri: Uri, now: Long): RecentDocument? = null
        override fun hasAccess(uri: String) = true
        override fun release(uri: String) = Unit
    }

    private fun show() {
        composeRule.setContent { KosmosTheme { DocumentHomeScreen(viewModel = DocumentHomeViewModel(store, access), onBack = {}) } }
        composeRule.waitForIdle()
    }

    @Test
    fun `빈 목록이면 어디서 연 문서가 쌓이는지 알린다`() {
        show()
        composeRule.onNodeWithText("파일 열기").assertIsDisplayed()
        composeRule.onNodeWithText("여기서 연 문서가 쌓여요. 다른 앱에서 '연결 프로그램'으로 연 문서는 남지 않아요.").assertIsDisplayed()
    }

    @Test
    fun `최근 문서를 보여 주고 휴지통을 누르면 목록에서 빼고 되돌릴 수 있다`() {
        runBlocking { store.add(RecentDocument("content://a", "3분기 매출.xlsx", null, 0L)) }
        show()

        composeRule.onNodeWithText("3분기 매출.xlsx").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("최근 목록에서 빼기").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("'3분기 매출.xlsx'을(를) 목록에서 뺐어요").assertIsDisplayed()
        composeRule.onNodeWithText("되돌리기").performClick()
        composeRule.waitUntil(5_000) { runBlocking { store.recentFlow.first() }.isNotEmpty() }
        composeRule.onNodeWithText("3분기 매출.xlsx").assertIsDisplayed()
    }
}

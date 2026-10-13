package com.kosmos.app.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [TopAlignScrollDeltaTest]
 * 원문 이동 정렬(0.36.2)을 고정합니다 — reverseLayout 은 오프셋이 바닥 기준이라 부호를 헷갈리기 쉬워, 계산식과 함께
 * 실제 역방향 LazyColumn 에서 대상 윗변이 화면 윗변에 닿는지, 최근 항목은 바닥에서 멈추는지 본다.
 */
@RunWith(RobolectricTestRunner::class)
class TopAlignScrollDeltaTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `바닥에 놓인 짧은 항목은 음수만큼 스크롤해 윗변을 화면 위로 올린다`() {
        // 화면 1,000px 중 바닥에 200px 항목 — 800px 더 최신 쪽으로 가야 윗변이 화면 위에 닿는다.
        assertEquals(-800, topAlignScrollDelta(itemOffset = 0, itemSize = 200, visibleEnd = 1_000))
        assertEquals("화면보다 긴 항목은 내려서 윗변을 보인다", 500, topAlignScrollDelta(0, 1_500, 1_000))
        assertEquals(0, topAlignScrollDelta(itemOffset = 600, itemSize = 400, visibleEnd = 1_000))
    }

    private fun setList(): Pair<LazyListState, CoroutineScope> {
        lateinit var state: LazyListState
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            LazyColumn(
                state = state,
                reverseLayout = true,
                modifier = Modifier.height(500.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(30) { Box(Modifier.fillMaxWidth().height(100.dp)) }
            }
        }
        composeRule.waitForIdle()
        return state to scope
    }

    private fun jump(state: LazyListState, scope: CoroutineScope, index: Int) {
        scope.launch {
            state.scrollToItem(index)
            alignItemTop(state, index)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `먼 과거 항목은 윗변이 목록 화면 윗변에 닿는다`() {
        val (state, scope) = setList()

        jump(state, scope, 20)

        val info = state.layoutInfo
        val item = info.visibleItemsInfo.first { it.index == 20 }
        assertEquals(info.viewportEndOffset - info.afterContentPadding, item.offset + item.size)
        assertTrue("더 최신 항목들이 그 아래에 보인다", info.visibleItemsInfo.any { it.index < 20 })
    }

    @Test
    fun `아래 대화가 한 화면이 안 되는 최근 항목은 바닥에서 멈추고 그대로 보인다`() {
        val (state, scope) = setList()

        jump(state, scope, 1)

        assertFalse("바닥(최신)에서 멈춘다", state.canScrollBackward)
        assertTrue(state.layoutInfo.visibleItemsInfo.any { it.index == 1 })
    }
}

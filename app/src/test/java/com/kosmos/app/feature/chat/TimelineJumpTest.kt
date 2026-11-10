package com.kosmos.app.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [TimelineJumpTest]
 * 원문 이동이 목표에 머무는지 고정합니다 (0.41.10 M3) — 자리표시자(40dp)가 정렬 뒤에 실제 높이로 커지면 reverseLayout 목록이 바닥 쪽을 기준으로
 * 자리를 지켜 목표가 화면 위로 밀려났다(0.41.9 휴대폰: 다음 대화의 긴 답에 도착). 목표 아래 이웃까지 채운 뒤 정렬하면 목표 윗변이 화면 윗변에 남는다.
 */
@RunWith(RobolectricTestRunner::class)
class TimelineJumpTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = mutableStateMapOf<Int, Boolean>()

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
                // 채워지면 긴 말풍선(150dp), 아니면 자리표시자(40dp).
                items(40) { i -> Box(Modifier.fillMaxWidth().height(if (loaded[i] == true) 150.dp else 40.dp)) }
            }
        }
        composeRule.waitForIdle()
        return state to scope
    }

    private fun LazyListState.targetTopGap(index: Int): Int? {
        val info = layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        return (info.viewportEndOffset - info.afterContentPadding) - (item.offset + item.size)
    }

    @Test
    fun `목표와 아래 이웃을 채운 뒤 정렬하면 목표 윗변이 화면 윗변에 남는다`() {
        val (state, scope) = setList()

        scope.launch {
            scrollToTimelineTarget(state, 20) {
                jumpLoadRange(20).forEach { loaded[it] = true }
                true
            }
        }
        composeRule.waitForIdle()

        assertEquals(0, state.targetTopGap(20))
    }

    @Test
    fun `목표만 채우고 정렬하면 뒤늦게 커진 이웃이 목표를 밀어낸다(고친 결함의 재현)`() {
        val (state, scope) = setList()

        scope.launch {
            scrollToTimelineTarget(state, 20) {
                loaded[20] = true
                true
            }
        }
        composeRule.waitForIdle()
        // 화면에 들어온 이웃이 이제야 로드돼 커진다.
        (8 until 20).forEach { loaded[it] = true }
        composeRule.waitForIdle()

        val gap = state.targetTopGap(20)
        assertFalse("목표 윗변이 그대로 화면 윗변에 있다 — 재현이 안 됐다(gap=$gap)", gap == 0)
    }

    @Test
    fun `이웃 범위는 목표와 그 아래 열두 개이고 맨 앞에서 멈춘다`() {
        assertEquals(8..20, jumpLoadRange(20))
        assertEquals(0..5, jumpLoadRange(5))
    }
}

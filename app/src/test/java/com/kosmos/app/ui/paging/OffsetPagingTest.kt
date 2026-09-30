package com.kosmos.app.ui.paging

import androidx.paging.PagingSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [OffsetPagingTest]
 * offset 페이징이 앞쪽 로드에서 행을 **다시 넣지 않는지** 고정합니다 — 드로어 아카이브를 수정한 뒤
 * refresh 가 앵커(1)에서 60개를 읽고, 앞쪽 로드가 0..19 를 다시 읽어 중복 키로 크래시하던 회귀.
 */
class OffsetPagingTest {

    private val rows = (0 until 100).toList()
    private val source = DefaultPagingSource { offset, limit -> rows.drop(offset).take(limit) }

    private suspend fun page(params: PagingSource.LoadParams<Int>) =
        source.load(params) as PagingSource.LoadResult.Page<Int, Int>

    @Test
    fun `앵커에서 새로고침한 뒤 앞쪽 로드는 빈 구간만 채운다`() = runTest {
        val refreshed = page(PagingSource.LoadParams.Refresh(key = 1, loadSize = 60, placeholdersEnabled = false))
        assertEquals((1..60).toList(), refreshed.data)
        assertEquals(1, refreshed.itemsBefore)

        val prepended = page(PagingSource.LoadParams.Prepend(key = requireNotNull(refreshed.prevKey), loadSize = 20, placeholdersEnabled = false))
        assertEquals(listOf(0), prepended.data)
        assertNull(prepended.prevKey)

        val all = prepended.data + refreshed.data
        assertEquals("겹친 행이 없어야 한다", all.distinct(), all)
    }

    @Test
    fun `뒤쪽 로드는 이어서 읽고 끝에서 멈춘다`() = runTest {
        val first = page(PagingSource.LoadParams.Refresh(key = null, loadSize = 60, placeholdersEnabled = false))
        assertNull(first.prevKey)
        val next = page(PagingSource.LoadParams.Append(key = requireNotNull(first.nextKey), loadSize = 60, placeholdersEnabled = false))
        assertEquals((60 until 100).toList(), next.data)
        assertNull(next.nextKey)
    }

    @Test
    fun `앞쪽 로드 구간은 끝 오프셋 기준이다`() {
        assertEquals(OffsetRange(10, 20), offsetRange(PagingSource.LoadParams.Prepend(key = 30, loadSize = 20, placeholdersEnabled = false)))
        assertEquals(OffsetRange(0, 5), offsetRange(PagingSource.LoadParams.Prepend(key = 5, loadSize = 20, placeholdersEnabled = false)))
    }
}

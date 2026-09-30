package com.kosmos.app.ui.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * 전체 개수를 아는 offset/limit 소스 — placeholder 와 O(1) 점프를 지원합니다.
 *
 * ### Architecture Context
 * - **Layer**: UI (Paging)
 *
 * [WHY] [DefaultPagingSource] 는 총수를 모르므로 placeholder 를 만들 수 없고, 그러면
 * `scrollToItem(먼 인덱스)` 점프(에피소드 → 원문 대화 이동, 시안 A′ M2-5)가 불가능하다 —
 * 로드된 구간까지만 스크롤이 닿는다. 총수(itemsBefore/After)를 채우면 Paging 이 미로드
 * 구간을 placeholder 로 채우고, 점프 후 주변 페이지를 알아서 로드한다.
 *
 * [WHY] 타임라인은 **앵커 이전의 불변 집합**이라(ChatViewModel.timelineAnchor) 총수가 로드
 * 중에 변하지 않는다 — 가변 집합이라면 count 와 fetch 사이의 경합으로 placeholder 수가
 * 어긋난다. 이 소스를 다른 데 재사용할 때 그 전제를 확인할 것.
 */
class CountedPagingSource<T : Any>(
    private val count: suspend () -> Int,
    private val fetch: suspend (offset: Int, limit: Int) -> List<T>
) : PagingSource<Int, T>() {

    override val jumpingSupported: Boolean get() = true

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        // [WHY] key = offset(행 인덱스) — DefaultPagingSource 와 같은 이유로 페이지 번호를 쓰지 않는다.
        // 앞쪽 로드 구간은 offsetRange 가 계산한다(끝 오프셋 키 — 겹침 중복 방지).
        val range = offsetRange(params)

        return try {
            val total = count()
            val data = if (range.count > 0) fetch(range.start, range.count) else emptyList()
            LoadResult.Page(
                data = data,
                prevKey = range.prevKey,
                nextKey = if (data.isEmpty() || range.start + data.size >= total) null else range.start + data.size,
                itemsBefore = range.start,
                itemsAfter = (total - range.start - data.size).coerceAtLeast(0)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

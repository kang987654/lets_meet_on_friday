package com.kosmos.app.ui.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kosmos.app.core.common.AppResult

/**
 * offset/limit 리포지토리 메서드를 Paging3 소스로 감쌉니다.
 *
 * [WHY] `feature/memory` 에 있던 것을 여기로 옮겼다 — 0.7.4 에서 `:domain` 의 `androidx.paging`
 * 의존을 제거하면서 `Pager` 생성이 ViewModel 로 올라왔고, 소비자가 `feature.memory` 와
 * `feature.settings` 두 곳이 됐다.
 */
class DefaultPagingSource<T : Any>(
    private val fetch: suspend (offset: Int, limit: Int) -> List<T>
) : PagingSource<Int, T>() {

    // [WHY] 새로고침은 화면에 보이던 자리(앵커)에서 다시 읽는다. 절대 위치여야 하므로 Page 가
    // itemsBefore 를 싣는다 — 싣지 않으면 앵커가 "로드된 목록 안의 인덱스"라 오프셋으로 쓸 수 없다.
    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchor -> (anchor - state.config.initialLoadSize / 2).coerceAtLeast(0) }

    // [WHY] 구간 계산(특히 앞쪽 로드)은 offsetRange 가 단일 출처다 — CountedPagingSource 와 공유.
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val range = offsetRange(params)
        return try {
            val data = if (range.count > 0) fetch(range.start, range.count) else emptyList()
            LoadResult.Page(
                data = data,
                prevKey = range.prevKey,
                // 앞쪽 로드로 붙은 페이지의 다음은 이미 있는 페이지다 — 뒤쪽 끝은 가득 찼을 때만 이어 읽는다.
                nextKey = if (params is LoadParams.Prepend || data.size == range.count) range.start + data.size else null,
                itemsBefore = range.start,
                itemsAfter = LoadResult.Page.COUNT_UNDEFINED
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

/**
 * [AppResult] 를 반환하는 리포지토리 메서드를 [DefaultPagingSource] 의 `fetch` 계약에 맞춥니다.
 *
 * [WHY] Paging3 의 `load` 는 예외로 실패를 표현하므로 `AppResult.Failure` 를 던져야 한다.
 * 세 ViewModel 이 같은 `when` 을 인라인으로 복제하고 있었기에 한 곳으로 모았다.
 */
fun <T> AppResult<T>.unwrapForPaging(): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> throw IllegalStateException(error.toString())
}

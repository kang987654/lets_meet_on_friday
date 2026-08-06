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

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition
    }

    // [WHY] key를 페이지 번호로 두고 offset = page * loadSize로 계산하면 Paging3의
    // initialLoadSize(기본 pageSize×3)와 충돌해 행이 중복 로드된다(LazyColumn duplicate key 크래시).
    // key 자체를 offset(행 인덱스)으로 사용해 loadSize와 무관하게 정확한 구간을 읽는다.
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        return try {
            val data = fetch(offset, limit)
            LoadResult.Page(
                data = data,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (data.size < limit) null else offset + data.size
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

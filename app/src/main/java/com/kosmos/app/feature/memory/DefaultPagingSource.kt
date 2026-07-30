package com.kosmos.app.feature.memory

import androidx.paging.PagingSource
import androidx.paging.PagingState

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

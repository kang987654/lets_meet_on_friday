package com.kosmos.app.ui.feature.memory

import androidx.paging.PagingSource
import androidx.paging.PagingState

class DefaultPagingSource<T : Any>(
    private val fetch: suspend (offset: Int, limit: Int) -> List<T>
) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { position ->
            val page = state.closestPageToPosition(position)
            page?.prevKey?.plus(1) ?: page?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 0
        val limit = params.loadSize
        val offset = page * limit

        return try {
            val data = fetch(offset, limit)
            LoadResult.Page(
                data = data,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (data.isEmpty()) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

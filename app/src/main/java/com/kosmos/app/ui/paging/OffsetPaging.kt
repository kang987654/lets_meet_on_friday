package com.kosmos.app.ui.paging

import androidx.paging.PagingSource.LoadParams

/**
 * offset 키 페이징의 한 번 로드 구간 — `[start, start + count)`.
 *
 * [WHY] key 는 행 오프셋이다. 새로고침·뒤쪽 로드(Append)는 key 가 **시작** 오프셋이지만, **앞쪽
 * 로드(Prepend)의 key 는 끝 오프셋(배타)** 으로 두고 `[끝 - loadSize, 끝)` 만 읽는다. 예전에는 두 소스
 * 모두 prevKey = (offset - loadSize) 를 시작 오프셋으로 넘겨 loadSize 만큼 읽었는데, offset 이
 * loadSize 보다 작으면 0 으로 잘린 뒤 loadSize 를 통째로 읽어 **이미 있는 행을 다시 넣었다** —
 * LazyColumn 중복 키 크래시(2026-09-30 에뮬레이터: 드로어 아카이브에서 에피소드 수정 → refresh).
 */
internal data class OffsetRange(val start: Int, val count: Int) {
    val end: Int get() = start + count

    /** 이 구간 앞 페이지를 부를 키(= 이 구간의 시작 = 앞 페이지의 끝). 맨 앞이면 null. */
    val prevKey: Int? get() = if (start == 0) null else start
}

internal fun offsetRange(params: LoadParams<Int>): OffsetRange = when (params) {
    is LoadParams.Prepend -> {
        val end = params.key.coerceAtLeast(0)
        val begin = (end - params.loadSize).coerceAtLeast(0)
        OffsetRange(begin, end - begin)
    }
    else -> OffsetRange((params.key ?: 0).coerceAtLeast(0), params.loadSize)
}

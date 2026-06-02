package com.localfriday.app.domain.memory

import androidx.paging.PagingSource
import com.localfriday.app.core.common.AppResult
import com.localfriday.app.domain.model.KnowledgeNote

/**
 * [v1] 확장: 장기 메모리(지식) 관리 저장소
 * 시간 변환이나 Enum 매핑과 같은 mapper 책임은 구현체(Impl)에서 일관되게 처리해야 합니다.
 */
interface KnowledgeRepository {
    suspend fun save(note: KnowledgeNote): AppResult<Unit>
    suspend fun delete(noteId: String): AppResult<Unit>
    suspend fun search(query: String): AppResult<List<KnowledgeNote>>
    fun getPaged(): PagingSource<Int, KnowledgeNote>
}

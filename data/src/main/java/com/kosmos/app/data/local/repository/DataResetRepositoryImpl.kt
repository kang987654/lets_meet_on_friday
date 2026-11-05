package com.kosmos.app.data.local.repository

import androidx.room.withTransaction
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.data.local.db.KosmosDatabase
import com.kosmos.app.domain.memory.DataResetRepository
import com.kosmos.app.domain.memory.MemoryVectorKey
import com.kosmos.app.domain.memory.ResetTarget
import javax.inject.Inject

/**
 * [DataResetRepositoryImpl]
 * 고른 대상의 테이블을 **한 트랜잭션**으로 비웁니다.
 *
 * [WHY] DAO 마다 전체 삭제 쿼리를 늘리는 대신 테이블 이름 표로 지운다 — 대상 → 테이블 대응이 한눈에 보이고, Room 의 무효화
 * 트리거는 테이블 단위라 관찰 중인 목록(Flow·페이징)도 그대로 갱신된다. 이름은 이 표에만 있는 상수라 SQL 주입 여지가 없다.
 */
class DataResetRepositoryImpl @Inject constructor(
    private val db: KosmosDatabase
) : DataResetRepository {

    override suspend fun reset(targets: Set<ResetTarget>): AppResult<Unit> = dbWrite(TABLES, "reset $targets") {
        val tables = targets.flatMap { tablesOf(it) }.toSet()
        if (tables.isEmpty()) return@dbWrite
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            tables.forEach { sql.execSQL("DELETE FROM `$it`") }
            targets.mapNotNull { vectorKindOf(it) }.forEach { sql.execSQL("DELETE FROM `memory_vector` WHERE `kind` = ?", arrayOf(it)) }
        }
    }

    internal companion object {
        const val TABLES = "reset"

        fun tablesOf(target: ResetTarget): List<String> = when (target) {
            ResetTarget.CONVERSATIONS -> listOf("conversation", "episode", "audit_log", "profile_suggestion")
            ResetTarget.TASKS -> listOf("task_item")
            ResetTarget.KNOWLEDGE -> listOf("knowledge_note")
            ResetTarget.PROFILE -> listOf("profile", "profile_suggestion")
            // [WHY] 보관함은 문서·조각·색인을 함께 지운다 — 하나만 남으면 색인이 없는 조각을 가리킨다(0.41.0).
            ResetTarget.LIBRARY -> listOf("library_document", "library_chunk", "library_fts", "library_vector")
        }

        /**
         * 기억 뜻 벡터(0.41.3)는 메모·대화 두 대상이 한 표를 나눠 쓴다 — 표째 지우지 않고 그 종류만 지운다.
         *
         * [WHY] 메모만 초기화했는데 대화 벡터까지 지우면 다시 만들 일이 생기고, 남겨 두면 지워진 메모의 벡터가 쌓인다.
         */
        fun vectorKindOf(target: ResetTarget): String? = when (target) {
            ResetTarget.KNOWLEDGE -> MemoryVectorKey.Kind.NOTE.code
            ResetTarget.CONVERSATIONS -> MemoryVectorKey.Kind.EPISODE.code
            else -> null
        }
    }
}

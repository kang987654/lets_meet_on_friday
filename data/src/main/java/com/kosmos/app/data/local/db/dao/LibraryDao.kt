package com.kosmos.app.data.local.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.kosmos.app.data.local.db.entity.LibraryChunkEntity
import com.kosmos.app.data.local.db.entity.LibraryDocumentEntity
import com.kosmos.app.data.local.db.entity.LibraryFtsEntity
import com.kosmos.app.data.local.db.entity.LibraryVectorEntity
import kotlinx.coroutines.flow.Flow

/** 색인 적중 행 — FTS4 `matchinfo(t, 'x')` BLOB 을 함께 받는다(후보 점수는 `LibrarySearch.candidateScore`). */
data class LibraryFtsMatch(
    @ColumnInfo(name = "rowid") val rowId: Long,
    @ColumnInfo(name = "mi") val matchinfo: ByteArray
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_document ORDER BY addedAt DESC")
    fun observeDocuments(): Flow<List<LibraryDocumentEntity>>

    @Query("SELECT * FROM library_document WHERE id = :id")
    suspend fun document(id: String): LibraryDocumentEntity?

    @Query("SELECT * FROM library_document")
    suspend fun documents(): List<LibraryDocumentEntity>

    @Upsert
    suspend fun upsertDocument(document: LibraryDocumentEntity)

    @Query("UPDATE library_document SET status = :status, chunkCount = :chunkCount, charCount = :charCount, error = NULL WHERE id = :id")
    suspend fun markReady(id: String, status: String, chunkCount: Int, charCount: Int)

    @Query("UPDATE library_document SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: String, status: String, error: String?)

    @Query("UPDATE library_document SET status = :to, error = :error WHERE status = :from")
    suspend fun replaceStatus(from: String, to: String, error: String?): Int

    @Query("DELETE FROM library_document WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("SELECT COALESCE(SUM(charCount), 0) FROM library_document WHERE id != :excludeId")
    suspend fun totalChars(excludeId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChunks(chunks: List<LibraryChunkEntity>): List<Long>

    @Query("SELECT * FROM library_chunk WHERE documentId = :documentId ORDER BY idx")
    suspend fun chunksOf(documentId: String): List<LibraryChunkEntity>

    @Query("SELECT * FROM library_chunk WHERE rowId IN (:rowIds)")
    suspend fun chunksByRowId(rowIds: List<Long>): List<LibraryChunkEntity>

    @Query("SELECT * FROM library_chunk WHERE article = :article")
    suspend fun chunksByArticle(article: String): List<LibraryChunkEntity>

    @Query("SELECT COUNT(*) FROM library_chunk")
    suspend fun chunkCount(): Int

    @Query("DELETE FROM library_chunk WHERE documentId = :documentId")
    suspend fun deleteChunksOf(documentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(rows: List<LibraryFtsEntity>)

    @Query("DELETE FROM library_fts WHERE rowid IN (SELECT rowId FROM library_chunk WHERE documentId = :documentId)")
    suspend fun deleteFtsOf(documentId: String)

    /** 토큰 하나의 문서빈도 — 그 토큰을 가진 조각 수. */
    @Query("SELECT COUNT(*) FROM library_fts WHERE library_fts MATCH :token")
    suspend fun documentFrequency(token: String): Int

    @Query("SELECT rowid, matchinfo(library_fts, 'x') AS mi FROM library_fts WHERE library_fts MATCH :match")
    suspend fun match(match: String): List<LibraryFtsMatch>

    // ---- 뜻 벡터 (0.41.2)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVectors(rows: List<LibraryVectorEntity>)

    @Query("DELETE FROM library_vector WHERE documentId = :documentId")
    suspend fun deleteVectorsOf(documentId: String)

    @Query("DELETE FROM library_vector")
    suspend fun deleteAllVectors()

    /** 다른 모델로 만든 벡터 — 모델이 바뀌면 지운다. */
    @Query("DELETE FROM library_vector WHERE modelId != :modelId")
    suspend fun deleteVectorsNotOf(modelId: String)

    @Query("SELECT COALESCE(SUM(chunkCount), 0) FROM library_document WHERE status = :ready")
    suspend fun readyChunkCount(ready: String): Int

    @Query(
        "SELECT COUNT(*) FROM library_vector v JOIN library_document d ON v.documentId = d.id " +
            "WHERE v.modelId = :modelId AND d.status = :ready"
    )
    suspend fun embeddedChunkCount(modelId: String, ready: String): Int

    @Query(
        "SELECT COUNT(*) FROM library_document d WHERE d.status = :ready AND d.chunkCount > " +
            "(SELECT COUNT(*) FROM library_vector v WHERE v.documentId = d.id AND v.modelId = :modelId)"
    )
    suspend fun pendingDocumentCount(modelId: String, ready: String): Int

    @Query(
        "SELECT c.* FROM library_chunk c JOIN library_document d ON c.documentId = d.id " +
            "WHERE d.status = :ready AND (:documentId IS NULL OR c.documentId = :documentId) " +
            "AND NOT EXISTS (SELECT 1 FROM library_vector v WHERE v.chunkRowId = c.rowId AND v.modelId = :modelId) " +
            "ORDER BY c.rowId LIMIT :limit"
    )
    suspend fun chunksWithoutVector(modelId: String, documentId: String?, ready: String, limit: Int): List<LibraryChunkEntity>

    /** 벡터를 조각 id 순으로 쪽 단위로 — 전체를 한 번에 올리지 않는다. */
    @Query("SELECT * FROM library_vector WHERE modelId = :modelId AND chunkRowId > :after ORDER BY chunkRowId LIMIT :limit")
    suspend fun vectorsAfter(modelId: String, after: Long, limit: Int): List<LibraryVectorEntity>
}

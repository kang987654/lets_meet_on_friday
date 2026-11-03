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
}

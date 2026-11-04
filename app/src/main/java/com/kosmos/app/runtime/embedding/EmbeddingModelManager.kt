package com.kosmos.app.runtime.embedding

import android.content.Context
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.library.LibraryRepository
import com.kosmos.app.domain.tool.ModelDownloadStatus
import com.kosmos.app.work.EmbeddingModelDownloadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [EmbeddingModelManager]
 * 임베딩 모델(선택 설치, 0.41.2)의 받기·지우기·설치 여부입니다.
 *
 * ### Architecture Context
 * - **Layer**: Runtime (Embedding) — 다운로드는 [EmbeddingModelDownloadScheduler](채팅 모델과 같은 전경 작업), 실행은 [EmbeddingGemmaRunner].
 * - 설정 화면(받기·지우기)과 보관함(뜻 검색 준비 표시)이 같이 본다.
 *
 * ### Key Flow
 * 1. [download] — 앱 내부 `embedding/` 폴더로 받는다. 끝나면 화면이 [refresh] 를 불러 [installed] 를 갱신한다.
 * 2. [delete] — 받는 중이면 취소, 엔진을 내리고 파일(내부·외부)과 저장된 벡터를 지운다 → 검색은 즉시 바이그램.
 *
 * [WHY] 기본은 미설치(사용자 결정 2026-11-04) — 받기 약 485MB 라 채팅에 꼭 필요한 것이 아니다. 설치하면 보관함 검색이 결합 검색으로 올라간다.
 */
@Singleton
class EmbeddingModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val scheduler: EmbeddingModelDownloadScheduler,
    private val runner: EmbeddingGemmaRunner,
    private val library: LibraryRepository
) {
    private val _installed = MutableStateFlow(runner.installed)
    val installed: StateFlow<Boolean> = _installed.asStateFlow()

    val downloadStatus: Flow<ModelDownloadStatus> get() = scheduler.status

    fun refresh() {
        _installed.value = runner.installed
    }

    fun download() = scheduler.enqueue(Constants.EMBEDDING_MODEL_DOWNLOAD_URL, "${Constants.EMBEDDING_DIR_NAME}/${Constants.EMBEDDING_MODEL_FILENAME}")

    fun cancelDownload() = scheduler.cancel(deletePartial = false)

    fun acknowledgeDownload() = scheduler.acknowledge()

    suspend fun delete() {
        scheduler.cancel(deletePartial = true)
        runner.release()
        withContext(Dispatchers.IO) {
            listOfNotNull(context.getExternalFilesDir(Constants.EMBEDDING_DIR_NAME), File(context.filesDir, Constants.EMBEDDING_DIR_NAME))
                .forEach { dir -> File(dir, Constants.EMBEDDING_MODEL_FILENAME).delete() }
        }
        library.clearVectors()
        refresh()
    }
}

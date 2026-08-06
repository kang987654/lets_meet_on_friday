package com.kosmos.app.feature.memory

import android.net.Uri
import androidx.paging.PagingData
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.mapper.ErrorMessages
import com.kosmos.app.domain.memory.KnowledgeRepository
import com.kosmos.app.domain.memory.TaskRepository
import com.kosmos.app.domain.usecase.ExportMemoryUseCase
import com.kosmos.app.domain.usecase.ImportMemoryUseCase
import com.kosmos.app.platform.file.BackupFileWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * [MemoryBackupStateTest]
 * 내보내기/가져오기 결과가 콜백이 아니라 [BackupState]로 노출되는지 검증합니다.
 *
 * [WHY] 이 계층에 테스트가 없던 상태에서 UI 진입점을 새로 붙였다. 특히 가져오기 중복 실행
 * 가드는 실패하면 DB 를 두 번 덮어쓰는 데이터 파괴로 이어지므로 테스트로 고정한다.
 *
 * [WHY] `Uri` 를 다루므로 Robolectric 이 필요하다 — `MemoryViewModel` 은 `uri.toString()`
 * 만 쓰지만 `Uri.parse` 자체가 Android 구현이다.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MemoryBackupStateTest {

    private val exportUseCase: ExportMemoryUseCase = mockk()
    private val importUseCase: ImportMemoryUseCase = mockk()
    private val backupFileWriter: BackupFileWriter = mockk()
    private lateinit var viewModel: MemoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val knowledgeRepository: KnowledgeRepository = mockk {
            every { getPagedData() } returns flowOf(PagingData.empty())
        }
        val taskRepository: TaskRepository = mockk()

        viewModel = MemoryViewModel(
            knowledgeRepository = knowledgeRepository,
            taskRepository = taskRepository,
            exportMemoryUseCase = exportUseCase,
            importMemoryUseCase = importUseCase,
            backupFileWriter = backupFileWriter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tempZip(): File =
        File.createTempFile("kosmos_backup_", ".zip").apply { deleteOnExit() }

    private fun uri(): Uri = Uri.parse("content://test/backup.zip")

    // --- 내보내기 ---

    @Test
    fun `내보내기 성공하면 저장 대기 상태가 되고 파일이 전달된다`() = runTest {
        val zip = tempZip()
        coEvery { exportUseCase() } returns AppResult.Success(zip)

        viewModel.confirmExport()

        val state = viewModel.uiState.value.backup
        assertTrue("실제 상태: $state", state is BackupState.ReadyToSave)
        assertEquals(zip, (state as BackupState.ReadyToSave).file)
        // 저장 대화상자에 제안할 파일명이 zip 이름과 같아야 한다.
        assertEquals(zip.name, state.suggestedName)
    }

    @Test
    fun `내보내기 실패하면 사용자용 문구가 담긴 실패 상태가 된다`() = runTest {
        val error = AppError.ExportFailed("disk on fire")
        coEvery { exportUseCase() } returns AppResult.Failure(error)

        viewModel.confirmExport()

        val state = viewModel.uiState.value.backup
        assertTrue("실제 상태: $state", state is BackupState.Failed)
        assertEquals(ErrorMessages.userMessage(error), (state as BackupState.Failed).message)
    }

    @Test
    fun `저장 위치 선택을 취소하면 대기 상태가 정리된다`() = runTest {
        val zip = tempZip()
        coEvery { exportUseCase() } returns AppResult.Success(zip)
        viewModel.confirmExport()

        viewModel.onExportCancelled()

        assertEquals(BackupState.Idle, viewModel.uiState.value.backup)
        // [WHY] cacheDir 사본을 남기면 캐시 용량을 잡아먹는다.
        assertFalse("취소 시 임시 zip 이 삭제되어야 한다", zip.exists())
    }

    @Test
    fun `저장이 끝나면 유휴 상태로 돌아간다`() = runTest {
        val zip = tempZip()
        coEvery { exportUseCase() } returns AppResult.Success(zip)
        coEvery { backupFileWriter.copyTo(any(), any()) } returns AppResult.Success(Unit)
        viewModel.confirmExport()

        viewModel.saveExportTo(uri())

        assertEquals(BackupState.Idle, viewModel.uiState.value.backup)
        assertFalse("저장 후 임시 zip 이 삭제되어야 한다", zip.exists())
    }

    // --- 가져오기 ---

    @Test
    fun `가져오기 성공하면 재시작 대기 상태가 된다`() = runTest {
        coEvery { importUseCase(any()) } returns AppResult.Success(Unit)

        viewModel.importData(uri())

        // [WHY] ViewModel 은 재시작을 수행하지 않는다 — 사용자가 확인 버튼을 누른 시점에만
        // 프로세스를 죽여야 하므로 화면이 할 일이다.
        assertEquals(BackupState.ImportSucceeded, viewModel.uiState.value.backup)
    }

    @Test
    fun `가져오기 실패하면 사용자용 문구가 담긴 실패 상태가 된다`() = runTest {
        val error = AppError.ImportFailed("bad manifest")
        coEvery { importUseCase(any()) } returns AppResult.Failure(error)

        viewModel.importData(uri())

        val state = viewModel.uiState.value.backup
        assertTrue("실제 상태: $state", state is BackupState.Failed)
        assertEquals(ErrorMessages.userMessage(error), (state as BackupState.Failed).message)
    }

    @Test
    fun `가져오기가 끝난 뒤 재호출해도 두 번 실행되지 않는다`() = runTest {
        coEvery { importUseCase(any()) } returns AppResult.Success(Unit)

        viewModel.importData(uri())
        viewModel.importData(uri())

        // [WHY] 가져오기는 DB 파일을 통째로 교체하므로 재진입이 곧 데이터 파괴다.
        coVerify(exactly = 1) { importUseCase(any()) }
    }

    @Test
    fun `재시작 대기 상태는 닫을 수 없다`() = runTest {
        coEvery { importUseCase(any()) } returns AppResult.Success(Unit)
        viewModel.importData(uri())

        viewModel.dismissBackupState()

        // [WHY] DB 가 이미 교체됐으므로 계속 쓰면 화면의 페이징 캐시와 실제 데이터가 어긋난다.
        assertEquals(BackupState.ImportSucceeded, viewModel.uiState.value.backup)
    }

    @Test
    fun `실패 상태는 닫을 수 있다`() = runTest {
        coEvery { importUseCase(any()) } returns AppResult.Failure(AppError.ImportFailed("x"))
        viewModel.importData(uri())

        viewModel.dismissBackupState()

        assertEquals(BackupState.Idle, viewModel.uiState.value.backup)
    }

    // --- 경고 다이얼로그 ---

    @Test
    fun `내보내기 요청은 먼저 개인정보 경고를 띄운다`() = runTest {
        coEvery { exportUseCase() } returns AppResult.Success(tempZip())

        viewModel.requestExport()

        // prd.md F8 — 확인 전에는 zip 생성이 시작되지 않아야 한다.
        assertTrue(viewModel.uiState.value.showExportNotice)
        assertEquals(BackupState.Idle, viewModel.uiState.value.backup)
        coVerify(exactly = 0) { exportUseCase() }
    }

    @Test
    fun `가져오기 요청은 먼저 덮어쓰기 경고를 띄운다`() = runTest {
        viewModel.requestImport()

        assertTrue(viewModel.uiState.value.showImportWarning)
        coVerify(exactly = 0) { importUseCase(any()) }
    }

    @Test
    fun `경고를 확인하면 플래그가 내려간다`() = runTest {
        coEvery { exportUseCase() } returns AppResult.Success(tempZip())
        viewModel.requestExport()

        viewModel.confirmExport()

        assertFalse(viewModel.uiState.value.showExportNotice)
    }
}

package com.kosmos.app.feature.settings

import com.kosmos.app.data.local.prefs.SettingsDataStore
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.runtime.gemma.GemmaRuntimeManager
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * [SettingsViewModelTest]
 * 설정 화면의 모델 새로고침이 **엔진 준비까지 이어지는지** 고정합니다.
 *
 * [WHY] 2026-08-14 실기기 스모크: 뒤로가기로 나가면 백그라운드 해제(`modelRunner.close()`)가
 * 엔진을 지우는데, 새로고침이 `checkModelFile()` 만 불러 상태가 FileFound 에서 멈췄다 —
 * FileFound → Ready 로 옮기는 유일한 경로는 `warmUp` 이고, 그 반응은 스플래시 뷰모델에만
 * 있어 이 화면에는 없었다. "엔진 준비 중" 스피너가 영원히 도는 형태로 나타난다.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val runtimeManager: GemmaRuntimeManager = mockk(relaxed = true)
    private val modelRunner: ModelRunner = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `모델 새로고침은 파일 재탐색에서 멈추지 않고 warmUp 까지 부른다`() = runTest {
        val viewModel = SettingsViewModel(settingsDataStore, runtimeManager, modelRunner)

        viewModel.refreshModelState()

        // warmUp 이 파일 재탐색을 포함하므로(runtimeManager.checkModelFile 선행 호출),
        // 이 한 호출이 FileFound 에서 Ready 까지의 전체 경로를 보장한다.
        coVerify(exactly = 1) { modelRunner.warmUp() }
    }
}

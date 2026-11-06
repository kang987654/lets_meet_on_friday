package com.kosmos.app.platform.launch

import androidx.lifecycle.LifecycleOwner
import com.kosmos.app.domain.modelrunner.ModelRunner
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ModelWarmUpObserverTest]
 * 채팅 화면이 보일 때마다 warmUp 을 건다(재진입 구멍 방지 계약, 0.34.0 에서 프로세스 단위 → MainActivity 단위로 이동).
 * 해제는 이 옵저버의 책임이 아니다 — onStop 에는 아무것도 하지 않는다.
 */
class ModelWarmUpObserverTest {

    private val modelRunner: ModelRunner = mockk(relaxed = true)
    private val owner: LifecycleOwner = mockk()
    private val scope = TestScope(StandardTestDispatcher())
    private val observer = ModelWarmUpObserver(modelRunner) { scope }

    @Test
    fun `화면이 보일 때마다 warmUp 을 건다`() {
        observer.onStart(owner)
        scope.advanceUntilIdle()
        observer.onStart(owner)
        scope.advanceUntilIdle()

        coVerify(exactly = 2) { modelRunner.warmUp() }
    }

    @Test
    fun `화면이 가려져도 모델을 해제하지 않는다`() {
        observer.onStop(owner)
        scope.advanceUntilIdle()

        coVerify(exactly = 0) { modelRunner.close() }
        coVerify(exactly = 0) { modelRunner.warmUp() }
    }

    @Test
    fun `기억 뜻 벡터 준비도 채팅 화면이 보일 때만 알린다(0·41·6 M4)`() {
        var foregrounds = 0
        val withEmbedder = ModelWarmUpObserver(modelRunner, onForeground = { foregrounds++ }) { scope }

        withEmbedder.onStart(owner)
        withEmbedder.onStop(owner)
        withEmbedder.onStart(owner)
        scope.advanceUntilIdle()

        assertEquals(2, foregrounds)
    }
}

package com.kosmos.app.platform.launch

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kosmos.app.domain.modelrunner.ModelRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [ModelWarmUpObserver]
 * 채팅 화면(MainActivity)이 보일 때마다 모델을 데웁니다 — 멱등이라 엔진이 살아 있으면 초기화를 건너뛴다.
 *
 * ### Architecture Context
 * - **Layer**: Platform (Launch)
 * - **Dependencies**: [ModelRunner]
 *
 * ### Key Flow
 * 1. 붙은 LifecycleOwner 의 `onStart` → `scope(owner)` 에서 `warmUp()`.
 * 2. 해제는 여기서 하지 않는다 — 앱 전체가 백그라운드일 때 `KosmosApp` 의 프로세스 옵저버가 `close()` 한다.
 *
 * [WHY] 프로세스가 아니라 채팅 화면 단위다 — 프로세스 단위면 문서 뷰어만 열어도 3.6GB 로드가 시작된다. 재진입마다 데우는
 * 이유(스플래시가 낡은 Ready 를 보고 통과하는 구멍)는 그대로 막힌다 — 채팅 재진입은 언제나 이 onStart 를 지난다(ADR-027).
 */
class ModelWarmUpObserver(
    private val modelRunner: ModelRunner,
    private val scope: (LifecycleOwner) -> CoroutineScope = { it.lifecycleScope }
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        scope(owner).launch { modelRunner.warmUp() }
    }
}

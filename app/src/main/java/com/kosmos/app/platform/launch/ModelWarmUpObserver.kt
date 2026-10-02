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
 * [WHY] 예전에는 이 반응이 `KosmosApp` 의 **프로세스** 전경 진입에 붙어 있었다(2026-08-14 재진입 결함 수정).
 * 0.34.0 문서 뷰어는 별도 Activity 로 모델 없이 열려야 하는데, 프로세스 단위면 "연결 프로그램"으로 문서만 열어도
 * 3.6GB 로드가 시작된다. 채팅 화면 단위로 옮겨도 원래 구멍(빠른 재진입에서 스플래시가 낡은 Ready 를 보고 통과한 뒤
 * FileFound 로 내려가면 다시 데워줄 곳이 없음)은 똑같이 막힌다 — 재진입은 언제나 MainActivity 의 onStart 를 지난다.
 */
class ModelWarmUpObserver(
    private val modelRunner: ModelRunner,
    private val scope: (LifecycleOwner) -> CoroutineScope = { it.lifecycleScope }
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        scope(owner).launch { modelRunner.warmUp() }
    }
}

package com.kosmos.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.work.Configuration
import com.kosmos.app.platform.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KosmosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var modelRunner: com.kosmos.app.domain.modelrunner.ModelRunner

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * [WHY] 백그라운드 상주 컴포넌트 3종은 여기서 eager 주입하고 onCreate 에서 명시적으로
     * `start()` 한다(멱등). 구독을 init 에 두면 Hilt 테스트가 주입만으로 백그라운드 추론을
     * 일으키고(AGENTS §2-④), 반대로 아무도 참조하지 않으면 인스턴스화조차 되지 않는다.
     * HiltTestApplication 에서는 onCreate 가 불리지 않아 테스트는 자동 격리된다.
     */
    @Inject
    lateinit var briefingGenerator: com.kosmos.app.assistant.briefing.MorningBriefingGenerator

    @Inject
    lateinit var episodeBoundaryManager: com.kosmos.app.assistant.episode.EpisodeBoundaryManager

    @Inject
    lateinit var episodeSummarizeScheduler: com.kosmos.app.assistant.episode.EpisodeSummarizeScheduler

    @Inject
    lateinit var speechOutput: com.kosmos.app.platform.speech.SpeechOutput

    @Inject
    lateinit var memoryCleanupRunner: com.kosmos.app.assistant.cleanup.MemoryCleanupRunner

    /**
     * [WHY] @HiltWorker 로 만든 Worker 에 의존성을 주입하려면 WorkManager 의 기본 초기화를
     * 매니페스트에서 제거하고(WorkManagerInitializer node:remove) HiltWorkerFactory 를 물려야 한다.
     * WorkManager 2.9+ 에서 이 계약은 메서드가 아니라 프로퍼티다 — 옛 getWorkManagerConfiguration()
     * 형태로 쓰면 컴파일은 되지만 호출되지 않아 주입이 조용히 실패한다. (ADR-006)
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        briefingGenerator.start()
        episodeBoundaryManager.start()
        episodeSummarizeScheduler.start()
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            // [WHY] "FileFound 를 보면 warmUp" 반응이 스플래시 뷰모델에만 있으면, 프로세스가
            // 살아남은 재진입에서 구멍이 난다 — 아주 빠른 재진입은 close() 의 비동기 정리
            // (실측 onStop 후 ~0.5초)가 끝나기 전에 스플래시가 낡은 Ready 를 보고 통과하고,
            // 그 뒤 상태가 FileFound 로 내려가면 다시 데워줄 곳이 없다(2026-08-14 실기기:
            // OS 가 프로세스를 죽이면 정상, 살리면 스피너 정지 — 재현이 복불복이던 이유).
            // 포그라운드 진입마다 warmUp 을 건다 — 멱등이라(엔진 살아 있으면 초기화 건너뜀)
            // 콜드 스타트의 스플래시 warmUp 과 겹쳐도 뮤텍스로 직렬화되어 Ready 로 수렴한다.
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                super.onStart(owner)
                owner.lifecycleScope.launch {
                    modelRunner.warmUp()
                }
            }

            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                super.onStop(owner)
                // 앱 백그라운드 전환 시 즉시 모델 리소스를 해제하여 메모리(RAM) 및 GPU 반환
                android.util.Log.d("KosmosApp", "App entered background, releasing model resources.")
                modelRunner.close()
                // [WHY] 백그라운드 낭독은 하지 않는다(0.29.0) — 엔진 바인딩도 함께 놓고, 다음 낭독이 다시 만든다.
                speechOutput.release()
                // [WHY] 엔진이 방금 해제됐다 — 정리를 이어 가면 3.6GB 를 백그라운드에서 다시 올린다(§2-⑥). 다음 버튼이 처음부터.
                memoryCleanupRunner.cancel()
            }
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            modelRunner.close()
        }
    }
}

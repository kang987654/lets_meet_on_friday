package com.kosmos.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
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
            // [WHY] 전경 진입 warmUp 은 0.34.0 에서 MainActivity 단위(ModelWarmUpObserver)로 옮겼다 —
            // 문서 뷰어(별도 Activity)만 열 때 모델을 올리지 않기 위해서다. 해제는 앱 전체가 백그라운드일
            // 때만이라 여기 프로세스 단위에 남는다.
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

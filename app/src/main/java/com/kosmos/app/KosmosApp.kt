package com.kosmos.app

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kosmos.app.platform.notification.NotificationChannels
import com.kosmos.app.runtime.gemma.GemmaRuntimeManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KosmosApp : Application(), Configuration.Provider {

    @Inject
    lateinit var runtimeManager: GemmaRuntimeManager

    @Inject
    lateinit var modelRunner: com.kosmos.app.domain.modelrunner.ModelRunner

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                super.onStop(owner)
                // 앱 백그라운드 전환 시 즉시 모델 리소스를 해제하여 메모리(RAM) 및 GPU 반환
                android.util.Log.d("KosmosApp", "App entered background, releasing model resources.")
                modelRunner.close()
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

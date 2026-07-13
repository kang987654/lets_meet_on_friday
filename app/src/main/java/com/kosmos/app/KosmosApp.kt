package com.kosmos.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.kosmos.app.runtime.gemma.GemmaRuntimeManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class KosmosApp : Application() {

    @Inject
    lateinit var runtimeManager: GemmaRuntimeManager

    @Inject
    lateinit var modelRunner: com.kosmos.app.domain.modelrunner.ModelRunner

    override fun onCreate() {
        super.onCreate()
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

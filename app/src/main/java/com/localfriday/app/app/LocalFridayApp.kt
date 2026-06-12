package com.localfriday.app.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.localfriday.app.runtime.gemma.GemmaRuntimeManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LocalFridayApp : Application() {

    @Inject
    lateinit var runtimeManager: GemmaRuntimeManager

    @Inject
    lateinit var modelRunner: com.localfriday.app.domain.modelrunner.ModelRunner

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // 백그라운드 전환 시 메모리 압박이 심해지면 로컬 모델 메모리를 해제하여 OOM 방지
            modelRunner.close()
        }
    }
}

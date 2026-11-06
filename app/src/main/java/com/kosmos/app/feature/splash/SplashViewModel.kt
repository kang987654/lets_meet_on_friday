package com.kosmos.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import com.kosmos.app.domain.startup.StartupProgress
import com.kosmos.app.domain.startup.StartupStage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 시작 화면 — 엔진 상태와 엔진 Ready 뒤의 시작 단계(0.41.7 M2)를 화면에 내린다.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val modelRunner: ModelRunner,
    startupProgress: StartupProgress = StartupProgress.NONE
) : ViewModel() {
    val loadState: StateFlow<ModelLoadState> = modelRunner.loadState

    /** 엔진 Ready 뒤 채팅을 열기 전의 단계 — [StartupStage.running] 이면 아직 채팅으로 가지 않는다. */
    val startupStage: StateFlow<StartupStage> = startupProgress.stage

    init {
        viewModelScope.launch {
            loadState.collect { state ->
                if (state is ModelLoadState.FileFound) {
                    modelRunner.warmUp()
                }
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            modelRunner.warmUp()
        }
    }
}

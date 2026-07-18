package com.kosmos.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.modelrunner.ModelRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val modelRunner: ModelRunner
) : ViewModel() {
    val loadState: StateFlow<ModelLoadState> = modelRunner.loadState

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

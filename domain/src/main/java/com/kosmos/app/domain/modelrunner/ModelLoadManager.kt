package com.kosmos.app.domain.modelrunner

import kotlinx.coroutines.flow.StateFlow

interface ModelLoadManager {
    val loadState: StateFlow<ModelLoadState>
    fun checkModelFile()
    fun setInitializing()
}

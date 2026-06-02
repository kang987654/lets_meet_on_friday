package com.localfriday.app.runtime.gemma

import android.content.Context
import com.localfriday.app.domain.modelrunner.ModelInfo
import com.localfriday.app.domain.modelrunner.ModelLoadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaRuntimeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
    val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    // TODO(v0): 실제 사용 시 설정(SettingsDataStore)에서 모델 경로를 읽어오는 구조로 개선 가능
    private val defaultModelFileName = "gemma-2b-it-gpu-int4.bin"

    init {
        checkModelFile()
    }

    fun checkModelFile() {
        val modelsDir = context.getExternalFilesDir("models")
        if (modelsDir == null) {
            _loadState.value = ModelLoadState.NotFound("External storage not available/models")
            return
        }

        val modelFile = File(modelsDir, defaultModelFileName)
        if (modelFile.exists()) {
            _loadState.value = ModelLoadState.Ready(
                ModelInfo(
                    modelId = "gemma-2b-it",
                    modelPath = modelFile.absolutePath,
                    modelVersion = "2b",
                    quantization = "int4",
                    lastLoadedAt = System.currentTimeMillis()
                )
            )
        } else {
            _loadState.value = ModelLoadState.NotFound(modelFile.absolutePath)
        }
    }
}

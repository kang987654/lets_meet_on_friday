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
    private val defaultModelFileName = "gemma-4-e4b-it-int4.litertlm"

    // [최적화 노트]
    // 1. mmap 적용: LiteRT-LM LlmInferenceOptions의 setModelPath는 
    //    내부 C++ LiteRT 엔진에서 자동으로 mmap(Memory Mapped File) 방식을 사용하여 모델을 로드하므로 별도의 MappedByteBuffer 처리가 필요 없습니다.
    // 2. GPU Delegate: litertlm-android 라이브러리는 지원되는 기기에서 자동으로 GPU/NPU Delegate를 활성화하며,
    //    수동 설정 없이도 최적화된 성능을 발휘합니다.

    init {
        checkModelFile()
    }

    fun setInitializing() {
        _loadState.value = ModelLoadState.InitializingEngine
    }

    fun checkModelFile() {
        val externalModelsDir = context.getExternalFilesDir("models")
        val internalModelsDir = File(context.filesDir, "models")
        
        var modelFile: File? = null

        // 1. Check external dir first (Android Studio push target usually)
        if (externalModelsDir != null) {
            val externalFile = File(externalModelsDir, defaultModelFileName)
            if (externalFile.exists()) {
                modelFile = externalFile
            }
        }
        
        // 2. Check internal dir if not found (standalone push target)
        if (modelFile == null && internalModelsDir.exists()) {
            val internalFile = File(internalModelsDir, defaultModelFileName)
            if (internalFile.exists()) {
                modelFile = internalFile
            }
        }

        if (modelFile != null) {
            _loadState.value = ModelLoadState.Ready(
                ModelInfo(
                    modelId = "gemma-4-e4b-it",
                    modelPath = modelFile.absolutePath,
                    modelVersion = "4-e4b",
                    quantization = "int4",
                    lastLoadedAt = System.currentTimeMillis()
                )
            )
        } else {
            _loadState.value = ModelLoadState.NotFound(
                externalModelsDir?.absolutePath?.plus("/$defaultModelFileName") 
                    ?: "Internal/External models directory"
            )
        }
    }
}

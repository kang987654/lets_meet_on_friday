package com.kosmos.app.runtime.gemma

import android.content.Context
import com.kosmos.app.domain.modelrunner.ModelInfo
import com.kosmos.app.domain.modelrunner.ModelLoadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GemmaRuntimeManager]
 * 디바이스 내의 Gemma 모델 파일 존재 여부를 확인하고 로드 상태를 관리하는 매니저 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Runtime / Infra
 * - **Dependencies**: Android [Context] (파일 시스템 접근)
 *
 * ### Key Flow
 * 1. 외부/내부 저장소에서 지정된 `.litertlm` 모델 파일 스캔
 * 2. 파일 발견 시 모델 경로, 버전, 양자화 정보 등을 담은 [ModelInfo] 생성
 * 3. 전체 시스템에 모델 로드 상태([ModelLoadState])를 Flow로 브로드캐스트
 */
import com.kosmos.app.domain.modelrunner.ModelLoadManager

@Singleton
class GemmaRuntimeManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ModelLoadManager {
    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

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

    override fun setInitializing() {
        _loadState.value = ModelLoadState.InitializingEngine
    }

    override fun setReady(modelInfo: ModelInfo) {
        _loadState.value = ModelLoadState.Ready(modelInfo)
    }

    override fun checkModelFile() {
        val externalModelsDir = context.getExternalFilesDir("models")
        val internalModelsDir = File(context.filesDir, "models")
        
        var modelFile: File? = null

        // 1. Check external dir first (Android Studio push target usually)
        if (externalModelsDir != null && externalModelsDir.exists()) {
            val externalFile = externalModelsDir.listFiles()?.firstOrNull { it.name.endsWith(".litertlm") }
            if (externalFile != null) {
                modelFile = externalFile
            }
        }
        
        // 2. Check internal dir if not found (standalone push target or Downloaded via App)
        if (modelFile == null && internalModelsDir.exists()) {
            // Find the most recently modified .litertlm file
            val internalFile = internalModelsDir.listFiles()
                ?.filter { it.name.endsWith(".litertlm") }
                ?.maxByOrNull { it.lastModified() }
            if (internalFile != null) {
                modelFile = internalFile
            }
        }

        if (modelFile != null) {
            _loadState.value = ModelLoadState.FileFound(
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

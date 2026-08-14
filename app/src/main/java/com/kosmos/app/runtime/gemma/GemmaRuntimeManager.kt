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
    @param:ApplicationContext private val context: Context
) : ModelLoadManager {
    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    // TODO(v0): 실제 사용 시 설정(SettingsDataStore)에서 모델 경로를 읽어오는 구조로 개선 가능
    private val defaultModelFileName = com.kosmos.app.core.common.Constants.DEFAULT_MODEL_FILENAME

    // [WHY] 모델 로드는 네이티브 엔진이 mmap 으로 처리하므로 Kotlin 쪽 MappedByteBuffer 가
    // 필요 없다. (예전 노트의 "GPU Delegate 자동 활성화" 주장은 삭제 — 실제로는
    // GemmaModelRunner.ensureInferenceInitialized 가 GPU 를 **수동 선택**하고 실패 시 CPU 로
    // 폴백한다. 주석이 코드와 정반대였다.)

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
            // [WHY] firstOrNull 은 파일시스템 나열 순서(비결정적)라 옛 모델이 남아 있으면 그게
            // 잡힐 수 있다. 내부 저장소 분기와 같은 기준(최신 수정 파일)으로 통일한다.
            val externalFile = externalModelsDir.listFiles()
                ?.filter { it.name.endsWith(".litertlm") }
                ?.maxByOrNull { it.lastModified() }
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
            // [WHY] 툴 호출 지원은 모델 파일에 달렸다(Gemma 4 = 지원, Gemma 3n = 미지원).
            // 어느 파일이 실행됐는지는 실기기 진단의 첫 단서라 로그로 남긴다.
            android.util.Log.i(
                "GemmaRuntimeManager",
                "model file: ${modelFile.name} (${modelFile.length()} bytes) at ${modelFile.parent}"
            )
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

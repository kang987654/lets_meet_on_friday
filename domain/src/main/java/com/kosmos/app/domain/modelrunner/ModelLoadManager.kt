package com.kosmos.app.domain.modelrunner

import com.kosmos.app.core.common.AppError
import kotlinx.coroutines.flow.StateFlow

/**
 * 모델 파일 탐색과 엔진 로드 상태([ModelLoadState])의 단일 출처입니다.
 */
interface ModelLoadManager {
    val loadState: StateFlow<ModelLoadState>
    fun checkModelFile()
    fun setInitializing()
    fun setReady(modelInfo: ModelInfo)

    /**
     * 엔진 초기화가 모든 백엔드에서 실패했음을 알립니다 — 스플래시가 오류와 "다시 시도"를 띄운다.
     *
     * [WHY] 기본 구현은 없음(no-op)이다. 로드 상태를 흉내 내는 테스트 대역들은 초기화 실패 경로를
     * 타지 않으므로, 실구현([ModelLoadState.Error] 를 방출하는 쪽)만 재정의하면 된다.
     */
    fun setError(error: AppError) {}
}

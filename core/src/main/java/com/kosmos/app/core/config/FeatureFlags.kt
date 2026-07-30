package com.kosmos.app.core.config

/**
 * [FeatureFlags]
 * 스트리밍 응답, 벡터 검색, 백업 암호화 등 실험적 기능의 활성화 여부를 전역적으로 통제하는 피처 플래그 상수입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Config)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 각 모듈(AI 엔진, 백업 유즈케이스 등)에서 해당 스위치를 조율하여 기능 실행 스코프를 제한합니다.
 */
object FeatureFlags {
    const val STREAMING_RESPONSE_ENABLED = false
    const val VECTOR_SEARCH_ENABLED = false
    const val EXPORT_ENCRYPTION_ENABLED = false
    const val AUDIO_INPUT_TO_MODEL_ENABLED = false
}

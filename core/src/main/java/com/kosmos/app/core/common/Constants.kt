package com.kosmos.app.core.common

/**
 * [Constants]
 * 프로젝트 전역에서 공유되는 시스템, 모델 설정 및 제약사항 상수를 정의하는 싱글톤 객체입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Common)
 * - **Dependencies**: 없음
 *
 * ### Key Flow
 * 1. 맥락 윈도우, 열 관리, 파일 크기 제약, 기본 모델 정보 등 공통 제어 상수를 제공합니다.
 */
object Constants {
    const val MAX_CONTEXT_TOKENS = 4096
    const val MAX_CONVERSATION_TURNS = 5
    const val MAX_KNOWLEDGE_CONTEXT_ITEMS = 3
    const val MAX_INPUT_CHARS = 8192
    const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
    const val MAX_IMAGE_DIMENSION_PX = 1024
    const val THERMAL_WARNING_CELSIUS = 43f
    const val THERMAL_SHUTDOWN_CELSIUS = 48f
    const val THERMAL_COOLDOWN_INFERENCE_COUNT = 5
    const val MODEL_DIR_NAME = "models"
    const val DEFAULT_MODEL_FILENAME = "gemma-4-e4b-it-int4.litertlm"
    const val DEFAULT_MODEL_DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    const val CALENDAR_DRAFT_MIN_CONFIDENCE = 0.7f
}


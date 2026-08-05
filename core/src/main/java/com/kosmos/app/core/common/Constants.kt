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
    const val DATABASE_NAME = "kosmos_db"
    const val MODEL_DIR_NAME = "models"
    // [WHY] 공식 다운로드 URL이 실제로 생성하는 파일명과 일치시켜, NotFound 안내가
    // 사용자가 절대 만들 수 없는 파일명을 요구하지 않도록 한다.
    const val DEFAULT_MODEL_FILENAME = "gemma-4-E4B-it.litertlm"
    const val DEFAULT_MODEL_DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    const val CALENDAR_DRAFT_MIN_CONFIDENCE = 0.7f

    // --- 모델 다운로드 (WorkManager 전경 작업, ADR-006) ---
    const val MODEL_DOWNLOAD_WORK_NAME = "model_download"
    // [WHY] 서버가 Content-Length 를 주지 않는 경우(chunked)에도 저장 공간 사전 점검을
    // 포기하지 않기 위한 보수적 하한값이다.
    const val EXPECTED_MODEL_SIZE_BYTES = 3_900_000_000L
    // [WHY] 모델 파일만 딱 들어가는 용량으로 다운로드를 허용하면 기기가 즉시 저장공간 부족에
    // 빠지므로 여유분을 요구한다.
    const val MODEL_DOWNLOAD_SPACE_SLACK_BYTES = 256L * 1024 * 1024
    const val MODEL_PART_SUFFIX = ".part"
    const val MODEL_PART_META_SUFFIX = ".part.meta"
    const val MODEL_BACKUP_SUFFIX = ".bak"
    const val MODEL_DOWNLOAD_MAX_ATTEMPTS = 5
}


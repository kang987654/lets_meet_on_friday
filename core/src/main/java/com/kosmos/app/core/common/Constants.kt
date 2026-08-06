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
    // [WHY] 프리필 전체 예산이다. 4096 이었으나 (a) 설정 슬라이더의 눈금(1000 단위)에 없는
    // 값이어서 사용자가 슬라이더를 만지는 순간 값이 튀었고, (b) 아래 오버헤드 예약을 도입하면
    // 히스토리에 남는 예산이 과도하게 줄어든다. 슬라이더 눈금과 맞는 6000 으로 올린다 —
    // gemma-4 컨텍스트는 32K 이고 `EngineConfig.maxNumTokens` 를 설정하지 않으므로 상한이 아니다.
    const val MAX_CONTEXT_TOKENS = 6000

    // [WHY] 시스템 지시 + 툴 선언 + few-shot 시범이 차지하는 프리필 오버헤드 추정치다.
    // 이전에는 대화 슬라이딩 윈도우가 `ChatMessage.content` 만 세고 이 오버헤드를 세지 않아,
    // "컨텍스트 윈도우 4096" 설정에도 실제 프리필이 6천 토큰을 넘었다 — 설정이 뜻대로 동작하지
    // 않았다. 툴 선언만 실측 ~2천 토큰이고(GemmaModelRunner 주석), 시스템 지시·few-shot 이
    // 더해진다. 보수적으로 잡아 예약한다.
    const val PREFILL_OVERHEAD_TOKENS = 2600

    // [WHY] 오버헤드 예약 후에도 히스토리에 최소한 남겨야 하는 예산. 사용자가 슬라이더를
    // 최소(1000)로 내려도 직전 대화 몇 턴은 유지되어야 대화가 성립한다.
    const val MIN_HISTORY_TOKENS = 500

    // [WHY] 슬라이딩 윈도우에 넣을 후보를 넉넉히 가져오는 수. 화면 히스토리 로드에도 같은 수를
    // 쓴다 — 이전에는 화면이 `getRecentBySession(sessionId)` 를 인자 없이 호출해 계약 기본값
    // (당시 5)이 적용됐고, **채팅을 열면 마지막 5개 메시지만 보였다.** 그 기본값은 제거했다.
    const val MAX_RECENT_CONVERSATIONS = 150

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


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
    /**
     * 엔진이 할당하는 KV 캐시 크기입니다. `EngineConfig.maxNumTokens` 로 **명시 전달**합니다.
     *
     * [WHY] 이 값이 아래 모든 토큰 예산의 출처다. 예전에는 넘기지 않았고, 주석에는
     * *"gemma-4 컨텍스트는 32K 이므로 상한이 아니다"* 라고 적혀 있었다. **두 군데가 틀렸다.**
     *
     * 첫째, **컨텍스트 창과 KV 할당량은 다른 것이다.** 모델 카드가 말하는 컨텍스트(E4B 128K)는
     * 모델이 *주의를 둘 수 있는* 상한이고, `maxNumTokens` 는 런타임이 *실제로 메모리를 잡는*
     * 크기다. 실측(`scratch/lab/measure_kv_memory.py`): 4096 → 5,003MB, 8192 → 9,188MB —
     * 늘린 토큰당 약 1MB다. 128K 를 KV 로 잡으려면 100GB 이상이 필요하니 온디바이스에서
     * 컨텍스트 창 전체를 할당한다는 선택지는 애초에 없다.
     *
     * 둘째, **넘기지 않으면 상한이 없어지는 것이 아니라 런타임 기본값(4096)이 된다.**
     * 실측(`scratch/lab/exp17_kv_capacity.py`): 5,857 토큰 프롬프트에서 `5857 >= 4096` 거부.
     * 그런데 앱 예산은 6000(슬라이더 최대 8000)이었으므로 **용량을 넘는 프롬프트를 의도적으로
     * 만들고 있었다.** 파이썬 0.15.0 은 오류를 내지만 AAR 0.14.0 은 실기기에서 오류 없이 답을
     * 냈다 — 초과분을 조용히 처리한다(2026-08-12).
     *
     * [WHY] 4096 을 유지한다(사용자 결정). 실기기에서 관측한 앱 PSS 5.5GB 가 위 4096 쪽 수치와
     * 같은 자릿수라 안드로이드 기본값도 4096 으로 보이며, 유지하면 메모리 거동이 오늘과 같다.
     * 올리려면 이 상수 한 줄이지만 위 실측대로 메모리를 지불한다.
     *
     * [WHY] 명시 전달하는 이유는 기본값을 Kotlin 쪽에서 볼 수 없기 때문이다. AAR 버전이 올라가며
     * 조용히 바뀌면 아래 예산이 다시 어긋나고, 그 어긋남은 오류가 아니라 품질 저하로 나타난다.
     */
    const val ENGINE_MAX_TOKENS = 4096

    /**
     * 생성분을 위해 남겨 두는 KV 여유입니다.
     *
     * [WHY] 프롬프트와 생성 토큰이 **같은 KV** 에 들어간다. 프리필만 용량에 맞추면 답변이 길어질
     * 때 그 경계를 넘는다.
     */
    const val GENERATION_HEADROOM_TOKENS = 768

    /**
     * 프리필(시스템 지시 + 툴 선언 + few-shot + 히스토리)이 넘을 수 없는 천장입니다.
     * 사용자 설정과 대화 재설정 임계값이 모두 이 값 이하로 클램프됩니다.
     */
    const val PREFILL_CEILING_TOKENS = ENGINE_MAX_TOKENS - GENERATION_HEADROOM_TOKENS

    // [WHY] 프리필 전체 예산의 기본값이다. 6000 이었으나 위 천장(3328)을 넘어 무효였다.
    // 슬라이더 눈금(1000 단위)에 맞춰 3000 으로 둔다.
    const val MAX_CONTEXT_TOKENS = 3000

    /**
     * 시스템 지시 + 툴 선언 + few-shot 시범이 차지하는 프리필 오버헤드입니다.
     *
     * [WHY] 슬라이딩 윈도우가 `ChatMessage.content` 만 세므로 이만큼을 먼저 예약하고 남은 것을
     * 히스토리에 배분한다. 예약하지 않으면 실제 프리필이 설정값을 크게 넘는다.
     *
     * [WHY] 2600 이었고 주석은 *"툴 선언만 실측 ~2천 토큰"* 이라고 적었는데 **둘 다 틀렸다.**
     * `Conversation.token_count` 실측(`scratch/lab/measure_overhead.py`): 시스템 지시 573,
     * 툴 5종 선언 **652**, few-shot 104 → 합계 **1,329**. 과대 예약이 히스토리 예산을 1,200 토큰
     * 넘게 깎고 있었다. 실측값에 여유를 얹어 1,400 으로 내린다.
     */
    const val PREFILL_OVERHEAD_TOKENS = 1400

    // [WHY] 오버헤드 예약 후에도 히스토리에 최소한 남겨야 하는 예산. 사용자가 슬라이더를
    // 최소(1000)로 내려도 직전 대화 몇 턴은 유지되어야 대화가 성립한다.
    const val MIN_HISTORY_TOKENS = 500

    /**
     * 런타임이 살아 있는 Conversation 을 버리고 새로 만드는 하한 임계값입니다.
     *
     * [WHY] 임계값 자체는 사용자 설정에서 파생되지만, 예산 최소값(1000)을 그대로 쓰면 오버헤드
     * 만으로 이미 넘어서서 **매 턴 재생성**된다. 재생성은 전체 프리필이라 우리가 없애려는 바로
     * 그 비용이므로 하한을 둔다.
     *
     * [WHY] 4000 이었다. 그 값은 [PREFILL_CEILING_TOKENS] 를 넘으므로, 예산을 낮춰도 임계값이
     * 4000 으로 밀려 **대화가 용량을 넘도록 자라는 것을 허용**했다 — 재설정을 막으려는 하한이
     * 오히려 초과를 보장하고 있었다. 천장 이하로 내린다.
     */
    const val MIN_CONVERSATION_RESET_TOKENS = 2600

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


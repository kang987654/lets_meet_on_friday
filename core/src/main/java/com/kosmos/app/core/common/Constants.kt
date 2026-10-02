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
     * [WHY] 아래 모든 토큰 예산의 출처다. 모델 카드의 컨텍스트 창(128K)과 달리 이 값은 런타임이 실제로 잡는 메모리다
     * (늘린 토큰당 약 1MB). 넘기지 않으면 상한이 없어지는 게 아니라 런타임 기본값(4096)이 되고, 그 기본값은 Kotlin 에서
     * 보이지 않아 AAR 이 바꾸면 예산이 조용히 어긋난다 — 그래서 명시한다. 4096 유지는 사용자 결정(ADR-016·017).
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
     * 프리필(시스템 지시 + 툴 선언 + 히스토리)이 넘을 수 없는 천장입니다.
     * 사용자 설정과 대화 재설정 임계값이 모두 이 값 이하로 클램프됩니다.
     */
    const val PREFILL_CEILING_TOKENS = ENGINE_MAX_TOKENS - GENERATION_HEADROOM_TOKENS

    /**
     * GPU 백엔드에서 숫자 토큰이 깨지기 시작하는 문맥 깊이의 실측 경계입니다 (exp30, PC GPU).
     *
     * [WHY] GPU FP16 활성화 정밀도의 누적 오차로 이 깊이부터 숫자가 깨진다(`20826-…` 같은 자릿수 유실). AAR 이 정밀도
     * 레버를 노출하지 않아 예산으로 발병점을 피한다(ADR-021). PC GPU 실측이라 기기 GPU 재검증이 최종 판정이다.
     */
    const val GPU_DIGIT_ONSET_SAFE_TOKENS = 1854
    const val GPU_DIGIT_ONSET_BROKEN_TOKENS = 2122

    // [WHY] 프리필 전체 예산의 기본값 — GPU 숫자 깨짐 발병점(위 경계) 아래로 둔다(ADR-021). 오버헤드 예약 + 최소
    // 히스토리(PREFILL_OVERHEAD_TOKENS + MIN_HISTORY_TOKENS)와 같아 내릴 수 있는 바닥이기도 하다.
    const val MAX_CONTEXT_TOKENS = 1700

    /**
     * 시스템 지시 + 툴 선언 + 프로필 블록(상한)이 차지하는 프리필 오버헤드 예약입니다.
     *
     * [WHY] 슬라이딩 윈도우가 `ChatMessage.content` 만 세므로 이만큼을 먼저 예약하고 남은 것을 히스토리에 준다.
     * 실측 오버헤드의 진실은 `TokenBudgetInvariantTest.MEASURED_OVERHEAD` 다 — 실측 + [PROFILE_MAX_TOKENS] 가 이 예약
     * 이하여야 하고, 그 불변식은 테스트가 지킨다. 이 값을 바꾸면 [MIN_HISTORY_TOKENS]·[MAX_CONTEXT_TOKENS] 의 바닥
     * 관계가 함께 흔들린다(AGENTS §2-⑤).
     */
    const val PREFILL_OVERHEAD_TOKENS = 1400

    // [WHY] 오버헤드 예약 후에도 히스토리에 남겨야 하는 최소 예산(대략 2~3턴). 더 키우면 최소 프리필이 GPU 발병점
    // 안전선([GPU_DIGIT_ONSET_SAFE_TOKENS])을 넘는다.
    const val MIN_HISTORY_TOKENS = 300

    // [WHY] 슬라이딩 윈도우가 메시지마다 더하는 턴 템플릿(역할 태그·턴 경계) 몫 — 본문 추정만으로는 빠진다.
    // 과대 방향 여유라 예산 불변식에 안전하다.
    const val PER_MESSAGE_TEMPLATE_TOKENS = 10

    /**
     * 툴 실행 결과(resultJson 전체)가 넘지 않아야 하는 토큰 예산입니다.
     *
     * [WHY] 툴 결과는 **턴 중간**에 KV 로 들어가 슬라이딩 윈도우·재설정 임계값 어느 쪽 보호도 받지 못한다. 대화가
     * 재설정 임계값([MIN_CONVERSATION_RESET_TOKENS] 이상) 근처까지 자란 상태에서도 초과 폭이 생성 여유와 같은
     * 자릿수에 머물게 하는 값이다. 완전한 보장은 구조적으로 불가능하고, 초과는 GemmaModelRunner 의 KV 계측 로그가
     * 관측한다(ADR-020).
     */
    const val TOOL_RESULT_MAX_TOKENS = 500

    /**
     * 툴 결과 예산 중 본문(data)이 아닌 부분 — JSON 래퍼(`{"status":...,"data":...}`)와
     * 근거 고정 지침 — 을 위해 예약하는 토큰입니다. 본문 캡 = [TOOL_RESULT_MAX_TOKENS] − 이 값.
     *
     * [WHY] exp26 실측 기준 JSON 래퍼 ~25토큰 + 한국어 지침 한 줄 ~60토큰에 여유를 얹었다.
     */
    const val TOOL_RESULT_ENVELOPE_RESERVE_TOKENS = 100

    /**
     * 채팅에 첨부하는 문서 본문의 문자 수 상한입니다.
     *
     * [WHY] 문서 메시지가 다음 턴에도 슬라이딩 윈도우에 남으려면 추정 토큰이 [MIN_HISTORY_TOKENS] 이하여야 한다.
     * 메시지 오버헤드·래퍼(약 25토큰)를 빼고 비ASCII 계수(1.2자/토큰, exp26)로 환산하면 ≈318자 — 여유를 두고 300.
     * 이보다 길면 그 턴은 GPU 발병점을 넘고 다음 턴부터 문서가 통째로 탈락한다. 긴 문서 요약은 expand.md D1 경로다.
     */
    const val MAX_ATTACHED_DOC_CHARS = 300

    /**
     * 추론 무활동 타임아웃(ms) — 이 시간 동안 토큰이 하나도 오지 않으면 생성을 끊습니다(PRD EC1).
     *
     * [WHY] 네이티브 추론이 멈추면 lifecycleMutex 를 쥔 채 이후 모든 턴이 막힌다. 총시간이 아니라 **무활동** 기준인
     * 이유는 정상 생성도 답이 길면 몇 분이 걸려서다. 120초는 콜드 재초기화(9~12초) + CPU 폴백 전체 재프리필의 최악을
     * 덮는 값 — 실측으로 좁힐 근거가 생기면 내린다.
     */
    const val INFERENCE_INACTIVITY_TIMEOUT_MS = 120_000L

    /**
     * 한 번에 모델로 보낼 수 있는 오디오 길이(초)입니다.
     *
     * [WHY] Gemma 4 공식 문서(capabilities/audio)의 상한이 30초다. 비용은 초당 25토큰이라 30초 = 750토큰 — 전사는
     * 일회성 경로라 [PREFILL_CEILING_TOKENS] 안에 들어간다. 올리려면 그 예산 계산부터 다시 해야 한다.
     */
    const val MAX_AUDIO_SECONDS = 30

    /**
     * 한 턴에 모델로 보내는 이미지 장수입니다. `EngineConfig.maxNumImages` 로 전달합니다.
     *
     * [WHY] 입력바 첨부 칩이 하나뿐이라 한 장이 사실이다. 명시하는 이유는 [ENGINE_MAX_TOKENS] 와 같다 — 보이지 않는
     * 기본값에 기대면 이미지 시각 토큰만큼 히스토리가 조용히 줄어든다.
     */
    const val MAX_IMAGES_PER_TURN = 1

    /**
     * 런타임이 살아 있는 Conversation 을 버리고 새로 만드는 하한 임계값입니다.
     *
     * [WHY] 재생성은 전체 프리필이라 너무 낮으면 매 턴 재생성 루프가 된다 — 바닥은 오버헤드 예약 + 최소 히스토리.
     * 동시에 GPU 발병점 아래여야 대화가 숫자 깨짐 구간까지 자라지 않는다(ADR-021).
     */
    const val MIN_CONVERSATION_RESET_TOKENS = 1700

    // [WHY] **프롬프트 슬라이딩 윈도우 후보 수(ContextBuilder 전용).** 화면 타임라인은 [CHAT_TIMELINE_PAGE_SIZE] 로
    // 따로 간다 — UI 페이지 크기를 조정하려고 이 값을 만지면 프롬프트 예산이 함께 흔들린다.
    const val MAX_RECENT_CONVERSATIONS = 150

    /**
     * 타임라인(연속 대화 화면) 페이지 크기 — UI 전용. 30 은 한 화면 분량(+여유)이다.
     */
    const val CHAT_TIMELINE_PAGE_SIZE = 30

    /**
     * 프로필 블록(`[User Profile]`, 상시 주입 기억)의 토큰 상한입니다 (C′1).
     *
     * [WHY] 상시 주입 1토큰 = 히스토리 1토큰 손실이라 상한이 필수다. 편집 시트가 저장 전에 GemmaTokenizer 추정(과대
     * 추정이 안전 방향)으로 집행한다. 재원은 few-shot 제거분(CHANGELOG 0.23.0).
     */
    const val PROFILE_MAX_TOKENS = 100

    const val MAX_KNOWLEDGE_CONTEXT_ITEMS = 3

    /**
     * 기억 검색의 바이그램 전수 스캔 상한 — 최근 지식 노트·에피소드 문서를 이만큼 읽어 점수를 매긴다 (C1, 0.25.0).
     *
     * [WHY] 인메모리 스코어러의 규모 경계다. 넘으면 FTS5 로 옮기는 규모 게이트(문서 2,000+ 또는 스캔 50ms+)를 연다(ADR-025).
     */
    const val MEMORY_SCAN_LIMIT = 500

    /**
     * 전수 스캔에서 문서를 결과로 인정하는 **최소 항 겹침**(가장 잘 맞은 항의 바이그램 겹침 비율).
     *
     * [WHY] 합계 점수만 쓰면 아무 문서나 조금씩 겹쳐 잡음이 올라온다. 0.5 는 exp37 실측값 — "자물쇠번호"↔"자물쇠
     * 비밀번호"(0.75)는 통과하고 "좋아하는 것"↔"커피보다 녹차…"(0.33)는 걸러진다.
     */
    const val BIGRAM_MIN_TERM_OVERLAP = 0.5
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

    // --- 아침 브리핑 알림 (WorkManager 자기 재예약, A4) ---
    const val BRIEFING_WORK_NAME = "morning_briefing_notification"
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

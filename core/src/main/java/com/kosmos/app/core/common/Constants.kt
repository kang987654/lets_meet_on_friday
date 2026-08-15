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

    /**
     * GPU 백엔드에서 숫자 토큰이 깨지기 시작하는 문맥 깊이의 실측 경계입니다 (exp30, PC GPU).
     *
     * [WHY] 같은 모델·같은 런타임(0.16.0)·같은 프롬프트에서 CPU 는 모든 깊이에서 온전한데
     * GPU 는 1,854 토큰까지 온전하고 2,122 토큰부터 깨진다(`202026-…`, `20826-…` 형태의
     * 자릿수 유실·중복). FLOAT32 활성화로 사라지므로 FP16 활성화 정밀도의 누적 오차로
     * 특정됐지만(exp29), AAR 0.16.0 은 그 레버(activationDataType)를 노출하지 않고 GPU 전용
     * 모델 변형은 오디오 인코더가 없어(exp32) 쓸 수 없다 — 그래서 예산으로 발병점을 피한다
     * (사용자 결정, ADR-021).
     *
     * [WHY] PC GPU(WebGPU/NVIDIA)의 실측이므로 기기 GPU(Adreno)와 같다는 보장은 없다.
     * 실기기 재검증이 이 경계의 최종 판정자다.
     */
    const val GPU_DIGIT_ONSET_SAFE_TOKENS = 1854
    const val GPU_DIGIT_ONSET_BROKEN_TOKENS = 2122

    // [WHY] 프리필 전체 예산의 기본값이다. 3000 이었으나 GPU 숫자 깨짐 발병점(위 실측
    // 1854~2122)보다 커서, 대화가 임계값까지 자라면 일정 시각·비밀번호의 숫자가 깨졌다.
    // 1700 이면 플레인 턴의 생성 시점 KV(임계값 + 사용자 발화 ≈ 1,800)가 온전선 아래다.
    // 오버헤드(1400) + 최소 히스토리(300) = 1700 이라 이것이 내릴 수 있는 바닥이기도 하다.
    const val MAX_CONTEXT_TOKENS = 1700

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
    //
    // [WHY] 500 에서 300 으로 내렸다. 예산 기본값이 GPU 발병점 아래(1700)로 내려오면서
    // 오버헤드(1400)를 빼면 300 만 남는다 — 500 을 유지하면 최소 프리필(1400+500=1900)이
    // 발병점 안전선(1854)을 넘는다. 300 은 대략 2~3턴이다.
    const val MIN_HISTORY_TOKENS = 300

    /**
     * 툴 실행 결과(resultJson 전체)가 넘지 않아야 하는 토큰 예산입니다.
     *
     * [WHY] 툴 결과는 **턴 중간**에 KV 로 들어간다. 슬라이딩 윈도우도 재설정 임계값도 턴 시작
     * 시점 검사라 여기엔 닿지 않고, 툴 회신 턴은 재생성이 금지된 턴이라(0.8.5 사고 가드) 어떤
     * 보호도 없다. 위키 결과의 예전 캡은 ko 기준 **5,000자**(실측 약 2,500토큰) — 예산이
     * 6000~8000 이던 최초 커밋 시절 값이 0.13.0 예산 개편(4096 파생) 때 재검토되지 않은
     * 유물이었다. 툴 루프 상한이 3회이므로 최악 3배가 된다.
     *
     * [WHY] 500 의 근거: 대화가 재설정 임계값(기본 3000) 근처까지 자란 상태에서도 툴 결과가
     * 생성 여유(768)와 같은 자릿수에 머물러야 초과 폭이 계측 가능한 범위에 남는다. **완전한
     * 보장은 구조적으로 불가능하다** — 재생성 금지 가드가 있는 한 임계값 직전 대화 + 툴 결과는
     * 4096 을 넘을 수 있다. 이 상수는 최악 초과를 3×~2,500 에서 3×~500 으로 줄이는 것이고,
     * 초과 자체는 GemmaModelRunner 의 KV 계측 로그가 관측한다 (ADR-020).
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
     * 채팅에 첨부하는 텍스트 문서를 자를 문자 수입니다.
     *
     * [WHY] 예전 값 **2500 은 예산이 6000 이던 시절의 유물**이다(주석도 영어 보일러플레이트뿐).
     * 2500자(≈한국어 1,300토큰 이상)를 사용자 발화에 이어붙이면 ① 그 턴의 KV 가 프리필(≈1700)
     * 위에 얹혀 GPU 숫자 깨짐 발병점([GPU_DIGIT_ONSET_SAFE_TOKENS])을 크게 넘고 ② 다음 턴부터는
     * 그 메시지의 추정 토큰이 히스토리 예산([MIN_HISTORY_TOKENS] 300)을 넘어 슬라이딩 윈도우에서
     * **통째로 탈락**한다 — 문서에 대해 이어 물으면 모델은 문서를 본 적 없는 상태가 되는데
     * 화면에는 아무 표시가 없다.
     *
     * [WHY] 300 의 유도: 문서 메시지가 윈도우에 살아남으려면 추정 토큰이 최소 히스토리 예산
     * (300) 이하여야 하는데, 거기서 메시지당 오버헤드(10)와 래퍼 텍스트("첨부된 문서 내용(파일명)"
     * + `[Attached Document]` 블록, 약 15~25토큰)를 빼면 본문 몫은 ≈265토큰이고, 추정기의
     * 비ASCII 계수(1.2자/토큰, exp26)로 환산하면 ≈318자 — 여유를 두고 300 으로 내림한다.
     * 이 길이로는 문서 요약 기능이 못 되는 것을 안다 — 긴 문서의 정식 경로는 페이지 단위
     * oneShot 요약(expand.md D1)이고, 이 캡은 그때까지 "조용히 깨지는 것"을 "정직하게 짧은
     * 것"으로 바꾼다. 잘리면 사용자에게 안내한다(ChatScreen).
     */
    const val MAX_ATTACHED_DOC_CHARS = 300

    /**
     * 추론 무활동 타임아웃(ms) — 이 시간 동안 토큰이 하나도 오지 않으면 생성을 끊습니다.
     *
     * [WHY] **PRD EC1 은 "추론 timeout → 오류 + 재시도" 를 요구하는데 집행이 없었다.**
     * `ModelInferenceTimeout` 오류 타입과 사용자 문구까지 준비돼 있었지만 생성하는 곳이 0곳 —
     * 네이티브 추론이 멈추면 lifecycleMutex 를 쥔 채 영원히 매달려 이후 모든 턴(warmUp·close
     * 포함)이 함께 막혔다.
     *
     * [WHY] 총시간이 아니라 **무활동** 기준이다. 정상 생성도 답이 길면 몇 분이 걸릴 수 있어
     * 총시간 상한은 정상 경로를 자른다. 행(hang)의 특징은 토큰이 안 오는 것이므로 토큰 간
     * 간격을 본다.
     *
     * [WHY] 120초 인 이유: 첫 토큰까지는 콜드 재초기화(실측 9~12초) + 전체 재프리필(1700토큰,
     * CPU 폴백에서 수십 초 가능)이 선행될 수 있다. 그 최악을 덮으면서 진짜 행은 유한 시간에
     * 끊는 값이다. 실측으로 좁힐 근거가 생기면 내린다.
     */
    const val INFERENCE_INACTIVITY_TIMEOUT_MS = 120_000L

    /**
     * 한 번에 모델로 보낼 수 있는 오디오 길이(초)입니다.
     *
     * [WHY] **Gemma 4 공식 문서(capabilities/audio)가 최대 30초로 못박는다.** 앱에는 상한이 아예
     * 없어서 마이크 버튼을 다시 누를 때까지 무한히 녹음됐고, 한계를 넘긴 오디오가 그대로 모델에
     * 들어갔다 — 잘리는지, 오류가 나는지, 엉뚱한 결과가 나오는지 **정의되지 않은 상태**였다.
     *
     * [WHY] 같은 문서가 Gemma 4 의 오디오 비용을 **초당 25 토큰**으로 적는다. 30초면 750 토큰이고,
     * 전사는 일회성 경로(툴·히스토리 없음)라 [PREFILL_CEILING_TOKENS] 안에 넉넉히 들어간다.
     * 이 상한을 올리려면 그 예산 계산부터 다시 해야 한다.
     */
    const val MAX_AUDIO_SECONDS = 30

    /**
     * 한 턴에 모델로 보내는 이미지 장수입니다. `EngineConfig.maxNumImages` 로 전달합니다.
     *
     * [WHY] 입력바의 첨부 칩이 하나뿐이라 **정확히 한 장**이 사실이다. 공식 문서는 한 프롬프트에
     * 여러 장을 허용하지만 우리 UI 가 그것을 만들지 않는다. 명시하는 이유는 `maxNumTokens` 와 같다 —
     * 이미지 한 장이 시각 토큰 예산만큼 KV 를 먹으므로 보이지 않는 기본값에 기대면 4096 안에서
     * 쓸 수 있는 히스토리가 조용히 줄어든다.
     */
    const val MAX_IMAGES_PER_TURN = 1

    /**
     * 런타임이 살아 있는 Conversation 을 버리고 새로 만드는 하한 임계값입니다.
     *
     * [WHY] 임계값 자체는 사용자 설정에서 파생되지만, 예산 최소값(1000)을 그대로 쓰면 오버헤드
     * 만으로 이미 넘어서서 **매 턴 재생성**된다. 재생성은 전체 프리필이라 우리가 없애려는 바로
     * 그 비용이므로 하한을 둔다. 하한의 바닥은 오버헤드(1400) + 최소 히스토리(300) — 이보다
     * 낮으면 갓 재생성한 대화가 즉시 임계값을 넘어 재생성 루프가 된다.
     *
     * [WHY] 2600 이었다. GPU 발병점 실측(1854~2122, exp30)보다 커서 대화가 깨짐 구간까지
     * 자라는 것을 허용했다 — 발병점 아래인 1700 으로 내린다 (ADR-021).
     */
    const val MIN_CONVERSATION_RESET_TOKENS = 1700

    // [WHY] **프롬프트 슬라이딩 윈도우 후보 수(ContextBuilder 전용).** 예전에는 화면 히스토리
    // 로드도 이 상수를 썼지만, 타임라인이 Paging(CHAT_TIMELINE_PAGE_SIZE)으로 바뀌며 갈라졌다 —
    // UI 페이지 크기를 조정하려고 이 값을 만지면 프롬프트 예산 계산이 함께 흔들린다.
    const val MAX_RECENT_CONVERSATIONS = 150

    /**
     * 타임라인(연속 대화 화면) 페이지 크기 — UI 전용.
     *
     * [WHY] 예전에는 화면이 150개를 즉시 전량 로드했고 그 이상은 도달 불가였다. 시안 A′의
     * 무한 스크롤 타임라인은 Paging 으로 과거 전체에 닿는다. 30 은 한 화면 분량(+여유)이다.
     */
    const val CHAT_TIMELINE_PAGE_SIZE = 30

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


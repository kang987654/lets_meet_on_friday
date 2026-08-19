# 🛠️ [소프트웨어 엔지니어링 평가 보고서] KOSMOS (Local Friday)

- **평가 대상**: 코드베이스 구조, 시스템 아키텍처, 온디바이스 AI 런타임 엔지니어링, 코드 품질 및 테스트 신뢰도
- **핵심 기준**: Clean Architecture, Kotlin/Android Best Practices, 시스템 리소스 제약 극복, 불변식 보호

---

## 1. 엔지니어링 총평 & 스코어카드 (Scorecard)

> **"단순한 LLM Wrapper 앱이 아닌, 모바일 하드웨어(GPU/RAM/발열)의 극단적 제약과 수치 정밀도 결함을 시스템 레벨에서 실측·해결한 최상급 온디바이스 AI 엔지니어링 프로젝트."**

| 평가 영역 | 평점 | 핵심 평가 요약 |
| :--- | :---: | :--- |
| **아키텍처 및 모듈화** | **9.8 / 10** | Clean Architecture 4개 모듈 분리, Pure Kotlin `:domain`의 완벽한 순수성 유지 |
| **온디바이스 AI 런타임 제어** | **10.0 / 10** | GPU FP16 정밀도 깨짐 실측 계측(exp30) 및 토큰 예산 불변식 수학적 방어 (ADR-021) |
| **타입 안전성 & 코드 품질** | **9.5 / 10** | `Any` 및 강제 언패킹 금지, 철저한 Sealed Class 계층화, 명확한 `[WHY]` 주석 체계 |
| **테스트 및 회귀 방어력** | **10.0 / 10** | 307개 단위/E2E Robolectric 테스트 100% 통과, Invariant Test를 통한 회귀 원천 차단 |
| **현대적 안드로이드/Compose** | **9.4 / 10** | Compose M3, Paging3 앵커 타임라인, ProcessLifecycleOwner 기반 멱등적 엔진 라이프사이클 |
| **종합 엔지니어링 등급** | **S (9.7 / 10)** | **프로덕션 레벨 이상의 견고함과 깊이 있는 시스템 엔지니어링 역량 입증** |

---

## 2. 아키텍처 및 계층 분리 (Architecture & Modularity)

```text
[레이어 간 의존성 원칙 준수도: 100%]
:app (Presentation & Platform Impl)
 ├──> :domain (Pure Kotlin Business Logic - UseCases, Interfaces, Models)
 ├──> :data (Room DB v6, DataStore, Repositories)
 └──> :core (AppResult, AppError, Constants, Security)
```

1. **Pure Kotlin 도메인 계층 격리**:
   - `:domain` 모듈에는 `android.*` import가 일절 존재하지 않으며, 안드로이드 프레임워크 종속성 없이 순수 JVM 단위 테스트가 가능합니다.
2. **단방향 데이터 흐름 및 관심사 분리**:
   - UI(`ChatScreen`) → ViewModel → UseCase(`SendChatMessageUseCase`) → Orchestrator → Agent → ModelRunner로 이어지는 호출 흐름이 엄격히 인터페이스 기반으로 결합되어 있습니다.
3. **비동기 & 디스패처 격리**:
   - 무거운 네이티브 JNI 추론 연산이 UI 스레드를 블로킹하지 않도록 `@LLMDispatcher` (단일 스레드 직렬 큐)와 IO 디스패처를 명확히 분리하여 ANR(Application Not Responding)을 원천 차단했습니다.

---

## 3. 온디바이스 AI 시스템 엔지니어링 (Systems & AI Runtime)

이 프로젝트의 가장 독보적인 엔지니어링 강점은 **온디바이스 LLM의 물리적 한계를 '감'이 아닌 '정밀 계측'으로 풀어낸 점**입니다.

### ① GPU FP16 수치 정밀도 붕괴 분석 및 토큰 예산 산식 (ADR-021)
- **문제**: 긴 멀티턴 대화 중 일정 시각(`2026-08-17T10:00:00`)이 `202026-081717T010000` 등으로 깨지는 치명적 결함 발생.
- **엔지니어링 분석**: CPU/GPU 대조 실험(exp27~30)을 통해 모바일 GPU 활성화(Activation)가 FP16으로 동작하여 **KV 컨텍스트 1,854 토큰 초과 시 부동소수점 정밀도 한계로 숫자가 왜곡됨**을 규명.
- **해결책**:
  $$\text{Prefill Budget (1,700)} = \text{Overhead (1,400)} + \text{Min History (300)} < \text{Safe Threshold (1,854)}$$
  예산을 수학적으로 재설계하고, `TokenBudgetInvariantTest`를 통해 상수의 불변 관계를 컴파일/테스트 단계에 영구 고정.

```kotlin
// [WHY] Constants.kt - 토큰 예산 불변식 방어선
const val ENGINE_MAX_TOKENS = 4096            // 물리적 KV 용량
const val MAX_CONTEXT_TOKENS = 1700           // GPU 안전 발병점(1,854) 미만으로 고정
const val TOOL_RESULT_MAX_TOKENS = 500        // 도구 관측값 캡 (SentenceTruncator 절단)
const val MAX_ATTACHED_DOC_CHARS = 300        // 첨부 문서 윈도우 생존 캡
```

### ② Gemma 4 네이티브 오디오 파이프라인 (Zero-STT Engine Overhead)
- 별도의 대용량 STT 엔진(Whisper, Vosk 등)을 앱에 번들링하지 않고, 안드로이드 `AudioRecord`로 **16,000Hz, Mono, 16-bit PCM**을 직접 캡처하여 44바이트 표준 WAV 헤더를 붙인 뒤 Gemma 4 멀티모달 ASR에 직결. (메모리 절약 + 지연 시간 최소화)

### ③ 에피소드 기반 장기 기억 백엔드 (ADR-022 / ADR-023)
- 1,700 토큰이라는 짧은 윈도우 제약을 극복하기 위해, 대화 중단 30분을 감지(`EpisodeBoundaryManager`)하여 백그라운드 oneShot 요약(`EpisodeSummarizeScheduler`) 문서를 자동 생성.
- 모델이 필요 시 `SearchMemory` 도구로 과거 대화를 회수하고, 응답 하단에 **회수 칩(🧠)**을 표기하여 O(1) 원문 점프를 지원하는 완성도 높은 기억 파이프라인 구축.

---

## 4. 코드 장인정신 및 품질 (Code Craftsmanship)

1. **엄격한 타입 시스템 및 불변성 (Immutability)**:
   - 느슨한 `Any` 타입이나 원시 JSON 객체 사용을 완전히 배제하고, `ModelOutput`, `ActionPayload`, `AppResult` 등 Kotlin Sealed Class 계층 구조를 철저히 활용.
2. **근거 중심의 주석 철학 (`// [WHY]` 규칙)**:
   - 코드가 '어떻게' 동작하는지 단순 설명하는 불필요한 주석은 배제하고, 프레임워크 버그 우회, 하드웨어 제약, 실측 데이터 등 **'왜 이렇게 작성했는가'**에 대한 기술적 배경을 주석으로 명시.
3. **UI 아키텍처 리팩터링 및 Paging3 최적화**:
   - 1,200줄이 넘던 거대한 `ChatScreen.kt`를 `ChatHeader`, `ChatBubbles`, `ChatInputBar`, `CalendarDraftCard` 5개 응집 컴포넌트로 깔끔하게 분리.
   - Paging3 `CountedPagingSource`를 자체 구현하여 수천 건의 대화 기록도 메모리 누수 없이 O(1) 인덱스로 점프 및 렌더링.

---

## 5. 테스트 및 검증 신뢰도 (Testing & Robustness)

```text
[테스트 슈트 현황]
- 총 단위 및 E2E Robolectric 테스트 : 307 건
- 테스트 통과율 : 100% (실패 0 / 에러 0 / 스킵 0)
- Android lintDebug 이슈 : 0 건 (Zero-warning Baseline)
```

1. **실제 환경을 모사한 Robolectric + Compose E2E 통합 테스트**:
   - `ChatScreenE2ETest`, `ToolApprovalE2ETest`, `VoiceChatIntegrationTest` 등 사용자 상호작용 전체 라이프사이클을 테스트 자동화로 검증.
2. **불변식 회귀 방어 테스트 (Invariant Tests)**:
   - `TokenBudgetInvariantTest`: 상수 변경 시 전체 예산 공식이 깨지지 않는지 검증.
   - `IsoDateTimeParserTest`: 한국어 날짜/시간 파싱 일관성 검증.
   - `SentenceTruncatorTest`: 긴 텍스트 절단 시 문장 중간이 잘려 환각을 유발하지 않는지 검증.

---

## 6. 기술적 부채 및 향후 과제 (Tech Debt & Roadmap)

1. **레거시 MediaPipe 임베더 정리 (Track C2)**:
   - 영어 전용인 MediaPipe 임베더가 APK 용량(52MB)을 차지하고 있어, SQLite FTS5 기반 바이그램 어휘 검색(C1) 도입 후 제거 필요.
2. **Robolectric SDK 에뮬레이션 상한 고정**:
   - `targetSdk`는 최신인 37로 상향되었으나, Robolectric 4.14의 상한(35)으로 인해 `robolectric.properties(sdk=35)`로 에뮬레이션을 고정해 둔 상태 → 추후 Robolectric 업데이트 시 동기화 권장.
3. **대용량 파일 백그라운드 처리 (WorkManager 확장)**:
   - 추후 로드맵의 PDF 다중 페이지 파싱(D1)이나 아침 브리핑(A4) 구현 시, WorkManager의 유휴/충전 제약 조건과 직렬 추론 큐를 연계하는 배치 아키텍처 고도화 필요.

---

## 7. 엔지니어링 최종 결론

KOSMOS는 **소프트웨어 아키텍처의 정갈함(Clean Architecture)**과 **하드웨어/런타임 바닥 레벨의 문제 해결력(Systems Engineering)**이 매우 훌륭하게 결합된 모범적인 프로젝트입니다.

단순 프로토타입 수준을 넘어, **상용 제품 수준의 안정성·테스트 커버리지·기기 자원 방어 메커니즘**을 갖추고 있어 엔지니어링 관점에서 대단히 높은 완성도를 지니고 있습니다.

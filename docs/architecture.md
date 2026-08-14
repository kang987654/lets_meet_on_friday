# architecture.md — Local Friday
> **문서 버전**: v1.2 | **최종 수정일**: 2026-07-31 | **상태**: Approved (승인)
> **관련 모듈**: `:app`, `:core`, `:domain`, `:data`

## Android Offline-First Personal AI Assistant

**기준 PRD**: PRD.md v1.2
**기준 계약 문서**: api_spec.yaml v1.2.0
**아키텍처 패턴**: Modular Monolith (Single Android App)
**v0(MVP) 대상 환경**: Samsung Galaxy S25 Ultra / Android
**버전 경계**: v0(MVP) = 핵심 기능 / v1 = v0 이후 확장 / v2 = Windows 및 고도화

---

## 목차

1. High-Level Architecture
2. Tech Stack
3. Domain Separation
4. Module Responsibilities
5. Directory Structure
6. Data Flow
7. State Management
8. Database Design Overview
9. Internal Contract Architecture
10. Authentication Strategy
11. Error Handling Strategy
12. Logging Strategy
13. Scalability Boundaries
14. Future Expansion Notes

---

## 1. High-Level Architecture

### 1-1. 아키텍처 철학

**Modular Monolith 채택**

이 프로젝트는 단일 Android APK로 배포되는 오프라인 우선 앱이다.
마이크로서비스나 멀티모듈 분리는 v0(MVP) 단계에서 과잉 설계이므로
**단일 앱 내에서 레이어와 도메인 경계를 명확히 분리**하는
Modular Monolith 구조를 채택한다.

핵심 원칙:
- 레이어 간 의존은 **단방향 하향**만 허용
- Domain 레이어는 Android 프레임워크 의존성 **금지** (core, domain, data 멀티 모듈 분리 원칙 적용)
- 각 레이어는 **인터페이스**를 통해서만 하위 레이어와 통신
- `api_spec.yaml`을 **내부 계약의 source of truth**로 사용한다
- AI agent가 **파일 단위**로 작업 가능한 크기로 분리한다

### 1-2. 레이어 구조

```text
┌─────────────────────────────────────────────────────┐
│                  Presentation Layer                  │
│         (Compose UI / ViewModel / Navigation)        │
├─────────────────────────────────────────────────────┤
│               Assistant Core Layer                   │
│   (Orchestrator / Router / Classifier / Approval)    │
├─────────────────────────────────────────────────────┤
│                  Domain Layer                        │
│    (Models / Use Cases / Repository Interfaces       │
│      / Tool Interfaces / Policy Interfaces)          │
├─────────────────────────────────────────────────────┤
│                   Data Layer                         │
│    (Room DB / DataStore / Repository Impls           │
│              / Export-Import(v1))                    │
├──────────────────────┬──────────────────────────────┤
│    Runtime Layer     │      Platform Layer           │
│  (Model Runner /     │  (Calendar / STT / File(v1) /│
│   Multimodal /       │   Search(v1) / Share(v1))     │
│   Metrics(v1))       │                               │
└──────────────────────┴──────────────────────────────┘
```

### 1-3. 레이어별 의존 방향 규칙

```text
Presentation
    │ depends on
    ▼
Assistant Core
    │ depends on
    ▼
Domain  ◄──── (인터페이스만 노출)
    │ implemented by
    ▼
Data / Runtime / Platform
```

> ⚠️ **AI agent 규칙**
> 상위 레이어가 하위 레이어를 의존한다.
> 역방향 의존은 컴파일 오류로 처리되어야 한다.
> Domain 레이어 파일에 `android.*` import가 있으면 구조 위반이다.

---

## 2. Tech Stack

### 2-1. 확정된 기술 스택

| 분류 | 선택 | 버전 기준 | 선택 이유 |
|---|---|---|---|
| 언어 | Kotlin | 2.3.21 | Android 최신 안정 계열 |
| Android Gradle Plugin | AGP | 9.2.0 | 최신 빌드 툴체인 |
| UI | Jetpack Compose | BOM 2026.04.01 | 선언형 UI, StateFlow 연동 |
| Navigation | Compose Navigation | 2.9.7 | Compose 공식 내비게이션 |
| DI | **Hilt** | 2.59 | 컴파일 타임 안전성, Jetpack 통합 |
| DI 처리기 | **KSP** | 2.3.7 | KAPT 대비 빌드 속도 향상 |
| DB | Room | 2.8.4 | SQLite 추상화, Paging 연동 |
| 설정 저장 | DataStore (Preferences) | 1.2.1 | 비동기 설정 저장 |
| 비동기 | Coroutines + Flow | 1.11.0 | Android 표준 비동기 |
| 상태 관리 | StateFlow + ViewModel | - | Lifecycle-aware 상태 |
| 로컬 모델 | **LiteRT-LM** | 최신 | Gemma 4 공식 Android 런타임 |
| 모델 포맷 | `.litertlm` (INT4) | - | 모바일 최적화 양자화 포맷 |
| STT | Android SpeechRecognizer | - | 온디바이스 오프라인 STT |
| 이미지 처리 | Android BitmapFactory | - | 별도 라이브러리 없이 처리 |
| JSON 직렬화 | kotlinx.serialization | JSON 1.9.0 / plugin 2.3.21 | Kotlin 친화적 직렬화 |
| 페이지네이션 | Paging 3 | 3.4.2 | `:app`에서만 사용 — ViewModel이 offset/limit 리포지토리를 `DefaultPagingSource`로 감싼다 (v0.7.4) |
| Hilt-Compose Navigation | androidx.hilt.navigation.compose | 1.3.0 | Compose Navigation + Hilt ViewModel 연동 |

### 2-2. 빌드 툴체인 원칙

- Kotlin / KSP / Hilt / AGP는 상호 호환성이 민감하므로 **항상 함께 검증**한다
- Compose BOM / Navigation / Room / DataStore / Paging은 stable 최신판을 우선 사용한다
- bootstrap 시 반드시 아래 조합을 하나의 세트로 검증한다:
  - AGP
  - Gradle wrapper
  - JDK
  - Kotlin plugin
  - Kotlin serialization plugin
  - KSP
  - Hilt

### 2-3. 미사용 / 보류 항목

| 항목 | 상태 | 이유 |
|---|---|---|
| Retrofit / OkHttp | v0 미사용 | 외부 REST API 없음 |
| Koin | 미채택 | Hilt 확정 |
| KAPT | 미사용 | KSP로 대체 |
| Vector DB | v2 보류 | v0 과잉 설계 |
| Whisper (로컬 STT) | v1 검토 | 현재 Android SpeechRecognizer 사용 |
| WorkManager | 필요 시 추가 | 모델 로드 백그라운드 처리 검토용 |
| MediaPipe LLM Inference API | 미채택 | LiteRT-LM으로 대체 |

---

## 3. Domain Separation

### 3-1. 도메인 경계 정의

이 앱은 하나의 Android 앱 안에서 아래 6개의 도메인으로
책임을 분리한다. 각 도메인은 독립적으로 테스트 가능해야 한다.

```text
┌─────────────────────────────────────────────────────────┐
│                     App Domain                          │
│              (초기화 / DI / Navigation)                │
├──────────────┬──────────────┬──────────────────────────┤
│ Chat Domain  │Calendar Domain│   Memory Domain          │
│ (대화 / 응답) │(일정 조회/생성)│(v0 저장 / v1 export)     │
├──────────────┴──────┬───────┴──────────────────────────┤
│   Assistant Domain  │         Settings Domain          │
│ (의도 분석/오케스트레│  (모델 정보 / 정책 / 로그)         │
│  이션/승인)          │                                   │
└─────────────────────┴───────────────────────────────────┘
```

### 3-2. 도메인별 책임

| 도메인 | 책임 | 주요 Use Case |
|---|---|---|
| **Chat** | 사용자 입력 수신, 응답 생성 조율 | SendChatMessage, ProcessVoiceInput, ProcessImageInput |
| **Calendar** | 캘린더 조회 및 일정 초안 생성 | GetTodaySchedule, CreateCalendarDraft, ApproveAndSaveEvent |
| **Memory** | v0 Profile / Conversation / 필수 Audit 저장, v1 Task / Knowledge / 전체 Audit / Export-Import 확장 | SearchKnowledge(v1), ExportMemory(v1), ImportMemory(v1) |
| **Assistant** | 의도 분류, 도구 선택, 승인 조율 | ClassifyIntent, RouteToAgent, RequestApproval |
| **Settings** | 모델 설정, 응답 스타일, 정책 | UpdateModelConfig, UpdatePersonaProfile, GetAuditLog |
| **App** | 전역 초기화, DI 조립, 화면 이동 | AppInit, PermissionCheck |

> **버전 주석**
> 이 문서의 디렉토리와 인터페이스는 확장 가능한 최종 구조를 함께 보여준다.
> 파일명 뒤의 `(v1)`, `(v2)` 표기가 있는 항목은 v0(MVP)에서 구현 대상이 아니며,
> v0에서는 인터페이스 또는 빈 확장 지점만 둘 수 있다.

### 3-3. 도메인 간 통신 규칙

- 도메인 간 직접 의존 **금지**
- Chat Domain이 Calendar 기능이 필요하면
  **Assistant Core를 통해 라우팅**한다
- 공유 데이터는 Domain Layer의 공통 모델을 사용한다
- 이벤트 기반 통신이 필요한 경우 SharedFlow를 사용한다
- 세부 필드/타입 정의는 **api_spec.yaml 기준**으로 정렬한다

---

## 4. Module Responsibilities

### 4-1. `app/` — 앱 진입점

| 파일 | 책임 |
|---|---|
| `KosmosApp.kt` | Application 클래스, 전역 초기화 |
| `MainActivity.kt` | Compose NavHost 진입점, 단일 Activity |
| `di/AppModule.kt` | 공통 싱글턴 바인딩 |
| `di/ModelModule.kt` | ModelRunner 바인딩 |
| `di/MemoryModule.kt` | Repository 바인딩 |
| `di/AgentModule.kt` | Orchestrator / Agent 바인딩 |
| `di/PlatformModule.kt` | Tool 구현체 바인딩 |

> **AI agent 작업 단위**
> `di/` 파일은 각각 독립적으로 생성 가능하다.
> 새로운 의존성 추가 시 해당 모듈 파일만 수정한다.

---

### 4-2. `core/` — 공통 기반

**어떤 레이어에서도 import 가능한 공통 타입만 포함한다.**
Android 의존성 최소화.

| 파일 | 책임 |
|---|---|
| `common/AppResult.kt` | `sealed class AppResult<out T>` — 성공/실패 래퍼 |
| `common/AppError.kt` | 에러 타입 열거 |
| `common/Constants.kt` | 앱 전역 상수 |
| `config/AppConfig.kt` | 런타임 설정 값 |
| `config/FeatureFlags.kt` | 기능 ON/OFF 플래그 |
| `config/ModelConfig.kt` | 모델 경로, 컨텍스트 제한 등 |
| `logging/AppLogger.kt` | 개발용 로그 래퍼 |
| `logging/AuditLogger.kt` | 감사 로그 기록 인터페이스 |
| `security/PermissionPolicy.kt` | 권한 정책 정의 |
| `security/ApprovalRules.kt` | 승인 필요 작업 정의 |
| `security/Redaction.kt` | 민감 정보 마스킹 |
| `mapper/ErrorCodeMapper.kt` | AppError ↔ ErrorCode 매핑 규칙 |
| `mapper/TimeMapper.kt` | Long(ms) ↔ ISO 8601 string 변환 유틸(권장) |

**`Constants.kt` 주요 상수**

> **정정 (2026-08-15)**: 아래 스니펫이 존재하지 않는 상수(`MAX_CONVERSATION_TURNS`)와 낡은
> 값(`MAX_CONTEXT_TOKENS = 4096` — KV 용량과 예산을 혼동한 값, ADR-017 이 바로잡은 오류)을
> "현재값"처럼 서술하고 있었다. 스니펫 복사 대신 **분류만** 적는다 — 값의 단일 출처는
> `core/.../Constants.kt` 이고, 각 상수의 근거는 그 파일의 [WHY] 주석이 담당한다.

`Constants.kt` 는 크게 다섯 무리다:
- **토큰 예산** — `ENGINE_MAX_TOKENS`(KV 용량, 4096)에서 파생되는 예산 사슬
  (`MAX_CONTEXT_TOKENS`·`PREFILL_OVERHEAD_TOKENS`·툴 결과/문서 첨부 캡 등, ADR-017·020·021).
  관계는 `TokenBudgetInvariantTest` 가 고정한다
- **멀티모달 상한** — 오디오 30초(공식 문서), 이미지 10MB/1024px/턴당 1장
- **발열 임계** — 43°C 경고 / 48°C 차단 / 쿨다운 횟수
- **모델 파일** — 파일명·다운로드 URL·크기 (현행 파일명: `gemma-4-E4B-it.litertlm`)
- **추론 방어** — 무활동 타임아웃(EC1), 대화 재설정 하한

---

### 4-3. `domain/` — 도메인 핵심 (Android 의존 금지)

**이 레이어에 `android.*` import가 있으면 구조 위반이다.**

#### `domain/model/` — 핵심 도메인 모델

| 파일 | 책임 |
|---|---|
| `ChatMessage.kt` | 채팅 메시지 |
| `AssistantResponse.kt` | 비서 응답 |
| `ActionCard.kt` | 액션 카드 (typed payload) |
| `ChatRequest.kt` | Orchestrator 내부 입력 모델 |
| `UserProfile.kt` | 사용자 프로필 |
| `CalendarEvent.kt` | 캘린더 일정 모델 |
| `CalendarDraft.kt` | 일정 초안 |
| `ScheduleData.kt` | 일정 조회 결과 |
| `KnowledgeNote.kt` | 지식 메모 (v1) |
| `TaskItem.kt` | 작업 아이템 (v1) |
| `AuditEvent.kt` | 감사 이벤트 |
| `SearchRequest.kt` | 검색 요청 (v1) |
| `SearchResultItem.kt` | 검색 결과 항목 (v1) |
| `ApprovalRequest.kt` | 승인 요청 |
| `ModelOutput.kt` | 모델 구조화 출력 sealed class |

**모델 표현 원칙**
- `ActionCard.payload`는 `Any`가 아니라 **typed payload**로 표현한다
- `ModelOutput`은 `TextOutput`, `CalendarDraftOutput`, `SearchOutput`, `KnowledgeSaveOutput`의 sealed class로 정의한다
- `ChatRequest`는 `AssistantOrchestrator` 내부 입력 모델이며, 화면/UseCase 입력과 구분한다

#### `domain/memory/` — 메모리 저장소 인터페이스

| 파일 | 책임 |
|---|---|
| `ProfileRepository.kt` | 프로필 CRUD 인터페이스 |
| `ConversationRepository.kt` | 대화 저장/조회 인터페이스 |
| `TaskRepository.kt` | 작업 저장/조회 인터페이스 (v1) |
| `KnowledgeRepository.kt` | 지식 메모 저장/검색 인터페이스 (v1) |
| `AuditRepository.kt` | 감사 로그 저장/조회 인터페이스 |

#### `domain/modelrunner/` — 모델 추상화

| 파일 | 책임 |
|---|---|
| `ModelRunner.kt` | `interface ModelRunner` — generate / generateWithImage / cancel |
| `ModelLoadState.kt` | 모델 로드 상태 모델 |
| `ModelInfo.kt` | 현재 모델 메타 정보 |
| `InferenceMetrics.kt` | 추론 성능 메트릭 (v1) |

**`ModelRunner` 인터페이스**

```kotlin
interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>

    suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)? = null
    ): AppResult<String>

    suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray
    ): AppResult<String>

    suspend fun cancel()
    suspend fun warmUp()
}
```

**`ModelLoadState` 표현 원칙**
- `ModelLoadState`는 단순 문자열 enum이 아니다
- 상태 + 부가 정보를 담는 도메인 모델이다
- 예시:
  - `Loading`
  - `Ready(modelInfo)`
  - `Error(error)`
  - `NotFound(expectedPath)`

#### `domain/agent/` — 에이전트 인터페이스

| 파일 | 책임 |
|---|---|
| `Agent.kt` | `interface Agent` — execute |
| `AgentResult.kt` | 에이전트 실행 결과 래퍼 |

#### `domain/tool/` — 도구 인터페이스

| 파일 | 책임 |
|---|---|
| `CalendarTool.kt` | readEvents / insert 인터페이스 |
| `SpeechToTextTool.kt` | start / stop / cancel / transcript state 인터페이스 |
| `WebSearchTool.kt` | search 인터페이스 (v1) |
| `FileTool.kt` | read / write / resolveUri 인터페이스 (v1) |

> 주의:
> 캘린더 초안 생성(createDraft)은 Tool의 책임이 아니다.
> 초안 생성은 `CreateCalendarDraftUseCase + ModelRunner` 책임이며,
> `CalendarTool`은 Android Calendar Provider 읽기/쓰기만 담당한다.

#### `domain/policy/` — 정책 인터페이스

| 파일 | 책임 |
|---|---|
| `ExecutionPolicy.kt` | 실행 허용/거부 판단 인터페이스 |
| `ApprovalPolicy.kt` | 승인 필요 여부 판단 인터페이스 |

---

### 4-4. `assistant/` — 비서 핵심 로직

**AI 처리 흐름을 조율하는 레이어.**
Domain 레이어의 인터페이스만 사용한다.

| 파일 | 책임 |
|---|---|
| `orchestrator/AssistantOrchestrator.kt` | 전체 입력→출력 흐름 총괄 |
| `orchestrator/TaskRouter.kt` | 의도에 따른 Agent 선택 및 라우팅 |
| `orchestrator/IntentClassifier.kt` | 입력 의도 분류 |
| `context/ContextBuilder.kt` | 대화 + 메모리 컨텍스트 조합 |
| `context/PromptAssembler.kt` | 시스템 프롬프트 + 컨텍스트 + 입력 조립 |
| `context/ResponseParser.kt` | 모델 출력 JSON 파싱 및 타입 판별 |
| `approval/ApprovalCoordinator.kt` | 승인 대기 상태 관리 |
| `guard/PreExecutionGuard.kt` | 실행 전 정책 검사 |
| `guard/PostExecutionValidator.kt` | 실행 결과 검증 |
| `audit/AuditTrailService.kt` | 감사 이벤트 기록 서비스 |
| `persona/PersonaProfileManager.kt` | 페르소나 프로필 관리 |
| `agent/CalendarAgent.kt` | 캘린더 관련 작업 처리 |
| `agent/SearchAgent.kt` | 웹 검색 처리 (v1) |
| `agent/KnowledgeAgent.kt` | 지식 메모 처리 (v1) |

**`PromptAssembler` 프롬프트 구조**

> **정정 (2026-08-15)**: 예전의 "4블록 예산표(총합 4096)"는 현재 산식과 전부 어긋나 삭제했다.
> 현행 구조: 시스템 지시(세션 고정) + 툴 선언 + few-shot = 오버헤드 실측 1,329(예약 1,400),
> 히스토리는 Token Sliding Window 가 프리필 예산(기본 1,700)에서 오버헤드를 뺀 몫을 배분한다.
> "메모리 블록" 자동 주입은 ADR-013 에서 폐지됐다(SearchMemory 툴로 대체). 수치의 단일 출처는
> `Constants.kt` 와 ADR-017·020·021 이다.

**출력 형식 정책**
- 채팅/에이전트 응답: 기본적으로 `ModelOutput` 구조화 JSON을 기대한다
- 일정 요약 등 보조 UseCase: plain text 응답을 기대할 수 있다

**승인 책임 분리**
- `ApprovalCoordinator`는 **승인 대기 상태**만 관리한다
- 실제 저장 및 Audit 기록은 `ApproveAndSaveEventUseCase`가 담당한다
- 승인/거부 Audit은 한 곳에서만 기록되도록 유지한다

---

### 4-5. `data/` — 데이터 레이어

#### `data/local/db/`

| 파일 | 책임 |
|---|---|
| `KosmosDatabase.kt` | Room DB 진입점 (WAL 모드 활성화) |
| `dao/ProfileDao.kt` | profiles 테이블 접근 |
| `dao/ConversationDao.kt` | conversations 테이블 접근 |
| `dao/TaskDao.kt` | tasks 테이블 접근 (v1) |
| `dao/KnowledgeDao.kt` | knowledge_notes 테이블 접근 (v1) |
| `dao/AuditDao.kt` | audit_events 테이블 접근 |
| `entity/ProfileEntity.kt` | profiles 엔티티 |
| `entity/ConversationEntity.kt` | conversations 엔티티 |
| `entity/TaskEntity.kt` | tasks 엔티티 (v1) |
| `entity/KnowledgeEntity.kt` | knowledge_notes 엔티티 (v1) |
| `entity/AuditEntity.kt` | audit_events 엔티티 |

#### `data/local/prefs/`

| 파일 | 책임 |
|---|---|
| `SettingsDataStore.kt` | 앱 설정 저장 |
| `ModelRegistryStore.kt` | 모델 경로 / 버전 정보 저장 |
| `SessionStore.kt` | 현재 활성 세션 ID 저장/복원 |

#### `data/local/file/`

| 파일 | 책임 |
|---|---|
| `ExportImportManager.kt` | export/import 처리 (v1), WAL Checkpoint TRUNCATE 수행 |
| `ExportManifest.kt` | export_manifest.json 데이터 클래스 |

#### `data/repository/`

| 파일 | 책임 |
|---|---|
| `ProfileRepositoryImpl.kt` | ProfileRepository 구현 |
| `ConversationRepositoryImpl.kt` | ConversationRepository 구현 |
| `TaskRepositoryImpl.kt` | TaskRepository 구현 (v1) |
| `KnowledgeRepositoryImpl.kt` | KnowledgeRepository 구현 (v1) |
| `AuditRepositoryImpl.kt` | AuditRepository 구현 |

---

### 4-6. `runtime/` — 모델 실행 레이어

| 파일 | 책임 |
|---|---|
| `gemma/GemmaModelRunner.kt` | `ModelRunner` 구현, `@LLMDispatcher` 활용 |
| `gemma/GemmaRuntimeManager.kt` | 모델 로드 / 언로드(`onTrimMemory` 연동) / 상태 관리 |
| `gemma/FakeModelRunner.kt` | 테스트용 FakeModelRunner |
| `multimodal/ImageInputAdapter.kt` | 이미지 URI → 리사이즈 → ByteArray 변환 |
| `metrics/RuntimeMetricsCollector.kt` | 추론 시간 / 온도 / 연속 횟수 기록 (v1) |

---

### 4-7. `platform/` — Android 플랫폼 연결

**Tool 인터페이스의 Android 구현체.**
이 레이어만 `android.*` 의존성을 가진다.

| 파일 | 책임 |
|---|---|
| `calendar/AndroidCalendarTool.kt` | Android Calendar Provider 접근 |
| `calendar/FakeCalendarTool.kt` | 테스트용 Fake Calendar Tool |
| `speech/AndroidSpeechToTextTool.kt` | Android SpeechRecognizer 연결 |
| `speech/FakeSpeechToTextTool.kt` | 테스트용 Fake STT Tool |
| `share/ShareIntentHandler.kt` | 공유 인텐트 수신 처리 (v1) |
| `storage/AndroidFileTool.kt` | 파일 / URI 접근 (v1) |
| `network/WebSearchGateway.kt` | 웹 검색 추상화 (v1) |
| `permissions/PermissionManager.kt` | 런타임 권한 요청 / 상태 확인 |

**`SpeechToTextTool` 계약**
```kotlin
interface SpeechToTextTool {
    val state: StateFlow<SttState>
    val transcript: StateFlow<String?>
    fun start(preferOffline: Boolean = true, language: String = "ko-KR")
    fun stop()
    fun cancel()
}
```

**STT 결과 전달 원칙**
- callback보다 `StateFlow` 기반 상태 관찰을 기본 계약으로 사용한다
- UI/ViewModel은 `state`와 `transcript`를 함께 구독한다
- transcript는 자동 전송하지 않고 사용자 확인 후 전송하는 UX를 기본 권장으로 둔다

**권한 책임 분리**
- `PermissionManager`: 사전 확인 / 요청 / 설정 이동 유도
- Platform Tool: 최종 방어선 (`SecurityException` → `AppError.PermissionDenied`)
- UseCase: 권한 오류를 상위로 전달하거나 UX에 맞게 변환

---

### 4-8. `feature/` — 화면 단위 UI

각 feature는 **ViewModel + Screen + UiState**의 3파일 구조를 유지한다.

| 디렉토리 | 파일 구성 | 책임 |
|---|---|---|
| `feature/chat/` | `ChatScreen.kt` / `ChatViewModel.kt` / `ChatUiState.kt` | 메인 채팅 화면 |
| `feature/voice/` | `VoiceOverlay.kt` / `VoiceViewModel.kt` / `VoiceUiState.kt` | 음성 입력 오버레이 |
| `feature/calendar/` | `CalendarScreen.kt` / `CalendarViewModel.kt` / `CalendarUiState.kt` | 일정 화면 |
| `feature/approval/` | `ApprovalSheet.kt` / `ApprovalViewModel.kt` / `ApprovalUiState.kt` | 승인 바텀시트 |
| `feature/memory/` | `MemoryScreen.kt` / `MemoryViewModel.kt` / `MemoryUiState.kt` | 메모리 관리 화면 (v1) |
| `feature/settings/` | `SettingsScreen.kt` / `SettingsViewModel.kt` / `SettingsUiState.kt` | 설정 화면 |

**세션 정책**
- v0에서는 하나의 활성 채팅 세션을 유지한다
- 사용자가 명시적으로 “새 대화 시작”을 하기 전까지 동일 세션을 유지한다
- `ChatViewModel`은 `SavedStateHandle` + 필요 시 `SessionStore`를 통해 sessionId를 복원한다
- 화면 회전, 일시적 프로세스 재생성에서도 sessionId 복원이 가능해야 한다

---

### 4-9. `navigation/`

| 파일 | 책임 |
|---|---|
| `AppNavHost.kt` | NavHost 정의, 전체 라우트 등록 |
| `AppDestination.kt` | 화면 경로 상수 sealed class |

---

## 5. Directory Structure

```text
com.kosmos.app
│
├── app/
│   ├── KosmosApp.kt
│   ├── MainActivity.kt
│   └── di/
│       ├── AppModule.kt
│       ├── ModelModule.kt
│       ├── MemoryModule.kt
│       ├── AgentModule.kt
│       └── PlatformModule.kt
│
├── core/
│   ├── common/
│   │   ├── AppResult.kt
│   │   ├── AppError.kt
│   │   └── Constants.kt
│   ├── config/
│   │   ├── AppConfig.kt
│   │   ├── FeatureFlags.kt
│   │   └── ModelConfig.kt
│   ├── logging/
│   │   ├── AppLogger.kt
│   │   └── AuditLogger.kt
│   ├── mapper/
│   │   ├── ErrorCodeMapper.kt
│   │   └── TimeMapper.kt
│   └── security/
│       ├── PermissionPolicy.kt
│       ├── ApprovalRules.kt
│       └── Redaction.kt
│
├── domain/
│   ├── model/
│   │   ├── ChatMessage.kt
│   │   ├── AssistantResponse.kt
│   │   ├── ActionCard.kt
│   │   ├── ChatRequest.kt
│   │   ├── UserProfile.kt
│   │   ├── CalendarEvent.kt
│   │   ├── CalendarDraft.kt
│   │   ├── ScheduleData.kt
│   │   ├── KnowledgeNote.kt
│   │   ├── TaskItem.kt
│   │   ├── AuditEvent.kt
│   │   ├── SearchRequest.kt
│   │   ├── SearchResultItem.kt
│   │   ├── ApprovalRequest.kt
│   │   └── ModelOutput.kt
│   ├── memory/
│   │   ├── ProfileRepository.kt
│   │   ├── ConversationRepository.kt
│   │   ├── TaskRepository.kt
│   │   ├── KnowledgeRepository.kt
│   │   └── AuditRepository.kt
│   ├── modelrunner/
│   │   ├── ModelRunner.kt
│   │   ├── ModelLoadState.kt
│   │   ├── ModelInfo.kt
│   │   └── InferenceMetrics.kt
│   ├── agent/
│   │   ├── Agent.kt
│   │   └── AgentResult.kt
│   ├── tool/
│   │   ├── CalendarTool.kt
│   │   ├── SpeechToTextTool.kt
│   │   ├── WebSearchTool.kt
│   │   └── FileTool.kt
│   ├── policy/
│   │   ├── ExecutionPolicy.kt
│   │   └── ApprovalPolicy.kt
│   └── usecase/
│       ├── SendChatMessageUseCase.kt
│       ├── ProcessVoiceInputUseCase.kt
│       ├── ProcessImageInputUseCase.kt
│       ├── GetTodayScheduleUseCase.kt
│       ├── CreateCalendarDraftUseCase.kt
│       ├── ApproveAndSaveEventUseCase.kt
│       ├── SearchKnowledgeUseCase.kt
│       ├── ExportMemoryUseCase.kt
│       └── ImportMemoryUseCase.kt
│
├── assistant/
│   ├── orchestrator/
│   │   ├── AssistantOrchestrator.kt
│   │   ├── TaskRouter.kt
│   │   └── IntentClassifier.kt
│   ├── context/
│   │   ├── ContextBuilder.kt
│   │   ├── PromptAssembler.kt
│   │   └── ResponseParser.kt
│   ├── approval/
│   │   └── ApprovalCoordinator.kt
│   ├── guard/
│   │   ├── PreExecutionGuard.kt
│   │   └── PostExecutionValidator.kt
│   ├── audit/
│   │   └── AuditTrailService.kt
│   ├── persona/
│   │   └── PersonaProfileManager.kt
│   └── agent/
│       ├── CalendarAgent.kt
│       ├── SearchAgent.kt
│       └── KnowledgeAgent.kt
│
├── data/
│   └── local/
│       ├── db/
│       │   ├── KosmosDatabase.kt
│       │   ├── dao/
│       │   │   ├── ProfileDao.kt
│       │   │   ├── ConversationDao.kt
│       │   │   ├── TaskDao.kt
│       │   │   ├── KnowledgeDao.kt
│       │   │   └── AuditDao.kt
│       │   └── entity/
│       │       ├── ProfileEntity.kt
│       │       ├── ConversationEntity.kt
│       │       ├── TaskEntity.kt
│       │       ├── KnowledgeEntity.kt
│       │       └── AuditEntity.kt
│       ├── prefs/
│       │   ├── SettingsDataStore.kt
│       │   ├── ModelRegistryStore.kt
│       │   └── SessionStore.kt
│       ├── file/
│       │   ├── ExportImportManager.kt
│       │   └── ExportManifest.kt
│       └── repository/
│           ├── ProfileRepositoryImpl.kt
│           ├── ConversationRepositoryImpl.kt
│           ├── TaskRepositoryImpl.kt
│           ├── KnowledgeRepositoryImpl.kt
│           └── AuditRepositoryImpl.kt
│
├── runtime/
│   ├── gemma/
│   │   ├── GemmaModelRunner.kt
│   │   ├── GemmaRuntimeManager.kt
│   │   └── FakeModelRunner.kt
│   ├── multimodal/
│   │   └── ImageInputAdapter.kt
│   └── metrics/
│       └── RuntimeMetricsCollector.kt
│
├── platform/
│   ├── calendar/
│   │   ├── AndroidCalendarTool.kt
│   │   └── FakeCalendarTool.kt
│   ├── speech/
│   │   ├── AndroidSpeechToTextTool.kt
│   │   └── FakeSpeechToTextTool.kt
│   ├── share/
│   │   └── ShareIntentHandler.kt
│   ├── storage/
│   │   └── AndroidFileTool.kt
│   ├── network/
│   │   └── WebSearchGateway.kt
│   └── permissions/
│       └── PermissionManager.kt
│
├── feature/
│   ├── chat/
│   │   ├── ChatScreen.kt
│   │   ├── ChatViewModel.kt
│   │   └── ChatUiState.kt
│   ├── voice/
│   │   ├── VoiceOverlay.kt
│   │   ├── VoiceViewModel.kt
│   │   └── VoiceUiState.kt
│   ├── calendar/
│   │   ├── CalendarScreen.kt
│   │   ├── CalendarViewModel.kt
│   │   └── CalendarUiState.kt
│   ├── approval/
│   │   ├── ApprovalSheet.kt
│   │   ├── ApprovalViewModel.kt
│   │   └── ApprovalUiState.kt
│   ├── memory/
│   │   ├── MemoryScreen.kt
│   │   ├── MemoryViewModel.kt
│   │   └── MemoryUiState.kt
│   └── settings/
│       ├── SettingsScreen.kt
│       ├── SettingsViewModel.kt
│       └── SettingsUiState.kt
│
└── navigation/
    ├── AppNavHost.kt
    └── AppDestination.kt
```

---

## 6. Data Flow

### 6-1. 텍스트 채팅 데이터 흐름

```text
[ChatScreen]
    │ 사용자 입력 이벤트
    ▼
[ChatViewModel]
    │ sessionId 확보(SavedStateHandle/SessionStore)
    │ SendChatMessageUseCase 호출
    ▼
[SendChatMessageUseCase]
    │ 입력 검증
    │ ChatRequest 구성
    ▼
[AssistantOrchestrator]
    ├─ ContextBuilder.build()
    │   ├─ ConversationRepository → 최근 5턴 조회 (v0)
    │   └─ KnowledgeRepository → 관련 항목 3개 조회 (v1)
    │
    ├─ PromptAssembler.assemble()
    │
    ├─ ModelRunner.generate()
    │
    ├─ ResponseParser.parse()
    │   ├─ TextOutput → 일반 응답
    │   ├─ CalendarDraftOutput → 승인 필요
    │   ├─ SearchOutput → v0 차단 / v1 SearchAgent
    │   └─ KnowledgeSaveOutput → v0 차단 / v1 KnowledgeAgent
    │
    ├─ PreExecutionGuard.check()
    │
    ├─ ConversationRepository.save(user)
    ├─ ConversationRepository.save(assistant)
    │
    └─ AuditTrailService.record()
    ▼
[ChatViewModel]
    │ UiState 업데이트
    ▼
[ChatScreen]
    └─ 응답 표시
```

**정책**
- v0에서 검색/지식 저장 출력은 실행하지 않고 안내 응답 또는 차단 처리한다
- 사용자 메시지는 optimistic append 가능
- assistant 메시지는 응답 확정 후 append 및 저장한다
- v0에서는 UI 메시지 리스트와 DB 저장은 분리하며, 실시간 DB observe는 도입하지 않는다

### 6-2. 일정 초안 생성 및 승인 흐름

```text
[ChatScreen / CalendarScreen]
    │ "내일 3시 병원 일정" 입력
    ▼
[AssistantOrchestrator]
    │ IntentClassifier → calendar_draft 의도 분류
    │ TaskRouter → CalendarAgent 라우팅
    ▼
[CalendarAgent]
    │ CreateCalendarDraftUseCase 호출
    ▼
[CreateCalendarDraftUseCase]
    │ ModelRunner로 구조화 출력 요청
    │ → CalendarDraft JSON 파싱
    ▼
[ApprovalCoordinator]
    │ ApprovalRequest 생성 및 pending state 관리
    ▼
[ApprovalViewModel → ApprovalSheet]
    │ 사용자 승인 / 거부
    ├─ 승인/거부 결과 전달 → ApproveAndSaveEventUseCase
    │
    └─ ApproveAndSaveEventUseCase
         ├─ 승인 시 AndroidCalendarTool.insert()
         ├─ 거부 시 저장 생략
         └─ 승인/거부 Audit 기록
```

**책임 분리 원칙**
- `ApprovalCoordinator`는 승인 대기 상태를 관리한다
- `ApproveAndSaveEventUseCase`는 승인 결과에 따라 실제 저장 및 Audit 기록을 담당한다
- 승인/거부 Audit은 한 곳에서만 기록되도록 유지한다

### 6-3. 음성 입력 데이터 흐름

```text
[VoiceOverlay]
    │ 녹음 시작
    ▼
[VoiceViewModel]
    │ SpeechToTextTool.start()
    ▼
[AndroidSpeechToTextTool]
    │ state: LISTENING → TRANSCRIBING → DONE
    │ transcript: null → "인식된 텍스트"
    ▼
[VoiceViewModel]
    │ transcript 구독
    │ 사용자 확인 후 전송
    ▼
[ProcessVoiceInputUseCase]
    │ SendChatMessageUseCase로 위임
    ▼
[AssistantOrchestrator]
    └─ 일반 채팅 흐름 재사용
```

### 6-4. 모델 구조화 출력 스키마

```json
{ "action": "text", "content": "응답 내용" }
```

```json
{
  "action": "calendar_draft",
  "title": "병원 진료",
  "startIso": "2025-05-12T15:00:00+09:00",
  "endIso": "2025-05-12T16:00:00+09:00",
  "note": "내과 진료",
  "confidence": 0.92
}
```

```json
{ "action": "search", "query": "검색어", "reason": "최신 정보 필요" }
```

```json
{
  "action": "save_knowledge",
  "title": "메모 제목",
  "content": "내용",
  "tags": ["태그1", "태그2"]
}
```

**파싱 실패 처리**
- JSON 파싱 실패 → TextOutput fallback
- `confidence < 0.7`인 CalendarDraft → 자동 승인 불가, 반드시 ApprovalSheet 표시
- v0에서 `SearchOutput`, `KnowledgeSaveOutput`은 실행하지 않고 차단/안내 처리

---

## 7. State Management

### 7-1. UI 상태 원칙

모든 화면은 **5가지 상태**를 명시적으로 관리한다.

```kotlin
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    object Empty : UiState<Nothing>()
    data class Error(val error: AppError) : UiState<Nothing>()
}

> **예외 조항 (Exception):** `ChatUiState`는 화면의 복합 상태(입력 필드, 채팅 리스트, 첨부 파일 등) 동시 관리 특수성을 고려하여 5-state sealed class 대신 단일 `data class` 구현을 허용한다.
```

### 7-2. UI 렌더링 성능 최적화 (Compose)
- **상태 관찰 (Lifecycle)**: 백그라운드 전환 시 메모리 및 배터리 누수를 방지하기 위해, 모든 Compose Screen에서의 Flow 상태 수집은 반드시 `collectAsStateWithLifecycle()`을 사용한다.
- **안정성 (Recomposition)**: `List`나 `Map`과 같은 불안정(Unstable) 자료형은 Compose 컴파일러가 상태 변경 여부를 판단할 수 없으므로, 데이터 변경 시마다 전체 UI가 재구성되는 원인이 된다. UI 모델(`UiState`) 내부의 컬렉션은 반드시 `kotlinx-collections-immutable`의 `ImmutableList` 등을 활용하여 작성한다.
- **대용량 리스트 렌더링**: AuditLog, Knowledge 등 대규모 데이터를 UI 리스트로 렌더링할 경우, 메모리 초과(OOM)를 방지하기 위해 페이징한다. 단, **리포지토리는 `Flow<PagingData<T>>`를 반환하지 않는다** — `:domain`은 Pure Kotlin JVM 모듈이므로 `androidx` 타입을 공개 계약에 노출하면 안 된다(v0.7.4). 리포지토리는 `suspend fun getX(offset: Int, limit: Int): AppResult<List<T>>`만 제공하고, ViewModel이 `ui/paging/DefaultPagingSource`로 감싸 `Pager`를 만든 뒤 `cachedIn(viewModelScope)`한다. Compose에서는 `collectAsLazyPagingItems()`로 수집한다.

### 7-3. ViewModel 상태 관리 패턴

```kotlin
class ChatViewModel @Inject constructor(
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var isInFlight = false

    private val sessionId: String =
        savedStateHandle["session_id"] ?: UUID.randomUUID().toString().also {
            savedStateHandle["session_id"] = it
        }

    fun sendMessage(input: String) {
        if (isInFlight) return
        if (input.isBlank()) return

        isInFlight = true

        viewModelScope.launch {
            try {
                when (val result = sendChatMessageUseCase(...)) {
                    is AppResult.Success -> { ... }
                    is AppResult.Failure -> { ... }
                }
            } finally {
                isInFlight = false
            }
        }
    }
}
```

### 7-3. 공유 상태 관리

| 상태 | 관리 위치 | 공유 범위 |
|---|---|---|
| 모델 로드 상태 | `GemmaRuntimeManager` | 전체 앱 |
| 현재 세션 ID | `ChatViewModel` + `SessionStore` | Chat 화면 / 앱 |
| 승인 요청 | `ApprovalCoordinator` | Chat + Approval |
| 검색 토글 상태 | `ChatViewModel` | Chat 화면 (v1) |
| STT transcript | `SpeechToTextTool` | Voice UI |

---

## 8. Database Design Overview

### 8-1. 저장 방식 구분

| 저장소 | 용도 | 형식 |
|---|---|---|
| Room (SQLite) | 대화, 작업, 지식, 감사 로그 | 관계형 테이블 |
| DataStore | 앱 설정, 모델 정보, 활성 세션 ID | Key-Value |
| 파일 시스템 | 모델 파일, export 파일 | Binary / ZIP |

### 8-2. Room DB 테이블 스키마

**`KosmosDatabase` 버전 관리**

```kotlin
@Database(
    version = 1,
    entities = [
        ProfileEntity::class,
        ConversationEntity::class,
        TaskEntity::class,
        KnowledgeEntity::class,
        AuditEntity::class
    ],
    exportSchema = true,
    autoMigrations = []
)
abstract class KosmosDatabase : RoomDatabase()
```

### 8-3. DataStore 키 목록

```kotlin
object SettingsKeys {
    val RESPONSE_STYLE = stringPreferencesKey("response_style")
    val SEARCH_AUDIT_ENABLED = booleanPreferencesKey("search_audit_enabled")
    val THERMAL_LOG_ENABLED = booleanPreferencesKey("thermal_log_enabled")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

object ModelRegistryKeys {
    val CURRENT_MODEL_ID = stringPreferencesKey("current_model_id")
    val MODEL_PATH = stringPreferencesKey("model_path")
    val MODEL_VERSION = stringPreferencesKey("model_version")
    val QUANTIZATION = stringPreferencesKey("quantization")
    val LAST_LOADED_AT = longPreferencesKey("last_loaded_at")
}

object SessionKeys {
    val ACTIVE_SESSION_ID = stringPreferencesKey("active_session_id")
}
```

### 8-4. 시간 표현 원칙

- **DB 저장**: Unix timestamp(ms)
- **도메인 내부**: Long(ms) 또는 명시적 ISO 문자열
- **계약/경계 타입**: ISO 8601 string 우선
- mapper 계층에서만 변환한다

### 8-5. 모델 파일 경로 규칙

```text
getExternalFilesDir("models")
→ /sdcard/Android/data/com.kosmos.app/files/models/
```

---

## 9. Internal Contract Architecture

### 9-1. 외부 API 없음 원칙

이 프로젝트는 v0(MVP) 범위에서 **외부 REST API를 사용하지 않는다.**
모든 기능은 로컬 함수 호출로 처리된다.

### 9-2. 내부 계약 문서 기준

이 프로젝트의 내부 계약은 **api_spec.yaml**을 기준 문서로 사용한다.

역할 분리:
- **PRD.md**: 제품 정책 / 범위 / UX 기준
- **api_spec.yaml**: 내부 계약의 source of truth
- **architecture.md**: 구조 / 레이어 책임 / 의존 방향
- **tasks.md**: 구현 순서 / 작업 단위

### 9-3. 핵심 계약 예시

**ModelRunner 계약**
```kotlin
interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>
    suspend fun generate(prompt: String, onToken: ((String) -> Unit)? = null): AppResult<String>
    suspend fun generateWithImage(prompt: String, imageBytes: ByteArray): AppResult<String>
    suspend fun cancel()
    suspend fun warmUp()
}
```

**CalendarTool 계약**
```kotlin
interface CalendarTool {
    suspend fun readEvents(startMs: Long, endMs: Long): AppResult<List<CalendarEvent>>
    suspend fun insert(draft: CalendarDraft): AppResult<Long>
}
```

**SpeechToTextTool 계약**
```kotlin
interface SpeechToTextTool {
    val state: StateFlow<SttState>
    val transcript: StateFlow<String?>
    fun start(preferOffline: Boolean = true, language: String = "ko-KR")
    fun stop()
    fun cancel()
}
```

**WebSearchTool 계약**
```kotlin
interface WebSearchTool {
    suspend fun search(request: SearchRequest): AppResult<List<SearchResultItem>>
}
```

### 9-4. Fake / Stub 구현 정책

| 인터페이스 | Fake 파일 위치 | 생성 시점 |
|---|---|---|
| `ModelRunner` | `runtime/gemma/FakeModelRunner.kt` | v0 초기 |
| `CalendarTool` | `platform/calendar/FakeCalendarTool.kt` | 캘린더 기능 구현 시 |
| `SpeechToTextTool` | `platform/speech/FakeSpeechToTextTool.kt` | 음성 기능 구현 시 |
| `ConversationRepository` | `data/repository/fake/FakeConversationRepository.kt` | Assistant 테스트 시 |
| `AuditRepository` | `data/repository/fake/FakeAuditRepository.kt` | Audit 테스트 시 |
| `WebSearchTool` | `platform/network/FakeWebSearchTool.kt` | v1 검색 시 |

---

## 10. Authentication Strategy

### v0(MVP) 인증 방식: 없음

- 앱 자체가 기기 잠금으로 보호됨
- 로컬 프로필은 앱 설치 시 자동 생성
- 다중 사용자 지원 없음
- export 파일은 파일 시스템 권한으로 보호

### 향후 확장 (v2)

Windows 확장 시 사용자 식별은
export 파일의 profile 정보 기반 import로 처리한다.
이 단계에서 선택적 비밀번호 기반 export 암호화(AES-256-GCM)를 도입한다.

---

## 11. Error Handling Strategy

### 11-1. `AppResult<T>` 래퍼

```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}
```

### 11-2. `AppError` 원칙

- 내부 로직: `AppError`
- 계약/직렬화: `ErrorCode`
- 매핑은 `ErrorCodeMapper`에서 단일 책임으로 관리한다

### 11-3. 레이어별 에러 처리 원칙

| 레이어 | 처리 원칙 |
|---|---|
| Presentation | `AppError`를 사용자 친화적 메시지로 변환 |
| ViewModel | `AppResult` 분기 처리 |
| UseCase | `AppResult`를 그대로 상위로 전달 |
| Orchestrator | fallback 처리 |
| Repository / Tool | try-catch 후 `AppResult.Failure(AppError)` 반환 |

### 11-4. 주요 fallback 정책

| 에러 상황 | fallback 처리 |
|---|---|
| v1 검색 실패 | 로컬 응답만 유지 + 안내 |
| JSON 파싱 실패 | TextOutput fallback |
| 모델 timeout | 오류 메시지 + 재시도 |
| STT 실패 | 텍스트 입력 유도 |
| 캘린더 쓰기 실패 | 오류 메시지 + Audit |
| 일정 시간 누락 | 내부 계약상 validation 실패, UX에서는 재확인 질문으로 변환 |

---

## 12. Logging Strategy

### 12-1. 로그 분류

| 분류 | 도구 | 저장 위치 | 목적 |
|---|---|---|---|
| 개발 로그 | `AppLogger` | Logcat | 디버깅 |
| 감사 로그 | `AuditTrailService` | `audit_events` | 실행 이력 추적 |
| 성능 로그 | `RuntimeMetricsCollector` | `audit_events` + 메모리 | 발열/추론 시간 모니터링 |

### 12-2. 감사 로그 기록 기준

v0 필수:
- MODEL_RUN
- TOOL_CALL
- APPROVAL_GRANTED
- APPROVAL_REJECTED
- ERROR

v1 확장:
- SEARCH_USED
- EXPORT
- IMPORT
- THERMAL_WARNING
- THERMAL_SHUTDOWN

### 12-3. 민감 정보 처리

```kotlin
object Redaction {
    fun sanitize(text: String): String {
        return if (isRedactionEnabled) truncate(text) else text
    }
}
```

---

## 13. Scalability Boundaries

### 13-1. v0(MVP)에서의 의도적 제한

| 항목 | v0 제한 | 확장 포인트 |
|---|---|---|
| 컨텍스트 길이 | 프리필 예산 1,700 토큰 (KV 4,096, GPU 발병점 아래 — ADR-021) | 상류 FP16 수정 시 예산 복원 (expand.md E-Phase 4) |
| 대화 히스토리 | Token Sliding Window (턴 수 고정 아님) | 예산 상향 시 자동 확장 |
| 메모리 검색 | Conversation 중심 | v1 Knowledge, v2 벡터 검색 |
| 검색 구현 | 구현하지 않음 | v1 WebSearchTool |
| 모델 | Gemma 4 E4B-it | ModelRunner 교체 |
| 스트리밍 응답 | 비스트리밍 고정 | v1 onToken 활성화 |
| 문서 요약 | 문서 이미지 기반 | 파일 직접 파싱은 후속 버전 |
| 세션 | 단일 활성 세션 | 다중 세션은 후속 검토 |

### 13-2. 확장 방법

**모델 교체**
```text
GemmaModelRunner → {새모델}ModelRunner
```

**새 Tool 추가**
```text
1. domain/tool 인터페이스 정의
2. platform 구현체 추가
3. PlatformModule 바인딩
4. TaskRouter 규칙 추가
```

**새 Agent 추가**
```text
1. domain/agent 구현
2. assistant/agent 추가
3. AgentModule 바인딩
4. TaskRouter 규칙 추가
```

---

## 14. Future Expansion Notes

### 14-1. v1 확장 항목

| 항목 | 준비된 구조 |
|---|---|
| 웹 검색 실제 구현 | `WebSearchTool` 인터페이스 준비 |
| 스트리밍 응답 | `onToken` 콜백 준비 |
| Export / Import | manifest 검증 구조 준비 |
| Paging 3 전환 | DAO 확장 구조 준비 |
| Task / Knowledge Memory | Repository / Entity / DAO 구조 준비 |

### 14-2. v2 확장 항목 (Windows)

- `domain/model/` 재사용 가능
- `domain/usecase/` 재사용 가능
- `data/local/`의 SQLite 구조 공유 가능
- 새로 필요한 것:
  - Windows ModelRunner
  - Windows UI
  - Windows Platform Tool 구현체

### 14-3. 과잉 설계 경고

> ⚠️ v0(MVP)에서 구현하지 말아야 할 것들
>
> - `assistant/harness/`
> - `assistant/planner/`
> - `assistant/session/` 고도화
> - `runtime/benchmark/`
> - Vector DB / 임베딩 모델
> - 멀티모듈 Gradle 분리
> - 문서 파일 직접 파싱 기반 요약
> - 세션 전체 웹 검색 ON
---

## 15. 아키텍처 결정 기록 (ADR)

### ADR-001. 웹 검색 승인 모델을 건별 승인에서 전역 토글로 변경 (2026-07-31)
- **결정**: `SearchWikipedia` 도구는 건별 승인 다이얼로그 대신 채팅 헤더의 영속형 토글(`SettingsDataStore.webSearchEnabledFlow`, 기본 OFF)로 허용을 제어한다.
- **근거**: 검색마다 승인 다이얼로그가 뜨는 UX가 사용을 방해했고, 사용자 요청으로 기획 변경. 프라이버시는 "기본 OFF + 이중 방어(프롬프트 미노출 + `BaseAgent` 실행 시점 allowlist 차단)"로 유지한다.
- **영향**: `core/security/ApprovalRules`의 `WEB_SEARCH`는 `requiresApproval=false`(토글 게이트)로 재정의됨. PRD F7 개정.

### ADR-002. 툴 승인·allowlist를 실행 공통 경로(BaseAgent)에서 강제 (2026-07-31)
- **결정**: 각 `ToolExecutor`가 `actionType`을 선언하고, `BaseAgent.executeToolInner`가 ① 에이전트별 allowlist 검증 ② `ApprovalRules.requiresApproval` 기반 `ApprovalCoordinator` 승인 대기를 일괄 수행한 후에만 실행한다.
- **근거**: 기존에는 allowlist가 프롬프트 텍스트에만 존재하고 승인은 `AddScheduleToolExecutor` 내부 하드코딩뿐이라, 모델(또는 주입된 문서)이 임의 툴을 무승인 실행할 수 있었다.
- **영향**: `AddMemory`는 MEMORY_WRITE 승인 대상으로 편입되어 DefaultAgent에 노출. 신규 툴은 executor의 `actionType` 선언만으로 정책이 적용된다.

### ADR-003. 첨부 문서를 SYSTEM 역할에서 USER 역할 구분 블록으로 격리 (2026-07-31)
- **결정**: `documentText`는 `[Attached Document]` 구분 블록을 붙여 USER 역할로 저장한다.
- **근거**: SYSTEM 역할 저장 시 문서 내 지시문이 시스템 프롬프트로 승격되어 세션 내내 지속되는 프롬프트 인젝션 경로가 됨.
- **영향**: `PromptAssembler`의 `[Context / Knowledge]` 블록에는 내부 RAG 메모리만 포함된다.

### ADR-004. 기기 시스템 캘린더 실연동 — 로컬 DB 우선 + Best-Effort 동기화 (2026-07-31)
- **결정**: `AddScheduleUseCase`는 로컬 Room DB 저장(단일 진실 원천) 후 `CalendarTool`(AndroidCalendarTool)로 기기 캘린더에 best-effort 삽입한다. `GetTodayScheduleUseCase`는 기기 캘린더 이벤트를 읽어 (제목, 시작 시각) 기준 중복 제거 후 로컬 일정과 병합한다.
- **근거**: 바인딩만 되고 미사용이던 `AndroidCalendarTool`을 PRD 원안대로 배선(사용자 결정 2026-07-31). 권한 미보유/캘린더 계정 부재 시에도 앱 기능이 죽지 않도록, 동기화 실패는 경고 로깅으로 강등한다(Graceful Degradation).
- **영향**: 캘린더 권한은 일괄 선요청 대신 컨텍스트 요청 — ChatScreen(일정 승인 시 WRITE+READ), CalendarScreen(진입 시 READ, 승인 시 재조회). Robolectric 환경(권한 거부)에서도 기존 E2E가 로컬 경로로 통과한다.

### ADR-005. 라이트/다크 테마 전환 지원 — 시맨틱 색상 토큰 도입 (2026-07-31)
- **결정**: 문서(DESIGN.md v1.2, Sky Blue 라이트 기획)와 구현(다크 Glassmorphism)의 불일치를 **양쪽 모두 지원**하는 방향으로 해소한다(사용자 결정: 옵션 D-b). 하드코딩된 top-level `Color` 상수(BgColor/Cyan/TextPrimary 등 20종)를 `KosmosColors` 시맨틱 토큰 데이터 클래스로 대체하고, `LocalKosmosColors` CompositionLocal로 테마별 팔레트를 주입한다.
- **토큰 접근**: 화면은 `KosmosTheme.colors.<토큰>`으로 접근한다. 색상 리터럴 직접 사용 금지.
- **팔레트**: 다크는 기존 v0.4.0 Glassmorphism 값을 계승, 라이트는 DESIGN.md의 Sky Blue 팔레트(canvas #F7FBFD / ink #1F2A33 / primary-strong #39AED8 / hairline #D9E6EC)를 Glassmorphism 구조에 적용한다. 라이트에서는 흰색 반투명 대신 카드 표면을 불투명에 가깝게 올리고 hairline 테두리로 계층을 만든다.
- **부가 토큰**: `onAccent`(accent 배경 위 전경색 — 다크는 어두운 남색, 라이트는 흰색)와 `auroraAlpha`(라이트에서 배경 연출 강도 0.35배 감쇠)를 도입해 대비·시각 과잉 문제를 해결한다.
- **모드 선택**: `ThemeMode`(SYSTEM/LIGHT/DARK, 기본 SYSTEM)를 설정 화면 APPEARANCE 섹션에서 선택하고 DataStore(`theme_mode`)에 영속한다. `ThemeViewModel`을 MainActivity 루트에서 구독해 전체 트리에 적용하며, 상태바 아이콘 명암(`isAppearanceLightStatusBars`)도 함께 동기화한다.
- **제약**: `DrawScope`/`LazyListScope` 람다는 `@Composable`이 아니므로 토큰을 바깥에서 `val`로 추출해 전달한다(AuroraBackground, OrbPulse, ScheduleContent). `Modifier.glassEffect`의 색상 기본값은 테마 의존이므로 `null` 기본값 + `composed {}` 내부 해석 방식을 사용한다.

### ADR-006. 모델 다운로드를 WorkManager 전경 작업으로 이관 + HTTP Range 재개 (2026-08-05)
- **결정**: 3.6GB 모델 다운로드를 `viewModelScope`에서 `@HiltWorker` 기반 `CoroutineWorker`(`ModelDownloadWorker`)로 이관하고, 전경 서비스 알림으로 진행률을 노출한다. 전송 실패는 HTTP `Range`/`If-Range`로 받은 지점부터 이어받는다.
- **근거**: 기존 구조는 화면을 벗어나거나 앱이 백그라운드로 가면 전송이 죽었다(진행 카드가 "앱을 종료하면 다운로드가 중단됩니다"라고 안내할 정도로 한계가 노출돼 있었다). 또한 재시도를 붙여도 `.part`를 `finally`에서 무조건 삭제했기 때문에 매번 0바이트부터 다시 받아야 해 재시도 자체가 무의미했다.
- **계층 배치**: `:domain`·`:core`는 Pure Kotlin JVM이라 Android 타입을 참조할 수 없으므로 인터페이스(`ModelDownloadScheduler`, `ModelDownloadStatus`)만 `:domain`에 두고 구현(`WorkManagerModelDownloadScheduler`, Worker, 알림)은 `:app`에 둔다. `:data`는 바이트 전송(`ModelDownloadService`)만 담당하며 WorkManager를 의존하지 않는다. `DownloadModelUseCase`는 `status`/`enqueue`/`cancel`/`acknowledge` 4개 위임만 남긴 얇은 파사드가 되어, ViewModel이 계속 `:domain`만 의존한다.
- **작업 정책**: 유니크 작업명 `model_download` + `ExistingWorkPolicy.KEEP`(버튼 연타로 3.6GB를 두 번 받지 않는다. 이전 작업이 이미 종료된 경우엔 KEEP도 새 작업을 넣으므로 재시도 경로는 막히지 않는다), 제약은 `NetworkType.UNMETERED`(사용자 결정 — 이동통신 데이터 요금 사고 방지) + `setRequiresStorageNotLow`, 백오프는 `EXPONENTIAL` 30초 시작, 최대 5회 시도.
- **중복 방지 이전**: `ModelManagementViewModel.downloadJob?.isActive` 가드를 제거했다. 이 가드는 ViewModel이 죽으면 함께 사라져 실제로 중복을 막지 못했다.
- **예외 분류**: `ModelDownloadException`을 `Transient`(IOException·타임아웃·5xx·408·429) / `Permanent`(4xx·크기 불일치·확정 실패) / `InsufficientStorage`로 나눠 `Transient`에만 `Result.retry()`를 반환한다. 이전 구현의 `message.contains("space")` 부분 문자열 판정(→ `InsufficientStorage(0L)` 하드코딩)을 제거했다 — ADR 이전 `ValidationReason` 도입과 같은 이유다.
- **실패 사유 전달**: 출력 `Data`에 렌더된 문자열이 아니라 `ErrorCode` 이름을 실어 프로세스 경계를 넘긴다. 사용자 문구의 단일 출처는 `ErrorMessages`로 유지되고, `InsufficientStorage`는 실제 필요 바이트 수를 함께 전달해 UI가 필요 용량을 안내할 수 있다.
- **`.part` 보존 및 `.bak` 롤백**: 일시적 실패와 사용자 취소에서는 `.part`와 `.part.meta`(url·ETag·총 크기 사이드카, 첫 바이트 이전에 기록)를 남겨 다음 시도가 이어받는다. 영구 실패에서만 정리한다. 확정 단계는 기존 모델을 `.bak`으로 옮긴 뒤 rename 하고 실패 시 되돌린다 — 이전 구현은 `target.delete()`를 먼저 해서 rename이 실패하면 사용자가 동작하던 모델까지 잃었다.
- **무결성 검증(수용된 리스크)**: 크기 비교만 한다. 3.6GB SHA-256은 수 분이 걸려 실용적이지 않다. 손상 파일이 크기만 일치하는 경우는 검출하지 못한다.
- **Graceful Degradation**: 알림 권한 거부나 API 31+ 전경 서비스 시작 제약으로 `setForeground`가 실패해도 경고 로깅으로 강등하고 전송을 계속한다(ADR-004 선례). POST_NOTIFICATIONS는 다운로드 시작 시 요청하되 결과와 무관하게 진행한다 — 알림 거부는 피드백을 떨어뜨릴 뿐 기능을 막지 않는다.
- **부수 발견**: `:data`에 `kotlin-serialization` 플러그인이 누락되어 있어 모듈 내부에서 선언한 `@Serializable` 클래스의 직렬화기가 생성되지 않았다(컴파일은 통과하고 런타임에 `SerializationException`). `PartMeta`가 이어받기 판정에 실패하며 드러났고, 같은 원인으로 `ExportManifest` 기반 내보내기/가져오기도 동작하지 않던 상태였다. 플러그인 추가로 함께 해소.
- **제약(단위 테스트 불가 — 수동 QA 필요)**: 실제 전경 서비스 승격, 알림 렌더링, Doze 모드 백오프 타이밍, 실제 3.6GB 처리량, 알림이 꺼진 상태의 `SystemForegroundService` 동작, API 31+ `ForegroundServiceStartNotAllowedException` 경로. 핵심 확인 시나리오: ① 다운로드 시작 → 앱 강제 종료 → 재진입 시 진행률이 이어지는지 ② Wi-Fi를 끊었다 다시 연결했을 때 받은 지점부터 이어받는지 ③ Wi-Fi 없을 때 "Wi-Fi 연결을 기다리는 중" 카드가 뜨는지.
- **아이콘 부채**: `res/`에 다운로드용 24dp 모노 벡터가 없어 `android.R.drawable.stat_sys_download` 계열을 임시 사용한다.

### ADR-007. 스트리밍 계약을 원시 토큰에서 파싱 결과로 전환 (2026-08-06)
- **결정**: 앱 내부 스트리밍 계약 `ChatRequest.onToken: ((String) -> Unit)?`(원시 델타 토큰)을 `onStream: ((StreamUpdate) -> Unit)?`(파싱된 `content`/`thinking`)으로 교체하고, 스트리밍 파싱 지점을 `BaseAgent` 한 곳으로 모은다.
- **근거**: 같은 토큰 스트림을 세 곳이 각자 누적했고, 그중 `ChatViewModel.accumulatedRaw`만 스코프가 어긋나 있었다. 근본 원인은 계약에 **턴 경계 신호가 없다는 것**이다 — 툴 루프가 다음 턴으로 넘어가도 UI 콜백은 그 사실을 알 수 없어 누적기를 비울 수 없었다. 그래서 1턴 문장이 2턴 문장 앞에 남아 보이다가, 완료 시 마지막 턴만 커밋돼 텍스트가 줄어드는 것처럼 보였고 DB와 화면이 불일치했다.
- **계층 경계**: `ModelRunner`의 원시 델타 계약은 **그대로 둔다**. 엔진 계층에서는 델타가 옳은 표현이고 `GemmaModelRunner`의 누적(`finalResponse`)은 generate 호출당 스코프라 이미 정확하다. 테스트 fake 5개가 이 인터페이스를 구현하므로 변경 비용도 크다. 바꾼 것은 앱 내부 계약뿐이다.
- **누적기 배치**: `BaseAgent.accumulatedToken`은 툴 루프 `while` 안에 선언돼 턴마다 리셋된다. 파싱을 여기로 모으면 턴 누적 결함이 **구조적으로 불가능**해진다 — 규율이 아니라 스코프가 보장한다.
- **성능 유지**: 0.5.10에서 도입한 "태그 감지 후에만 파싱" 최적화(토큰마다 전체 문자열을 정규식 재파싱하면 O(n²))를 유지한다. 태그가 하나도 없는 구간에서는 누적 문자열이 곧 본문이므로 정규식 파싱을 건너뛰고, 꼬리에 걸린 태그 조각만 `ToolParser.stripIncompleteTag`로 잘라낸다. 감지 윈도우는 `<tool_call`과 `<|think|` 양쪽을 본다.
- **미완성 태그 절단**: `toolRegex`가 닫는 태그를 요구하므로 스트리밍 중 열린 `<tool_call>`은 매칭되지 않고 본문에 남아 화면에 렌더됐다(툴 콜 턴에서는 가시 구간 대부분 동안 `<tool_call>{"name":"AddSch`가 한 글자씩 자라는 것이 보였다). 완전한 여는 태그와 꼬리의 부분 접두사(`<tool_c`, `<` 하나까지)를 모두 잘라낸다. 다음 토큰에 복원되므로 손실은 없고, 모델이 실제로 쓴 `<`가 한 틱 늦게 보이는 것을 대가로 받아들인다.
- **빈 본문의 `null` 정규화**: `StreamUpdate.content`는 보일 것이 없으면 `null`이다. 빈 문자열을 넘기면 UI가 "텍스트 있음"으로 오해해 타이핑 인디케이터를 숨겼고(`ChatScreen`의 조건이 `streamingText == null`), 생각 블록만 스트리밍되는 구간에서 **빈 버블에 스피너도 없는** 상태가 됐다.
- **다중 생각 블록**: `thinkRegex`를 `find`(단수)에서 `findAll`로 바꿨다. 이전에는 두 번째 이후 블록이 본문에 남아 프로토콜 문법이 노출됐다. 닫히지 않은 블록을 버퍼 끝까지 매칭하는 `|$` 대안은 스트리밍에 필요하므로 유지한다.
- **제약**: 스트리밍 렌더 자체(실기기 체감, 재구성 빈도)는 단위 테스트로 확인할 수 없다. 핵심 확인 시나리오: ① 툴 콜 대화에서 `<tool_call>` 문법이 보이지 않고 완료 시 텍스트가 줄어들지 않는지 ② 응답 초반 빈 버블 대신 타이핑 인디케이터가 보이는지.

### ADR-008. 자체 XML 툴 규약을 LiteRT 네이티브 함수호출로 전환 + 라우터 폐지 (2026-08-06)
- **결정**: 시스템 프롬프트로 `<tool_call>{"name":...}</tool_call>` 형식을 가르치던 방식을 버리고, LiteRT-LM 의 `@Tool`/`@ToolParam` + `ToolSet` 선언을 `ConversationConfig(tools = ...)` 로 넘겨 **모델의 정식 함수호출 템플릿**을 쓴다. `IntentClassifier`/`TaskRouter`/`CalendarAgent` 를 제거하고 `KosmosAgent` 하나로 합친다.
- **근거(실기기 증거)**: 감사 로그에서 모델이 `<tool_call>` 을 **한 번도** 생성하지 않았다. "내 자전거 비밀번호는 1234야, 기억해줘"에 평문으로 답하며 *"저는 이 정보를 영구적으로 저장하지 않고 대화 세션 내에서 기억"* 이라고 스스로 말했다 — 모델이 자기에게 툴이 있다는 사실을 몰랐다. 자체 규약은 Gemma 의 채팅 템플릿에 없으므로 온디바이스 4B급 모델이 프롬프트만으로 따라올 사전 지식이 없다. 조사에서 배제한 가설: `systemInstruction` 전달 누락(정상 전달됨), 토큰 예산 절단(시스템 지시는 절단 대상이 아님).
- **참조**: google-ai-edge/gallery(구글 공식 Gemma 예제 앱)가 같은 API 를 쓴다 — `TinyGardenTools`(`ToolSet` + `@Tool`), `LlmChatModelHelper`(`ConversationConfig(tools = ...)`). `kotlin-reflect` 의존이 필요하다(`ReflectionTool` 이 `KFunction` 으로 스키마를 만든다) — 없으면 컴파일은 되고 툴 선언 시점에 런타임 실패한다.
- **`automaticToolCalling = false`**: 런타임이 툴을 스스로 실행하는 기능을 **끈다.** PRD F4 의 승인 관문(캘린더 쓰기·메모리 쓰기)을 거쳐야 하므로 호출을 우리가 받아야 한다. `Message.toolCalls` 로 도착한 호출을 `ToolArguments.of(map)` 로 감싸면 기존 executor 4개와 승인 경로는 **변경이 없다.** 실행 결과는 `Content.ToolResponse(name, json)` 로 되돌린다 — 이전에는 `<tool_response>` 텍스트를 사용자 턴으로 위장해 보냈다.
- **라우터 폐지 근거**: 일정 등록 실패의 직접 원인이 라우팅 미스였다. `IntentClassifier` 는 쓰기 키워드와 캘린더 문맥 키워드가 **둘 다** 필요했는데 "치과 예약"에는 문맥 키워드가 없었다(목록에 `약속` 은 있지만 `예약` 은 없다 — 글자 순서가 반대인 다른 단어다). 그 결과 `DefaultAgent` 로 갔고 그 툴 목록에는 `AddSchedule` 이 없어 프롬프트에서 사라지고 실행 단계에서도 차단됐다. **에이전트별로 툴 목록이 다른 구조 자체가 "라우팅 오류 = 툴 소멸"이라는 실패 모드**였고, 한국어 표현을 키워드로 다 덮는 것은 불가능하다. 네이티브 함수호출에서는 모델이 선언된 툴 중에서 스스로 고르므로 사전 라우팅이 불필요하다. 실행 시점 allowlist 재검증(`BaseAgent.executeToolInner`)은 **유지한다** — 프롬프트 주입 방어는 라우팅과 별개 관심사다.
- **`ToolParser` 의 위치 변경**: 툴 호출 판정은 더 이상 정규식이 아니다(구조화된 값). `ToolParser` 는 `<|think|>` 처리와 **표시 단계 위생 처리**로 남는다 — 모델이 옛 규약을 흉내내 `<tool_call>` 텍스트를 흘려도 화면에 노출되지 않게 걸러낸다. 그에 따라 스트리밍 중 조기 취소(`<tool_call` 태그 감지 후 `modelRunner.cancel()`)도 제거했다.
- **샘플러 조정**: `temperature 0.8 → 0.3`. 실기기에서 "비밀번호 1234"가 342/4213/2143 으로 왜곡됐다. 사실 회상과 툴 인자(숫자·날짜·고유명사)의 정확성이 창의성보다 중요하다.
- **부수 수정**: `buildTimeBlock()` 이 초 단위여서 시스템 지시가 매 턴 달라졌고, 그 결과 `getOrCreateConversation` 의 재사용 판정이 항상 거짓이 되어 대화를 매번 파괴·재생성(전체 프리필)했다 — 분 단위로 내려 그 판정을 살렸다. Wikipedia 요청에 `User-Agent` 를 추가하고 무의미한 `origin=*` 을 제거했다(Wikimedia 정책 — 없으면 403/레이트리밋).
- **검증 한계(중요)**: 기존 테스트는 fake 모델이 툴 호출을 스크립트하므로 **배관만 검증하고 실제 모델이 호출을 생성하는지는 검증하지 못한다.** 이것이 이 결함이 오래 숨어 있던 이유다. `automaticToolCalling = false` 에서 `Message.toolCalls` 가 채워지는지는 문서가 아니라 디컴파일된 API 표면으로 확인했으므로, **실기기 확인이 이 ADR 의 전제**다. 실패 시 대안: `automaticToolCalling = true` + `ToolSet` 메서드 본문에서 승인 처리(LLM 디스패처가 승인 대기 동안 블로킹되는 단점).
- **0.8.1 추기 — 첫 실기기 확인은 실패(`toolCalls=[]`), 원인 2건 보강**: 0.14.0 AAR 바이트코드 검증으로 배관 정상을 확정했다 — 툴 JSON 은 `automaticToolCalling` 과 무관하게 `nativeCreateConversation` 으로 무조건 전달되고, `automaticToolCalling = false` 일 때 `tool_calls` 메시지는 우리가 collect 하는 동일 Flow 로 배달된다(별도 이벤트 없음). 놓친 것은 두 겹이다. ① **`ExperimentalFlags.enableConversationConstrainedDecoding`** — gallery 는 툴을 쓰는 모든 태스크에서 이 전역 플래그를 `createConversation` 직전에 켜고 직후에 끈다. 툴 스키마로부터 FST 문법을 만들어 출력을 호출 구문으로 강제하는 장치다. `ConversationConfig` 필드가 아니라 생성 시점에만 읽히는 전역이라 전환 때 누락됐다. ② **모델 파일이 관문이다** — 네이티브는 모델 메타데이터의 jinja 템플릿(`LlmMetadata.jinja_prompt_template`)을 렌더링하며, 템플릿에 툴 블록이 없으면 선언이 통째로 탈락한다. 네이티브 proto 에서 `Gemma4`/`FunctionGemma` 는 툴 구문 필드를 갖지만 `Gemma3`/`Gemma3N` 은 빈 메시지고, gallery 허용 목록도 `llm_agent_chat` 을 Gemma 4 계열에만 부여한다. 실기기 판별 단서로 `renderPrefaceIntoString()` 로그를 심었다 — **preface 에 툴 블록이 없으면 코드가 아니라 모델 파일 교체가 답이다.** 부수: gallery 의 `automaticToolCalling = false` 경로(`ToolDispatcher.dispatchManualCall`)는 호출부가 없는 죽은 스캐폴딩이다 — 우리가 이 경로의 사실상 첫 사용자이므로 Plan B 를 유지한다.

### ADR-009. 프롬프트·모델 성향은 PC 실험 하네스로 실측 검증한다 (2026-08-07)
- **결정**: 시스템 프롬프트, 툴 설명, 샘플러 설정처럼 **모델의 성향에 의존하는 변경**은 실기기 왕복 대신 PC 하네스(`scratch/lab/`, `litert-lm-api` Python + 같은 `.litertlm` 파일)에서 먼저 검증한다. 실기기는 최종 확인용으로 남긴다.
- **근거**: 0.8.0~0.8.6 은 실기기 왕복 8회로 진행됐고, 매 회가 빌드→설치→수동 입력→로그 수집이었다. 그 사이 **가설 대부분이 틀렸다**(모델 파일 미지원, 스키마 충돌, 자동 실행 필요 등). 반면 PC 하네스는 엔진 로드 7~11초, 케이스당 6~9초(GPU)로 **17케이스 배치가 2분**이다. 첫 배치 한 번이 0.8.6 까지 못 찾은 결함 두 개(되묻기로 인한 일정 실패, `date='tomorrow'` 오해석)를 즉시 드러냈다.
- **재현 조건(하네스가 맞춰야 하는 것)**: 툴을 스키마로 선언(`tools=`), constrained decoding on, greedy(`top_k=1`), thinking off, `automatic_tool_calling=False`. 이 다섯 개가 앱의 `GemmaModelRunner` 설정이다. `render_message_to_string()` 으로 렌더링된 preface 를 실기기 로그의 preface 와 대조해 재현성을 확인했다(구조 동일).
- **한계(중요)**: ① 파이썬 패키지는 0.15.0, Android 는 0.14.0 이 최신이다(Maven 확인). 0.15.0 은 constrained decoding·thinking 을 전역 플래그가 아닌 config 객체로 노출하므로 **API 세부는 동일하지 않다.** ② 스키마 렌더링도 다르다 — 파이썬은 기본값 있는 파라미터를 `required` 에서 빼지만 Kotlin `ReflectionTool` 은 nullable 이어도 남긴다(둘 다 실측). ③ **하드웨어 고유 결함은 재현되지 않는다** — 숫자 왜곡("1234"→"134")이 PC 의 CPU·GPU 양쪽에서 한 번도 재현되지 않아 안드로이드 GPU(Adreno) 고유로 좁혀졌다. 따라서 하네스는 **프롬프트·성향 검증용이고, 수치 정확도나 런타임 배관 검증에는 쓸 수 없다.**
- **하네스 자체의 함정 2건(기록)**: `from __future__ import annotations` 를 쓰면 타입 힌트가 문자열이 되어 `tool_from_function` 의 스키마가 전부 string 으로 fallback 한다(`tags: list[str]` 가 array 로 선언되지 않는다). `Message.model()`/`Message.tool()` 은 `str` 을 받지 않고 `Contents` 를 요구한다(`Message.user()` 만 허용).
- **위치**: `scratch/` 는 gitignore 대상이라 하네스는 저장소에 포함되지 않는다. 모델 파일(3.7GB)이 같은 폴더에 있어야 하므로 의도된 배치이며, 실험 결론은 CHANGELOG 와 단위 테스트로 저장소에 남긴다 — 특히 `PromptAssemblerTest` 가 **예전 "DO NOT guess" 문구의 부활을 실패로 잡는다.**

### ADR-010. 시스템 지시는 하루 동안 고정한다 — 날짜는 날짜 단위로, 검색된 기억만 사용자 턴으로 (2026-08-07)
- **결정**: `systemInstruction` 에는 하루 동안 변하지 않는 것(역할, 언어, 응답 스타일, **날짜 블록**, 툴 사용 태도)만 담는다. 날짜 블록에서 **분 단위 시계를 뺀다.** 질의마다 달라지는 RAG 검색 기억만 `ChatPrompt.turnContext` 로 분리해 사용자 턴 본문 앞머리에 싣는다.
- **근거(측정)**: `GemmaModelRunner.getOrCreateConversation` 은 시스템 지시가 달라지면 Conversation 을 파괴하고 다시 만든다. 그런데 시각(분 단위)과 RAG 결과는 **정상적으로** 턴마다 달라지므로 재사용 판정이 거의 항상 거짓이었다. 재생성은 시스템 지시 + 툴 선언(~2천 토큰) + few-shot + 히스토리 전체의 재프리필이다. PC 하네스에서 같은 3턴을 재사용/재생성으로 나눠 재니 **4.3·0.5·0.9초 대 4.0·3.8·4.0초** — 2번째 턴부터 턴당 3초 이상이 프리필뿐이었다. 프리필은 기기에서 더 느리므로 실기기 체감 지연의 지배 요인이다.
- **날짜 블록을 사용자 턴으로 옮기려던 첫 설계는 실측으로 기각됐다(중요)**: `[System Data] 오늘=…, 내일=…` 목록이 사용자 발화 앞에 붙으면 **조회 질문이 툴 호출로 샜다.** "내 자전거 비밀번호 뭐였지?" → `add_memory("자전거 비밀번호는 8282")`(few-shot 예시의 숫자를 끌어옴), "내가 뭘 좋아한다고 했지?" → `get_schedule(date='today')`. 조회 3케이스 중 1/3 만 정상이었다. 변형 실험으로 원인을 분리했다 — 규칙 문구만 새것으로 바꾸고 블록을 시스템 지시에 두면 **3/3 정상**, 문구는 옛것 그대로 두고 블록만 사용자 턴으로 옮기면 2/3. 즉 **문구가 아니라 블록의 위치**가 변수였다. 날짜 목록이 날짜·캘린더 툴을 프라이밍하는 것으로 보인다.
- **채택안의 검증**: 날짜 블록을 시스템 지시에 두고 시계만 빼면, 전체 16케이스(메모리 3 + 일정 4 + 조회 2 + 검색 2 + 대조군 2 + 회상 3)에서 **16/16** 이다 — 분 단위 시각(기존 앱)이 15/16, 날짜 블록을 턴으로 옮긴 변형이 11/14 였다. 일정 등록 정확도는 시계를 빼도 전혀 떨어지지 않는다.
- **대가(기록)**: "지금 몇 시야?", "한 시간 뒤" 같은 시계 의존 발화를 다룰 수 없다. 알림·리마인더 기능이 없는 현재로서는 지불할 값이 있으며, 되돌리려면 `buildDateBlock()` 한 줄에 시각을 넣으면 된다 — 그 순간 프리필 비용이 함께 돌아온다.
- **보안상 이점(부수적이지만 독립적으로 정당하다)**: 검색된 기억을 시스템 지시에 넣으면 **저장된 메모 안의 문장이 시스템 권한으로 승격**된다. 사용자가 저장한 메모는 웹·문서·대화에서 온 텍스트일 수 있으므로 이것은 실재하는 주입 경로다. ADR-003 이 첨부 문서에 대해 내린 결정(SYSTEM → USER 구분 블록)과 같은 근거로 사용자 턴에 싣는다.
- **문구와 위치의 결합**: 날짜 규칙은 `"the [System Data] values above"` 로 **같은 시스템 지시 안**을 가리킨다. 블록을 옮기면 이 문구도 함께 옮겨야 한다 — 가리키는 곳이 틀리면 규칙이 무력해진다. `PromptAssemblerTest` 가 블록이 규칙보다 앞에 있음을 문자열 위치로 고정한다.
- **응답 스타일 설정**: `[Style: CONCISE]` 라벨 한 줄만 넣던 것을 실제 지시문으로 풀었다("Answer in one or two short sentences…"). 모델은 그 대문자 토큰이 무엇을 요구하는지 알 길이 없어 설정이 사실상 무효였다. 설정 화면에 없는 자유 문장은 그대로 전달한다(하위 호환).
- **대화 재설정 임계값**: 런타임에 박혀 있던 `8000` 을 사용자 설정(프리필 예산, `ChatPrompt.contextBudgetTokens`)에서 파생시킨다. 예전에는 설정을 최소로 내려도 살아 있는 대화의 KV 는 8000 토큰까지 자라 **설정이 메모리에 아무 영향을 주지 못했다.** 예산 최소값(1000)을 그대로 쓰면 오버헤드만으로 임계값을 넘어 매 턴 재생성되므로 `MIN_CONVERSATION_RESET_TOKENS = 4000` 하한을 둔다.
- **`EngineConfig.maxNumTokens` 는 계속 넘기지 않는다**: 그 값은 엔진 KV 캐시 크기를 정하고 초기화 시점에만 읽히므로 설정 슬라이더로 바꾸려면 엔진 재적재(7~11초)가 필요하다. 더 중요한 것은 우리가 임의로 정한 값이 지금 동작하는 런타임 기본값보다 **작을** 위험이다(gallery 의 기본값은 1024 다). 히스토리 윈도우와 재설정 임계값이 KV 성장의 실질적 지배 요인이므로 그 둘을 통제하는 편이 안전하다.

### ADR-011. 샘플링 파라미터를 사용자에게 노출하지 않고, thinking·speculative decoding 도 채용하지 않는다 (2026-08-07)
- **결정**: `topK`/`topP`/`temperature` 를 설정 화면에 (이름을 바꿔서라도) 노출하지 않는다. `enable_thinking` 은 계속 false 로 고정한다. `ExperimentalFlags.enableSpeculativeDecoding` 도 켜지 않는다.
- **greedy 는 장식이 아니라 하중을 받는 부품이다**: `SamplerConfig(topK = 1)` 은 0.8.4 에서 툴 호출을 성립시킨 조건이고 숫자 왜곡 완화에도 기여한다. gallery 자신도 agent chat 진입 시 `AgentChatSamplingParamsManager` 로 `topK = 1` 을 **강제**하며 그 태스크에서는 이 설정들을 사용자에게 보여주지 않는다. 노출은 8회의 실기기 왕복으로 막아 놓은 실패 모드를 사용자가 슬라이더 한 번으로 되살릴 수 있게 만드는 것이다. 응답의 성격을 바꾸는 손잡이는 **샘플러가 아니라 응답 스타일 지시문**으로 제공한다(ADR-010).
- **thinking·speculative decoding 은 구글 자신이 우리 태스크에서 배제한다**: gallery 의 모델 허용 목록(`model_allowlists/1_0_17.json`)에서 `Gemma-4-E4B-it`(우리 모델)의 `capabilityToTaskTypes` 는 `llm_thinking: [llm_chat, llm_ask_image, llm_ask_audio]`, `speculative_decoding: [llm_chat, llm_ask_image, llm_ask_audio, llm_prompt_lab]` 이다. **둘 다 `llm_agent_chat` 만 빠져 있다.** speculative decoding 은 단순 태스크인 `llm_prompt_lab` 에도 허용되므로, agent chat 만 제외되는 차이는 툴 선언 + constrained decoding 뿐이다 — 드래프터가 제안한 토큰과 FST 문법 마스크가 충돌한다는 해석과 일치한다. 우리 앱은 모든 턴이 agent chat 이다(툴이 항상 선언된다).
- **모델 파일에는 드래프터가 실제로 들어 있다**: `gemma-4-E4B-it.litertlm` 컨테이너 헤더에서 `tf_lite_mtp_drafter` 섹션을 확인했다(MTP = multi-token prediction). 즉 `Capabilities(modelPath).hasSpeculativeDecodingSupport()` 는 true 를 돌려줄 것이다 — **켤 수 있는데 켜지 않는다**는 점이 이 결정의 핵심이다. 되돌리기는 쉽다(엔진 초기화 직전 전역 플래그 1줄). 다만 켜려면 툴 호출 회귀 검증이 함께 필요하고, PC 하네스로는 확인할 수 없다(Python 0.15.0 에 대응 플래그가 없다).
- **0.14.0 API 표면 확인**: `ExperimentalFlags.enableSpeculativeDecoding` 은 `Boolean?` 이고 `Engine.initialize()` **전에** 읽힌다(gallery 는 set → initialize → reset 순서). `Capabilities` 클래스도 0.14.0 AAR 에 있다. 즉 채용은 API 문제가 아니라 품질 위험의 문제다.
- **대신 노출한 것**: 기존 설정의 이름을 실제 동작과 맞췄다. "CONTEXT WINDOW / Token Limit" 은 모델의 컨텍스트 윈도우가 아니라 **우리가 매 턴 실어 보내는 분량**이므로 "대화 기억 범위 / 한 번에 참고할 분량"으로 바꿨다. 이름이 동작과 다르면 사용자는 만져도 효과가 없다고 느낀다.

### ADR-012. 스트리밍 수신에 무한 버퍼를 끼운다 — 네이티브 콜백이 토큰을 조용히 버린다 (2026-08-07)
- **결정**: `Conversation.sendMessageAsync(…): Flow` 를 collect 하기 전에 `.buffer(Channel.UNLIMITED)` 를 끼운다. 그리고 토큰마다 걸던 발열 지연(`delay(15)`/`delay(50)`)을 **제거한다.**
- **근거(바이트코드)**: 0.14.0 의 `Conversation$sendMessageAsync$1$1.onMessage` 는 `ProducerScope.trySend(message)` 를 호출하고 **반환값을 검사하지 않는다.** `callbackFlow` 의 기본 채널 용량은 64(`Channel.BUFFERED`)이고, 가득 차면 `trySend` 는 예외도 로그도 남기지 않고 실패한다 — 그 메시지는 사라진다. 즉 **수신이 느리면 토큰이 조용히 유실되는 구조**이며, 우리는 그 위에 토큰마다 인위적 지연까지 걸고 있었다.
- **증상 대조**: 실기기 답변에서 "다국적 9인조"→"다국 9조", "2015년 10월 20일"→"205년 10 20일", "오디션"→"오선", 이전의 "1234"→"134". **모두 정확히 한 토큰씩 삭제이고 치환·삽입은 한 건도 없다.** greedy 디코딩에서 원문(툴 응답)이 문맥에 그대로 있는데 모델이 이런 외과적 삭제를 낼 이유가 없다. 반면 채널에서 메시지 하나를 잃으면 정확히 이 모양이 된다.
- **이전 판단 철회**: 0.8.7~0.9.0 에서 이 현상을 "안드로이드 GPU(Adreno) 고유 결함"으로 좁혔다(ADR-009). 근거는 "PC 의 CPU·GPU 양쪽에서 재현되지 않는다"였는데, **PC 하네스는 비스트리밍(`send_message`)이라 이 채널을 지나지 않는다.** 재현되지 않은 것은 하드웨어가 달라서가 아니라 **경로가 달라서**였다. ADR-009 의 "하드웨어 고유 결함은 재현되지 않는다"는 한계 서술은 유효하지만, 그 예시로 든 숫자 왜곡은 잘못된 귀속이었다.
- **발열 지연을 되살리지 않는 이유**: `trySend` 가 논블로킹이므로 **우리가 느려져도 네이티브 디코더는 조금도 느려지지 않는다.** 즉 그 지연은 발열을 줄이지 못한다. 무한 버퍼를 끼운 뒤에도 남는 효과는 화면이 모델보다 뒤처지는 것뿐이다(300토큰 × 50ms = 15초). 실제로 동작하는 완화책은 이미 셋 있다 — 임계 온도 사전 차단, 턴 사이 쿨다운(ADR-011 이후 온도 조건부), 감사 기록. **NFR3 의 "Token Delay" 항목과 충돌하므로 PRD 개정이 필요하다.**
- **미해결로 남긴 것**: 2턴 이후 툴 호출이 사라지는 문제는 이 결정으로 설명되지 **않을 수도** 있다. PC 하네스에서 대화 재사용·턴마다 재생성·무관한 RAG 잡음 세 구성을 모두 시험했으나 전부 3/3 으로 툴을 호출했다. 툴 호출도 같은 채널로 배달되므로 함께 유실됐을 가능성이 남아 있고, 그렇다면 이 수정으로 해소된다. 가르기 위해 감사의 `MODEL_RUN` 에 **선언된 툴 목록**을 함께 남기도록 했다 — 선언은 있는데 호출이 없으면 모델의 거부, 선언 자체가 없으면 토글·allowlist 문제다.

### ADR-013. 기억 조회를 매 턴 자동 RAG 주입에서 `SearchMemory` 툴로 전환한다 (2026-08-07)
- **결정**: `ContextBuilder` 가 매 턴 수행하던 벡터 검색 + SYSTEM 메시지 주입을 **제거**하고, 모델이 필요할 때 부르는 `SearchMemory` 툴(어휘 검색)로 대체한다. 툴 인자는 **모델이 뽑아 준 짧은 키워드**다.
- **근거(실측 — 임베더가 한국어에서 동작하지 않는다)**: 앱이 싣고 있는 `universal_sentence_encoder.tflite`(6MB, MediaPipe)를 PC 에서 같은 옵션으로 올려 코사인 유사도를 쟀다.

  | | 관련쌍 평균 | 무관쌍 평균 | **분리도** |
  |---|---|---|---|
  | 영어(대조군) | 0.842 | 0.587 | **+0.255** |
  | 한국어 | 0.973 | 0.972 | **+0.000** |

  같은 기준 문장에 대해 `"자전거 비밀번호는 1234" ↔ "내 자전거 비밀번호 뭐였지?"` 가 **0.946**, `"자전거 비밀번호는 1234" ↔ "트와이스 나연에 대해 알려줘"` 도 **0.946** 이다 — 소수점 셋째 자리까지 같아서 **어떤 임계값으로도 가를 수 없다.**
- **왜 그런가**: 서로 무관한 한국어 문장 8개의 쌍별 코사인이 0.930~1.000(평균 0.964)로 한 점에 뭉친다. 같은 내용을 영어로 쓰면 0.635~0.817(평균 0.734)로 정상 분포한다. 즉 **한국어 입력에 대해 벡터가 거의 상수**다. 모델 파일의 어휘를 뜯어보면 한글 토큰이 **1글자짜리 16,079개, 2글자 이상 8개**(그중 실제 단어는 `오전`·`오후`·`주의`·`참고` 넷)로, 학습된 한국어 서브워드 없이 유니코드 음절 블록만 fallback 으로 들어 있는 **영어 전용 모델**이다.
- **실제 검색 정확도**: 메모 8건·질의 7건으로 앱과 같은 방식(전량 코사인 → 상위 3)을 돌리면 **top-1 1/7, top-3 2/7** — 무작위 기대치(1/8)와 같다. 증상도 특징적이다: **어떤 질의를 넣어도 같은 3건**이 1~3위로 나온다.
- **그래서 기존 동작은 마이너스였다**: "관련 기억을 찾아 준다"가 아니라 **"무작위로 고른 메모 3건을 매 턴 질문 앞에 붙인다"** 였다. 프리필 토큰을 쓰고, 무관한 사실을 문맥에 넣어 환각의 재료를 주고, 정작 회상은 되지 않았다(PRD F6 미충족). 임계값 도입이 답이 아닌 이유가 여기 있다 — 점수 자체가 의미를 담고 있지 않다.
- **왜 툴인가**: 어휘 검색(SQLite LIKE)은 **질의어만 좋으면** 한국어에서 정확하다. 사용자 문장 전체("내 자전거 비밀번호 뭐였지?")로는 0건이지만 "자전거 비밀번호"로는 정확히 맞는다. 한국어 이해는 이미 온디바이스 LLM 이 하고 있으므로 **질의어 추출을 모델에게 맡기는 것**이 이 앱에서 가장 값싼 의미 검색이다. 덤으로 (a) 필요할 때만 도므로 프리필을 아끼고, (b) 조회한 사실이 감사 로그(`TOOL_CALL`)에 남는다.
- **방향 혼동 방지**: 저장("기억해줘")과 조회("뭐였지?")는 한국어 트리거가 비슷해 모델이 `add_memory` 를 잘못 부르는 것이 실측됐다. 규칙에 `This is a LOOKUP, never call add_memory for it` 을 못 박고 `PromptAssemblerTest` 로 고정했다.
- **찾지 못했을 때 success 로 돌려주는 이유**: error 로 돌리면 모델이 도구 실패로 받아들여 자기 파라미터 기억으로 지어내는 쪽으로 기운다. "없습니다 / 추측하지 마세요"를 success 로 명시해 돌려준다.
- **벡터 경로를 지우지 않고 남긴 것**: `KnowledgeRepository.searchByVector`, `KnowledgeNote.embedding`, `TextEmbedder`, `.tflite` 자산은 그대로 둔다. **막힌 곳이 외부 자산 하나**이므로 한국어를 다루는 임베더로 갈아 끼우면 그대로 되살아나고, BLOB 인코딩·마이그레이션 테스트도 그 계약을 계속 지켜 준다. 인터페이스 KDoc 에 "다시 배선하기 전에 반드시 임베더의 한국어 분별력을 먼저 재라"를 남겼다. 대신 `SearchKnowledgeUseCase`(벡터 우선 + 텍스트 폴백 글루)는 호출자가 사라졌으므로 삭제했다.
- **부수 수정**: `SaveKnowledgeUseCase` 가 임베딩 실패 시 **저장 자체를 실패시키던** 것을 best-effort 로 바꿨다. 지금 임베딩은 어떤 조회 경로에서도 읽히지 않는데, 그 값을 만들지 못했다는 이유로 사용자의 "기억해줘"를 버리는 것은 명백히 잘못이다.

### ADR-014. 부수 계산용 일회성 대화 경로 + 음성은 전사 후 텍스트로 (2026-08-09)
- **결정**: `ChatPrompt.oneShot` 을 신설해, 사용자 대화가 아닌 **부수 계산**(음성 전사, 일정 요약)은 임시 Conversation 을 만들어 쓰고 즉시 닫는다. 음성은 이 경로로 먼저 전사한 뒤 **텍스트 턴**으로 대화를 진행한다.
- **문제(선행)**: 런타임은 채팅 Conversation 하나를 캐시해 재사용한다(ADR-010). 그런데 부수 계산은 시스템 지시와 `sessionId` 가 달라 그냥 보내면 재사용 판정이 깨지고 **캐시된 채팅 대화가 파괴된다.** 실제로 `GetTodayScheduleUseCase` 의 요약 호출 때문에 **캘린더 화면을 열 때마다 채팅 대화가 날아가** 다음 질문이 전체 프리필(툴 선언 ~2천 토큰 포함)을 다시 내고 있었다 — 이번에 함께 해소했다.
- **일회성 대화가 싼 이유**: 툴·few-shot·히스토리를 싣지 않는다. 툴 선언만 실측 ~2천 토큰이므로, 그것이 빠지면 프리필이 짧은 시스템 지시 한 줄뿐이다. 전사·요약은 도구를 쓸 일도 없다.
- **음성 전사 결정(PRD F2 개정)**: 예전에는 사용자 메시지를 `"(음성 메시지)"` 로 저장하고 오디오를 채팅 대화에 실었다. 그러면 (a) 대화를 다시 열었을 때 **자기가 무슨 말을 했는지 기록에 없고**, (b) 오디오가 KV 에 남아 컨텍스트를 더 먹으며, (c) 검색·히스토리에서 그 턴이 의미를 갖지 못한다. 전사문을 사용자 메시지로 삼으면 셋 다 해소되고, PRD F2 의 원래 의도("음성을 텍스트로 변환 후 채팅과 동일한 흐름")에 오히려 가까워진다.
- **확인 단계는 두지 않는다(사용자 결정)**: 초기 PRD 는 "transcript 확인 후 사용자가 전송"을 요구했으나 라이브 대화의 흐름을 끊는다. 사용자는 **채팅에 뜬 자기 문장으로** 인식 결과를 확인한다.
- **대가**: 추론이 2회가 된다(전사 + 답변). 전사 출력은 짧아 비용이 작고, 대신 채팅 대화가 파괴되지 않아 **두 번째 질문부터의 프리필 절약이 유지된다** — 순증이다. 다만 모델이 원본 오디오를 직접 듣지 않으므로 어조·배경음은 반영되지 않는다.
- **무음 처리**: 전사가 비면 **사용자 메시지를 저장하지 않고** `AppError.SttError` 로 올린다. 빈 말풍선을 남기지 않으며, 화면은 이미 있는 문구("음성을 인식하지 못했어요")를 띄운다 (PRD EC3).

### ADR-015. 채팅 상단에 기기 상태를 상시 표시한다 — GPU 사용률은 표시할 수 없다 (2026-08-09)
- **결정**: 채팅 상단에 `🌡 온도 · RAM 사용/전체(앱 몫) · tok/s` 를 상시 표시한다. 기존 발열 경고 배너를 이 줄에 **통합**한다.
- **GPU 사용률 불가(조사 결과)**: 안드로이드에는 GPU 사용률 공개 API 가 없다. 벤더 sysfs(Adreno `/sys/class/kgsl/kgsl-3d0/gpubusy`, `gpu_busy_percentage`)가 존재하지만 SELinux 정책상 앱 도메인에서 읽을 수 없다. 루팅이나 시스템 권한 없이는 방법이 없다.
- **대체 지표로 tok/s 를 쓰는 이유**: "지금 GPU 가 얼마나 잘 돌고 있나"에 실제로 답하는 값이다. 스트리밍 경로는 네이티브 메시지 1건 = 토큰 1개이므로 **정확히 셀 수 있고**, 발열로 스로틀링되면 즉시 떨어진다. 비스트리밍 경로(전사·요약)는 셀 방법이 없으므로 0 을 넘겨 직전 값을 유지한다 — 부수 계산 한 번에 화면 숫자가 0 으로 떨어지면 오해를 준다.
- **RAM 은 PSS 로 본다**: `Runtime.getRuntime()` 의 자바 힙 수치는 쓰지 않는다. 3.7GB 모델은 네이티브/mmap 이라 **자바 힙에는 전혀 보이지 않기 때문이다.** `Debug.getMemoryInfo().totalPss` 가 앱의 실제 몫이고, 시스템 전체는 `ActivityManager.MemoryInfo` 로 따로 읽는다(둘 다 무권한).
- **폴링은 화면이 결정한다**: `Debug.getMemoryInfo` 는 프로세스 맵을 훑어 수십 ms 가 걸린다. 수집기가 스스로 타이머를 돌면 아무도 안 보는 동안에도 배터리를 쓰므로, 주기 결정은 화면 생명주기를 아는 ViewModel 에 맡긴다(추론 중 2초 / 유휴 5초, `viewModelScope` 라 화면이 사라지면 멈춘다).
- **경고 배너와 통합한 이유**: 상태 줄과 배너가 따로 있으면 발열 상황에서 화면 위쪽이 두 겹으로 밀린다. 평소에는 수치만 옅게 보이고, 경고·임계 온도에서 같은 줄이 `warning` 색으로 승격되며 안내 문구가 붙는다.

### ADR-016. 실기기 결함 4건의 원인 규명 — 오디오 백엔드 누락, 히스토리 모방, KV 용량 불일치 (2026-08-12)

- **Status**: Accepted (원인 4건 중 3건 확정·수정, 1건 미확정)
- **Context**: 2026-08-12 Galaxy S25 Ultra(Android 16, 0.11.1) 검증에서 통과 6건과 실패 5건이 나왔다. `adb` 로 앱 DB·감사 로그·logcat 을 직접 읽어 증상을 값으로 확정했고, 그 DB 를 `scratch/lab/fixtures/kosmos_db` 로 보관해 **실기기 없이 재현하는 실험실**을 만들었다(exp14~17). 아래 결론은 모두 그 실험실의 실측이다.

#### 확정 1 — 음성 입력은 처음부터 불가능했다 (수정)

`EngineConfig` 가 `backend`·`visionBackend` 만 설정하고 **`audioBackend` 를 한 번도 설정하지 않았다.** 기본값은 `null` 이고(테스트로 못박음), 그러면 엔진이 `Content.AudioFile` 을 받지 못한다. exp16 이 네이티브 오류 원문까지 재현했다:

```
INVALID_ARGUMENT: Audio executor should not be null, please TryLoadingAudioExecutor() first.
```

0.11.0 회귀가 아니라 이전부터 있던 결함이다. **그리고 `TranscribeAudioUseCaseTest` 7건이 이것을 잡지 못한 이유가 이 ADR 의 교훈이다** — 그 테스트는 `ModelRunner` 를 목으로 대체했고, 잘못 설정된 것이 정확히 그 목이 가린 컴포넌트였다. 통과하는 테스트가 **동작할 수 없는 경로**를 검증하고 있었다. 그래서 설정 조립을 `buildEngineConfig` 순수 함수로 꺼내 목을 거치지 않고 단언한다.

오디오는 `Backend.CPU()` 로 고정한다 — gallery `LlmChatModelHelper.kt:129` 가 `must be CPU for Gemma 3n` 주석과 함께 같은 선택을 한다. 비전은 GPU 로 둔다.

#### 확정 2 — 무음이면 지시문이 전사문으로 저장된다 (수정)

exp16 에서 무음 2초를 넣자 모델이 사용자 턴의 지시 문장(`"이 오디오를 들리는 그대로 받아써 주세요."`)을 **그대로 되읊었다.** 공백이 아니므로 `TranscribeAudioUseCase` 의 빈 검사를 통과해, 앱은 그 문장을 사용자 메시지로 저장하고 그것에 답한다 — PRD EC3 이 막으려던 상황이다.

되읊음을 문자열로 걸러내지 않고 **되읊을 대상을 없앤다.** 오디오만 보내면 전사는 정상이고 무음에서는 빈 결과가 온다(exp16 실측). 무엇을 할지는 시스템 지시가 이미 말하므로 사용자 턴에 문장이 필요하지 않다. 런타임은 `currentInput` 이 비면 텍스트 파트를 붙이지 않는다.

#### 확정 3 — 툴을 안 부르는 것은 히스토리 모방이다 (미수정, 처방 결정 필요)

실기기에서 위키 검색이 필요한 발화 4연속 모두 `TOOL_CALL` 0건이었다. `Tools declared:` 에는 툴이 매 턴 있었고 명시 지시("위키에서 에스파 검색해줘")도 호출되지 않았다. 그러면서 *"위키백과에서 검색하여 요약 정보를 가져왔습니다"* 로 시작하는 **전부 거짓인 답변**을 냈다(2020→2023, SM→JYP).

exp14 가 원인을 갈랐다. 히스토리의 어시스턴트 답변만 `"네, 알겠습니다."` 로 대체하고 턴 수·사용자 발화·대략적 길이를 그대로 두면:

| 히스토리 형태 | depth=8 | depth=20 | depth=40 |
|---|---|---|---|
| verbatim (실제 답변) | 미호출 | 미호출 | 미호출 |
| terse (답변만 대체) | 호출 OK | 호출 OK | 호출 OK |

길이 가설과 대화 재사용 가설은 이것으로 배제된다. KV 용량도 아니다(exp17: 8192 로 올려도 미호출).

**원인은 프롬프트 문구가 아니라 우리가 재생하는 히스토리가 손실된 버전이라는 것이다.** `ChatMessage` 에는 툴 호출 필드가 없다. 그래서 DB 에서 복원한 대화는 "어시스턴트가 언제나 툴 없이 답했다"는 이야기가 되고, 모델은 그 패턴을 충실히 따른다. few-shot 시범(`add_memory`)이 툴 호출을 **포함**해 넣었을 때 depth 0 에서 잘 동작하는 것과 정확히 같은 원리다.

~~처방 후보: ① 툴 호출을 DB 에 저장해 히스토리에 함께 재생(Room v5→v6), ② 히스토리에 싣는 어시스턴트 메시지를 짧게 요약, ③ 툴을 쓴 턴만 재구성.~~

**정정 (ADR-017, 2026-08-13): 위 진단과 처방이 모두 틀렸다.** ① 을 실제로 해 봤더니(exp18) 일정 툴은 0/3 이었다 — 구조 누락이 원인이 아니다. 모방도 아니었다: 같은 답변을 60자로 자르기만 해도 회복된다(exp19). 진짜 원인은 **지침과 사용자 발화 사이의 거리**이고, 처방은 지침을 사용자 턴 앞에 한 줄 재게시하는 것이었다(exp20·21, 6/6). 위 문장이 "프롬프트 문구 수정은 처방이 아니다" 라고 단정한 것도 틀렸다 — 문구가 아니라 **배치**가 처방이었다.

#### 확정 4 — 엔진 KV 용량이 앱 예산보다 작다 (미수정, 처방 결정 필요)

`Constants.kt:18` 은 *"gemma-4 컨텍스트는 32K 이고 `EngineConfig.maxNumTokens` 를 설정하지 않으므로 상한이 아니다"* 라고 적어 두었다. **틀린 전제다.** 모델의 컨텍스트 창과 엔진이 할당하는 KV 크기는 별개이고, 설정하지 않으면 상한이 없어지는 것이 아니라 런타임 기본값 **4096** 이 된다(exp17 실측: `5857 >= 4096` 거부).

그런데 앱은 `MAX_CONTEXT_TOKENS = 6000`(설정 슬라이더 최대 **8000**) + `PREFILL_OVERHEAD_TOKENS = 2600` 으로 잡는다 — **엔진 용량을 넘는 프롬프트를 의도적으로 만든다.** 파이썬 0.15.0 은 오류를 내지만 AAR 0.14.0 은 실기기에서 같은 조건(히스토리 79개)에 오류 없이 답을 냈다. 즉 초과분을 조용히 처리한다.

**KV 할당량은 모델의 컨텍스트 창과 다른 것이다.** 모델 카드는 E4B 의 컨텍스트를 128,000 토큰으로 적지만, 그것은 모델이 *주의를 둘 수 있는* 상한이고 `maxNumTokens` 는 런타임이 *실제로 메모리를 잡는* 크기다. 실측(`measure_kv_memory.py`, cpu):

| `max_num_tokens` | 프로세스 메모리 |
|---|---|
| 기본(4096) | 5,003 MB |
| 8192 | 9,188 MB |

**+4096 토큰에 +4,185MB — 토큰당 약 1MB다.** 128K 를 KV 로 잡으려면 100GB 이상이 필요하니 온디바이스에서 컨텍스트 창 전체를 할당한다는 선택지는 애초에 없다. gallery 가 이 값을 사용자 설정으로 노출하고 기본을 1024 로 두는 이유이기도 하다.

이 수치가 처방을 정한다. 실기기에서 관측한 앱 PSS 5.5GB 는 위 기본값 5.0GB 와 같은 자릿수이므로 **안드로이드 기본값도 4096 으로 보인다**(AAR 에서 직접 확인한 것은 아니다). 8192 로 올리면 9GB 대가 되어 12GB 기기에서도 OS 가 앱을 죽일 위험이 크다 — **용량을 올리는 것은 현실적 선택이 아니고, 예산을 4096 안에 맞추는 방향이어야 한다.** 그리고 두 값을 한 곳에서 파생시켜 다시 어긋나지 않게 해야 한다.

#### 미확정 — 글자 유실

DB 실측: `2026-08-17T10:00` → `2026-08-17T01:000`, `2026-08-12T20:00` → `208:000`, `11월` → `111월`.

**배제됨**: 우리 스트리밍 채널. 툴 인자는 `collectToolCalls` 가 `message.toolCalls`(네이티브가 파싱한 구조체)에서 읽고 우리가 조립한 문자열을 거치지 않는데도 깨져 있다. 0.9.1 의 무한 버퍼가 듣지 않은 이유다.

**배제됨**: 제약 디코딩 FST. exp15 에서 `constrained=True`, greedy, depth 0 으로 `2026-08-17T10:00:00` 이 두 번 모두 정확히 나왔다.

**남은 1순위**: AAR 0.14.0 과 파이썬 0.15.0 의 런타임 차이. 확정 4(용량 초과)와의 상호작용도 후보다 — 0.14.0 이 초과분을 조용히 버린다면 그것이 곧 글자 유실일 수 있다.

> **정정 (2026-08-13)**: *"버전이 달라 이 하네스로는 가를 수 없다"* 고 적었으나 사실이 아니다. Google Maven 에 **`litertlm-android` 0.15.0 과 0.16.0 이 존재한다**(0.16.1·0.17.0 은 없음). 즉 하네스가 깨끗한 그 버전을 앱에 그대로 올릴 수 있고, 버전 가설은 **검증 가능한** 가설이다. 우리가 0.14.0 에 머문 것은 선택이 아니라 확인하지 않은 결과였다.

ADR-009 의 "Adreno GPU 고유" 귀속은 0.11.0 에서 이미 철회했고, 이제 더 구체적인 후보가 생겼다.

#### 부수 결론 — 프롬프트 사본은 표류한다

실험실의 시스템 지시가 **손으로 베낀 사본**이라 0.8.6 시절에 멈춰 있었다(날짜 블록 없음, `search_memory` 트리거 없음, 툴 4종). 그 상태로 돌린 실험은 앱과 다른 프롬프트를 측정하며, 틀렸다는 사실도 조용히 남는다. `PromptFixtureExportTest` 가 실제 `PromptAssembler`·`TranscribeAudioUseCase` 출력을 `scratch/lab/fixtures/` 로 내보내고, 실험은 그것을 읽는다 — 앱이 바뀌면 픽스처도 함께 바뀌므로 사본이 낡을 수 없다.

### ADR-017. 툴 호출을 되살린 것은 지침의 위치였다 — 그리고 예산을 KV 용량에서 파생한다 (2026-08-13)

- **Status**: Accepted
- **Context**: ADR-016 이 남긴 두 항목(#1 툴 호출 실패, KV 용량 불일치)을 해소한다. 판정은 전부 `scratch/lab/` 실험실에서 했고 실기기를 쓰지 않았다.

#### 원인은 히스토리의 **내용**이 아니라 지침과 발화 사이의 **거리**였다

ADR-016 은 원인을 "히스토리 모방"으로 적었다. **그 결론은 틀렸고, 처방도 틀렸다.** 실험 네 개가 순서대로 뒤집었다.

| 실험 | 조작 | 결과 |
|---|---|---|
| exp14 | 어시스턴트 답변을 `"네, 알겠습니다."` 로 대체(terse) | verbatim 0/3 → terse 3/3 |
| exp18 | 문서 형식대로 `tool_calls`+`tool_responses` 구조 복원 | 일정 툴 **0/3** — 구조 누락이 아니다 |
| exp19 | 같은 답변을 60자로 절단(내용 유지) | **3/3** — 모방이 아니라 산문의 양이다 |
| exp20 | 툴 지침을 사용자 턴 앞에 재게시 | 원본 히스토리로 **2/2**, 깊이 40 에서도 |
| exp21 | 앱이 내보낸 픽스처 그대로 재판정 | 툴 3종 × 깊이 2 = **6/6** |

exp18 이 특히 중요하다. [function-calling-gemma4](https://ai.google.dev/gemma/docs/capabilities/text/function-calling-gemma4?hl=ko) 가 멀티턴 히스토리에 `tool_calls`·`tool_responses` 를 넣으라고 **명시**하므로 그것이 곧 처방이라고 판단했는데, 구조를 복원해도 일정 툴은 한 번도 호출되지 않았다. **규격 준수와 이 결함의 해법은 다른 문제였다.**

exp19 의 절단 임계값은 **60자 통과 / 120자 실패** 로 매우 낮았다. 절단으로 해결하려면 대화 기억을 사실상 포기해야 하므로 제품 해법이 못 된다. 남은 후보가 위치였고, 그것이 답이었다.

#### 처방: 사용자 턴 앞에 지침 한 줄

`PromptAssembler.withTurnToolReminder` 가 툴이 선언된 턴에만 166자 한 줄을 `currentInput` 앞에 붙인다. 전체 지침 재게시(1,519자)와 결과가 같으므로 **9배 싼 쪽**을 쓴다.

- **시스템 지시의 지침은 옮기지 않고 그대로 둔다.** 검증한 조건이 "시스템 지시에 지침이 있는 상태에서 한 줄을 더 붙인 것" 이므로, 옮기면 검증하지 않은 배치가 된다.
- 새 필드도 DB 변경도 없다. `currentInput` 은 모델에게만 가고 감사 로그는 `request.message` 를 쓴다.
- 히스토리 중복 제거 비교는 **원문 발화**로 한다 — 접두사가 붙은 문자열로 비교하면 절대 일치하지 않아 마지막 사용자 메시지가 두 번 실린다.

**ADR-010 과 같은 종류의 발견이다.** 그때도 `[System Data]` 블록의 문구가 아니라 **위치**가 툴 선택을 갈랐다(조회 3/3 vs 1/3). 이 모델에게 프롬프트의 거리는 내용만큼 중요하다 — 지침은 시스템 턴에 한 번뿐이고 히스토리가 쌓이면 사용자 발화와 수천 토큰 떨어진다.

#### 보류: 히스토리의 툴 호출 구조 보존

문서가 요구하는 형식이므로 **규격 준수 항목으로 남긴다.** 다만 #1 을 풀지 못하고 Room v5→v6 마이그레이션이 필요하므로 우선순위를 내렸다. 되살릴 때의 근거와 실측은 exp18 에 남아 있다.

#### 예산을 KV 용량에서 파생한다

`ENGINE_MAX_TOKENS = 4096` 을 `EngineConfig.maxNumTokens` 로 **명시 전달**하고, 나머지를 거기서 파생시켜 다시 어긋날 수 없게 했다.

```
PREFILL_CEILING_TOKENS  = ENGINE_MAX_TOKENS - GENERATION_HEADROOM_TOKENS(768) = 3328
MAX_CONTEXT_TOKENS      = 3000   (6000 에서 — 천장을 넘어 무효였다)
PREFILL_OVERHEAD_TOKENS = 1400   (2600 에서 — 실측 1,329)
MIN_CONVERSATION_RESET  = 2600   (4000 에서 — 천장을 넘어 초과를 보장했다)
설정 슬라이더            = 1000..3000  (1000..8000 에서)
```

- **오버헤드 실측이 예상의 절반이었다.** `Conversation.token_count`(`measure_overhead.py`): 시스템 지시 573 + 툴 5종 선언 **652** + few-shot 104 = **1,329**. 예전 주석의 "툴 선언만 ~2천 토큰" 은 틀렸고, 2600 예약이 히스토리 예산을 1,200 토큰 넘게 조용히 깎고 있었다. 그래서 예산을 3000 으로 내려도 히스토리는 오히려 늘어난다.
- **리셋 하한이 초과를 보장하고 있었다.** 4000 은 천장(3328)보다 크므로, 예산을 낮춰도 임계값이 밀려 올라가 대화가 용량을 넘도록 자라는 것을 허용했다 — 재생성을 막으려는 하한이 정반대로 작동했다.
- **저장된 설정값을 읽을 때 자른다.** 슬라이더가 8000 이던 시절 값이 기존 설치에 남아 있고, 그대로 되살리면 이 결함이 **업그레이드한 사용자에게만** 남는다. 저장값은 덮어쓰지 않는다 — 사용자가 만지지도 않은 설정을 바꾸는 것이고, 나중에 천장이 올라가면 자연히 복구된다.
- `TokenBudgetInvariantTest` 가 관계를 고정한다. 값들이 서로 다른 파일에 흩어져 각자 그럴듯한 이유로 바뀌므로 코드로는 강제할 수 없다.
- **초과를 조용히 넘기지 않는다.** AAR 0.14.0 은 용량을 넘겨도 오류를 내지 않으므로(파이썬 0.15.0 은 거부한다) 이 경계는 품질 저하로만 드러난다. 디버그 빌드에서 경계에 닿는 것을 로그로 남긴다.

#### 실험실이 앱과 어긋나지 않게 하는 장치

`PromptFixtureExportTest` 가 시스템 지시·**턴 지침**·툴 목록·전사 지시를 `scratch/lab/fixtures/` 로 내보내고, 실험은 그것을 읽는다. 턴 지침을 내보내지 않으면 실험이 앱과 다른 프롬프트를 측정하게 되는데, 그것이 이 체계를 만든 원래 이유다(실험실 사본이 0.8.6 시절에 멈춰 `search_memory` 트리거가 없었다).

#### 글자 유실 재판정 — 앱 설정에서는 재현되지 않는다 (exp22)

두 수정을 넣은 뒤 앱의 실제 설정(`max_num_tokens=4096`, 턴 지침 재게시, 원본 실기기 히스토리)으로 자릿수를 다시 쟀다. 이전에는 깊은 히스토리에서 **툴 호출 자체가 없어 인자를 볼 수 없었다** — 자릿수를 재려면 먼저 호출이 돼야 하므로, 지침 위치를 고친 지금이 처음으로 측정 가능한 시점이다.

| 조건 | 결과 |
|---|---|
| constrained on / off × depth 0·8·20 × 발화 3종 × 반복 2회 | **36/36 온전, 깨짐 0** |
| `다음주 월요일 10시` → `2026-08-17T10:00:00` | 온전 |
| `오늘 저녁 8시` → `2026-08-13T20:00:00` | 온전 |
| `비밀번호 1234` → 인자에 `1234` | 온전 (기기에서는 `234` 로 저장됐다) |

반복 2회가 모두 동일해 greedy 결정성도 확인됐다.

~~**남은 후보는 AAR 버전 하나다.**~~ 그리고 그것은 검증 가능하다 — Google Maven 에 `litertlm-android` **0.15.0·0.16.0** 이 있고(0.16.1 이상은 없음), 0.15.0 은 이 하네스가 쓰는 바로 그 버전이다. ADR-016 이 *"버전이 달라 가를 수 없다"* 고 적은 것은 확인하지 않은 추정이었다.

> **정정 (ADR-020, 2026-08-13)**: "하나다" 는 틀렸다. 0.16.0 상향 후 실기기에서 여전히 깨졌다(`202026-081717T010000`). 후보를 하나로 단정할 때, 실험실이 **구조적으로 검증할 수 없는 영역** — KV 용량 초과의 조용한 진행(파이썬은 거부하므로 실험실 통과는 전부 용량 이내) — 이 목록에서 빠져 있었다.

### ADR-018. 근거를 Gemma 4 공식 문서와 우리 실측으로 재기준화한다 (2026-08-13)

- **Status**: Accepted
- **Context**: 사용자 결정 — *"gemma4 공식 문서들을 기준으로 하는게 맞고, gallery는 gemma3n·diffusiongemma 등 이전 혹은 다른 변형 모델을 위한 어플이라서 참고하는 정도에 그쳐야 할 것 같다."* 공식 문서 5종(model card, prompt formatting, capabilities: vision/image/video·audio·thinking·function-calling)을 코드와 대조하며 이 결정이 필요해졌다.

#### 근거 등급 (`.agents/04_MODEL_EVIDENCE.md`)

1. **Gemma 4 공식 문서** 2. **우리 실측**(실험 번호·관측 날짜 명기) 3. **litertlm API 계약**.
**gallery 는 가설의 출처이고 근거가 아니다.** 주석에 근거의 등급을 함께 적는다.

이 결정 없이 두 번 틀렸다:

- `maxNumTokens` 를 넘기지 않은 근거가 *"gallery 기본값이 1024 라 우리가 더 작게 잡을 위험"* 이었다. gallery 의 사정이었고, 우리는 그 때문에 아무것도 설정하지 않아 **예산이 엔진 용량을 넘는 상태**를 만들었다(ADR-017).
- `audioBackend`/`visionBackend` 의 근거가 gallery 의 `must be CPU/GPU for **Gemma 3n**` 이었다. 우리 모델은 Gemma 4 다.

#### 실측으로 승격 — 생각 모드는 툴 호출을 깎는다 (exp24)

| | 툴 호출 |
|---|---|
| `enable_thinking=false` | **4/4** |
| `enable_thinking=true` | **2/4** (깎이는 쪽은 일정 등록, 위키는 살아남음) |

gallery 의 *"생각 모드에서는 함수호출 동작이 달라진다"* 가 Gemma 4 E4B 에서도 맞다. 다만 이제 근거는 gallery 가 아니라 이 표다.

**그리고 위험한 사실이 하나 딸려 나왔다: 응답 텍스트로는 생각 모드가 켜졌는지 알 수 없다.** `enable_thinking=true` 조건에서도 `<|think|>` 마커가 출력에 한 번도 나오지 않았다. 앱은 AAR 0.14.0 에 `ThinkingConfig` 가 없어 `extraContext` 맵의 키 이름 하나로 끄고 있는데, 그것이 조용히 실패하면 **툴 호출만 나빠지고 우리는 눈치채지 못한다.** 0.16.0 의 타입 있는 `ThinkingConfig` 로 올릴 이유가 여기 있다.

#### 실측으로 승격 — 백엔드는 오디오만 고정이 필요하다 (exp23)

| | 결과 |
|---|---|
| `vision_backend` = CPU | 이미지 인식 정상 (`빨간색 사각형과 파란색 원이 있습니다.`) |
| `vision_backend` = GPU | 동일 |
| `vision_backend` 미설정 | 추론 실패 |
| `audio_backend` = CPU | 전사 정상 |
| `audio_backend` = GPU | **엔진 생성 자체 실패** |
| `audio_backend` 미설정 | 추론 실패 |

- **오디오는 CPU 여야 한다** — GPU 로는 엔진이 올라가지 않는다. 0.12.0 의 선택은 옳았고 근거만 바뀌었다.
- **비전은 CPU·GPU 양쪽에서 된다** — 내가 *"CPU 폴백 기기에서 이미지가 조용히 깨질 수 있다"* 고 한 것은 **추측이었고 틀렸다.** `visionBackend = backend` 를 그대로 둔다.
- **둘 다 미설정이면 실패한다** — `audioBackend` 누락이 음성을 죽인 것과 같은 형태가 비전에도 있다는 뜻이다.

#### 공식 문서와 대조해 확인된 것

- **프롬프트 형식은 맞다.** 실기기 렌더 프리페이스가 `<|turn>system` → 우리 지시 → `<|tool>declaration:...`, 문자열 구분자 `<|"|>` 로 문서 형식을 그대로 따른다. 런타임이 만들어 주는 부분이라 우리가 틀릴 여지가 없었다 — 프롬프트에 형식을 글로 적던 방식을 버린 ADR-008 의 값이 여기서 드러난다.
- **이미지는 텍스트보다 앞에 온다** — 문서 요구와 코드가 일치한다.
- **비디오는 반영할 것이 없다.** 문서가 프레임 샘플링·fps·토큰 비용·최대 길이를 아무것도 명시하지 않는다.

#### 공식 문서와 어긋난 것 (백로그)

| # | 항목 | 문서 | 우리 |
|---|---|---|---|
| 1 | **녹음 길이** | 최대 **30초**, 25토큰/초 | 제한 없음 — 1분 녹음의 동작이 정의되지 않았다 |
| 2 | **시각 토큰 예산** | 70/140/280/560/1120 중 선택이 Gemma 4 비전의 요점 | `ExperimentalFlags.visualTokenBudget`(0.14.0 에 존재)을 쓰지 않는다 |
| 3 | **이미지 선축소** | 해상도 레버는 토큰 예산 | 주석은 *"리사이징 금지"* 라 적었지만 코드는 1024px 로 반씩 줄인다 — 픽셀을 먼저 버리면 예산을 높여도 되돌아오지 않는다 |
| 4 | **`maxNumImages`** | 한 프롬프트에 여러 장 가능 | 설정하지 않는다 — `maxNumTokens`·`audioBackend` 와 같은 "기본값 모르는 필드" |
| 5 | thought 삭제 예외 | *"툴 호출 시퀀스 중에는 삭제하지 말 것"* | `ToolParser` 가 툴 루프 안에서도 걷어낸다(지금은 생각 모드가 꺼져 있어 대상이 없다) |

부수: 진단 로그가 프리페이스를 2000자에서 잘라 **툴 선언 블록도 다 못 보여준다** — "툴이 선언됐나"를 답하려고 넣은 로그인데 정작 그 답이 잘린다.

#### 판단 보류

오디오 문서의 *"32비트 부동소수점, [-1,1] 정규화"* 는 transformers 파이프라인의 텐서 표현으로 보인다 — 같은 문서가 WAV 를 입력 포맷으로 허용하고, 우리 16비트 PCM WAV 로 전사가 정상 동작한다(exp16·exp23). 결함으로 단정하지 않는다.

#### 버전 추정을 닫지 말 것

ADR-016 이 *"버전이 달라 이 하네스로는 가를 수 없다"* 고 적었으나 Google Maven 에 **0.15.0·0.16.0 AAR 이 모두 존재**한다. 파이썬 하네스를 0.16.0 으로 올려 재판정한 결과 자릿수는 **36/36 온전**(0.15.0 과 동일)이므로, ~~글자 유실의 남은 후보는 **AAR 0.14.0** 하나로 좁혀졌다~~. API 표면 비교로 업그레이드 위험도 낮음을 확인했다 — 0.14.0 → 0.16.0 은 클래스 8개 추가·**제거 0개**이고 우리가 쓰는 심볼이 전부 남아 있다.

> **정정 (ADR-020, 2026-08-13)**: 0.16.0 실기기에서 여전히 깨졌으므로 AAR 버전 가설은 기각됐다. "36/36 온전" 을 다른 가설들의 반증으로 쓴 것이 좁힘의 오류였다 — 파이썬은 KV 용량 초과를 거부하므로 그 36/36 은 전부 **용량 이내**에서 실행된 것이고, 초과 영역(실기기에서만 조용히 진행됨)은 애초에 검증 범위 밖이었다.

### ADR-019. litertlm 0.16.0 상향과 문서 어긋남 정리 (2026-08-13)

- **Status**: Accepted
- **Context**: ADR-018 이 남긴 두 갈래 — 글자 유실의 마지막 후보(AAR 버전)와 공식 문서 대조에서 나온 어긋남 5건 — 를 처리한다.

#### 0.14.0 → 0.16.0: 소스 수정 0

카탈로그 한 줄로 끝났다. **컴파일 오류 없음, 테스트 236건 전부 통과, 경고 0.** 사전에 classes.jar 을 비교해 클래스 8개 추가·제거 0 임을 확인한 대로였다.

APK 는 151.66MB → 152.67MB(**+1.0MB**)로 `.so` 증가분(+1.76MB 비압축)과 맞는다.

> **정정**: 상향 직후 *"APK 가 127MB → 153MB 로 26MB 늘었다"* 고 적었는데 **잘못된 귀속이었다.** 127MB 는 어제 0.11.1 빌드였고 그 사이 0.12.0·0.13.0 의 코드가 들어갔다. 0.14.0 으로 되돌려 다시 빌드한 A/B 로 확인했다.

부수 관측: `lib/` 이 APK 의 95MB 를 차지하고 그중 **MediaPipe 가 4개 ABI 로 약 46MB** 다. 우리가 쓰는 것은 영어 전용으로 판명된 임베더 하나뿐이라(ADR-013), assets 의 6.1MB 모델까지 합쳐 **죽은 경로가 APK 52MB** 를 쓰고 있다. 걷어내는 판단은 별건이다.

#### 생각 모드를 타입 API 로

`extraContext = mapOf("enable_thinking" to false)` → `ThinkingConfig(enableThinking = false)`.

**맵의 키 이름 하나에 의존하던 상태를 없앴다.** exp24 에서 생각 모드가 툴 호출을 4/4 → 2/4 로 깎는 것을 확인했고, 동시에 `enable_thinking=true` 조건에서도 `<|think|>` 마커가 출력에 나오지 않아 **응답을 봐서는 켜졌는지 알 수 없다**는 것도 확인했다. 즉 이 설정이 조용히 실패하면 툴 호출만 나빠지고 눈치챌 방법이 없었다. 이제 컴파일러가 검증한다.

#### 어긋남 처리 결과

| # | 항목 | 처리 |
|---|---|---|
| 1 | 녹음 길이 | **고침** — `MAX_AUDIO_SECONDS = 30`. 화면 타이머가 30초에 자동 종료·전송하고, `AudioRecorder` 가 파일 자체를 상한에서 끊는다(타이머가 늦어도 모델이 받는 것은 항상 상한 이내). 초과분은 버퍼 전체를 버리지 않고 남은 만큼만 쓴다 |
| 4 | `maxNumImages` | **고침** — 우리는 한 턴에 정확히 한 장을 붙이므로 1 로 명시. 보이지 않는 기본값이 우리 사용량보다 크면 4096 KV 안의 히스토리가 조용히 줄어든다 |
| 3 | 이미지 선축소 | **주석을 사실로 고침** — 코드는 1024px 로 축소하는데 주석은 *"리사이징 금지"* 라 적혀 있었다. 이 축소는 **OOM 방어**이고 해상도 정책이 아님을 명시했다 |
| 2 | 시각 토큰 예산 | **설정하지 않는다** (아래) |
| 5 | thought 삭제 예외 | **결함이 아니었다** (아래) |

부수: 프리페이스 진단 로그가 `take(2000)` 으로 **툴 선언 블록 중간에서 잘렸다** — "툴이 선언됐나" 를 답하려고 넣은 로그인데 정작 그 답이 안 보였다. 자르는 대신 1500자씩 나눠 전체를 남긴다.

#### 왜 시각 토큰 예산을 설정하지 않는가

공식 문서는 70·140·280·560·1120 중 선택이 Gemma 4 비전의 요점이라고 하고, `ExperimentalFlags.visualTokenBudget` 이 0.14.0·0.16.0 양쪽에 존재한다. 그런데 **파이썬 패키지는 그 플래그를 노출하지 않는다** — 값을 고를 근거를 로컬에서 만들 수 없다.

근거 없이 고르면 손해가 한쪽으로 기운다. 낮게 잡으면 문서 스크린샷 읽기(PRD F3 의 핵심 용도)를 조용히 깎고, 높게 잡으면 4096 KV 에서 히스토리를 잃는다. 어느 쪽도 측정 없이 정할 값이 아니다. `.agents/04_MODEL_EVIDENCE.md` 의 *"둘 다 불가능하면 모른다고 적는다"* 를 적용해 런타임 기본값에 맡기고, 실기기에서 예산별 비교가 가능해질 때 정한다.

#### thought 삭제 예외는 이미 만족한다

문서는 *"thought 를 히스토리로 되돌리기 전에 삭제하되 툴 호출 시퀀스 중에는 예외"* 라고 한다. 앞선 보고에서 *"우리는 예외를 지키지 않는다"* 고 적었는데 **틀렸다.**

`ToolParser` 가 걷어내는 것은 **화면과 DB 에 남길 본문**이고 모델에게 되돌아가는 문맥이 아니다. 대화 재사용 경로에서는 런타임이 자기 KV 에 생성 토큰을 그대로 들고 있고, 재생성 경로에서는 DB 의 thought 없는 텍스트를 싣는데 그것이 문서의 기본 규칙과 같다. 예외에 해당하는 "툴 시퀀스 중 재생성" 은 `GemmaModelRunner` 가 토큰 초과보다 우선해 막는다 — 0.8.5 실기기에서 승인 대기 중 재생성이 일어나 모델이 "자기가 호출한 적 없는 툴" 의 응답을 받은 사고 때문에 넣은 가드다. 규칙을 위해 넣은 코드가 아닌데 결과적으로 규칙을 만족한다.

#### 남은 판정은 실기기 한 줄

글자 유실은 이제 후보가 하나뿐이고 그 후보를 바꿨다. `"다음주 월요일 10시 팀 회의"` → 승인 카드의 시각이 `10:00` 으로 온전하면 원인이 AAR 0.14.0 이었던 것이 확정된다. 여전히 깨지면 후보 목록을 다시 열어야 한다 — 그때는 Android arm64 백엔드 고유가 남는다.

> **후속 (ADR-020, 2026-08-13)**: 여전히 깨졌다(`202026-081717T010000`) — AAR 가설 기각. 다만 다시 연 후보 목록의 1순위는 arm64 백엔드가 아니라 **KV 조용한 초과**가 됐다. 검증 세션이 **이어지던 긴 세션**이었음이 확인됐고(사용자), 코드 감사에서 초과를 만드는 구멍 3개가 나왔다.

### ADR-020. AAR 가설 기각과 KV 초과 후보의 부활 — 예산 구멍 3개와 방어선 (2026-08-13)

- **Status**: Accepted (판정은 실기기 재검증 대기 — 아래 프로토콜)
- **Context**: ADR-019 가 남긴 실기기 한 줄 검증을 수행했다(Galaxy S25 Ultra, litertlm 0.16.0). `"다음주 월요일 10시 팀 회의"` → 승인 카드 `202026-081717T010000`. **AAR 버전 가설 기각.** 같은 세션의 모델 산문에도 "01시", 별건 위키 답변에도 "20202년" — 깨짐은 표시가 아니라 생성/디코딩 시점이다. 부수로 위키 요약에 없는 데뷔일을 지어내는 환각도 관측됐다.

#### 판정 — 기각된 것과 부활한 것

검증 세션은 **이어지던 긴 세션**이었다(사용자 확인). 이것이 후보 순위를 바꾼다: ADR-016 이 미확정으로 남겼던 *"용량 초과와의 상호작용 — 0.14.0 이 초과분을 조용히 버린다면 그것이 곧 글자 유실일 수 있다"* 가 **1순위로 부활**한다(가설 제기: 사용자).

**실험실 36/36(exp22)은 이 가설의 반증이 아니다.** 파이썬 런타임은 용량 초과를 오류로 거부하므로(exp17: `5857 >= 4096`) 실험실에서 실행에 성공한 판정은 전부 **용량 이내**에서 이루어진 것이다. 조용히 초과 상태로 진행하는 유일한 환경이 실기기다 — 즉 이 가설은 실험실이 구조적으로 검증할 수 없고, 그래서 후보 목록에서 빠져 있었다(`.agents/04_MODEL_EVIDENCE.md` 에 규칙으로 승격).

**앱은 인자 값을 변형하지 않는다** — `collectToolCalls` → `ToolArguments` → 승인 카드 → 감사 로그 전 구간을 감사한 결과 값을 만지는 코드가 없다(키 이름만 camelCase 변환). 깨진 문자열은 런타임/모델이 만든 원문이다.

#### 코드 감사 — 맞는 예산이 잘못 집행되는 구멍 3개

0.13.0 은 예산 **산식**을 고쳤다(`ENGINE_MAX_TOKENS = 4096` 파생). 이번 감사는 그 산식이 **집행되는 계층**에서 구멍 3개를 찾았다:

| # | 구멍 | 실측 |
|---|---|---|
| (a) | `GemmaTokenizer` 가 `length/3+1` 로 추정 — 한국어·숫자를 과소평가 | exp26: 한국어 채팅 실측 1.48~1.70자/토큰(추정이 실측의 0.52~0.60배), **ISO 문자열 1.00자/토큰**(0.35배 — `2026-08-17T10:00:00` = 19자 = 19토큰) |
| (b) | 위키 결과 ko **5,000자** 하드컷(예산 6000~8000 시절 유물)이 **턴 중간**에 KV 진입 — 슬라이딩 윈도우·재설정 임계값은 턴 시작 검사이고, 툴 회신 턴은 재생성 금지 턴(0.8.5 가드)이라 보호 장치가 전혀 없음 | 5,000자 한국어 ≈ 실측 약 2,500토큰. 생성 여유(768)의 3배가 무예산으로 들어감 |
| (c) | 툴 루프 상한 3회가 (b)를 증폭 | 최악 3 × ~2,500토큰 |

즉 재설정 임계값(3000) 근처의 대화 + 위키 결과 하나로 4096 을 넘는 시나리오가 성립하고, 구멍 (a) 때문에 **재생성 직후의 대화조차** 실토큰이 예산을 넘을 수 있다.

#### 처방 — 봉합과 계측 (0.15.0)

| 대상 | 처방 |
|---|---|
| (a) 토크나이저 | 문자 3클래스 보정 — 영문·공백 /4.25, 그 외 ASCII /0.9, 비ASCII /1.2 (exp26 격자 탐색: 전 14샘플 **과소평가 0건** AND 과대 1.5배 이내, 최악 1.46배). ASCII 를 쪼갠 이유: 영어 산문 4.65자/토큰과 숫자 1.00자/토큰이 같은 클래스에 있었다. 언어별 분기 불요(중국어도 비ASCII 클래스가 덮음) — `GemmaTokenizerTest` 가 실측 픽스처로 게이트 고정 |
| (b) 위키 캡 | `TOOL_RESULT_MAX_TOKENS = 500` 을 Constants 에 신설하고 자수 캡을 토큰 캡으로 교체. 절단은 단어 중간이 아니라 **문장 경계**(`SentenceTruncator`, domain) — 잘린 조각이 사실처럼 읽히는 것 자체가 환각의 재료다 |
| (c) 루프 증폭 | (b)의 캡이 자동으로 3배 최악치를 3×~500 으로 낮춤. 상한 초과 시 이제 말풍선을 남긴다(무반응 종료 제거) |
| 계측 | `GemmaModelRunner` 가 턴 종료·툴 회신 직전에 `getTokenCount()` 를 디버그 로그로 남기고, `> 4096` 이면 "KV 용량 초과 상태로 계속 진행" 경고 — 초과와 깨짐의 시간적 상관을 실기기에서 확정할 증거 |

**정직한 한계**: 툴 회신 턴은 재생성 금지 가드(0.8.5 사고)가 있는 한 완전한 예산 보장이 구조적으로 불가능하다. 이번 처방은 최악 초과 폭을 5배 줄이고 **관측 가능**하게 만드는 것이다. 재생성 금지 가드 자체를 건드리는 것은 검증된 사고 방지 장치를 검증 없이 빼는 일이라 하지 않는다.

#### 방어선 — 깨진 값이 하류로 흐르지 않게 (원인과 무관하게 유효)

- **ISO 8601 인자 검증** (`ToolArguments.requireIsoDateTime`, `Reason.BAD_FORMAT` 신설): 판정은 하류(기기 캘린더)와 같은 `IsoDateTimeParser` 를 쓴다 — 여기서 통과한 값이 하류에서 실패하는 틈이 없다. 검증은 `buildApprovalRequest` 가 공유하는 `parse()` 안에 있어 **승인 카드가 뜨기 전에** 걸리고, BAD_FORMAT 오류는 모델 자가수정 루프로 돌아간다. 안내에는 깨진 원문을 에코하지 않는다 — 깨진 토큰열을 문맥에 재주입하면 모델이 따라 쓸 재료만 는다.
- **부수 발견(별개 버그)**: `AndroidCalendarTool.isoToMs` 에 LocalDateTime 폴백이 없어 **툴 선언이 모델에게 지시하는 바로 그 형식**(`2026-08-07T15:00:00`, 오프셋 없음)이 항상 파싱 실패 → catch 에서 `System.currentTimeMillis()` 무음 대체 → **정상 일정도 기기 캘린더에 '지금' 시각으로** 들어가고 있었다. `IsoDateTimeParser` 로 교체하고, 시작 시각 파싱 실패는 '지금' 대체 대신 **동기화 실패**로 처리한다 — 호출자(`AddScheduleUseCase.syncToDeviceCalendar`)가 이미 실패를 경고 로깅 후 로컬 저장을 유지하므로(ADR-004) 상위 변경 0 으로 안착한다. 틀린 시각의 실캘린더 기록은 "동기화 안 됨"보다 나쁜 무음 데이터 오염이다.

#### 환각 — 툴 결과 옆 근거 고정 (레버 A)

위키 요약은 도입부(`exintro`)라 질문의 답이 없을 수 있고, 그때 모델이 파라미터 지식으로 메꾼다("20202년 1월1일" — 픽스처에 데뷔일 없음). 툴 회신 턴은 턴 지침(exp20)이 `currentInput=""` 로 덮여 사라지는 턴이라 **시스템 지시로부터 가장 먼 턴**이다 — 그래서 지침을 데이터 바로 옆에 붙인다. `SearchMemoryToolExecutor` 가 같은 방식으로 실기기에서 돌고 있는 선례다.

**실측 (exp25)** — 데뷔일이 없는 동결 위키 픽스처, 질문 3변형, 앱 동일 설정(제약 디코딩·greedy·턴 지침·툴 5종):

| 조건 | fabrication | 명시적 "요약에 없다" 답변 |
|---|---|---|
| baseline (현행) | 0/3 | 1/3 |
| **A — 데이터 옆 지침** | 0/3 | **3/3** |
| B — 시스템 지시 정직성 규칙 | 0/3 | 2/3 |
| A+B | 0/3 | 3/3 (=A) |

- 실기기의 fabrication("20202년")은 depth 0 실험실에서 **재현되지 않았다**(전 조건 0/3) — 실기기 환각은 긴 세션 조건이었다. 그래서 fabrication rate 로는 조건을 가를 수 없었고, 제품이 원하는 행동인 **명확한 "없다" 답변**이 갈랐다: baseline 은 1/3만 명확했고 나머지는 "혹시 다른 정보가 필요하신가요" 류로 얼버무렸다.
- **레버 B(일반 정직성 규칙)는 기각한다.** 회귀 게이트는 통과했지만(일정 4 + 조회 3 = **7/7**, ISO 인자 전부 온전 — 과거 *"DO NOT guess. Ask the user first."* 가 일정 4중 3을 죽인 사고의 재발 없음) A 단독이 이미 3/3 천장이라 **추가 효과가 측정되지 않는다**. 측정으로 정당화되지 않는 시스템 지시 변경은 넣지 않고(시스템 지시가 바뀌면 살아 있는 대화가 전부 재생성되는 비용도 있다), 후보 문구와 회귀 결과를 exp25 에 남겨 필요할 때 근거를 갖고 재개한다.
- greedy 결정성 재확인(반복 동일). 부수 probe: 프리필이 용량을 넘는 대화를 파이썬 0.16.0 이 `RuntimeError` 로 **거부** — "실험실 통과는 용량 이내에서만 유효" 의 실측 근거다(exp17 의 0.16.0 재확인).

#### 실기기 재검증 프로토콜 (릴리스 게이트)

디버그 빌드로 **긴 세션을 재현**(위키 포함 다턴)하고 logcat 의 KV 계측을 함께 읽는다:

| 관측 | 판정 |
|---|---|
| 초과 경고 없음 + 깨짐 없음 | 가설 지지, 처방 유효 |
| 초과 경고 발생 + 그 시점 깨짐 | **가설 확정** (인과 로그 확보) |
| 초과 없는데 깨짐 | 가설 약화 → 다음 라운드에 CPU 강제 토글로 백엔드/arm64 판정 (사용자 결정: 이번 라운드 제외) |

AddSchedule 실호출로 방어선도 확인한다 — 깨진 값이면 자가수정 재시도, 3회 초과 시 말풍선.

> **후속 (ADR-021, 2026-08-13)**: 재검증 결과는 **3행(초과 없는데 깨짐)** 이었다 — 계측 실측 2,783/4096, 초과 로그 0건. 같은 날 실험실이 원인을 끝까지 추적해 확정했다.

### ADR-021. 글자 유실의 원인 확정 — GPU FP16 활성화 정밀도 × 문맥 깊이 (2026-08-13)

- **Status**: Accepted
- **Context**: ADR-020 재검증에서 "초과 없는데 깨짐"이 나왔다(KV 2,783/4096, 경고 0건 — 계측이 첫 가동에서 가설 하나를 기각했다). 같은 로그의 렌더 프리페이스에서 어제의 깨진 출력들(`20817일 01시`, `2023년 111월 1일`, 원시 `<|tool_call>` 조각)이 히스토리로 재생되는 것이 발견돼 **오염 모방**이 새 가설로 제기됐고, 실험 여섯이 하루 안에 전 가설을 정리했다. 판정은 전부 PC 실험실(litert-lm 0.16.0, 같은 모델 파일)이며 greedy 라 전 결과가 결정적(반복 동일)이다.

#### 판정 여정 — 기각 셋, 확정 하나

| # | 실험 | 조건 | 결과 | 기각/확정 |
|---|---|---|---|---|
| 1 | 실기기 계측 | 기기 GPU, 긴 세션 | 2,783/4096 에서 깨짐, 초과 경고 0 | ~~KV 조용한 초과~~ (ADR-016 이후 후보) |
| 2 | exp27 | PC **CPU** × 오염 깊은 문맥(2,784 — 실기기 2,783 과 사실상 동일) | **온전** | ~~오염 모방~~ |
| 3 | exp28 | PC **GPU** × 같은 문맥 | **깨짐** (`20826-08-17T0100:000`) | GPU 재현 확보 |
| 4 | exp29-D | PC GPU × **깨끗한** 깊은 문맥 | **깨짐** | 방아쇠는 오염이 아니라 **깊이** |
| 5 | exp29-E | PC GPU × 오염 문맥 + **FLOAT32 활성화** | **온전** | 원인 = **FP16 활성화 정밀도** |
| 6 | exp30 | PC GPU, 깊이 이등분 | **1,854 온전 / 2,122 깨짐** | 발병 경계 실측 |

런타임 로더가 메커니즘을 직접 확인해 준다: *"System's default activation type for **Text decoder is fp16**. Vision encoder and audio encoder default is fp32."* — 깊은 문맥에서 fp16 활성화의 수치 오차가 누적되어 greedy 의 숫자 토큰 선택이 뒤집힌다. arm64 는 무죄다(x86_64 PC 에서 재현).

#### 처방 후보 검토 — 왜 예산 완화인가

| 후보 | 판정 |
|---|---|
| `activationDataType = FLOAT32` | **불가** — 파이썬 패키지에만 있고 AAR 0.16.0 은 노출하지 않는다(EngineConfig·ExperimentalFlags 를 javap 로 전수 확인) |
| GPU 전용 모델 변형(`-gpu.litertlm`, 6일 전 공개) | **기각** — 같은 재현 조건에서 온전했으나(exp31), **오디오 인코더·표준 비전 인코더·CPU 디코더가 전부 없다**(exp32 + 섹션 덤프: `TF_LITE_AUDIO_ENCODER_HW`·`TF_LITE_VISION_ENCODER`·`TF_LITE_PREFILL_DECODE` not found). 텍스트 전용 GPU 온리 빌드라 음성(PRD F2)·이미지(F3)가 죽는다. 기본 모델 파일은 미갱신(SHA256 일치 확인) |
| CPU 백엔드 전환 | 기각 — 정확하지만 4B CPU 추론 속도가 채팅 UX 로 성립하지 않는다 |
| **예산을 발병점 아래로** | **채택** (사용자 결정) — 음성·비전 유지, 숫자 안전. 대가는 아래에 |

#### 처방 — 예산 1700 설계와 그 대가

`MAX_CONTEXT_TOKENS` 3000→**1700**, `MIN_CONVERSATION_RESET` 2600→**1700**, `MIN_HISTORY` 500→**300**, 슬라이더 상한 3000→**1700**(저장값은 기존 패턴대로 읽기 시 클램프). 1700 은 우연이 아니라 **바닥**이다: 오버헤드(1400) + 최소 히스토리(300) 아래로 내리면 재생성 직후 임계값을 넘어 매 턴 재생성 루프가 된다. `TokenBudgetInvariantTest` 가 발병점·루프 방지 관계를 고정한다.

- **플레인 턴은 안전권**: 생성 시점 KV ≈ 임계값 + 발화 ≈ 1,800 < 1,854(온전 실측선). **일정 인자 생성이 이 구간**이므로 핵심 경로가 보호된다.
- **정직한 한계**: 툴 회신 턴의 생성은 ~2,350 까지 올라가 노출이 남는다(회신 턴은 재생성 금지 턴). 노출되는 것은 툴 결과를 요약하는 산문의 숫자이고, 일정 **인자**는 BAD_FORMAT 방어선(0.15.0)이 하류를 지킨다.
- **대가**: 히스토리 약 300토큰(2~3턴) + 재설정 빈도 증가. 0.13.0 이 줄이려던 프리필 비용 일부가 돌아온다 — 숫자 정확성(일정 시각·비밀번호)과 맞바꾼 것이다.
- **PC↔기기 전이 한계**: 발병점은 PC GPU(WebGPU/NVIDIA) 실측이다. 기기(Adreno)의 경계가 다를 수 있으므로 실기기 재검증이 최종 판정자다 — 남은 실기기 계측 로그가 그 증거를 만든다.

> **실기기 확정 (2026-08-13)**: 예산 1700 빌드에서 같은 발화의 승인 카드가 `2026-08-17 / 10:00–11:00` 으로 **온전**했고, 승인된 일정이 기기 캘린더에 지정 시각(10:00)으로 저장됐다(isoToMs 수정 동시 확인). 위키 근거 답변의 날짜(`2015년 10월 20일`)도 온전 — PC 발병점이 기기에도 유효했다. 음성 전사만 환경 제약으로 미확인(이번 라운드에서 오디오 경로 무변경, PC 대조군 exp32b 통과라 위험 낮음).

#### 상류 보고

PC 결정적 재현 케이스가 확보됐다(같은 파일·같은 버전·같은 프롬프트에서 CPU 온전/GPU 깨짐, FLOAT32 로 완치, 발병 경계 1,854~2,122). 요청 사항 둘: ① AAR 에 `activationDataType` 노출(파이썬과 동등) ② 오디오·비전 포함 GPU 변형. 보고 초안은 `scratch/lab/upstream_issue_draft.md`.

#### 부수 — 오염 히스토리는 위생 결함으로 강등

원시 `<|tool_call>` 조각이 말풍선으로 저장·재생되는 것은 실측상 숫자 깨짐의 원인이 아니지만(exp27·29·31 — 오염 문맥에서도 CPU·GPU변형·FLOAT32 는 온전) 템플릿 구조를 오염시키는 실재 결함이다. 저장·재생 시 소독은 별건으로 처리한다.

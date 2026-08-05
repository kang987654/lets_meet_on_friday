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
| 페이지네이션 | Paging 3 | 3.4.2 | Room PagingSource 연동 |
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

```kotlin
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

    const val CALENDAR_DRAFT_MIN_CONFIDENCE = 0.7f

    const val MODEL_DIR_NAME = "models"
    const val DEFAULT_MODEL_FILENAME = "gemma4-e4b-it-q4.litertlm"
}
```

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

**`PromptAssembler` 프롬프트 구조 (4블록)**

```text
[시스템 블록]          ~300 tokens
[메모리 블록]          ~500 tokens
[대화 블록]            ~1200 tokens
[현재 입력 블록]       ~500 tokens
[응답 여유 공간]       ~1596 tokens
─────────────────────────────────────
총합: 4096 tokens
```

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
- **대용량 리스트 렌더링**: AuditLog, Knowledge 등 대규모 데이터를 UI 리스트로 렌더링할 경우, 메모리 초과(OOM)를 방지하기 위해 Room DB의 `PagingSource`와 연동하여 `Flow<PagingData<T>>`를 반환하고, Compose에서는 `collectAsLazyPagingItems()`로 수집하여 표시한다.

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
| 컨텍스트 길이 | 4096 tokens | Constants 변경 |
| 대화 히스토리 | 최근 5턴 | Constants 변경 |
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

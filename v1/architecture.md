# architecture.md — Local Friday
## Android Offline-First Personal AI Assistant

**문서 버전**: 1.0
**기준 PRD**: PRD.md v1.0
**아키텍처 패턴**: Modular Monolith (Single Android App)
**대상 환경**: Samsung Galaxy S25 Ultra / Android

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
9. API Architecture
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
마이크로서비스나 멀티모듈 분리는 MVP 단계에서 과잉 설계이므로
**단일 앱 내에서 레이어와 도메인 경계를 명확히 분리**하는
Modular Monolith 구조를 채택한다.

핵심 원칙:
- 레이어 간 의존은 **단방향 하향**만 허용
- Domain 레이어는 Android 프레임워크 의존성 **금지**
- 각 레이어는 **인터페이스**를 통해서만 하위 레이어와 통신
- AI agent가 **파일 단위**로 작업 가능한 크기로 분리

### 1-2. 레이어 구조

```
┌─────────────────────────────────────────────────────┐
│                  Presentation Layer                  │
│         (Compose UI / ViewModel / Navigation)        │
├─────────────────────────────────────────────────────┤
│               Assistant Core Layer                   │
│   (Orchestrator / Router / Planner / Approval)       │
├─────────────────────────────────────────────────────┤
│                  Domain Layer                        │
│      (Models / Use Cases / Repository Interfaces     │
│             / Policy Interfaces)                     │
├─────────────────────────────────────────────────────┤
│                   Data Layer                         │
│    (Room DB / DataStore / Repository Impls           │
│              / Export-Import)                        │
├──────────────────────┬──────────────────────────────┤
│    Runtime Layer     │      Platform Layer           │
│  (Model Runner /     │  (Calendar / STT / File /    │
│   Multimodal /       │   Search / Share)             │
│   Metrics)           │                               │
└──────────────────────┴──────────────────────────────┘
```

### 1-3. 레이어별 의존 방향 규칙

```
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
| 언어 | Kotlin | 2.0+ | Android 표준 |
| UI | Jetpack Compose | BOM 최신 | 선언형 UI, StateFlow 연동 |
| Navigation | Compose Navigation | - | Compose 공식 통합 |
| DI | **Hilt** | 2.52+ | 컴파일 타임 안전성, Jetpack 통합 |
| DI 처리기 | **KSP** | - | KAPT 대비 빌드 속도 향상 |
| DB | Room | 2.6+ | SQLite 추상화, AutoMigration 지원 |
| 설정 저장 | DataStore (Preferences) | - | 비동기 설정 저장 |
| 비동기 | Coroutines + Flow | - | Android 표준 비동기 |
| 상태 관리 | StateFlow + ViewModel | - | Lifecycle-aware 상태 |
| 로컬 모델 | **LiteRT-LM** | 최신 | Gemma 4 공식 Android 런타임 |
| 모델 포맷 | `.litertlm` (INT4) | - | 모바일 최적화 양자화 포맷 |
| STT | Android SpeechRecognizer | - | 온디바이스 오프라인 STT |
| 이미지 처리 | Android BitmapFactory | - | 별도 라이브러리 없이 처리 |
| JSON 직렬화 | kotlinx.serialization | - | Kotlin 네이티브 |
| 페이지네이션 | Paging 3 | - | Room PagingSource 연동 |

### 2-2. 미사용 / 보류 항목

| 항목 | 상태 | 이유 |
|---|---|---|
| Retrofit / OkHttp | MVP 미사용 | 외부 REST API 없음 |
| Koin | 미채택 | Hilt 확정 |
| KAPT | 미사용 | KSP로 대체 |
| Vector DB | v2 보류 | MVP 과잉 설계 |
| Whisper (로컬 STT) | v1 검토 | 현재 Android SpeechRecognizer 사용 |
| WorkManager | 필요 시 추가 | 모델 로드 백그라운드 처리 검토용 |
| MediaPipe LLM Inference API | 미채택 | deprecated, LiteRT-LM으로 대체 |

---

## 3. Domain Separation

### 3-1. 도메인 경계 정의

이 앱은 하나의 Android 앱 안에서 아래 6개의 도메인으로
책임을 분리한다. 각 도메인은 독립적으로 테스트 가능해야 한다.

```
┌─────────────────────────────────────────────────────────┐
│                     App Domain                          │
│              (초기화 / DI / Navigation)                  │
├──────────────┬──────────────┬──────────────────────────┤
│ Chat Domain  │Calendar Domain│   Memory Domain          │
│ (대화 / 응답) │(일정 조회/생성)│(저장/조회/export)        │
├──────────────┴──────┬───────┴──────────────────────────┤
│   Assistant Domain  │         Settings Domain           │
│ (의도 분석/오케스트레│  (모델 정보 / 정책 / 로그)          │
│  이션/승인)          │                                   │
└─────────────────────┴───────────────────────────────────┘
```

### 3-2. 도메인별 책임

| 도메인 | 책임 | 주요 Use Case |
|---|---|---|
| **Chat** | 사용자 입력 수신, 응답 생성 조율 | SendChatMessage, ProcessVoiceInput, ProcessImageInput |
| **Calendar** | 캘린더 조회 및 일정 초안 생성 | GetTodaySchedule, CreateCalendarDraft, ApproveAndSaveEvent |
| **Memory** | 5계층 메모리 저장/조회/관리 | SaveConversation, SearchKnowledge, ExportMemory, ImportMemory |
| **Assistant** | 의도 분류, 도구 선택, 승인 조율 | ClassifyIntent, RouteToAgent, RequestApproval |
| **Settings** | 모델 설정, 응답 스타일, 정책 | UpdateModelConfig, UpdatePersonaProfile, GetAuditLog |
| **App** | 전역 초기화, DI 조립, 화면 이동 | AppInit, PermissionCheck |

### 3-3. 도메인 간 통신 규칙

- 도메인 간 직접 의존 **금지**
- Chat Domain이 Calendar 기능이 필요하면
  **Assistant Domain을 통해 라우팅**
- 공유 데이터는 Domain Layer의 공통 모델을 사용
- 이벤트 기반 통신이 필요한 경우 SharedFlow 사용

---

## 4. Module Responsibilities

### 4-1. `app/` — 앱 진입점

| 파일 | 책임 |
|---|---|
| `LocalFridayApp.kt` | Application 클래스, 전역 초기화 |
| `MainActivity.kt` | Compose NavHost 진입점, 단일 Activity |
| `di/AppModule.kt` | 공통 싱글턴 바인딩 |
| `di/ModelModule.kt` | ModelRunner 바인딩 |
| `di/MemoryModule.kt` | Repository 바인딩 |
| `di/AgentModule.kt` | Orchestrator / Agent 바인딩 |
| `di/PlatformModule.kt` | Tool 구현체 바인딩 |

> **AI agent 작업 단위**: `di/` 파일은 각각 독립적으로 생성 가능.
> 새로운 의존성 추가 시 해당 모듈 파일만 수정한다.

---

### 4-2. `core/` — 공통 기반

**어떤 레이어에서도 import 가능한 공통 타입만 포함.**
Android 의존성 최소화.

| 파일 | 책임 |
|---|---|
| `common/Result.kt` | `sealed class Result<out T>` — 성공/실패 래퍼 |
| `common/AppError.kt` | 에러 타입 열거 (ModelError / DBError / PermissionError 등) |
| `common/Constants.kt` | 앱 전역 상수 (토큰 제한, 온도 임계값 등) |
| `config/AppConfig.kt` | 런타임 설정 값 |
| `config/FeatureFlags.kt` | 기능 ON/OFF 플래그 |
| `config/ModelConfig.kt` | 모델 경로, 컨텍스트 제한 등 |
| `logging/AppLogger.kt` | 개발용 로그 래퍼 |
| `logging/AuditLogger.kt` | 감사 로그 기록 인터페이스 |
| `security/PermissionPolicy.kt` | 권한 정책 정의 |
| `security/ApprovalPolicy.kt` | 승인 필요 작업 정의 |
| `security/Redaction.kt` | 민감 정보 마스킹 (감사 로그용) |

**`Constants.kt` 주요 상수**

```kotlin
object Constants {
    // 컨텍스트 설정
    const val MAX_CONTEXT_TOKENS = 4096
    const val MAX_CONVERSATION_TURNS = 5
    const val MAX_KNOWLEDGE_CONTEXT_ITEMS = 3
    const val MAX_INPUT_TOKENS = 2048

    // 이미지 제한
    const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024  // 10MB
    const val MAX_IMAGE_DIMENSION_PX = 1024

    // 발열 임계값
    const val THERMAL_WARNING_CELSIUS = 43f
    const val THERMAL_SHUTDOWN_CELSIUS = 48f
    const val THERMAL_COOLDOWN_INFERENCE_COUNT = 5

    // 모델 경로
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
| `ChatMessage.kt` | 채팅 메시지 (role, content, inputType, timestamp) |
| `AssistantResponse.kt` | 비서 응답 (content, actionCard, searchUsed) |
| `UserProfile.kt` | 사용자 프로필 (name, responseStyle) |
| `CalendarEvent.kt` | 캘린더 일정 도메인 모델 |
| `CalendarDraft.kt` | 일정 초안 (title, startIso, endIso, note, confidence) |
| `KnowledgeNote.kt` | 지식 메모 (title, content, tags, sourceType) |
| `TaskItem.kt` | 작업 아이템 (title, status, priority) |
| `AuditEvent.kt` | 감사 이벤트 (eventType, detail, approvalStatus, networkUsed) |
| `SearchRequest.kt` | 검색 요청 (query, reason) |
| `ApprovalRequest.kt` | 승인 요청 (actionType, payload, riskLevel) |

#### `domain/memory/` — 메모리 저장소 인터페이스

| 파일 | 책임 |
|---|---|
| `ProfileRepository.kt` | 프로필 CRUD 인터페이스 |
| `ConversationRepository.kt` | 대화 저장/조회 인터페이스 |
| `TaskRepository.kt` | 작업 저장/조회 인터페이스 |
| `KnowledgeRepository.kt` | 지식 메모 저장/검색 인터페이스 |
| `AuditRepository.kt` | 감사 로그 저장/조회 인터페이스 |

#### `domain/modelrunner/` — 모델 추상화

| 파일 | 책임 |
|---|---|
| `ModelRunner.kt` | `interface ModelRunner` — generate / generateWithImage / cancel |
| `ModelLoadState.kt` | 모델 로드 상태 (Loading / Ready / Error / NotFound) |

**`ModelRunner` 인터페이스**

```kotlin
interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>

    suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)? = null  // 스트리밍 콜백 (선택)
    ): Result<String, AppError>

    suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray
    ): Result<String, AppError>

    suspend fun cancel()
    suspend fun warmUp()
}
```

#### `domain/agent/` — 에이전트 인터페이스

| 파일 | 책임 |
|---|---|
| `Agent.kt` | `interface Agent` — execute |
| `AgentResult.kt` | 에이전트 실행 결과 래퍼 |

#### `domain/tool/` — 도구 인터페이스

| 파일 | 책임 |
|---|---|
| `CalendarTool.kt` | readEvents / createDraft / insert 인터페이스 |
| `SpeechToTextTool.kt` | start / stop / cancel 인터페이스 |
| `WebSearchTool.kt` | search 인터페이스 |
| `FileTool.kt` | read / write / resolveUri 인터페이스 |

#### `domain/policy/` — 정책 인터페이스

| 파일 | 책임 |
|---|---|
| `ExecutionPolicy.kt` | 실행 허용/거부 판단 인터페이스 |
| `ApprovalPolicy.kt` | 승인 필요 여부 판단 인터페이스 |

#### `domain/usecase/` — 유스케이스

각 유스케이스는 **단일 책임**을 가진다.
하나의 파일이 하나의 비즈니스 동작을 담당한다.

| 파일 | 책임 |
|---|---|
| `SendChatMessageUseCase.kt` | 텍스트 채팅 메시지 전송 및 응답 처리 |
| `ProcessVoiceInputUseCase.kt` | 음성 입력을 채팅 흐름으로 변환 |
| `ProcessImageInputUseCase.kt` | 이미지 첨부 질의응답 처리 |
| `GetTodayScheduleUseCase.kt` | 오늘/주간 일정 조회 |
| `CreateCalendarDraftUseCase.kt` | 자연어 → 캘린더 초안 생성 |
| `ApproveAndSaveEventUseCase.kt` | 승인 후 캘린더 저장 |
| `SearchKnowledgeUseCase.kt` | 로컬 지식 메모 검색 |
| `ExportMemoryUseCase.kt` | 메모리 export |
| `ImportMemoryUseCase.kt` | 메모리 import 및 검증 |
| `SaveConversationUseCase.kt` | 대화 저장 |

---

### 4-4. `assistant/` — 비서 핵심 로직

**AI 처리 흐름을 조율하는 레이어.**
Domain 레이어의 인터페이스만 사용한다.

| 파일 | 책임 |
|---|---|
| `orchestrator/AssistantOrchestrator.kt` | 전체 입력→출력 흐름 총괄 |
| `orchestrator/TaskRouter.kt` | 의도에 따른 Agent 선택 및 라우팅 |
| `orchestrator/IntentClassifier.kt` | 입력 의도 분류 (chat / calendar / search / knowledge) |
| `context/ContextBuilder.kt` | 대화 + 메모리 컨텍스트 조합 |
| `context/PromptAssembler.kt` | 시스템 프롬프트 + 컨텍스트 + 입력 조립 |
| `context/ResponseParser.kt` | 모델 출력 JSON 파싱 및 타입 판별 |
| `approval/ApprovalCoordinator.kt` | 승인 플로우 제어 |
| `guard/PreExecutionGuard.kt` | 실행 전 정책 검사 (승인 필요 여부, 검색 허용 여부) |
| `guard/PostExecutionValidator.kt` | 실행 결과 검증 |
| `audit/AuditTrailService.kt` | 감사 이벤트 기록 서비스 |
| `persona/PersonaProfileManager.kt` | 페르소나 프로필 관리 |
| `agent/CalendarAgent.kt` | 캘린더 관련 작업 처리 |
| `agent/SearchAgent.kt` | 웹 검색 처리 |
| `agent/KnowledgeAgent.kt` | 지식 메모 처리 |

**`PromptAssembler` 프롬프트 구조 (4블록)**

```
[시스템 블록]          ~300 tokens  (고정)
  - 페르소나 / 응답 스타일 / 날짜 / 정책

[메모리 블록]          ~500 tokens
  - KnowledgeRepository 최근 3개 항목

[대화 블록]            ~1200 tokens
  - ConversationRepository 최근 5턴
  - 토큰 초과 시 오래된 턴부터 FIFO 제거

[현재 입력 블록]       ~500 tokens (최대)

[응답 여유 공간]       ~1596 tokens
─────────────────────────────────────
총합: 4096 tokens (MVP 기준)
```

---

### 4-5. `data/` — 데이터 레이어

#### `data/local/db/`

| 파일 | 책임 |
|---|---|
| `LocalFridayDatabase.kt` | Room DB 진입점, version 관리, AutoMigration 등록 |
| `dao/ProfileDao.kt` | profiles 테이블 접근 |
| `dao/ConversationDao.kt` | conversations 테이블 접근 |
| `dao/TaskDao.kt` | tasks 테이블 접근 |
| `dao/KnowledgeDao.kt` | knowledge_notes 테이블 접근 |
| `dao/AuditDao.kt` | audit_events 테이블 접근 |
| `entity/ProfileEntity.kt` | profiles 테이블 엔티티 |
| `entity/ConversationEntity.kt` | conversations 테이블 엔티티 |
| `entity/TaskEntity.kt` | tasks 테이블 엔티티 |
| `entity/KnowledgeEntity.kt` | knowledge_notes 테이블 엔티티 |
| `entity/AuditEntity.kt` | audit_events 테이블 엔티티 |

#### `data/local/prefs/`

| 파일 | 책임 |
|---|---|
| `SettingsDataStore.kt` | 앱 설정 저장 (DataStore) |
| `ModelRegistryStore.kt` | 모델 경로 / 버전 정보 저장 |

#### `data/local/file/`

| 파일 | 책임 |
|---|---|
| `ExportImportManager.kt` | export (JSON+SQLite 묶음) / import 처리 |
| `ExportManifest.kt` | export_manifest.json 데이터 클래스 |

#### `data/repository/`

| 파일 | 책임 |
|---|---|
| `ProfileRepositoryImpl.kt` | ProfileRepository 구현 |
| `ConversationRepositoryImpl.kt` | ConversationRepository 구현 |
| `TaskRepositoryImpl.kt` | TaskRepository 구현 |
| `KnowledgeRepositoryImpl.kt` | KnowledgeRepository 구현 |
| `AuditRepositoryImpl.kt` | AuditRepository 구현 |

---

### 4-6. `runtime/` — 모델 실행 레이어

| 파일 | 책임 |
|---|---|
| `gemma/GemmaModelRunner.kt` | `ModelRunner` 구현 — LiteRT-LM 연결 |
| `gemma/GemmaRuntimeManager.kt` | 모델 로드 / 언로드 / 상태 관리 |
| `multimodal/ImageInputAdapter.kt` | 이미지 URI → 리사이즈 → ByteArray 변환 |
| `metrics/RuntimeMetricsCollector.kt` | 추론 시간 / 온도 / 연속 횟수 기록 |

> **AI agent 주의**: `benchmark/` 디렉토리는 MVP에서 생성하지 않는다.

---

### 4-7. `platform/` — Android 플랫폼 연결

**Tool 인터페이스의 Android 구현체.**
이 레이어만 `android.*` 의존성을 가진다.

| 파일 | 책임 |
|---|---|
| `calendar/AndroidCalendarTool.kt` | Android Calendar Provider 접근 |
| `speech/AndroidSpeechToTextTool.kt` | Android SpeechRecognizer 연결 |
| `share/ShareIntentHandler.kt` | 공유 인텐트 수신 처리 |
| `storage/AndroidFileTool.kt` | 파일 / URI 접근 |
| `network/WebSearchGateway.kt` | 웹 검색 추상화 (MVP는 Stub 구현) |
| `permissions/PermissionManager.kt` | 런타임 권한 요청 / 상태 확인 |

**`AndroidSpeechToTextTool` 정책**

```kotlin
// 반드시 오프라인 우선 설정
intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
```

---

### 4-8. `feature/` — 화면 단위 UI

각 feature는 **ViewModel + Screen + UiState**의 3파일 구조로 유지한다.

| 디렉토리 | 파일 구성 | 책임 |
|---|---|---|
| `feature/chat/` | `ChatScreen.kt` / `ChatViewModel.kt` / `ChatUiState.kt` | 메인 채팅 화면 |
| `feature/voice/` | `VoiceOverlay.kt` / `VoiceViewModel.kt` / `VoiceUiState.kt` | 음성 입력 오버레이 |
| `feature/calendar/` | `CalendarScreen.kt` / `CalendarViewModel.kt` / `CalendarUiState.kt` | 일정 화면 |
| `feature/approval/` | `ApprovalSheet.kt` / `ApprovalViewModel.kt` / `ApprovalUiState.kt` | 승인 바텀시트 |
| `feature/memory/` | `MemoryScreen.kt` / `MemoryViewModel.kt` / `MemoryUiState.kt` | 메모리 관리 화면 |
| `feature/settings/` | `SettingsScreen.kt` / `SettingsViewModel.kt` / `SettingsUiState.kt` | 설정 화면 |

---

### 4-9. `navigation/`

| 파일 | 책임 |
|---|---|
| `AppNavHost.kt` | NavHost 정의, 전체 라우트 등록 |
| `AppDestination.kt` | 화면 경로 상수 sealed class |

---

## 5. Directory Structure

```
com.localfriday.app
│
├── app/
│   ├── LocalFridayApp.kt
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
│   │   ├── Result.kt
│   │   ├── AppError.kt
│   │   └── Constants.kt
│   ├── config/
│   │   ├── AppConfig.kt
│   │   ├── FeatureFlags.kt
│   │   └── ModelConfig.kt
│   ├── logging/
│   │   ├── AppLogger.kt
│   │   └── AuditLogger.kt
│   └── security/
│       ├── PermissionPolicy.kt
│       ├── ApprovalPolicy.kt
│       └── Redaction.kt
│
├── domain/
│   ├── model/
│   │   ├── ChatMessage.kt
│   │   ├── AssistantResponse.kt
│   │   ├── UserProfile.kt
│   │   ├── CalendarEvent.kt
│   │   ├── CalendarDraft.kt
│   │   ├── KnowledgeNote.kt
│   │   ├── TaskItem.kt
│   │   ├── AuditEvent.kt
│   │   ├── SearchRequest.kt
│   │   └── ApprovalRequest.kt
│   ├── memory/
│   │   ├── ProfileRepository.kt
│   │   ├── ConversationRepository.kt
│   │   ├── TaskRepository.kt
│   │   ├── KnowledgeRepository.kt
│   │   └── AuditRepository.kt
│   ├── modelrunner/
│   │   ├── ModelRunner.kt
│   │   └── ModelLoadState.kt
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
│       ├── ImportMemoryUseCase.kt
│       └── SaveConversationUseCase.kt
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
│       │   ├── LocalFridayDatabase.kt
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
│       │   └── ModelRegistryStore.kt
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
│   │   └── GemmaRuntimeManager.kt
│   ├── multimodal/
│   │   └── ImageInputAdapter.kt
│   └── metrics/
│       └── RuntimeMetricsCollector.kt
│
├── platform/
│   ├── calendar/
│   │   └── AndroidCalendarTool.kt
│   ├── speech/
│   │   └── AndroidSpeechToTextTool.kt
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

```
[ChatScreen]
    │ 사용자 입력 이벤트
    ▼
[ChatViewModel]
    │ SendChatMessageUseCase 호출
    ▼
[SendChatMessageUseCase]
    │ AssistantOrchestrator 호출
    ▼
[AssistantOrchestrator]
    ├─ PreExecutionGuard.check()
    │   ├─ 검색 ON 여부 확인
    │   └─ 승인 필요 여부 확인
    │
    ├─ ContextBuilder.build()
    │   ├─ ConversationRepository → 최근 5턴 조회
    │   └─ KnowledgeRepository → 관련 항목 3개 조회
    │
    ├─ PromptAssembler.assemble()
    │   └─ 4블록 프롬프트 조합
    │
    ├─ ModelRunner.generate()
    │   └─ GemmaModelRunner → LiteRT-LM 추론
    │
    ├─ ResponseParser.parse()
    │   ├─ TextResponse → 화면 표시
    │   └─ ActionDraft → ApprovalCoordinator
    │
    ├─ SaveConversationUseCase()
    │   └─ ConversationRepository.save()
    │
    └─ AuditTrailService.record()
        └─ AuditRepository.save()
    │
    ▼
[ChatViewModel]
    │ UiState 업데이트
    ▼
[ChatScreen]
    └─ 응답 표시
```

### 6-2. 일정 초안 생성 및 승인 흐름

```
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
    │ ApprovalRequest 생성
    ▼
[ApprovalViewModel → ApprovalSheet]
    │ 사용자 승인 / 거부
    ├─ 승인 → ApproveAndSaveEventUseCase
    │           └─ AndroidCalendarTool.insert()
    │           └─ AuditRepository.save(APPROVED)
    └─ 거부 → AuditRepository.save(REJECTED)
```

### 6-3. 모델 구조화 출력 스키마

모델은 아래 4가지 중 하나의 JSON을 반환한다.
`ResponseParser`가 action 필드로 타입을 판별한다.

```kotlin
// 1. 일반 텍스트 응답
{ "action": "text", "content": "응답 내용" }

// 2. 캘린더 초안
{
  "action": "calendar_draft",
  "title": "병원 진료",
  "startIso": "2025-05-12T15:00:00",
  "endIso": "2025-05-12T16:00:00",
  "note": "내과 진료",
  "confidence": 0.92
}

// 3. 검색 요청
{ "action": "search", "query": "검색어", "reason": "최신 정보 필요" }

// 4. 지식 저장 요청
{
  "action": "save_knowledge",
  "title": "메모 제목",
  "content": "내용",
  "tags": ["태그1", "태그2"]
}
```

**파싱 실패 처리**
- JSON 파싱 실패 → TextResponse로 fallback
- `confidence < 0.7`인 CalendarDraft → 자동 승인 불가, 반드시 ApprovalSheet 표시

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
```

### 7-2. ViewModel 상태 관리 패턴

```kotlin
class ChatViewModel @Inject constructor(
    private val sendChatMessageUseCase: SendChatMessageUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 중복 전송 차단
    private var isInFlight = false

    fun sendMessage(input: String) {
        if (isInFlight) return          // 중복 전송 차단
        if (input.isBlank()) return     // 빈 입력 차단

        isInFlight = true
        _uiState.value = ChatUiState.Loading

        viewModelScope.launch {
            sendChatMessageUseCase(input)
                .onSuccess { response ->
                    _uiState.value = ChatUiState.Success(response)
                }
                .onFailure { error ->
                    _uiState.value = ChatUiState.Error(error)
                }
            isInFlight = false
        }
    }
}
```

### 7-3. 상태 전환 규칙

```
Idle ──────────────► Loading
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
           Success     Empty      Error
              │                     │
              └──────────┬──────────┘
                         ▼
                        Idle (다음 입력 대기)
```

### 7-4. 공유 상태 관리

화면 간 공유가 필요한 상태는 ViewModel 범위로 관리한다.

| 상태 | 관리 위치 | 공유 범위 |
|---|---|---|
| 모델 로드 상태 | `GemmaRuntimeManager` (싱글턴) | 전체 앱 |
| 현재 세션 ID | `ChatViewModel` | Chat 화면 |
| 승인 요청 | `ApprovalCoordinator` | Chat + Approval |
| 검색 토글 상태 | `ChatViewModel` | Chat 화면 (질문 단위로 초기화) |

---

## 8. Database Design Overview

### 8-1. 저장 방식 구분

| 저장소 | 용도 | 형식 |
|---|---|---|
| Room (SQLite) | 대화, 작업, 지식, 감사 로그 | 관계형 테이블 |
| DataStore | 앱 설정, 모델 정보 | Key-Value (Proto/Preferences) |
| 파일 시스템 | 모델 파일, export 파일 | Binary / ZIP |

### 8-2. Room DB 테이블 스키마

**`LocalFridayDatabase` 버전 관리**

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
    exportSchema = true,       // 반드시 true — AutoMigration 필수 조건
    autoMigrations = []        // 버전 업 시 여기에 추가
)
abstract class LocalFridayDatabase : RoomDatabase()
```

---

**`profiles` 테이블**

```sql
CREATE TABLE profiles (
    id          TEXT PRIMARY KEY,
    user_name   TEXT NOT NULL,
    response_style  TEXT NOT NULL DEFAULT 'concise',
    persona_description TEXT,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
)
```

---

**`conversations` 테이블**

```sql
CREATE TABLE conversations (
    id          TEXT PRIMARY KEY,
    session_id  TEXT NOT NULL,
    role        TEXT NOT NULL,        -- 'user' | 'assistant'
    content     TEXT NOT NULL,
    input_type  TEXT NOT NULL DEFAULT 'text',  -- 'text' | 'voice' | 'image'
    search_used INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL
)
-- 인덱스: session_id, created_at
```

---

**`tasks` 테이블**

```sql
CREATE TABLE tasks (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    description TEXT,
    status      TEXT NOT NULL DEFAULT 'pending',  -- 'pending' | 'done' | 'cancelled'
    priority    TEXT NOT NULL DEFAULT 'normal',
    source_session_id TEXT,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
)
```

---

**`knowledge_notes` 테이블**

```sql
CREATE TABLE knowledge_notes (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    content     TEXT NOT NULL,
    source_type TEXT NOT NULL,   -- 'manual' | 'image_summary' | 'chat_extract'
    tags        TEXT,            -- JSON 배열 문자열
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
)
-- 인덱스: created_at, source_type
```

---

**`audit_events` 테이블**

```sql
CREATE TABLE audit_events (
    id              TEXT PRIMARY KEY,
    event_type      TEXT NOT NULL,
    -- 'model_run' | 'tool_call' | 'approval_granted' |
    -- 'approval_rejected' | 'search_used' | 'export' | 'import' | 'error'
    event_detail    TEXT,            -- 민감 본문은 Redaction 처리
    approval_status TEXT,            -- 'granted' | 'rejected' | null
    network_used    INTEGER NOT NULL DEFAULT 0,
    error_code      TEXT,
    session_id      TEXT,
    created_at      INTEGER NOT NULL
)
-- 인덱스: event_type, created_at
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
```

### 8-4. Export Manifest 구조

```kotlin
@Serializable
data class ExportManifest(
    val version: String = "1.0",
    val appVersion: String,
    val dbSchemaVersion: Int,
    val createdAt: String,          // ISO 8601
    val encrypted: Boolean = false,
    val includes: List<String>      // ["profiles", "conversations", ...]
)
```

### 8-5. Room Migration 전략

```
개발 단계 (version = 1 유지):
  └─ fallbackToDestructiveMigration() 허용
     (데이터 손실 허용, 개발 편의 우선)

실사용 시작 이후:
  ├─ 컬럼 추가 / 테이블 추가: @AutoMigration 사용
  ├─ 테이블명 변경 / 컬럼 삭제: AutoMigrationSpec + 수동 Migration
  └─ fallbackToDestructiveMigration() 제거

공통 규칙:
  - 스키마 변경 시 반드시 version +1
  - exportSchema = true 유지 (schemas/ 폴더 Git 커밋)
  - 모든 Migration은 MigrationManager.kt 한 곳에서 관리
```

### 8-6. 모델 파일 경로 규칙

```
기본 경로:
  getExternalFilesDir("models")
  → /sdcard/Android/data/com.localfriday.app/files/models/

파일명 규칙:
  {model_id}-{quantization}.litertlm
  예: gemma4-e4b-it-q4.litertlm

선택 이유:
  - 추가 권한 없이 접근 가능
  - Scoped Storage 정책 준수
  - 앱 삭제 시 자동 정리
  - 파일 관리자로 직접 sideload 가능
```

---

## 9. API Architecture

### 9-1. 외부 API 없음 원칙

이 프로젝트는 MVP 범위에서 **외부 REST API를 사용하지 않는다.**
모든 기능은 로컬 함수 호출로 처리된다.

### 9-2. 내부 인터페이스 계약

외부 API 대신 레이어 간 **인터페이스 계약**으로 통신한다.

**ModelRunner 계약**

```kotlin
interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>
    suspend fun generate(prompt: String, onToken: ((String) -> Unit)? = null): Result<String, AppError>
    suspend fun generateWithImage(prompt: String, imageBytes: ByteArray): Result<String, AppError>
    suspend fun cancel()
    suspend fun warmUp()
}
```

**CalendarTool 계약**

```kotlin
interface CalendarTool {
    suspend fun readEvents(startMs: Long, endMs: Long): Result<List<CalendarEvent>, AppError>
    suspend fun insert(draft: CalendarDraft): Result<Long, AppError>  // Long = event ID
}
```

**SpeechToTextTool 계약**

```kotlin
interface SpeechToTextTool {
    val state: StateFlow<SttState>       // Idle / Listening / Transcribing / Done / Error
    fun start()
    fun stop()
    fun cancel()
}
```

**WebSearchTool 계약**

```kotlin
interface WebSearchTool {
    // MVP: Stub 구현 (항상 빈 결과 반환)
    // v1: 실제 검색 엔진 연결
    suspend fun search(request: SearchRequest): Result<List<SearchResult>, AppError>
}
```

### 9-3. Fake / Stub 구현 정책

AI agent가 상위 레이어 구현 시 하위 레이어 Fake를 먼저 만든다.

| 인터페이스 | Fake 파일 위치 | 생성 시점 |
|---|---|---|
| `ModelRunner` | `runtime/gemma/FakeModelRunner.kt` | Phase 2 시작 시 필수 |
| `CalendarTool` | `platform/calendar/FakeCalendarTool.kt` | Phase 8 시작 시 |
| `WebSearchTool` | `platform/network/StubWebSearchGateway.kt` | Phase 12 시작 시 |

---

## 10. Authentication Strategy

### MVP 인증 방식: 없음

MVP는 단일 기기 / 단일 사용자 구조이므로
JWT / OAuth / 생체인증 등 별도 인증 메커니즘을 사용하지 않는다.

```
접근 제어 방식:
  - 앱 자체가 기기 잠금으로 보호됨
  - 로컬 프로필은 앱 설치 시 자동 생성
  - 다중 사용자 지원 없음
  - export 파일은 파일 시스템 권한으로 보호
```

### 향후 확장 (v2)

Windows 확장 시 사용자 식별은
export 파일의 profile 정보 기반 import로 처리한다.
이 단계에서 선택적 비밀번호 기반 export 암호화(AES-256-GCM)를 도입한다.

---

## 11. Error Handling Strategy

### 11-1. `Result<T, AppError>` 래퍼

모든 비동기 작업의 반환 타입은 `Result<T, AppError>`를 사용한다.
Exception을 직접 throw하지 않는다.

```kotlin
sealed class Result<out T, out E> {
    data class Success<T>(val data: T) : Result<T, Nothing>()
    data class Failure<E>(val error: E) : Result<Nothing, E>()
}

sealed class AppError {
    // 모델 관련
    data class ModelNotFound(val path: String) : AppError()
    data class ModelLoadFailed(val reason: String) : AppError()
    data class ModelInferenceTimeout(val durationMs: Long) : AppError()
    data class ModelInferenceError(val reason: String) : AppError()

    // DB 관련
    data class DbWriteError(val table: String) : AppError()
    data class DbMigrationError(val fromVersion: Int, val toVersion: Int) : AppError()

    // 권한 관련
    data class PermissionDenied(val permission: String) : AppError()

    // 플랫폼 관련
    data class CalendarReadError(val reason: String) : AppError()
    data class CalendarWriteError(val reason: String) : AppError()
    data class SttError(val reason: String) : AppError()

    // 입력 관련
    data class ValidationError(val field: String, val reason: String) : AppError()
    data class ImageTooLarge(val sizeBytes: Long) : AppError()
    data class UnsupportedImageFormat(val mimeType: String) : AppError()

    // 네트워크 관련
    data class SearchError(val reason: String) : AppError()
    data class NetworkUnavailable(val reason: String) : AppError()

    // Export/Import 관련
    data class ExportFailed(val reason: String) : AppError()
    data class ImportManifestMismatch(val expected: String, val actual: String) : AppError()
    data class ImportSchemaMismatch(val reason: String) : AppError()
    data class InsufficientStorage(val requiredBytes: Long) : AppError()
}
```

### 11-2. 레이어별 에러 처리 원칙

| 레이어 | 처리 원칙 |
|---|---|
| **Presentation** | `AppError`를 사용자 친화적 메시지로 변환하여 표시 |
| **ViewModel** | `Result` 분기 처리, UiState.Error로 변환 |
| **UseCase** | `Result`를 그대로 상위로 전달 (변환하지 않음) |
| **Orchestrator** | `Result` 분기 후 fallback 처리 (예: 검색 실패 시 로컬 응답 유지) |
| **Repository / Tool** | try-catch 후 `Result.Failure(AppError)` 반환 |

### 11-3. 주요 에러 fallback 정책

| 에러 상황 | fallback 처리 |
|---|---|
| 검색 실패 | 로컬 응답만 유지 + '웹 검색 보강 실패' 안내 |
| JSON 파싱 실패 | TextResponse로 fallback |
| 모델 추론 timeout | 오류 메시지 + 재시도 버튼 |
| STT 실패 | 텍스트 직접 입력 유도 |
| 캘린더 쓰기 실패 | 오류 메시지 + Audit 기록 |
| DB 쓰기 실패 | 오류 기록 + 사용자 안내 (데이터 유실 가능성 없음) |

---

## 12. Logging Strategy

### 12-1. 로그 분류

| 분류 | 도구 | 저장 위치 | 목적 |
|---|---|---|---|
| **개발 로그** | `AppLogger` (Timber 래퍼) | 메모리 / Logcat | 디버깅 |
| **감사 로그** | `AuditTrailService` | `audit_events` 테이블 | 실행 이력 추적 |
| **성능 로그** | `RuntimeMetricsCollector` | `audit_events` + 메모리 | 발열 / 추론 시간 모니터링 |

### 12-2. 감사 로그 기록 기준

아래 이벤트는 **반드시** `audit_events`에 기록한다.

```kotlin
enum class AuditEventType {
    MODEL_RUN,          // 모델 추론 실행
    TOOL_CALL,          // Android API 호출 (Calendar, STT 등)
    APPROVAL_GRANTED,   // 사용자 승인
    APPROVAL_REJECTED,  // 사용자 거부
    SEARCH_USED,        // 웹 검색 실행 (네트워크 사용 이력)
    EXPORT,             // 메모리 export
    IMPORT,             // 메모리 import
    THERMAL_WARNING,    // 발열 경고
    THERMAL_SHUTDOWN,   // 발열로 인한 추론 중단
    ERROR               // 오류 발생
}
```

### 12-3. 민감 정보 처리

```kotlin
// Redaction.kt — 감사 로그 저장 전 처리
object Redaction {
    fun sanitize(text: String): String {
        // 기본: 전문 저장 (단일 사용자이므로 허용)
        // 설정에서 "민감 본문 저장" 비활성화 시:
        // 100자 초과 텍스트를 "[내용 생략 - {length}자]"로 대체
        return if (isRedactionEnabled) truncate(text) else text
    }
}
```

### 12-4. 성능 로그 수집 항목

```kotlin
data class InferenceMetrics(
    val promptTokens: Int,
    val responseTokens: Int,
    val timeToFirstTokenMs: Long,
    val totalInferenceMs: Long,
    val deviceTemperatureCelsius: Float,
    val consecutiveInferenceCount: Int
)
```

---

## 13. Scalability Boundaries

### 13-1. MVP에서의 의도적 제한

아래 항목은 MVP에서 **의도적으로 단순화**한 부분이다.
이 경계를 인식하고 구현해야 향후 확장이 용이하다.

| 항목 | MVP 제한 | 확장 포인트 |
|---|---|---|
| 컨텍스트 길이 | 4096 tokens | `Constants.MAX_CONTEXT_TOKENS` 값 변경 |
| 대화 히스토리 | 최근 5턴 | `Constants.MAX_CONVERSATION_TURNS` 값 변경 |
| 메모리 검색 | 최신순 / 태그 기반 | `KnowledgeRepository`에 벡터 검색 메서드 추가 |
| 검색 구현 | Stub (빈 결과) | `WebSearchTool` 구현체 교체 |
| 모델 | Gemma 4 E4B-it | `ModelRunner` 구현체 교체 |
| 페이지네이션 | LazyColumn 단순 로드 | `PagingSource` 전환 (데이터 100건 이상 시) |
| 스트리밍 응답 | 비스트리밍 기본 | `ModelRunner.generate(onToken)` 콜백 활성화 |

### 13-2. 레이어별 확장 방법

**모델 교체**
```
GemmaModelRunner → {새모델}ModelRunner
ModelRunner 인터페이스 유지
ModelModule.kt 바인딩만 변경
```

**새로운 Tool 추가**
```
1. domain/tool/에 인터페이스 정의
2. platform/에 Android 구현체 추가
3. PlatformModule.kt에 바인딩 추가
4. TaskRouter에 라우팅 규칙 추가
```

**새로운 Agent 추가**
```
1. domain/agent/Agent.kt 구현
2. assistant/agent/에 구현체 추가
3. AgentModule.kt에 바인딩 추가
4. TaskRouter에 라우팅 규칙 추가
```

### 13-3. 데이터 규모 예측

| 테이블 | 월 예상 행수 | 1년 예상 크기 |
|---|---|---|
| conversations | ~3,000행 | ~5MB |
| knowledge_notes | ~100행 | ~1MB |
| audit_events | ~5,000행 | ~3MB |
| tasks | ~50행 | <1MB |
| profiles | 1행 | <1KB |

> 단일 사용자 1년 기준 총 DB 크기 ~10MB 예상.
> Paging 3 도입 없이도 1년간 성능 이슈 없을 것으로 예측.
> 다만 `ConversationDao`에는 `created_at` 인덱스를 반드시 추가한다.

---

## 14. Future Expansion Notes

### 14-1. v1 확장 항목

| 항목 | 준비된 구조 |
|---|---|
| 웹 검색 실제 구현 | `WebSearchTool` 인터페이스 교체만 필요 |
| 스트리밍 응답 | `ModelRunner.generate(onToken)` 콜백 활성화 |
| Export 암호화 | `ExportImportManager`에 AES-256-GCM 레이어 추가 |
| Paging 3 전환 | DAO를 `PagingSource` 반환으로 변경 |

### 14-2. v2 확장 항목 (Windows)

```
현재 구조에서 변경 없이 활용 가능한 부분:
  - domain/model/ — 공통 도메인 모델 재사용
  - domain/usecase/ — 비즈니스 로직 재사용
  - data/local/ — SQLite 스키마 공유 (export/import로 이동)

새로 구현이 필요한 부분:
  - Windows용 ModelRunner (Gemma 4 26B-A4B / llama.cpp)
  - Windows UI (Compose Desktop 또는 별도)
  - Windows용 Platform Tool 구현체
```

### 14-3. 벡터 RAG 확장 경로

```
현재: KnowledgeRepository — 최신순 / 태그 기반 검색
           │
           ▼ (v2 이후)
향후: KnowledgeRepository — 벡터 유사도 검색 메서드 추가
      + VectorIndexStore (로컬 벡터 인덱스 파일)
      + EmbeddingModelRunner (임베딩 모델 별도)

변경 범위:
  - KnowledgeRepository 인터페이스에 메서드 추가
  - KnowledgeRepositoryImpl 구현 확장
  - ContextBuilder가 벡터 검색 결과 활용하도록 수정
  - 기존 태그 기반 검색은 fallback으로 유지
```

### 14-4. 과잉 설계 경고

> ⚠️ **MVP에서 구현하지 말아야 할 것들**
>
> 아래 항목은 설계는 되어 있으나 MVP에서 실제 구현하면 안 된다.
> TODO 주석만 남기고 구현을 건너뛴다.
>
> - `assistant/harness/` — MVP에서 불필요. Orchestrator만으로 충분.
> - `assistant/planner/` — 복잡한 멀티스텝 계획 수립. MVP 과잉 설계.
> - `assistant/session/` — 세션 관리 고도화. ConversationRepository로 충분.
> - `runtime/benchmark/` — 성능 벤치마크. RuntimeMetricsCollector로 충분.
> - Vector DB / 임베딩 모델 — v2 이후.
> - 멀티모듈 Gradle 분리 — 현재 규모에서 불필요.
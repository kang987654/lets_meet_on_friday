# tasks.md — Local Friday
## AI Agent Task Breakdown

**문서 버전**: 1.0
**기준 문서**: PRD.md v1.0 / architecture.md v1.0 / api_spec.yaml v1.0
**작업 단위 기준**: AI agent 1회 컨텍스트에서 완결 가능한 단위
**총 태스크 수**: 71개

---

## 목차

- [Phase 0. 프로젝트 초기 세팅](#phase-0-프로젝트-초기-세팅)
- [Phase 1. Core 공통 타입](#phase-1-core-공통-타입)
- [Phase 2. Domain 모델 정의](#phase-2-domain-모델-정의)
- [Phase 3. Domain 인터페이스 정의](#phase-3-domain-인터페이스-정의)
- [Phase 4. 로컬 모델 런타임](#phase-4-로컬-모델-런타임)
- [Phase 5. 데이터 레이어 — DB](#phase-5-데이터-레이어--db)
- [Phase 6. 데이터 레이어 — Repository 구현](#phase-6-데이터-레이어--repository-구현)
- [Phase 7. Assistant Core — Context](#phase-7-assistant-core--context)
- [Phase 8. Assistant Core — Orchestration](#phase-8-assistant-core--orchestration)
- [Phase 9. 채팅 Feature](#phase-9-채팅-feature)
- [Phase 10. 음성 입력 Feature](#phase-10-음성-입력-feature)
- [Phase 11. 이미지 입력 Feature](#phase-11-이미지-입력-feature)
- [Phase 12. 캘린더 Feature](#phase-12-캘린더-feature)
- [Phase 13. 승인 플로우 Feature](#phase-13-승인-플로우-feature)
- [Phase 14. 메모리 관리 Feature](#phase-14-메모리-관리-feature)
- [Phase 15. 설정 Feature](#phase-15-설정-feature)
- [Phase 16. Export / Import](#phase-16-export--import)
- [Phase 17. 웹 검색](#phase-17-웹-검색)
- [Phase 18. 공유 인텐트](#phase-18-공유-인텐트)
- [Phase 19. 운영 품질](#phase-19-운영-품질)

---

## Task 상태 표기

| 기호 | 의미 |
|---|---|
| `[ ]` | 미완료 |
| `[x]` | 완료 |
| `P0` | MVP 핵심 — 최우선 |
| `P1` | MVP 포함 — P0 이후 |
| `P2` | v1 범위 |

---

## Phase 0. 프로젝트 초기 세팅

---

### TASK-001 `P0`
**Android 프로젝트 생성 및 기본 Gradle 설정**

| 항목 | 내용 |
|---|---|
| **Goal** | Android 프로젝트를 생성하고 핵심 라이브러리 의존성을 추가한다 |
| **Scope** | Gradle 설정 파일만. 소스 코드 작성 없음 |
| **Files to Create/Modify** | `build.gradle.kts (project)` `build.gradle.kts (app)` `gradle/libs.versions.toml` |
| **Dependencies** | 없음 |

**추가할 의존성 목록**
```toml
[versions]
kotlin = "2.0.0"
compose-bom = "2025.05.00"
hilt = "2.52"
ksp = "2.0.0-1.0.21"
room = "2.6.1"
datastore = "1.1.1"
navigation = "2.7.7"
serialization = "1.6.3"
paging = "3.3.0"
coroutines = "1.8.1"
```

**Done Criteria**
- [ ] `./gradlew build` 성공
- [ ] 모든 의존성 다운로드 완료
- [ ] KSP 플러그인 적용 확인

**Test Criteria**
- Gradle sync 오류 없음

---

### TASK-002 `P0`
**패키지 구조 및 빈 디렉토리 생성**

| 항목 | 내용 |
|---|---|
| **Goal** | `architecture.md` Directory Structure에 정의된 패키지 골격을 생성한다 |
| **Scope** | 디렉토리 구조와 패키지 선언만. 실제 구현 없음 |
| **Files to Create** | 각 패키지의 `.gitkeep` 또는 패키지 수준 빈 파일 |
| **Dependencies** | TASK-001 |

**생성할 최상위 패키지**
```
com.localfriday.app/
├── app/di/
├── core/common/ core/config/ core/logging/ core/security/
├── domain/model/ domain/memory/ domain/modelrunner/
│   domain/agent/ domain/tool/ domain/policy/ domain/usecase/
├── assistant/orchestrator/ assistant/context/ assistant/approval/
│   assistant/guard/ assistant/audit/ assistant/persona/ assistant/agent/
├── data/local/db/dao/ data/local/db/entity/
│   data/local/prefs/ data/local/file/ data/local/repository/
├── runtime/gemma/ runtime/multimodal/ runtime/metrics/
├── platform/calendar/ platform/speech/ platform/share/
│   platform/storage/ platform/network/ platform/permissions/
├── feature/chat/ feature/voice/ feature/calendar/
│   feature/approval/ feature/memory/ feature/settings/
└── navigation/
```

**Done Criteria**
- [ ] 모든 패키지 디렉토리 존재
- [ ] `architecture.md` 구조와 일치

**Test Criteria**
- 디렉토리 구조 육안 확인

---

### TASK-003 `P0`
**Hilt Application 클래스 및 MainActivity 생성**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 진입점을 구성하고 Hilt DI를 초기화한다 |
| **Scope** | `LocalFridayApp.kt`, `MainActivity.kt` 생성. DI 모듈 빈 파일 생성 |
| **Files to Create** | `app/LocalFridayApp.kt` `app/MainActivity.kt` `app/di/AppModule.kt` `app/di/ModelModule.kt` `app/di/MemoryModule.kt` `app/di/AgentModule.kt` `app/di/PlatformModule.kt` |
| **Dependencies** | TASK-001, TASK-002 |

**구현 내용**
```kotlin
// LocalFridayApp.kt
@HiltAndroidApp
class LocalFridayApp : Application()

// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(...) {
        super.onCreate(savedInstanceState)
        setContent { LocalFridayTheme { /* NavHost placeholder */ } }
    }
}
```

**Done Criteria**
- [ ] `@HiltAndroidApp` 적용 확인
- [ ] 앱 실행 시 크래시 없음
- [ ] 빈 DI 모듈 파일 5개 생성

**Test Criteria**
- 에뮬레이터에서 빈 화면 실행 확인

---

### TASK-004 `P0`
**Navigation 및 AppDestination 설정**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 내 화면 이동 구조를 정의한다 |
| **Scope** | NavHost, Destination 정의. 실제 화면 Composable은 placeholder |
| **Files to Create** | `navigation/AppDestination.kt` `navigation/AppNavHost.kt` |
| **Dependencies** | TASK-003 |

**구현 내용**
```kotlin
// AppDestination.kt
sealed class AppDestination(val route: String) {
    object Chat : AppDestination("chat")
    object Calendar : AppDestination("calendar")
    object Memory : AppDestination("memory")
    object Settings : AppDestination("settings")
}

// AppNavHost.kt — 각 화면은 Text("준비 중") placeholder
```

**Done Criteria**
- [ ] 하단 탭 4개 (채팅/일정/메모리/설정) 전환 가능
- [ ] 뒤로가기 정상 동작

**Test Criteria**
- 탭 전환 시 크래시 없음

---

## Phase 1. Core 공통 타입

---

### TASK-005 `P0`
**Result / AppError 공통 타입 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 모든 레이어에서 사용할 Result 래퍼와 AppError 타입을 정의한다 |
| **Scope** | `core/common/` 하위 3개 파일. Android 의존성 없음 |
| **Files to Create** | `core/common/Result.kt` `core/common/AppError.kt` `core/common/Constants.kt` |
| **Dependencies** | TASK-002 |

**구현 내용 — Result.kt**
```kotlin
sealed class Result<out T, out E> {
    data class Success<T>(val data: T) : Result<T, Nothing>()
    data class Failure<E>(val error: E) : Result<Nothing, E>()
}

inline fun <T, E> Result<T, E>.onSuccess(block: (T) -> Unit): Result<T, E> {
    if (this is Result.Success) block(data)
    return this
}

inline fun <T, E> Result<T, E>.onFailure(block: (E) -> Unit): Result<T, E> {
    if (this is Result.Failure) block(error)
    return this
}
```

**구현 내용 — AppError.kt**
```kotlin
sealed class AppError {
    // api_spec.yaml ErrorCode 전체 매핑
    data class ModelNotFound(val path: String) : AppError()
    data class ModelNotReady(val reason: String) : AppError()
    data class ModelInferenceTimeout(val durationMs: Long) : AppError()
    data class ModelInferenceError(val reason: String) : AppError()
    data class DbWriteError(val table: String) : AppError()
    data class DbMigrationError(val from: Int, val to: Int) : AppError()
    data class PermissionDenied(val permission: String) : AppError()
    data class CalendarReadError(val reason: String) : AppError()
    data class CalendarWriteError(val reason: String) : AppError()
    data class SttError(val reason: String) : AppError()
    data class ValidationError(val field: String, val reason: String) : AppError()
    data class ImageTooLarge(val sizeBytes: Long) : AppError()
    data class UnsupportedImageFormat(val mimeType: String) : AppError()
    data class SearchError(val reason: String) : AppError()
    data class NetworkUnavailable(val reason: String) : AppError()
    data class ExportFailed(val reason: String) : AppError()
    data class ImportManifestMismatch(val expected: String, val actual: String) : AppError()
    data class ImportSchemaMismatch(val reason: String) : AppError()
    data class InsufficientStorage(val requiredBytes: Long) : AppError()
    data class InFlightConflict(val reason: String) : AppError()
    data class DuplicateEvent(val reason: String) : AppError()
}
```

**구현 내용 — Constants.kt**
```kotlin
object Constants {
    const val MAX_CONTEXT_TOKENS = 4096
    const val MAX_CONVERSATION_TURNS = 5
    const val MAX_KNOWLEDGE_CONTEXT_ITEMS = 3
    const val MAX_INPUT_TOKENS = 2048
    const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
    const val MAX_IMAGE_DIMENSION_PX = 1024
    const val THERMAL_WARNING_CELSIUS = 43f
    const val THERMAL_SHUTDOWN_CELSIUS = 48f
    const val THERMAL_COOLDOWN_INFERENCE_COUNT = 5
    const val MODEL_DIR_NAME = "models"
    const val DEFAULT_MODEL_FILENAME = "gemma4-e4b-it-q4.litertlm"
    const val CALENDAR_DRAFT_MIN_CONFIDENCE = 0.7f
}
```

**Done Criteria**
- [ ] `Result<T, E>` sealed class 완성
- [ ] `AppError` 모든 케이스 정의 (api_spec.yaml ErrorCode 전체 포함)
- [ ] `Constants` 모든 상수 정의
- [ ] Android import 없음

**Test Criteria**
```kotlin
// ResultTest.kt
val success = Result.Success("data")
val failure = Result.Failure(AppError.ValidationError("field", "reason"))
assert(success is Result.Success)
assert(failure is Result.Failure)
```

---

### TASK-006 `P0`
**Config 및 FeatureFlags 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 런타임 설정과 기능 플래그를 정의한다 |
| **Scope** | `core/config/` 하위 3개 파일 |
| **Files to Create** | `core/config/AppConfig.kt` `core/config/FeatureFlags.kt` `core/config/ModelConfig.kt` |
| **Dependencies** | TASK-005 |

**구현 내용**
```kotlin
// FeatureFlags.kt
object FeatureFlags {
    const val STREAMING_RESPONSE_ENABLED = false  // MVP: 비스트리밍
    const val VECTOR_SEARCH_ENABLED = false        // v2
    const val EXPORT_ENCRYPTION_ENABLED = false    // v1 검토
    const val AUDIO_INPUT_TO_MODEL_ENABLED = false // v1: STT 경유 사용
}

// ModelConfig.kt
data class ModelConfig(
    val modelId: String = "gemma4-e4b-it",
    val modelFileName: String = Constants.DEFAULT_MODEL_FILENAME,
    val quantization: String = "INT4",
    val maxContextTokens: Int = Constants.MAX_CONTEXT_TOKENS,
    val maxConversationTurns: Int = Constants.MAX_CONVERSATION_TURNS
)
```

**Done Criteria**
- [ ] `FeatureFlags` MVP 관련 플래그 정의
- [ ] `ModelConfig` 기본값 설정

**Test Criteria**
- 컴파일 성공

---

### TASK-007 `P0`
**Logger 및 AuditLogger 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 로그와 감사 로그 인터페이스를 정의한다 |
| **Scope** | `core/logging/` 2개 파일 |
| **Files to Create** | `core/logging/AppLogger.kt` `core/logging/AuditLogger.kt` |
| **Dependencies** | TASK-005 |

**구현 내용**
```kotlin
// AppLogger.kt — Android Log 래퍼
object AppLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String)
}

// AuditLogger.kt — 인터페이스 (구현은 TASK-039)
interface AuditLogger {
    suspend fun log(event: AuditEvent)
}
```

**Done Criteria**
- [ ] `AppLogger` 정적 메서드 구현
- [ ] `AuditLogger` 인터페이스 정의

**Test Criteria**
- `AppLogger.d("tag", "msg")` 호출 시 Logcat 출력 확인

---

### TASK-008 `P0`
**PermissionPolicy 및 ApprovalPolicy 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 권한 정책과 승인 필요 작업을 정의한다 |
| **Scope** | `core/security/` 3개 파일. 정책 상수만 정의, 실행 로직 없음 |
| **Files to Create** | `core/security/PermissionPolicy.kt` `core/security/ApprovalPolicy.kt` `core/security/Redaction.kt` |
| **Dependencies** | TASK-005 |

**구현 내용**
```kotlin
// PermissionPolicy.kt
object PermissionPolicy {
    val CALENDAR_READ = android.Manifest.permission.READ_CALENDAR
    val CALENDAR_WRITE = android.Manifest.permission.WRITE_CALENDAR
    val MICROPHONE = android.Manifest.permission.RECORD_AUDIO
    // 읽기 자동 허용 목록
    val AUTO_ALLOWED = setOf(CALENDAR_READ)
    // 권한 필요 목록
    val REQUIRES_PERMISSION = setOf(CALENDAR_READ, CALENDAR_WRITE, MICROPHONE)
}

// ApprovalPolicy.kt
object ApprovalPolicy {
    // 반드시 사용자 승인이 필요한 액션 유형
    enum class ActionType {
        CALENDAR_WRITE,
        WEB_SEARCH
    }
    fun requiresApproval(actionType: ActionType): Boolean = true
}

// Redaction.kt
object Redaction {
    private const val MAX_DETAIL_LENGTH = 100
    fun sanitize(text: String, redactionEnabled: Boolean): String =
        if (redactionEnabled && text.length > MAX_DETAIL_LENGTH)
            "[내용 생략 - ${text.length}자]"
        else text
}
```

**Done Criteria**
- [ ] 권한 목록 정의
- [ ] 승인 필요 액션 목록 정의
- [ ] Redaction 마스킹 로직 구현

**Test Criteria**
```kotlin
assert(ApprovalPolicy.requiresApproval(ActionType.CALENDAR_WRITE))
assert(Redaction.sanitize("a".repeat(200), true).contains("생략"))
```

---

## Phase 2. Domain 모델 정의

> **AI agent 주의**: 이 Phase의 모든 파일은 `android.*` import 금지.
> `api_spec.yaml`의 DomainModel 스키마를 기준으로 생성한다.

---

### TASK-009 `P0`
**ChatMessage / AssistantResponse 도메인 모델**

| 항목 | 내용 |
|---|---|
| **Goal** | 채팅 관련 핵심 도메인 모델을 정의한다 |
| **Scope** | `domain/model/` 내 채팅 관련 3개 파일 |
| **Files to Create** | `domain/model/ChatMessage.kt` `domain/model/AssistantResponse.kt` `domain/model/InputType.kt` |
| **Dependencies** | TASK-005 |

**구현 내용 — api_spec.yaml ChatMessage / AssistantResponse 스키마 기준**
```kotlin
// InputType.kt
enum class InputType { TEXT, VOICE, IMAGE }

// ChatMessage.kt
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: Role,
    val content: String,
    val inputType: InputType,
    val searchUsed: Boolean = false,
    val createdAt: Long
) {
    enum class Role { USER, ASSISTANT }
}

// AssistantResponse.kt
data class AssistantResponse(
    val responseId: String,
    val content: String,
    val responseType: ResponseType,
    val actionCard: ActionCard? = null,
    val searchUsed: Boolean = false,
    val searchAugmented: Boolean = false,
    val createdAt: Long
) {
    enum class ResponseType { TEXT, CALENDAR_DRAFT, SEARCH_AUGMENTED, KNOWLEDGE_SAVE }
}

data class ActionCard(
    val actionType: ActionType,
    val payload: Any  // CalendarDraft 또는 KnowledgeNote
) {
    enum class ActionType { CALENDAR_DRAFT, KNOWLEDGE_SAVE }
}
```

**Done Criteria**
- [ ] Android import 없음
- [ ] `api_spec.yaml` 스키마와 필드 일치

**Test Criteria**
- 인스턴스 생성 컴파일 확인

---

### TASK-010 `P0`
**CalendarEvent / CalendarDraft 도메인 모델**

| 항목 | 내용 |
|---|---|
| **Goal** | 캘린더 관련 도메인 모델을 정의한다 |
| **Scope** | `domain/model/` 내 캘린더 관련 2개 파일 |
| **Files to Create** | `domain/model/CalendarEvent.kt` `domain/model/CalendarDraft.kt` |
| **Dependencies** | TASK-005 |

**구현 내용 — api_spec.yaml CalendarEvent / CalendarDraft 스키마 기준**
```kotlin
// CalendarDraft.kt
data class CalendarDraft(
    val title: String,
    val startIso: String,
    val endIso: String,
    val note: String? = null,
    val confidence: Float  // 0.0 ~ 1.0
) {
    fun isConfident(): Boolean = confidence >= Constants.CALENDAR_DRAFT_MIN_CONFIDENCE
}
```

**Done Criteria**
- [ ] `isConfident()` 헬퍼 메서드 포함
- [ ] Android import 없음

**Test Criteria**
```kotlin
val draft = CalendarDraft("제목", "...", "...", confidence = 0.8f)
assert(draft.isConfident())
val lowDraft = draft.copy(confidence = 0.6f)
assert(!lowDraft.isConfident())
```

---

### TASK-011 `P0`
**KnowledgeNote / TaskItem / AuditEvent 도메인 모델**

| 항목 | 내용 |
|---|---|
| **Goal** | 메모리 계층 관련 도메인 모델을 정의한다 |
| **Scope** | `domain/model/` 내 메모리 관련 3개 파일 |
| **Files to Create** | `domain/model/KnowledgeNote.kt` `domain/model/TaskItem.kt` `domain/model/AuditEvent.kt` |
| **Dependencies** | TASK-005 |

**구현 내용 — api_spec.yaml 스키마 기준**
```kotlin
// AuditEvent.kt
data class AuditEvent(
    val id: String,
    val eventType: AuditEventType,
    val eventDetail: String? = null,
    val approvalStatus: ApprovalStatus? = null,
    val networkUsed: Boolean = false,
    val errorCode: String? = null,
    val sessionId: String? = null,
    val createdAt: Long
) {
    enum class AuditEventType {
        MODEL_RUN, TOOL_CALL, APPROVAL_GRANTED, APPROVAL_REJECTED,
        SEARCH_USED, EXPORT, IMPORT, THERMAL_WARNING, THERMAL_SHUTDOWN, ERROR
    }
    enum class ApprovalStatus { GRANTED, REJECTED }
}
```

**Done Criteria**
- [ ] `AuditEventType` enum 전체 정의 (api_spec.yaml 기준)
- [ ] Android import 없음

**Test Criteria**
- 인스턴스 생성 컴파일 확인

---

### TASK-012 `P0`
**모델 출력 구조화 타입 정의 (ModelOutput)**

| 항목 | 내용 |
|---|---|
| **Goal** | AI 모델이 반환하는 JSON을 Kotlin sealed class로 정의한다 |
| **Scope** | `domain/model/` 내 1개 파일. ResponseParser가 이 타입으로 파싱 |
| **Files to Create** | `domain/model/ModelOutput.kt` |
| **Dependencies** | TASK-009, TASK-010, TASK-011 |

**구현 내용 — api_spec.yaml ModelOutput 스키마 기준**
```kotlin
// ModelOutput.kt — api_spec.yaml discriminator 기준
sealed class ModelOutput {
    data class TextOutput(val content: String) : ModelOutput()
    data class CalendarDraftOutput(
        val title: String,
        val startIso: String,
        val endIso: String,
        val note: String? = null,
        val confidence: Float
    ) : ModelOutput()
    data class SearchOutput(
        val query: String,
        val reason: String
    ) : ModelOutput()
    data class KnowledgeSaveOutput(
        val title: String,
        val content: String,
        val tags: List<String> = emptyList()
    ) : ModelOutput()
}
```

**Done Criteria**
- [ ] 4가지 출력 타입 정의
- [ ] api_spec.yaml action 값과 매핑 주석 포함

**Test Criteria**
- sealed class when 분기 exhaustive 확인

---

### TASK-013 `P0`
**ApprovalRequest / SearchRequest / UserProfile 도메인 모델**

| 항목 | 내용 |
|---|---|
| **Goal** | 승인/검색/프로필 관련 도메인 모델을 정의한다 |
| **Scope** | `domain/model/` 내 3개 파일 |
| **Files to Create** | `domain/model/ApprovalRequest.kt` `domain/model/SearchRequest.kt` `domain/model/UserProfile.kt` |
| **Dependencies** | TASK-010 |

**Done Criteria**
- [ ] `ApprovalRequest` — draft, approved, sessionId 필드 포함
- [ ] `SearchRequest` — query, reason 필드 포함
- [ ] `UserProfile` — name, responseStyle 필드 포함
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

## Phase 3. Domain 인터페이스 정의

---

### TASK-014 `P0`
**ModelRunner 인터페이스 및 ModelLoadState 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 추론 추상화 인터페이스를 정의한다 |
| **Scope** | `domain/modelrunner/` 2개 파일. 구현 없음 |
| **Files to Create** | `domain/modelrunner/ModelRunner.kt` `domain/modelrunner/ModelLoadState.kt` |
| **Dependencies** | TASK-005, TASK-009 |

**구현 내용 — api_spec.yaml ModelRunner 계약 기준**
```kotlin
// ModelLoadState.kt
sealed class ModelLoadState {
    object Loading : ModelLoadState()
    data class Ready(val modelInfo: ModelInfo) : ModelLoadState()
    data class Error(val error: AppError) : ModelLoadState()
    data class NotFound(val expectedPath: String) : ModelLoadState()
}

// ModelRunner.kt
interface ModelRunner {
    val loadState: StateFlow<ModelLoadState>
    suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)? = null
    ): Result<String, AppError>
    suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray
    ): Result<String, AppError>
    suspend fun cancel()
    suspend fun warmUp()
}
```

**Done Criteria**
- [ ] `ModelLoadState` sealed class 4가지 상태
- [ ] `ModelRunner` 인터페이스 메서드 완성
- [ ] Android import 없음

**Test Criteria**
- 인터페이스 컴파일 확인

---

### TASK-015 `P0`
**Repository 인터페이스 5개 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 5계층 메모리 저장소 인터페이스를 정의한다 |
| **Scope** | `domain/memory/` 5개 파일. 구현 없음 |
| **Files to Create** | `domain/memory/ProfileRepository.kt` `domain/memory/ConversationRepository.kt` `domain/memory/TaskRepository.kt` `domain/memory/KnowledgeRepository.kt` `domain/memory/AuditRepository.kt` |
| **Dependencies** | TASK-009, TASK-010, TASK-011 |

**구현 내용**
```kotlin
// ConversationRepository.kt
interface ConversationRepository {
    suspend fun save(message: ChatMessage): Result<Unit, AppError>
    suspend fun getRecentBySession(
        sessionId: String,
        limit: Int = Constants.MAX_CONVERSATION_TURNS
    ): Result<List<ChatMessage>, AppError>
    fun getPagedBySession(sessionId: String): PagingSource<Int, ChatMessage>
}

// KnowledgeRepository.kt
interface KnowledgeRepository {
    suspend fun save(note: KnowledgeNote): Result<Unit, AppError>
    suspend fun searchRecent(
        limit: Int = Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS
    ): Result<List<KnowledgeNote>, AppError>
    suspend fun searchByTags(tags: List<String>): Result<List<KnowledgeNote>, AppError>
    fun getPaged(): PagingSource<Int, KnowledgeNote>
}
// ProfileRepository, TaskRepository, AuditRepository 동일 패턴
```

**Done Criteria**
- [ ] 5개 인터페이스 모두 정의
- [ ] ConversationRepository — getRecentBySession 메서드 포함
- [ ] KnowledgeRepository — searchRecent 메서드 포함
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

### TASK-016 `P0`
**Tool 인터페이스 4개 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Android 플랫폼 도구 추상화 인터페이스를 정의한다 |
| **Scope** | `domain/tool/` 4개 파일. 구현 없음 |
| **Files to Create** | `domain/tool/CalendarTool.kt` `domain/tool/SpeechToTextTool.kt` `domain/tool/WebSearchTool.kt` `domain/tool/FileTool.kt` |
| **Dependencies** | TASK-010, TASK-013 |

**구현 내용 — api_spec.yaml Tool 계약 기준**
```kotlin
// CalendarTool.kt
interface CalendarTool {
    suspend fun readEvents(startMs: Long, endMs: Long): Result<List<CalendarEvent>, AppError>
    suspend fun insert(draft: CalendarDraft): Result<Long, AppError>
}

// SpeechToTextTool.kt
interface SpeechToTextTool {
    val state: StateFlow<SttState>
    fun start(preferOffline: Boolean = true, language: String = "ko-KR")
    fun stop()
    fun cancel()
}

enum class SttState { IDLE, LISTENING, TRANSCRIBING, DONE, ERROR }

// WebSearchTool.kt — MVP: Stub
interface WebSearchTool {
    suspend fun search(request: SearchRequest): Result<List<SearchResultItem>, AppError>
}
```

**Done Criteria**
- [ ] 4개 인터페이스 정의
- [ ] `SttState` enum 5가지 상태
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

### TASK-017 `P0`
**Agent 인터페이스 및 Policy 인터페이스 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Agent와 Policy 추상화 인터페이스를 정의한다 |
| **Scope** | `domain/agent/` `domain/policy/` 합계 4개 파일 |
| **Files to Create** | `domain/agent/Agent.kt` `domain/agent/AgentResult.kt` `domain/policy/ExecutionPolicy.kt` `domain/policy/ApprovalPolicyInterface.kt` |
| **Dependencies** | TASK-005, TASK-012 |

**구현 내용**
```kotlin
// Agent.kt
interface Agent {
    suspend fun execute(input: String, context: Map<String, Any>): AgentResult
}

// AgentResult.kt
sealed class AgentResult {
    data class Text(val content: String) : AgentResult()
    data class ActionRequired(val output: ModelOutput) : AgentResult()
    data class Error(val error: AppError) : AgentResult()
}

// ExecutionPolicy.kt
interface ExecutionPolicy {
    fun canExecute(actionType: ApprovalPolicy.ActionType): Boolean
    fun requiresApproval(actionType: ApprovalPolicy.ActionType): Boolean
}
```

**Done Criteria**
- [ ] `Agent` 인터페이스 정의
- [ ] `AgentResult` sealed class 3가지 케이스
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

## Phase 4. 로컬 모델 런타임

---

### TASK-018 `P0`
**FakeModelRunner 구현 (테스트용 Stub)**

| 항목 | 내용 |
|---|---|
| **Goal** | 실제 모델 없이 상위 레이어를 테스트하기 위한 Fake 구현을 만든다 |
| **Scope** | `runtime/gemma/FakeModelRunner.kt` 1개 파일 |
| **Files to Create** | `runtime/gemma/FakeModelRunner.kt` |
| **Dependencies** | TASK-014 |

**구현 내용**
```kotlin
class FakeModelRunner : ModelRunner {
    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Ready(
        ModelInfo("fake-model", "/fake/path", "1.0", "INT4")
    ))
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    // 고정 응답 반환 (테스트별 커스터마이징 가능)
    var fakeResponse: String = """{"action":"text","content":"테스트 응답입니다."}"""

    override suspend fun generate(prompt: String, onToken: ((String) -> Unit)?): Result<String, AppError> {
        delay(100) // 추론 시뮬레이션
        return Result.Success(fakeResponse)
    }

    override suspend fun generateWithImage(prompt: String, imageBytes: ByteArray): Result<String, AppError> =
        Result.Success(fakeResponse)

    override suspend fun cancel() {}
    override suspend fun warmUp() {}
}
```

**Done Criteria**
- [ ] `ModelRunner` 인터페이스 완전 구현
- [ ] 응답 커스터마이징 가능한 구조
- [ ] 즉시 Ready 상태 반환

**Test Criteria**
```kotlin
val fake = FakeModelRunner()
val result = fake.generate("test")
assert(result is Result.Success)
```

---

### TASK-019 `P0`
**GemmaRuntimeManager — 모델 로드/언로드 관리**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 파일 존재 확인 및 LiteRT-LM 로드/언로드를 관리한다 |
| **Scope** | `runtime/gemma/GemmaRuntimeManager.kt` 1개 파일 |
| **Files to Create** | `runtime/gemma/GemmaRuntimeManager.kt` |
| **Dependencies** | TASK-014, TASK-006 |

**구현 내용**
```kotlin
@Singleton
class GemmaRuntimeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelConfig: ModelConfig
) {
    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Loading)
    val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    suspend fun load() {
        val modelFile = File(context.getExternalFilesDir(Constants.MODEL_DIR_NAME),
                             modelConfig.modelFileName)
        if (!modelFile.exists()) {
            _loadState.value = ModelLoadState.NotFound(modelFile.absolutePath)
            return
        }
        // LiteRT-LM 로드 (실제 SDK 연결)
        // TODO: LiteRT-LM SDK API 연결
        _loadState.value = ModelLoadState.Ready(
            ModelInfo(modelConfig.modelId, modelFile.absolutePath,
                     modelConfig.modelFileName, modelConfig.quantization)
        )
    }

    fun unload() {
        // TODO: LiteRT-LM 언로드
        _loadState.value = ModelLoadState.Loading
    }
}
```

**Done Criteria**
- [ ] 모델 파일 존재 확인 로직
- [ ] NotFound 상태 처리
- [ ] `getExternalFilesDir("models")` 경로 사용

**Test Criteria**
- 모델 파일 없을 때 `ModelLoadState.NotFound` 반환 확인

---

### TASK-020 `P0`
**GemmaModelRunner — LiteRT-LM 추론 연결**

| 항목 | 내용 |
|---|---|
| **Goal** | `ModelRunner` 인터페이스의 실제 LiteRT-LM 구현을 작성한다 |
| **Scope** | `runtime/gemma/GemmaModelRunner.kt` 1개 파일 |
| **Files to Create** | `runtime/gemma/GemmaModelRunner.kt` |
| **Dependencies** | TASK-018, TASK-019 |

**구현 내용**
```kotlin
@Singleton
class GemmaModelRunner @Inject constructor(
    private val runtimeManager: GemmaRuntimeManager
) : ModelRunner {

    override val loadState: StateFlow<ModelLoadState> = runtimeManager.loadState

    override suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)?
    ): Result<String, AppError> = withContext(Dispatchers.Default) {
        if (loadState.value !is ModelLoadState.Ready) {
            return@withContext Result.Failure(AppError.ModelNotReady("모델이 준비되지 않았습니다"))
        }
        try {
            // TODO: LiteRT-LM SDK generateResponse() 연결
            // val response = litertSession.generateResponse(prompt)
            // FeatureFlags.STREAMING_RESPONSE_ENABLED 기준으로 스트리밍 분기
            Result.Success("TODO: LiteRT-LM 연결 후 실제 응답")
        } catch (e: Exception) {
            Result.Failure(AppError.ModelInferenceError(e.message ?: "Unknown"))
        }
    }

    override suspend fun generateWithImage(
        prompt: String,
        imageBytes: ByteArray
    ): Result<String, AppError> = withContext(Dispatchers.Default) {
        // TODO: LiteRT-LM multimodal 연결
        Result.Failure(AppError.ModelInferenceError("멀티모달 미구현"))
    }

    override suspend fun cancel() { /* TODO */ }
    override suspend fun warmUp() { runtimeManager.load() }
}
```

**Done Criteria**
- [ ] `ModelRunner` 인터페이스 완전 구현
- [ ] 모델 미준비 시 `ModelNotReady` 오류 반환
- [ ] Dispatchers.Default에서 추론 실행

**Test Criteria**
- `FakeModelRunner`와 동일한 인터페이스 충족 확인
- `ModelNotReady` 상태에서 Failure 반환 확인

---

### TASK-021 `P1`
**ImageInputAdapter — 이미지 전처리**

| 항목 | 내용 |
|---|---|
| **Goal** | 이미지 URI를 받아 유효성 검사 후 리사이즈하여 ByteArray를 반환한다 |
| **Scope** | `runtime/multimodal/ImageInputAdapter.kt` 1개 파일 |
| **Files to Create** | `runtime/multimodal/ImageInputAdapter.kt` |
| **Dependencies** | TASK-005 |

**구현 내용 — api_spec.yaml 이미지 제한 기준**
```kotlin
class ImageInputAdapter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun process(uri: Uri): Result<ByteArray, AppError> {
        // 1. MIME 타입 확인
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType !in listOf("image/jpeg", "image/png", "image/webp")) {
            return Result.Failure(AppError.UnsupportedImageFormat(mimeType ?: "unknown"))
        }
        // 2. 파일 크기 확인
        val size = getFileSize(uri)
        if (size > Constants.MAX_IMAGE_SIZE_BYTES) {
            return Result.Failure(AppError.ImageTooLarge(size))
        }
        // 3. 디코딩 및 리사이즈
        return try {
            val bitmap = decodeSampled(uri, Constants.MAX_IMAGE_DIMENSION_PX)
            Result.Success(bitmap.toByteArray())
        } catch (e: Exception) {
            Result.Failure(AppError.ModelInferenceError("이미지 처리 실패: ${e.message}"))
        }
    }
    // decodeSampled: BitmapFactory.Options.inSampleSize 계산 후 리사이즈
}
```

**Done Criteria**
- [ ] MIME 타입 검사 (JPEG/PNG/WEBP만 허용)
- [ ] 10MB 크기 제한
- [ ] 장축 1024px 리사이즈
- [ ] 오류별 AppError 반환

**Test Criteria**
```kotlin
// 크기 초과 이미지 → ImageTooLarge
// 미지원 형식 → UnsupportedImageFormat
// 정상 이미지 → Success(ByteArray)
```

---

## Phase 5. 데이터 레이어 — DB

---

### TASK-022 `P0`
**Room Entity 5개 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 5계층 메모리에 대응하는 Room Entity를 정의한다 |
| **Scope** | `data/local/db/entity/` 5개 파일 |
| **Files to Create** | `ProfileEntity.kt` `ConversationEntity.kt` `TaskEntity.kt` `KnowledgeEntity.kt` `AuditEntity.kt` |
| **Dependencies** | TASK-009, TASK-010, TASK-011 |

**구현 내용 — api_spec.yaml DB Entity 스키마 기준**
```kotlin
// ConversationEntity.kt
@Entity(
    tableName = "conversations",
    indices = [Index("session_id"), Index("created_at")]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val role: String,      // "user" | "assistant"
    val content: String,
    @ColumnInfo(name = "input_type") val inputType: String,
    @ColumnInfo(name = "search_used") val searchUsed: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

// KnowledgeEntity.kt
@Entity(
    tableName = "knowledge_notes",
    indices = [Index("created_at"), Index("source_type")]
)
data class KnowledgeEntity(...)

// AuditEntity.kt
@Entity(
    tableName = "audit_events",
    indices = [Index("event_type"), Index("created_at")]
)
data class AuditEntity(...)
```

**Done Criteria**
- [ ] 5개 Entity 모두 정의
- [ ] 인덱스 설정 (api_spec.yaml x-db-indices 기준)
- [ ] Primary Key는 UUID String 타입

**Test Criteria**
- Room 컴파일 타임 스키마 검증 통과

---

### TASK-023 `P0`
**Room DAO 5개 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 각 Entity에 대한 DAO 인터페이스를 정의한다 |
| **Scope** | `data/local/db/dao/` 5개 파일 |
| **Files to Create** | `ProfileDao.kt` `ConversationDao.kt` `TaskDao.kt` `KnowledgeDao.kt` `AuditDao.kt` |
| **Dependencies** | TASK-022 |

**구현 내용**
```kotlin
// ConversationDao.kt
@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE session_id = :sessionId ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentBySession(sessionId: String, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE session_id = :sessionId ORDER BY created_at DESC")
    fun getPagedBySession(sessionId: String): PagingSource<Int, ConversationEntity>
}

// AuditDao.kt
@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AuditEntity)

    @Query("SELECT * FROM audit_events ORDER BY created_at DESC")
    fun getPaged(): PagingSource<Int, AuditEntity>
}
```

**Done Criteria**
- [ ] 5개 DAO 모두 정의
- [ ] Paging 3 PagingSource 메서드 포함
- [ ] Suspend 함수 적용

**Test Criteria**
- Room 컴파일 타임 SQL 검증 통과

---

### TASK-024 `P0`
**LocalFridayDatabase 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Room DB 진입점을 생성하고 버전/AutoMigration을 설정한다 |
| **Scope** | `data/local/db/LocalFridayDatabase.kt` 1개 파일 |
| **Files to Create** | `data/local/db/LocalFridayDatabase.kt` |
| **Dependencies** | TASK-022, TASK-023 |

**구현 내용**
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
    exportSchema = true,  // schemas/ 폴더에 JSON 스키마 내보내기
    autoMigrations = []   // 버전 업 시 AutoMigration 추가
)
abstract class LocalFridayDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun conversationDao(): ConversationDao
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun auditDao(): AuditDao

    companion object {
        // fallbackToDestructiveMigration: 개발 중 허용, 실사용 전 제거
        fun buildDevelopment(context: Context): LocalFridayDatabase =
            Room.databaseBuilder(context, LocalFridayDatabase::class.java, "friday_db.sqlite")
                .fallbackToDestructiveMigration()
                .build()
    }
}
```

**Done Criteria**
- [ ] `exportSchema = true` 설정
- [ ] 5개 Entity 모두 포함
- [ ] 개발용 `fallbackToDestructiveMigration` 분리

**Test Criteria**
- DB 생성 후 테이블 존재 확인

---

### TASK-025 `P0`
**DataStore — SettingsDataStore / ModelRegistryStore**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 설정과 모델 정보를 DataStore에 저장/조회한다 |
| **Scope** | `data/local/prefs/` 2개 파일 |
| **Files to Create** | `data/local/prefs/SettingsDataStore.kt` `data/local/prefs/ModelRegistryStore.kt` |
| **Dependencies** | TASK-005 |

**구현 내용 — api_spec.yaml DataStore 키 기준**
```kotlin
// ModelRegistryStore.kt
class ModelRegistryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.createDataStore("model_registry")

    suspend fun saveModelInfo(info: ModelInfo) { /* DataStore write */ }
    suspend fun getModelInfo(): ModelInfo? { /* DataStore read */ }
    suspend fun getModelPath(): String? { /* DataStore read */ }
}

// SettingsDataStore.kt
class SettingsDataStore @Inject constructor(
    @ApplicationContext context: Context
) {
    suspend fun getResponseStyle(): String { /* default: "concise" */ }
    suspend fun saveResponseStyle(style: String) {}
    suspend fun isOnboardingCompleted(): Boolean {}
    suspend fun setOnboardingCompleted(completed: Boolean) {}
}
```

**Done Criteria**
- [ ] `ModelRegistryStore` — 모델 경로/버전 저장
- [ ] `SettingsDataStore` — 응답 스타일 저장
- [ ] Flow 기반 실시간 관찰 가능

**Test Criteria**
- 저장 후 읽기 값 일치 확인

---

## Phase 6. 데이터 레이어 — Repository 구현

---

### TASK-026 `P0`
**ProfileRepositoryImpl / ConversationRepositoryImpl 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Profile과 Conversation 메모리 저장소 구현체를 작성한다 |
| **Scope** | `data/local/repository/` 2개 파일 |
| **Files to Create** | `data/local/repository/ProfileRepositoryImpl.kt` `data/local/repository/ConversationRepositoryImpl.kt` |
| **Dependencies** | TASK-015, TASK-023, TASK-024 |

**구현 내용**
```kotlin
// ConversationRepositoryImpl.kt
class ConversationRepositoryImpl @Inject constructor(
    private val dao: ConversationDao
) : ConversationRepository {

    override suspend fun save(message: ChatMessage): Result<Unit, AppError> =
        try {
            dao.insert(message.toEntity())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(AppError.DbWriteError("conversations"))
        }

    override suspend fun getRecentBySession(
        sessionId: String,
        limit: Int
    ): Result<List<ChatMessage>, AppError> =
        try {
            val entities = dao.getRecentBySession(sessionId, limit)
            Result.Success(entities.map { it.toDomain() })
        } catch (e: Exception) {
            Result.Failure(AppError.DbWriteError("conversations"))
        }
}
```

**Done Criteria**
- [ ] Entity ↔ Domain 변환 함수 포함
- [ ] try-catch → AppError 변환
- [ ] `@Inject constructor` Hilt 주입

**Test Criteria**
```kotlin
// save 후 getRecentBySession 결과에 저장된 메시지 포함 확인
```

---

### TASK-027 `P0`
**KnowledgeRepositoryImpl / TaskRepositoryImpl / AuditRepositoryImpl 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Knowledge, Task, Audit 저장소 구현체를 작성한다 |
| **Scope** | `data/local/repository/` 3개 파일 |
| **Files to Create** | `KnowledgeRepositoryImpl.kt` `TaskRepositoryImpl.kt` `AuditRepositoryImpl.kt` |
| **Dependencies** | TASK-015, TASK-023, TASK-024 |

**구현 내용 (TASK-026 패턴 동일)**

**Done Criteria**
- [ ] `KnowledgeRepositoryImpl.searchRecent()` — created_at 역순 N개 반환
- [ ] `AuditRepositoryImpl.save()` — 모든 Audit 이벤트 타입 처리
- [ ] Entity ↔ Domain 변환 포함

**Test Criteria**
- 각 Repository save → getPaged 확인

---

### TASK-028 `P0`
**DI 모듈 바인딩 — MemoryModule / AppModule**

| 항목 | 내용 |
|---|---|
| **Goal** | Repository 인터페이스와 구현체를 Hilt에 바인딩한다 |
| **Scope** | `app/di/MemoryModule.kt` `app/di/AppModule.kt` |
| **Files to Modify** | `app/di/MemoryModule.kt` `app/di/AppModule.kt` |
| **Dependencies** | TASK-024, TASK-025, TASK-026, TASK-027 |

**구현 내용**
```kotlin
// MemoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryModule {
    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds @Singleton
    abstract fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds @Singleton
    abstract fun bindKnowledgeRepository(impl: KnowledgeRepositoryImpl): KnowledgeRepository

    @Binds @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds @Singleton
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository
}

// AppModule.kt — Room DB, DataStore 제공
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LocalFridayDatabase =
        LocalFridayDatabase.buildDevelopment(context)

    @Provides fun provideConversationDao(db: LocalFridayDatabase) = db.conversationDao()
    // 나머지 DAO 동일 패턴
}
```

**Done Criteria**
- [ ] 5개 Repository 모두 바인딩
- [ ] `@Singleton` 스코프 적용
- [ ] Hilt 컴파일 성공

**Test Criteria**
- Hilt 그래프 컴파일 오류 없음

---

## Phase 7. Assistant Core — Context

---

### TASK-029 `P0`
**ContextBuilder — 대화 + 메모리 컨텍스트 조합**

| 항목 | 내용 |
|---|---|
| **Goal** | 프롬프트 조립을 위한 컨텍스트를 구성한다 |
| **Scope** | `assistant/context/ContextBuilder.kt` 1개 파일 |
| **Files to Create** | `assistant/context/ContextBuilder.kt` |
| **Dependencies** | TASK-015, TASK-026, TASK-027 |

**구현 내용**
```kotlin
class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val knowledgeRepository: KnowledgeRepository
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val knowledgeItems: List<KnowledgeNote>,
        val sessionId: String
    )

    suspend fun build(sessionId: String): Result<Context, AppError> {
        val conversations = conversationRepository
            .getRecentBySession(sessionId, Constants.MAX_CONVERSATION_TURNS)
            .getOrElse { return Result.Failure(it) }

        val knowledge = knowledgeRepository
            .searchRecent(Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS)
            .getOrElse { emptyList() }  // 지식 없어도 계속 진행

        return Result.Success(Context(conversations, knowledge, sessionId))
    }
}
```

**Done Criteria**
- [ ] 대화 최근 5턴 로드
- [ ] 지식 최근 3개 로드
- [ ] 지식 로드 실패 시 빈 리스트로 계속 진행 (non-fatal)

**Test Criteria**
```kotlin
// FakeRepository 사용 → 대화 3개 저장 후 build() 결과 확인
```

---

### TASK-030 `P0`
**PromptAssembler — 4블록 프롬프트 조립**

| 항목 | 내용 |
|---|---|
| **Goal** | 시스템/메모리/대화/입력 4블록으로 완성된 프롬프트를 조립한다 |
| **Scope** | `assistant/context/PromptAssembler.kt` 1개 파일 |
| **Files to Create** | `assistant/context/PromptAssembler.kt` |
| **Dependencies** | TASK-029, TASK-025 |

**구현 내용 — architecture.md 프롬프트 구조 기준**
```kotlin
class PromptAssembler @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    suspend fun assemble(
        context: ContextBuilder.Context,
        userInput: String,
        searchEnabled: Boolean,
        personaName: String = "Friday"
    ): String {
        val responseStyle = settingsDataStore.getResponseStyle()
        return buildString {
            // [시스템 블록] ~300 tokens
            appendLine("[시스템]")
            appendLine("당신은 $personaName 입니다.")
            appendLine("응답 스타일: $responseStyle")
            appendLine("현재 날짜: ${LocalDate.now()}")
            appendLine("웹 검색 허용: $searchEnabled")
            appendLine("쓰기 작업 승인 필요: true")
            appendLine()

            // [메모리 블록] ~500 tokens
            if (context.knowledgeItems.isNotEmpty()) {
                appendLine("[관련 메모]")
                context.knowledgeItems.forEach { appendLine("- ${it.content}") }
                appendLine()
            }

            // [대화 블록] ~1200 tokens (FIFO 토큰 초과 제거)
            if (context.recentConversations.isNotEmpty()) {
                appendLine("[이전 대화]")
                context.recentConversations.reversed().forEach { msg ->
                    val roleLabel = if (msg.role == ChatMessage.Role.USER) "User" else "Assistant"
                    appendLine("$roleLabel: ${msg.content}")
                }
                appendLine()
            }

            // [현재 입력 블록] ~500 tokens
            appendLine("[현재 요청]")
            appendLine("User: $userInput")
            appendLine()

            // [출력 형식]
            appendLine("[응답 형식] JSON으로만 응답: {\"action\":\"text|calendar_draft|search|save_knowledge\",...}")
        }
    }
}
```

**Done Criteria**
- [ ] 4블록 구조 구현
- [ ] 검색 허용 여부 프롬프트 반영
- [ ] 대화 시간순 정렬 (최신이 아래)

**Test Criteria**
```kotlin
val prompt = assembler.assemble(fakeContext, "오늘 일정 알려줘", false)
assert(prompt.contains("[시스템]"))
assert(prompt.contains("[현재 요청]"))
```

---

### TASK-031 `P0`
**ResponseParser — 모델 출력 JSON 파싱**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 출력 JSON을 `ModelOutput` sealed class로 파싱한다 |
| **Scope** | `assistant/context/ResponseParser.kt` 1개 파일 |
| **Files to Create** | `assistant/context/ResponseParser.kt` |
| **Dependencies** | TASK-012 |

**구현 내용 — api_spec.yaml ModelOutput discriminator 기준**
```kotlin
class ResponseParser @Inject constructor() {
    fun parse(rawOutput: String): ModelOutput {
        return try {
            val json = JSONObject(rawOutput)
            when (val action = json.getString("action")) {
                "text" -> ModelOutput.TextOutput(json.getString("content"))
                "calendar_draft" -> ModelOutput.CalendarDraftOutput(
                    title = json.getString("title"),
                    startIso = json.getString("startIso"),
                    endIso = json.getString("endIso"),
                    note = json.optString("note").ifBlank { null },
                    confidence = json.getDouble("confidence").toFloat()
                )
                "search" -> ModelOutput.SearchOutput(
                    query = json.getString("query"),
                    reason = json.getString("reason")
                )
                "save_knowledge" -> ModelOutput.KnowledgeSaveOutput(
                    title = json.getString("title"),
                    content = json.getString("content"),
                    tags = json.optJSONArray("tags")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                )
                else -> ModelOutput.TextOutput(rawOutput)  // fallback
            }
        } catch (e: Exception) {
            // 파싱 실패 → TextOutput fallback
            ModelOutput.TextOutput(rawOutput)
        }
    }
}
```

**Done Criteria**
- [ ] 4가지 action 타입 파싱
- [ ] 파싱 실패 시 `TextOutput` fallback (앱 크래시 없음)
- [ ] `confidence` 필드 파싱 포함

**Test Criteria**
```kotlin
val output = parser.parse("""{"action":"calendar_draft","title":"병원","startIso":"...","endIso":"...","confidence":0.9}""")
assert(output is ModelOutput.CalendarDraftOutput)

// 잘못된 JSON → TextOutput fallback
val fallback = parser.parse("완전히 잘못된 텍스트")
assert(fallback is ModelOutput.TextOutput)
```

---

## Phase 8. Assistant Core — Orchestration

---

### TASK-032 `P0`
**PreExecutionGuard — 실행 전 정책 검사**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 실행 전 검색 허용 여부와 승인 필요 여부를 검사한다 |
| **Scope** | `assistant/guard/PreExecutionGuard.kt` 1개 파일 |
| **Files to Create** | `assistant/guard/PreExecutionGuard.kt` |
| **Dependencies** | TASK-008, TASK-017 |

**구현 내용**
```kotlin
class PreExecutionGuard @Inject constructor() {
    sealed class GuardResult {
        object Allowed : GuardResult()
        data class RequiresApproval(val actionType: ApprovalPolicy.ActionType) : GuardResult()
        data class Blocked(val reason: String) : GuardResult()
    }

    fun check(output: ModelOutput, searchEnabled: Boolean): GuardResult {
        return when (output) {
            is ModelOutput.SearchOutput -> {
                if (!searchEnabled) GuardResult.Blocked("검색이 비활성화되어 있습니다")
                else GuardResult.RequiresApproval(ApprovalPolicy.ActionType.WEB_SEARCH)
            }
            is ModelOutput.CalendarDraftOutput ->
                GuardResult.RequiresApproval(ApprovalPolicy.ActionType.CALENDAR_WRITE)
            else -> GuardResult.Allowed
        }
    }
}
```

**Done Criteria**
- [ ] 검색 비활성 시 Blocked 반환
- [ ] 캘린더 쓰기 → RequiresApproval 반환
- [ ] 텍스트/지식 저장 → Allowed 반환

**Test Criteria**
```kotlin
assert(guard.check(CalendarDraftOutput(...), false) is GuardResult.RequiresApproval)
assert(guard.check(SearchOutput(...), false) is GuardResult.Blocked)
assert(guard.check(TextOutput(""), true) is GuardResult.Allowed)
```

---

### TASK-033 `P0`
**AuditTrailService — 감사 이벤트 기록**

| 항목 | 내용 |
|---|---|
| **Goal** | 모든 주요 이벤트를 AuditRepository에 기록하는 서비스를 구현한다 |
| **Scope** | `assistant/audit/AuditTrailService.kt` 1개 파일 |
| **Files to Create** | `assistant/audit/AuditTrailService.kt` |
| **Dependencies** | TASK-007, TASK-015, TASK-027 |

**구현 내용**
```kotlin
class AuditTrailService @Inject constructor(
    private val auditRepository: AuditRepository,
    private val redaction: Redaction
) : AuditLogger {

    override suspend fun log(event: AuditEvent) {
        val sanitized = event.copy(
            eventDetail = event.eventDetail?.let {
                redaction.sanitize(it, redactionEnabled = false)
            }
        )
        auditRepository.save(sanitized)
            .onFailure { AppLogger.e("Audit", "감사 로그 저장 실패: $it") }
    }

    suspend fun logModelRun(sessionId: String, networkUsed: Boolean = false) =
        log(AuditEvent(id = UUID.randomUUID().toString(),
                      eventType = AuditEvent.AuditEventType.MODEL_RUN,
                      networkUsed = networkUsed, sessionId = sessionId,
                      createdAt = System.currentTimeMillis()))

    suspend fun logApproval(sessionId: String, approved: Boolean) =
        log(AuditEvent(id = UUID.randomUUID().toString(),
                      eventType = if (approved) AuditEvent.AuditEventType.APPROVAL_GRANTED
                                  else AuditEvent.AuditEventType.APPROVAL_REJECTED,
                      approvalStatus = if (approved) AuditEvent.ApprovalStatus.GRANTED
                                       else AuditEvent.ApprovalStatus.REJECTED,
                      networkUsed = false, sessionId = sessionId,
                      createdAt = System.currentTimeMillis()))

    // logSearchUsed, logError, logThermal 동일 패턴
}
```

**Done Criteria**
- [ ] 모든 AuditEventType에 대한 편의 메서드
- [ ] 저장 실패 시 앱 크래시 없이 로그만 출력
- [ ] `AuditLogger` 인터페이스 구현

**Test Criteria**
- `logModelRun()` 호출 후 FakeAuditRepository에 이벤트 저장 확인

---

### TASK-034 `P0`
**IntentClassifier — 입력 의도 분류**

| 항목 | 내용 |
|---|---|
| **Goal** | 사용자 입력의 의도를 분류하여 라우팅 힌트를 제공한다 |
| **Scope** | `assistant/orchestrator/IntentClassifier.kt` 1개 파일 |
| **Files to Create** | `assistant/orchestrator/IntentClassifier.kt` |
| **Dependencies** | TASK-009 |

**구현 내용**
```kotlin
class IntentClassifier @Inject constructor() {
    enum class Intent {
        CHAT,           // 일반 대화
        CALENDAR_READ,  // 일정 조회
        CALENDAR_WRITE, // 일정 생성
        KNOWLEDGE,      // 메모/문서 관련
        SEARCH          // 검색 필요
    }

    // MVP: 키워드 기반 단순 분류 (모델 기반은 v1)
    fun classify(input: String): Intent {
        val lower = input.lowercase()
        return when {
            lower.contains("일정") && (lower.contains("잡") || lower.contains("추가") ||
                lower.contains("만들") || lower.contains("생성")) -> Intent.CALENDAR_WRITE
            lower.contains("일정") || lower.contains("스케줄") ||
                lower.contains("오늘") && lower.contains("뭐") -> Intent.CALENDAR_READ
            lower.contains("메모") || lower.contains("저장") ||
                lower.contains("기억") -> Intent.KNOWLEDGE
            else -> Intent.CHAT
        }
    }
}
```

**Done Criteria**
- [ ] 5가지 Intent 분류
- [ ] 키워드 기반 MVP 구현
- [ ] 기본값 `CHAT` 반환

**Test Criteria**
```kotlin
assert(classifier.classify("내일 3시 병원 일정 잡아줘") == Intent.CALENDAR_WRITE)
assert(classifier.classify("오늘 일정 뭐야?") == Intent.CALENDAR_READ)
assert(classifier.classify("안녕") == Intent.CHAT)
```

---

### TASK-035 `P0`
**AssistantOrchestrator — 전체 흐름 총괄**

| 항목 | 내용 |
|---|---|
| **Goal** | 사용자 입력부터 응답 생성까지 전체 흐름을 조율한다 |
| **Scope** | `assistant/orchestrator/AssistantOrchestrator.kt` 1개 파일 |
| **Files to Create** | `assistant/orchestrator/AssistantOrchestrator.kt` |
| **Dependencies** | TASK-029, TASK-030, TASK-031, TASK-032, TASK-033, TASK-034 |

**구현 내용**
```kotlin
class AssistantOrchestrator @Inject constructor(
    private val contextBuilder: ContextBuilder,
    private val promptAssembler: PromptAssembler,
    private val modelRunner: ModelRunner,
    private val responseParser: ResponseParser,
    private val preExecutionGuard: PreExecutionGuard,
    private val conversationRepository: ConversationRepository,
    private val auditTrailService: AuditTrailService
) {
    suspend fun process(request: ChatRequest): Result<AssistantResponse, AppError> {
        // 1. 컨텍스트 구성
        val context = contextBuilder.build(request.sessionId)
            .getOrElse { return Result.Failure(it) }

        // 2. 프롬프트 조립
        val prompt = promptAssembler.assemble(context, request.content, request.searchEnabled)

        // 3. 모델 추론
        val rawOutput = if (request.imageBytes != null) {
            modelRunner.generateWithImage(prompt, request.imageBytes)
        } else {
            modelRunner.generate(prompt)
        }.getOrElse { return Result.Failure(it) }

        // 4. 응답 파싱
        val output = responseParser.parse(rawOutput)

        // 5. 실행 전 정책 검사
        val guardResult = preExecutionGuard.check(output, request.searchEnabled)

        // 6. Audit 기록
        auditTrailService.logModelRun(request.sessionId, networkUsed = false)

        // 7. 대화 저장
        val userMsg = ChatMessage(UUID.randomUUID().toString(), request.sessionId,
            ChatMessage.Role.USER, request.content, request.inputType, false,
            System.currentTimeMillis())
        conversationRepository.save(userMsg)

        // 8. 응답 반환
        return Result.Success(output.toAssistantResponse(guardResult))
    }
}
```

**Done Criteria**
- [ ] 8단계 흐름 완성
- [ ] 각 단계 실패 시 적절한 오류 전파
- [ ] 대화 저장 포함

**Test Criteria**
```kotlin
// FakeModelRunner, FakeRepositories 사용
val result = orchestrator.process(ChatRequest("안녕", sessionId = "test"))
assert(result is Result.Success)
```

---

### TASK-036 `P0`
**ApprovalCoordinator — 승인 플로우 제어**

| 항목 | 내용 |
|---|---|
| **Goal** | 승인이 필요한 액션의 요청/승인/거부 흐름을 제어한다 |
| **Scope** | `assistant/approval/ApprovalCoordinator.kt` 1개 파일 |
| **Files to Create** | `assistant/approval/ApprovalCoordinator.kt` |
| **Dependencies** | TASK-033, TASK-013 |

**구현 내용**
```kotlin
class ApprovalCoordinator @Inject constructor(
    private val auditTrailService: AuditTrailService
) {
    // 승인 대기 중인 요청 (1개만 허용)
    private val _pendingRequest = MutableStateFlow<ApprovalRequest?>(null)
    val pendingRequest: StateFlow<ApprovalRequest?> = _pendingRequest.asStateFlow()

    fun requestApproval(request: ApprovalRequest) {
        _pendingRequest.value = request
    }

    suspend fun handleApproval(approved: Boolean, sessionId: String) {
        val request = _pendingRequest.value ?: return
        _pendingRequest.value = null
        auditTrailService.logApproval(sessionId, approved)
    }

    fun clearPending() { _pendingRequest.value = null }
}
```

**Done Criteria**
- [ ] `pendingRequest` StateFlow 노출
- [ ] 승인/거부 시 Audit 기록
- [ ] 동시에 1개 요청만 허용

**Test Criteria**
```kotlin
coordinator.requestApproval(fakeRequest)
assert(coordinator.pendingRequest.value != null)
coordinator.handleApproval(true, "session")
assert(coordinator.pendingRequest.value == null)
```

---

## Phase 9. 채팅 Feature

---

### TASK-037 `P0`
**SendChatMessageUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 채팅 메시지 전송 비즈니스 로직을 UseCase로 구현한다 |
| **Scope** | `domain/usecase/SendChatMessageUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/SendChatMessageUseCase.kt` |
| **Dependencies** | TASK-035 |

**구현 내용 — api_spec.yaml sendChatMessage 계약 기준**
```kotlin
class SendChatMessageUseCase @Inject constructor(
    private val orchestrator: AssistantOrchestrator
) {
    data class Request(
        val content: String,
        val inputType: InputType = InputType.TEXT,
        val imageBytes: ByteArray? = null,
        val searchEnabled: Boolean = false,
        val sessionId: String
    )

    suspend operator fun invoke(request: Request): Result<AssistantResponse, AppError> {
        // 유효성 검사
        if (request.content.isBlank()) {
            return Result.Failure(AppError.ValidationError("content", "메시지를 입력해주세요"))
        }
        if (request.content.length > Constants.MAX_INPUT_TOKENS * 4) {
            return Result.Failure(AppError.ValidationError("content", "메시지가 너무 깁니다"))
        }
        return orchestrator.process(request.toChatRequest())
    }
}
```

**Done Criteria**
- [ ] 빈 입력 ValidationError 반환
- [ ] Orchestrator 위임
- [ ] operator fun invoke 패턴

**Test Criteria**
```kotlin
val result = useCase(Request("", sessionId = "test"))
assert(result is Result.Failure)
assert((result as Result.Failure).error is AppError.ValidationError)
```

---

### TASK-038 `P0`
**ChatUiState 및 ChatViewModel 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 채팅 화면의 상태 관리와 이벤트 처리를 구현한다 |
| **Scope** | `feature/chat/ChatUiState.kt` `feature/chat/ChatViewModel.kt` |
| **Files to Create** | `feature/chat/ChatUiState.kt` `feature/chat/ChatViewModel.kt` |
| **Dependencies** | TASK-037 |

**구현 내용**
```kotlin
// ChatUiState.kt
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val pendingApproval: ApprovalRequest? = null,
    val searchEnabled: Boolean = false,
    val modelState: ModelLoadState = ModelLoadState.Loading
)

// ChatViewModel.kt
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val approvalCoordinator: ApprovalCoordinator,
    private val modelRunner: ModelRunner
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var isInFlight = false
    private val sessionId = UUID.randomUUID().toString()

    fun sendMessage(content: String, imageBytes: ByteArray? = null) {
        if (isInFlight) return
        if (content.isBlank()) return
        isInFlight = true
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val request = SendChatMessageUseCase.Request(
                content = content,
                inputType = if (imageBytes != null) InputType.IMAGE else InputType.TEXT,
                imageBytes = imageBytes,
                searchEnabled = _uiState.value.searchEnabled,
                sessionId = sessionId
            )
            sendChatMessageUseCase(request)
                .onSuccess { response ->
                    _uiState.update { it.copy(isLoading = false) }
                    // searchEnabled 다음 질문 초기화
                    _uiState.update { it.copy(searchEnabled = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error) }
                }
            isInFlight = false
        }
    }

    fun toggleSearch(enabled: Boolean) {
        _uiState.update { it.copy(searchEnabled = enabled) }
    }
}
```

**Done Criteria**
- [ ] `isInFlight` 중복 전송 차단
- [ ] 검색 토글 후 다음 질문에서 자동 초기화
- [ ] 5가지 UiState 관리

**Test Criteria**
```kotlin
// 동시 전송 2회 → 두 번째는 무시 확인
// 오류 시 isLoading = false 확인
```

---

### TASK-039 `P0`
**ChatScreen Composable 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 메인 채팅 화면 UI를 구현한다 |
| **Scope** | `feature/chat/ChatScreen.kt` 1개 파일 |
| **Files to Create** | `feature/chat/ChatScreen.kt` |
| **Dependencies** | TASK-038 |

**포함 UI 요소 (PRD.md §4 기준)**
- 상태 카드 (오프라인/모델 상태/검색 상태)
- 메시지 리스트 (LazyColumn, 사용자/AI 말풍선 구분)
- 이미지 첨부 프리뷰
- 입력창 + 이미지 버튼 + 검색 토글 + 전송 버튼
- 음성 FAB (우하단)
- 로딩 인디케이터
- 오류 시 재시도 버튼

**디자인 기준**
```
메인 컬러: #5bc2e7
배경: #f7fbfd
Surface: #ffffff
```

**Done Criteria**
- [ ] 5가지 UiState 화면 표현
- [ ] 검색 토글 UI 포함
- [ ] 음성 입력 FAB 포함
- [ ] 빈 대화 empty state 표시

**Test Criteria**
- 로딩 상태 → 인디케이터 표시 확인
- 오류 상태 → 재시도 버튼 표시 확인

---

### TASK-040 `P0`
**DI 바인딩 — AgentModule / ModelModule**

| 항목 | 내용 |
|---|---|
| **Goal** | Orchestrator, UseCase, ModelRunner를 Hilt에 바인딩한다 |
| **Scope** | `app/di/AgentModule.kt` `app/di/ModelModule.kt` |
| **Files to Modify** | `app/di/AgentModule.kt` `app/di/ModelModule.kt` |
| **Dependencies** | TASK-035, TASK-036, TASK-037, TASK-020 |

**구현 내용**
```kotlin
// ModelModule.kt
@Module @InstallIn(SingletonComponent::class)
abstract class ModelModule {
    @Binds @Singleton
    abstract fun bindModelRunner(impl: GemmaModelRunner): ModelRunner
}

// AgentModule.kt
@Module @InstallIn(SingletonComponent::class)
object AgentModule {
    @Provides @Singleton
    fun provideAssistantOrchestrator(...): AssistantOrchestrator = ...
}
```

**Done Criteria**
- [ ] `ModelRunner` → `GemmaModelRunner` 바인딩
- [ ] `AuditLogger` → `AuditTrailService` 바인딩
- [ ] Hilt 그래프 컴파일 성공

**Test Criteria**
- ChatViewModel 주입 시 크래시 없음

---

## Phase 10. 음성 입력 Feature

---

### TASK-041 `P1`
**AndroidSpeechToTextTool 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Android SpeechRecognizer를 사용하는 STT Tool을 구현한다 |
| **Scope** | `platform/speech/AndroidSpeechToTextTool.kt` 1개 파일 |
| **Files to Create** | `platform/speech/AndroidSpeechToTextTool.kt` |
| **Dependencies** | TASK-016 |

**구현 내용 — api_spec.yaml SpeechToTextTool 계약 기준**
```kotlin
class AndroidSpeechToTextTool @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToTextTool {

    private val _state = MutableStateFlow<SttState>(SttState.IDLE)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var resultCallback: ((String) -> Unit)? = null

    override fun start(preferOffline: Boolean, language: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)  // 오프라인 우선 필수
        }
        _state.value = SttState.LISTENING
        // SpeechRecognizer 시작
    }

    override fun stop() { recognizer?.stopListening() }
    override fun cancel() {
        recognizer?.cancel()
        _state.value = SttState.IDLE
    }
}
```

**Done Criteria**
- [ ] `EXTRA_PREFER_OFFLINE = true` 설정
- [ ] 5가지 SttState 전환
- [ ] 실패 시 SttState.ERROR 상태 전환

**Test Criteria**
- 권한 없을 때 ERROR 상태 전환 확인

---

### TASK-042 `P1`
**ProcessVoiceInputUseCase / VoiceViewModel / VoiceOverlay 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 음성 입력 UseCase와 오버레이 UI를 구현한다 |
| **Scope** | UseCase 1개 + ViewModel 1개 + Composable 1개 |
| **Files to Create** | `domain/usecase/ProcessVoiceInputUseCase.kt` `feature/voice/VoiceViewModel.kt` `feature/voice/VoiceOverlay.kt` `feature/voice/VoiceUiState.kt` |
| **Dependencies** | TASK-037, TASK-041 |

**Done Criteria**
- [ ] STT 결과를 `SendChatMessageUseCase`로 위임
- [ ] 오버레이 상태: idle / listening / transcribing / success / error
- [ ] 취소/전송 버튼 포함
- [ ] 빈 STT 결과 처리 (전송 안 함)

**Test Criteria**
- STT 결과 → sendMessage 호출 확인

---

## Phase 11. 이미지 입력 Feature

---

### TASK-043 `P1`
**ProcessImageInputUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 이미지 입력 처리 UseCase를 구현한다 |
| **Scope** | `domain/usecase/ProcessImageInputUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/ProcessImageInputUseCase.kt` |
| **Dependencies** | TASK-021, TASK-037 |

**구현 내용 — api_spec.yaml processImageInput 계약 기준**
```kotlin
class ProcessImageInputUseCase @Inject constructor(
    private val imageInputAdapter: ImageInputAdapter,
    private val sendChatMessageUseCase: SendChatMessageUseCase
) {
    suspend operator fun invoke(
        imageUri: Uri,
        prompt: String,
        sessionId: String
    ): Result<AssistantResponse, AppError> {
        val imageBytes = imageInputAdapter.process(imageUri)
            .getOrElse { return Result.Failure(it) }

        return sendChatMessageUseCase(
            SendChatMessageUseCase.Request(
                content = prompt,
                inputType = InputType.IMAGE,
                imageBytes = imageBytes,
                sessionId = sessionId
            )
        )
    }
}
```

**Done Criteria**
- [ ] ImageInputAdapter 전처리 후 UseCase 위임
- [ ] 이미지 유효성 오류 그대로 전파

**Test Criteria**
- 10MB 초과 이미지 → `ImageTooLarge` 오류 반환 확인

---

## Phase 12. 캘린더 Feature

---

### TASK-044 `P0`
**AndroidCalendarTool 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Android Calendar Provider에서 일정을 읽고 저장한다 |
| **Scope** | `platform/calendar/AndroidCalendarTool.kt` 1개 파일 |
| **Files to Create** | `platform/calendar/AndroidCalendarTool.kt` |
| **Dependencies** | TASK-016 |

**구현 내용 — api_spec.yaml calendarReadEvents / calendarInsert 계약 기준**
```kotlin
class AndroidCalendarTool @Inject constructor(
    @ApplicationContext private val context: Context
) : CalendarTool {

    override suspend fun readEvents(startMs: Long, endMs: Long): Result<List<CalendarEvent>, AppError> =
        withContext(Dispatchers.IO) {
            try {
                val uri = CalendarContract.Events.CONTENT_URI
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DESCRIPTION
                )
                val cursor = context.contentResolver.query(uri, projection,
                    "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTEND} <= ?",
                    arrayOf(startMs.toString(), endMs.toString()),
                    "${CalendarContract.Events.DTSTART} ASC"
                )
                val events = cursor?.use { c ->
                    buildList {
                        while (c.moveToNext()) { add(c.toCalendarEvent()) }
                    }
                } ?: emptyList()
                Result.Success(events)
            } catch (e: SecurityException) {
                Result.Failure(AppError.PermissionDenied(PermissionPolicy.CALENDAR_READ))
            } catch (e: Exception) {
                Result.Failure(AppError.CalendarReadError(e.message ?: "Unknown"))
            }
        }

    override suspend fun insert(draft: CalendarDraft): Result<Long, AppError> =
        withContext(Dispatchers.IO) {
            // ContentValues 구성 및 insert
            // 성공 시 생성된 이벤트 ID 반환
        }
}
```

**Done Criteria**
- [ ] `readEvents` — 기간별 일정 조회
- [ ] `insert` — CalendarDraft → ContentValues 변환 후 저장
- [ ] SecurityException → PermissionDenied 오류 변환
- [ ] Dispatchers.IO 사용

**Test Criteria**
- 권한 없을 때 `PermissionDenied` 반환 확인

---

### TASK-045 `P0`
**GetTodayScheduleUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 오늘/주간 일정을 조회하고 AI 요약을 포함하여 반환한다 |
| **Scope** | `domain/usecase/GetTodayScheduleUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/GetTodayScheduleUseCase.kt` |
| **Dependencies** | TASK-044, TASK-014 |

**구현 내용 — api_spec.yaml getTodaySchedule 계약 기준**
```kotlin
class GetTodayScheduleUseCase @Inject constructor(
    private val calendarTool: CalendarTool,
    private val modelRunner: ModelRunner
) {
    enum class RangeType { TODAY, WEEK }

    suspend operator fun invoke(
        rangeType: RangeType = RangeType.TODAY,
        includeSummary: Boolean = true
    ): Result<ScheduleData, AppError> {
        val (startMs, endMs) = rangeType.toMillisRange()
        val events = calendarTool.readEvents(startMs, endMs)
            .getOrElse { return Result.Failure(it) }

        val summary = if (includeSummary && events.isNotEmpty()) {
            modelRunner.generate("다음 일정을 간결하게 요약해줘: ${events.toPromptString()}")
                .getOrNull()
        } else null

        return Result.Success(ScheduleData(events, summary, rangeType))
    }
}
```

**Done Criteria**
- [ ] TODAY / WEEK 범위 지원
- [ ] 빈 일정 시 empty 반환 (오류 아님)
- [ ] AI 요약 실패 시 요약 없이 일정만 반환

**Test Criteria**
- 빈 캘린더 → `events = emptyList()`, 오류 없음 확인

---

### TASK-046 `P0`
**CreateCalendarDraftUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 자연어 일정 요청을 CalendarDraft로 변환한다 |
| **Scope** | `domain/usecase/CreateCalendarDraftUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/CreateCalendarDraftUseCase.kt` |
| **Dependencies** | TASK-031, TASK-014 |

**Done Criteria**
- [ ] 모델 출력 `CalendarDraftOutput` 파싱 및 `CalendarDraft` 변환
- [ ] `confidence < 0.7` → `isConfident = false` 플래그
- [ ] 시간 정보 누락 시 `ValidationError` 반환
- [ ] 저장 동작 없음 (초안만 반환)

**Test Criteria**
```kotlin
// FakeModelRunner가 CalendarDraftOutput JSON 반환 시 Success 확인
// confidence 0.6 → isConfident = false 확인
```

---

### TASK-047 `P0`
**ApproveAndSaveEventUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 사용자 승인 후 캘린더에 일정을 저장하고 Audit을 기록한다 |
| **Scope** | `domain/usecase/ApproveAndSaveEventUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/ApproveAndSaveEventUseCase.kt` |
| **Dependencies** | TASK-044, TASK-033 |

**Done Criteria**
- [ ] 승인 시 `CalendarTool.insert()` 실행
- [ ] 거부 시 저장 없이 Audit만 기록
- [ ] 결과와 무관하게 Audit 항상 기록

**Test Criteria**
- 승인 → calendarTool.insert 호출 확인
- 거부 → calendarTool.insert 미호출 확인

---

### TASK-048 `P0`
**CalendarUiState / CalendarViewModel / CalendarScreen 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 일정 화면 UI와 상태 관리를 구현한다 |
| **Scope** | `feature/calendar/` 3개 파일 |
| **Files to Create** | `feature/calendar/CalendarUiState.kt` `feature/calendar/CalendarViewModel.kt` `feature/calendar/CalendarScreen.kt` |
| **Dependencies** | TASK-045, TASK-046 |

**Done Criteria**
- [ ] 오늘/이번 주/초안 세그먼트 탭
- [ ] 권한 거부 시 안내 카드 표시
- [ ] empty state 표시

**Test Criteria**
- 캘린더 권한 없을 때 안내 카드 표시 확인

---

## Phase 13. 승인 플로우 Feature

---

### TASK-049 `P0`
**ApprovalUiState / ApprovalViewModel / ApprovalSheet 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 승인 바텀시트 UI와 상태 관리를 구현한다 |
| **Scope** | `feature/approval/` 3개 파일 |
| **Files to Create** | `feature/approval/ApprovalUiState.kt` `feature/approval/ApprovalViewModel.kt` `feature/approval/ApprovalSheet.kt` |
| **Dependencies** | TASK-036, TASK-047 |

**구현 내용**
```kotlin
// ApprovalSheet.kt
@Composable
fun ApprovalSheet(
    draft: CalendarDraft,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    BottomSheet {
        // 일정 초안 정보 표시
        // confidence < 0.7이면 경고 아이콘 표시
        // 승인/거부 버튼
    }
}
```

**Done Criteria**
- [ ] CalendarDraft 내용 표시
- [ ] `isConfident = false` 시 경고 메시지
- [ ] 승인/거부 콜백 연결
- [ ] 승인 후 ApproveAndSaveEventUseCase 호출

**Test Criteria**
- 승인 탭 → Audit APPROVAL_GRANTED 기록 확인
- 거부 탭 → Audit APPROVAL_REJECTED 기록 확인

---

## Phase 14. 메모리 관리 Feature

---

### TASK-050 `P1`
**SaveConversationUseCase / SearchKnowledgeUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 대화 저장과 지식 검색 UseCase를 구현한다 |
| **Scope** | `domain/usecase/` 2개 파일 |
| **Files to Create** | `domain/usecase/SaveConversationUseCase.kt` `domain/usecase/SearchKnowledgeUseCase.kt` |
| **Dependencies** | TASK-015, TASK-026, TASK-027 |

**Done Criteria**
- [ ] `SaveConversationUseCase` — ChatMessage → Repository 저장
- [ ] `SearchKnowledgeUseCase` — 최신순 + 태그 필터 검색
- [ ] `limit` 파라미터 지원 (기본값 3)

**Test Criteria**
- save 후 search 결과에 포함 확인

---

### TASK-051 `P1`
**MemoryUiState / MemoryViewModel / MemoryScreen 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 5계층 메모리 조회 화면을 구현한다 |
| **Scope** | `feature/memory/` 3개 파일 |
| **Files to Create** | `feature/memory/MemoryUiState.kt` `feature/memory/MemoryViewModel.kt` `feature/memory/MemoryScreen.kt` |
| **Dependencies** | TASK-050 |

**Done Criteria**
- [ ] 메모리 유형 필터 칩 (전체/대화/작업/지식/감사)
- [ ] export/import 버튼 위치 포함
- [ ] Paging 3 연결 (LazyColumn + PagingSource)

**Test Criteria**
- 필터 변경 시 해당 유형만 표시 확인

---

## Phase 15. 설정 Feature

---

### TASK-052 `P0`
**SettingsUiState / SettingsViewModel / SettingsScreen 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 정보, 응답 스타일, 정책 설정 화면을 구현한다 |
| **Scope** | `feature/settings/` 3개 파일 |
| **Files to Create** | `feature/settings/SettingsUiState.kt` `feature/settings/SettingsViewModel.kt` `feature/settings/SettingsScreen.kt` |
| **Dependencies** | TASK-025, TASK-019 |

**Done Criteria**
- [ ] 모델 파일 경로 및 상태 표시
- [ ] 응답 스타일 선택 (간결/상세/친근)
- [ ] 모델 파일 없을 때 경로 안내 메시지
- [ ] 감사 로그 목록 보기 연결

**Test Criteria**
- 모델 NotFound 상태 → 경로 안내 표시 확인

---

## Phase 16. Export / Import

---

### TASK-053 `P1`
**ExportManifest 데이터 클래스 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Export 파일의 메타 정보 스키마를 정의한다 |
| **Scope** | `data/local/file/ExportManifest.kt` 1개 파일 |
| **Files to Create** | `data/local/file/ExportManifest.kt` |
| **Dependencies** | TASK-005 |

**구현 내용 — api_spec.yaml ExportManifest 스키마 기준**
```kotlin
@Serializable
data class ExportManifest(
    val version: String = "1.0",
    val appVersion: String,
    val dbSchemaVersion: Int = 1,
    val createdAt: String,
    val encrypted: Boolean = false,
    val includes: List<String> = listOf(
        "profiles", "conversations", "tasks", "knowledge_notes", "audit_events", "settings"
    )
)
```

**Done Criteria**
- [ ] `version = "1.0"` 기본값
- [ ] `encrypted = false` MVP 기본값
- [ ] `@Serializable` 애노테이션

**Test Criteria**
- JSON 직렬화/역직렬화 확인

---

### TASK-054 `P1`
**ExportImportManager — Export 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 5계층 메모리와 설정을 ZIP 파일로 export한다 |
| **Scope** | `data/local/file/ExportImportManager.kt` 1개 파일 (export 부분) |
| **Files to Create** | `data/local/file/ExportImportManager.kt` |
| **Dependencies** | TASK-053, TASK-024, TASK-025 |

**구현 내용**
```kotlin
// export 구조:
// local_friday_export_{timestamp}.zip
// ├── export_manifest.json
// ├── friday_db.sqlite
// └── settings.json
```

**Done Criteria**
- [ ] ZIP 파일 생성
- [ ] manifest.json 포함
- [ ] DB 파일 복사 포함
- [ ] 저장 공간 부족 시 `InsufficientStorage` 오류

**Test Criteria**
- export 후 ZIP 파일 존재 및 manifest 파싱 확인

---

### TASK-055 `P1`
**ExportImportManager — Import 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Export ZIP 파일을 검증하고 복원한다 |
| **Scope** | `ExportImportManager.kt` import 부분 추가 |
| **Files to Modify** | `data/local/file/ExportImportManager.kt` |
| **Dependencies** | TASK-054 |

**검증 순서 — api_spec.yaml importMemory 계약 기준**
1. ZIP 구조 확인
2. manifest.json 파싱
3. 버전 호환성 확인 (dbSchemaVersion)
4. DB 복원
5. 설정 복원
6. Audit IMPORT 기록

**Done Criteria**
- [ ] manifest 버전 불일치 → `ImportManifestMismatch`
- [ ] DB 스키마 불일치 → `ImportSchemaMismatch`
- [ ] 복원 완료 후 Audit 기록

**Test Criteria**
- export → import 왕복 후 데이터 동일 확인
- 잘못된 ZIP → 오류 반환 확인

---

### TASK-056 `P1`
**ExportMemoryUseCase / ImportMemoryUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Export/Import UseCase를 구현한다 |
| **Scope** | `domain/usecase/` 2개 파일 |
| **Files to Create** | `domain/usecase/ExportMemoryUseCase.kt` `domain/usecase/ImportMemoryUseCase.kt` |
| **Dependencies** | TASK-054, TASK-055, TASK-033 |

**Done Criteria**
- [ ] `ExportMemoryUseCase` — 개인정보 포함 경고 반환
- [ ] `ImportMemoryUseCase` — overwriteExisting 파라미터 지원
- [ ] 완료 후 Audit 기록

**Test Criteria**
- export 성공 응답에 warning 필드 포함 확인

---

## Phase 17. 웹 검색

---

### TASK-057 `P2`
**StubWebSearchGateway 구현 (MVP Stub)**

| 항목 | 내용 |
|---|---|
| **Goal** | 검색 인터페이스를 만족하는 MVP Stub을 구현한다 |
| **Scope** | `platform/network/WebSearchGateway.kt` 1개 파일 |
| **Files to Create** | `platform/network/WebSearchGateway.kt` |
| **Dependencies** | TASK-016 |

**구현 내용**
```kotlin
// MVP: 항상 빈 결과 반환
class StubWebSearchGateway @Inject constructor() : WebSearchTool {
    override suspend fun search(request: SearchRequest): Result<List<SearchResultItem>, AppError> {
        // TODO v1: 실제 검색 엔진 연결
        return Result.Success(emptyList())
    }
}
```

**Done Criteria**
- [ ] `WebSearchTool` 인터페이스 구현
- [ ] 빈 결과 반환
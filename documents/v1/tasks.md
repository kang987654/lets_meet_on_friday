# tasks.md — Local Friday
## AI Agent Task Breakdown

**문서 버전**: 1.2
**기준 문서**: PRD.md v1.2 / architecture.md v1.2 / api_spec.yaml v1.2.0
**작업 단위 기준**: AI agent 1회 컨텍스트에서 완결 가능한 단위
**총 태스크 수**: 74개
**버전 경계**: v0(MVP) = 핵심 기능 / v1 = v0 이후 구현 / v2 = Windows 및 고도화

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
| `v0` | 앱의 핵심 가치를 전달하기 위한 최소 필수 기능(MVP) |
| `v1` | v0 이후 구현 |
| `v2` | 향후 버전에서 구현(Windows 버전 및 고도화) |

> **버전 규칙**
> `v1`, `v2` 태스크는 해당 버전 작업을 명시적으로 시작하기 전까지 구현하지 않는다.
> v0 태스크에서 v1 기능이 필요한 위치에는 `// TODO(v1):` 주석 또는 확장 가능한 인터페이스만 둔다.
> 구현 순서는 Phase 번호보다 `Dependencies`와 버전 태그를 우선한다.
> v0 구현 중에는 중간에 있는 v1 Phase를 건너뛰고 뒤쪽 v0 태스크를 계속 진행한다.

> **정렬 규칙**
> - 내부 계약의 기준은 `api_spec.yaml`이다
> - 구조/책임 기준은 `architecture.md`이다
> - 태스크 구현 시 계약과 구조가 충돌하면 **api_spec → architecture → tasks 예시 코드** 순으로 우선한다
> - Kotlin 표준 `Result<T>`와 혼동을 피하기 위해 프로젝트 공통 결과 타입은 `AppResult<T>`를 사용한다

---

## Phase 0. 프로젝트 초기 세팅

---

### TASK-001 `v0`
**Android 프로젝트 생성 및 기본 Gradle 설정**

| 항목 | 내용 |
|---|---|
| **Goal** | Android 프로젝트를 생성하고 핵심 라이브러리 및 빌드 툴체인 의존성을 추가한다 |
| **Scope** | Gradle 설정 파일과 빌드 툴체인 설정만. 소스 코드 작성 없음 |
| **Files to Create/Modify** | `build.gradle.kts (project)` `build.gradle.kts (app)` `gradle/libs.versions.toml` `gradle/wrapper/gradle-wrapper.properties` |
| **Dependencies** | 없음 |

**추가할 의존성 목록**
```toml
[versions]
kotlin = "2.3.21"
compose-bom = "2026.04.01"
hilt = "2.59"
ksp = "2.3.7"
room = "2.8.4"
datastore = "1.2.1"
navigation = "2.9.7"
kotlin-serialization-plugin = "2.3.21"
kotlinx-serialization-json = "1.10.0"
paging = "3.4.2"
coroutines = "1.11.0"
androidx-hilt-navigation-compose = "1.3.0"
agp = "9.2.0"
```

**버전 선택 원칙**
- stable 버전을 우선 사용한다
- Kotlin / KSP / Hilt core / AGP는 상호 호환성이 민감하므로 **항상 함께 검증**한다
- Compose BOM / Navigation / Room / DataStore / Paging은 stable 최신판을 사용한다
- 버전 상향 후 반드시 `./gradlew build`, KSP codegen, Hilt graph 컴파일을 확인한다
- 버전 충돌이 발생하면 문서(PRD / architecture / api_spec)를 수정하지 말고 **빌드 조합을 먼저 재검증**한다

**Serialization 명칭 원칙**
- `kotlin-serialization-plugin`:
  Kotlin compiler plugin 버전이며 **Kotlin 버전과 동일 계열로 맞춘다**
- `kotlinx-serialization-json`:
  실제 JSON 직렬화 라이브러리 버전이며, **plugin 버전과는 별개**로 관리한다
- 구현 시 `libs.versions.toml`에서 plugin과 library alias를 혼동하지 않도록 분리 선언한다

**빌드 툴체인 원칙**
- `agp = "9.2.0"` 사용 시 Gradle wrapper 버전 요구사항을 함께 확인한다
- JDK는 AGP 9.2.0이 요구하는 최소 버전 이상을 사용한다
- 프로젝트 최초 bootstrap 시 아래 조합을 하나의 세트로 검증한다:
  - AGP
  - Gradle wrapper
  - JDK
  - Kotlin plugin
  - KSP
  - Hilt

**권장 구현 메모**
- `libs.versions.toml`에는 plugin과 library를 분리해서 선언한다
- 예:
  - plugin: `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`
  - library: `kotlinx-serialization-json`, `androidx-room-runtime`, `androidx-navigation-compose` 등
- `androidx-hilt-navigation-compose`는 Compose Navigation + Hilt ViewModel 연동에 사용한다

**Done Criteria**
- [ ] `./gradlew build` 성공
- [ ] 모든 의존성 다운로드 완료
- [ ] KSP 플러그인 적용 확인
- [ ] Hilt 코드 생성 확인
- [ ] Compose / Navigation / Room / DataStore / Paging 버전 충돌 없음
- [ ] Gradle wrapper / JDK / AGP 조합 호환성 확인
- [ ] Kotlin plugin / Kotlin serialization plugin / kotlinx.serialization library 구분 적용 확인

**Test Criteria**
- Gradle sync 오류 없음
- Hilt/KSP generated source 생성 확인
- `kotlinx.serialization` 사용 예제 1건 직렬화/역직렬화 성공
- Debug 빌드 1회, Unit Test 태스크 1회 정상 수행

---

### TASK-002 `v0`
**패키지 구조 및 빈 디렉토리 생성**

| 항목 | 내용 |
|---|---|
| **Goal** | `architecture.md` Directory Structure에 정의된 패키지 골격을 생성한다 |
| **Scope** | 디렉토리 구조와 패키지 선언만. 실제 구현 없음 |
| **Files to Create** | 각 패키지의 `.gitkeep` 또는 패키지 수준 빈 파일 |
| **Dependencies** | TASK-001 |

**생성할 최상위 패키지**
```text
com.localfriday.app/
├── app/di/
├── core/common/ core/config/ core/logging/ core/security/ core/mapper/
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

### TASK-003 `v0`
**Hilt Application 클래스 및 MainActivity 생성**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 진입점을 구성하고 Hilt DI를 초기화한다 |
| **Scope** | `LocalFridayApp.kt`, `MainActivity.kt` 생성. DI 모듈 빈 파일 생성 |
| **Files to Create** | `app/LocalFridayApp.kt` `app/MainActivity.kt` `app/di/AppModule.kt` `app/di/ModelModule.kt` `app/di/MemoryModule.kt` `app/di/AgentModule.kt` `app/di/PlatformModule.kt` |
| **Dependencies** | TASK-001, TASK-002 |

**구현 내용**
```kotlin
@HiltAndroidApp
class LocalFridayApp : Application()

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
- 에뮬레이터 또는 실기기에서 빈 화면 실행 확인

---

### TASK-004 `v0`
**Navigation 및 AppDestination 설정**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 내 화면 이동 구조를 정의한다 |
| **Scope** | NavHost, Destination 정의. 실제 화면 Composable은 placeholder |
| **Files to Create** | `navigation/AppDestination.kt` `navigation/AppNavHost.kt` |
| **Dependencies** | TASK-003 |

**구현 내용**
```kotlin
sealed class AppDestination(val route: String) {
    object Chat : AppDestination("chat")
    object Calendar : AppDestination("calendar")
    object Memory : AppDestination("memory")
    object Settings : AppDestination("settings")
}
```

**구현 메모**
- `androidx-hilt-navigation-compose`는 Compose Navigation + Hilt ViewModel 연동에 사용한다

**Done Criteria**
- [ ] 하단 탭 4개 (채팅/일정/메모리/설정) 전환 가능
- [ ] 뒤로가기 정상 동작

**Test Criteria**
- 탭 전환 시 크래시 없음

---

## Phase 1. Core 공통 타입

---

### TASK-005 `v0`
**AppResult / AppError 공통 타입 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 모든 레이어에서 사용할 AppResult 래퍼와 AppError 타입을 정의한다 |
| **Scope** | `core/common/` 하위 3개 파일. Android 의존성 없음 |
| **Files to Create** | `core/common/AppResult.kt` `core/common/AppError.kt` `core/common/Constants.kt` |
| **Dependencies** | TASK-002 |

**구현 내용 — AppResult.kt**
```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> null
    }

inline fun <T> AppResult<T>.getOrElse(default: (AppError) -> T): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> default(error)
    }
```

**구현 내용 — AppError.kt**
```kotlin
sealed class AppError {
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
    const val MAX_INPUT_CHARS = 8192
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
- [ ] `AppResult<T>` sealed class 완성
- [ ] `AppError` 모든 케이스 정의
- [ ] `Constants` 모든 상수 정의
- [ ] Android import 없음

**Test Criteria**
```kotlin
val success = AppResult.Success("data")
val failure = AppResult.Failure(AppError.ValidationError("field", "reason"))
assert(success is AppResult.Success)
assert(failure is AppResult.Failure)
```

---

### TASK-006 `v0`
**Config 및 FeatureFlags 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 런타임 설정과 기능 플래그를 정의한다 |
| **Scope** | `core/config/` 하위 3개 파일 |
| **Files to Create** | `core/config/AppConfig.kt` `core/config/FeatureFlags.kt` `core/config/ModelConfig.kt` |
| **Dependencies** | TASK-005 |

**구현 내용**
```kotlin
object FeatureFlags {
    const val STREAMING_RESPONSE_ENABLED = false
    const val VECTOR_SEARCH_ENABLED = false
    const val EXPORT_ENCRYPTION_ENABLED = false
    const val AUDIO_INPUT_TO_MODEL_ENABLED = false
}

data class ModelConfig(
    val modelId: String = "gemma4-e4b-it",
    val modelFileName: String = Constants.DEFAULT_MODEL_FILENAME,
    val quantization: String = "INT4",
    val maxContextTokens: Int = Constants.MAX_CONTEXT_TOKENS,
    val maxConversationTurns: Int = Constants.MAX_CONVERSATION_TURNS
)
```

**Done Criteria**
- [ ] `FeatureFlags` v0/v1/v2 관련 플래그 정의
- [ ] `ModelConfig` 기본값 설정

**Test Criteria**
- 컴파일 성공

---

### TASK-007 `v0`
**Logger 및 AuditLogger 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 로그와 감사 로그 인터페이스를 정의한다 |
| **Scope** | `core/logging/` 2개 파일 |
| **Files to Create** | `core/logging/AppLogger.kt` `core/logging/AuditLogger.kt` |
| **Dependencies** | TASK-005 |

**구현 내용**
```kotlin
object AppLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String)
}

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

### TASK-008 `v0`
**PermissionPolicy / ApprovalRules / Redaction 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 권한 정책과 승인 필요 작업을 정의한다 |
| **Scope** | `core/security/` 3개 파일. 정책 상수만 정의, 실행 로직 없음 |
| **Files to Create** | `core/security/PermissionPolicy.kt` `core/security/ApprovalRules.kt` `core/security/Redaction.kt` |
| **Dependencies** | TASK-005 |

**구현 내용**
```kotlin
object PermissionPolicy {
    const val CALENDAR_READ = android.Manifest.permission.READ_CALENDAR
    const val CALENDAR_WRITE = android.Manifest.permission.WRITE_CALENDAR
    const val MICROPHONE = android.Manifest.permission.RECORD_AUDIO

    val AUTO_ALLOWED = setOf(CALENDAR_READ)
    val REQUIRES_PERMISSION = setOf(CALENDAR_READ, CALENDAR_WRITE, MICROPHONE)
}

/**
 * ApprovalRules:
 * - 고정 액션 타입과 기본 승인 필요 여부를 정의하는 core 규칙
 * - domain/policy/ApprovalPolicy 는 이 규칙을 사용하는 도메인 정책 인터페이스
 */
object ApprovalRules {
    enum class ActionType {
        CALENDAR_WRITE,
        WEB_SEARCH
    }

    fun requiresApproval(actionType: ActionType): Boolean =
        when (actionType) {
            ActionType.CALENDAR_WRITE -> true
            ActionType.WEB_SEARCH -> true
        }
}

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
- [ ] `ApprovalRules`와 `domain/policy/ApprovalPolicy`의 역할 차이를 주석으로 명시
- [ ] Redaction 마스킹 로직 구현

**Test Criteria**
```kotlin
assert(ApprovalRules.requiresApproval(ApprovalRules.ActionType.CALENDAR_WRITE))
assert(Redaction.sanitize("a".repeat(200), true).contains("생략"))
```

---

### TASK-009 `v0`
**AppError ↔ ErrorCode 매퍼 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 내부 AppError를 계약용 ErrorCode로 변환하는 단일 매핑 규칙을 정의한다 |
| **Scope** | `core/mapper/` 1개 파일 |
| **Files to Create** | `core/mapper/ErrorCodeMapper.kt` |
| **Dependencies** | TASK-005, TASK-008 |

**구현 내용**
```kotlin
object ErrorCodeMapper {
    fun toErrorCode(error: AppError): ErrorCode = when (error) {
        is AppError.ValidationError -> when {
            error.field == "content" && error.reason.contains("blank", ignoreCase = true) ->
                ErrorCode.EMPTY_INPUT

            error.field == "content" && (
                error.reason.contains("too_long", ignoreCase = true) ||
                error.reason.contains("length", ignoreCase = true) ||
                error.reason.contains("길", ignoreCase = true)
            ) -> ErrorCode.INPUT_TOO_LONG

            error.field == "naturalLanguageRequest" && (
                error.reason.contains("missing_time", ignoreCase = true) ||
                error.reason.contains("time", ignoreCase = true) ||
                error.reason.contains("시간", ignoreCase = true)
            ) -> ErrorCode.MISSING_TIME_INFO

            else -> ErrorCode.INPUT_TOO_LONG
        }

        is AppError.ImageTooLarge -> ErrorCode.IMAGE_TOO_LARGE
        is AppError.UnsupportedImageFormat -> ErrorCode.UNSUPPORTED_IMAGE_FORMAT
        is AppError.InFlightConflict -> ErrorCode.IN_FLIGHT_CONFLICT
        is AppError.DuplicateEvent -> ErrorCode.DUPLICATE_EVENT

        is AppError.PermissionDenied -> when (error.permission) {
            PermissionPolicy.CALENDAR_READ,
            PermissionPolicy.CALENDAR_WRITE -> ErrorCode.PERMISSION_DENIED_CALENDAR

            PermissionPolicy.MICROPHONE -> ErrorCode.PERMISSION_DENIED_MICROPHONE

            else -> ErrorCode.PERMISSION_DENIED_STORAGE
        }

        is AppError.ModelNotFound -> ErrorCode.MODEL_NOT_FOUND
        is AppError.ModelNotReady -> ErrorCode.MODEL_NOT_READY
        is AppError.ModelInferenceTimeout -> ErrorCode.MODEL_INFERENCE_TIMEOUT
        is AppError.ModelInferenceError -> ErrorCode.MODEL_INFERENCE_ERROR

        is AppError.CalendarReadError,
        is AppError.CalendarWriteError -> ErrorCode.CALENDAR_PROVIDER_ERROR

        is AppError.SttError -> ErrorCode.STT_ERROR

        is AppError.NetworkUnavailable -> ErrorCode.NETWORK_UNAVAILABLE
        is AppError.SearchError -> ErrorCode.SEARCH_TIMEOUT

        is AppError.DbWriteError -> ErrorCode.DB_WRITE_ERROR
        is AppError.DbMigrationError -> ErrorCode.DB_MIGRATION_ERROR

        is AppError.ExportFailed -> ErrorCode.EXPORT_FAILED
        is AppError.ImportManifestMismatch -> ErrorCode.IMPORT_MANIFEST_MISMATCH
        is AppError.ImportSchemaMismatch -> ErrorCode.IMPORT_SCHEMA_MISMATCH
        is AppError.InsufficientStorage -> ErrorCode.INSUFFICIENT_STORAGE
    }
}
```

**Done Criteria**
- [ ] 단일 ErrorCode 매퍼 정의
- [ ] api_spec.yaml의 ErrorCode와 정렬
- [ ] 권한 오류 분기 포함
- [ ] `ValidationError`가 `DB_WRITE_ERROR`로 매핑되지 않음
- [ ] `SearchError`와 `NetworkUnavailable`이 구분 매핑됨

**Test Criteria**
```kotlin
assert(ErrorCodeMapper.toErrorCode(AppError.ImageTooLarge(1L)) == ErrorCode.IMAGE_TOO_LARGE)
assert(ErrorCodeMapper.toErrorCode(AppError.NetworkUnavailable("offline")) == ErrorCode.NETWORK_UNAVAILABLE)
assert(ErrorCodeMapper.toErrorCode(AppError.SearchError("timeout")) == ErrorCode.SEARCH_TIMEOUT)
```

---

## Phase 2. Domain 모델 정의

> **AI agent 주의**: 이 Phase의 모든 파일은 `android.*` import 금지.
> `api_spec.yaml`의 DomainModel 스키마를 기준으로 생성한다.

---

### TASK-010 `v0`
**ChatMessage / AssistantResponse / InputType / ActionCard 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 채팅 관련 핵심 도메인 모델을 정의한다 |
| **Scope** | `domain/model/` 내 채팅 관련 4개 파일 |
| **Files to Create** | `domain/model/ChatMessage.kt` `domain/model/AssistantResponse.kt` `domain/model/InputType.kt` `domain/model/ActionCard.kt` |
| **Dependencies** | TASK-005 |

**구현 원칙**
- `ChatMessage.createdAt`, `AssistantResponse.createdAt`는 내부 도메인에서는 `Long` 사용 가능
- contract 계층으로 나갈 때 ISO 8601 string으로 매핑
- `ActionCard.payload`는 `Any` 사용 금지, typed payload로 구현

**예시**
```kotlin
enum class InputType { TEXT, VOICE, IMAGE }

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

sealed class ActionPayload {
    data class CalendarDraftPayload(val draft: CalendarDraft) : ActionPayload()
    data class KnowledgeSavePayload(val note: KnowledgeNote) : ActionPayload()
}

data class ActionCard(
    val actionType: ActionType,
    val payload: ActionPayload
) {
    enum class ActionType { CALENDAR_DRAFT, KNOWLEDGE_SAVE }
}
```

**Done Criteria**
- [ ] Android import 없음
- [ ] `api_spec.yaml` 스키마와 필드 일치
- [ ] `payload: Any` 금지, typed payload 사용

**Test Criteria**
- 인스턴스 생성 컴파일 확인

---

### TASK-011 `v0`
**CalendarEvent / CalendarDraft / ScheduleData 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 캘린더 관련 도메인 모델을 정의한다 |
| **Scope** | `domain/model/` 내 캘린더 관련 3개 파일 |
| **Files to Create** | `domain/model/CalendarEvent.kt` `domain/model/CalendarDraft.kt` `domain/model/ScheduleData.kt` |
| **Dependencies** | TASK-005 |

**구현 내용**
```kotlin
data class CalendarDraft(
    val title: String,
    val startIso: String,
    val endIso: String,
    val note: String? = null,
    val confidence: Float
) {
    fun isConfident(): Boolean = confidence >= Constants.CALENDAR_DRAFT_MIN_CONFIDENCE
}

data class ScheduleData(
    val events: List<CalendarEvent>,
    val summary: String?,
    val rangeType: RangeType
) {
    enum class RangeType { TODAY, WEEK }
}
```

**Done Criteria**
- [ ] `isConfident()` 헬퍼 메서드 포함
- [ ] `ScheduleData` 추가
- [ ] Android import 없음

**Test Criteria**
```kotlin
val draft = CalendarDraft("제목", "...", "...", confidence = 0.8f)
assert(draft.isConfident())
```

---

### TASK-012 `v0`
**AuditEvent 도메인 모델 및 v1 메모리 확장 타입 선언**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 필수 AuditEvent를 정의하고, v1 Task/Knowledge 확장 타입은 계약만 준비한다 |
| **Scope** | `domain/model/` 내 메모리 관련 3개 파일 |
| **Files to Create** | `domain/model/KnowledgeNote.kt` `domain/model/TaskItem.kt` `domain/model/AuditEvent.kt` |
| **Dependencies** | TASK-005 |

**버전 범위**
- v0 구현 대상: `AuditEvent`
- v1 확장 계약: `KnowledgeNote`, `TaskItem`

**Done Criteria**
- [ ] `AuditEventType` enum 전체 정의
- [ ] v0 필수 이벤트: `MODEL_RUN`, `TOOL_CALL`, `APPROVAL_GRANTED`, `APPROVAL_REJECTED`, `ERROR`
- [ ] v1 확장 이벤트 포함
- [ ] Android import 없음

**Test Criteria**
- 인스턴스 생성 컴파일 확인

---

### TASK-013 `v0`
**모델 출력 구조화 타입 정의 (ModelOutput)**

| 항목 | 내용 |
|---|---|
| **Goal** | AI 모델이 반환하는 JSON을 Kotlin sealed class로 정의한다 |
| **Scope** | `domain/model/` 내 1개 파일 |
| **Files to Create** | `domain/model/ModelOutput.kt` |
| **Dependencies** | TASK-010, TASK-011, TASK-012 |

**버전 범위**
- v0 파싱/처리 대상: `TextOutput`, `CalendarDraftOutput`
- v1 확장 대상: `SearchOutput`, `KnowledgeSaveOutput`

**Done Criteria**
- [ ] 4가지 출력 타입 정의
- [ ] v0/v1 action 값과 매핑 주석 포함
- [ ] v0 Orchestrator는 `SearchOutput`, `KnowledgeSaveOutput`을 실행하지 않음

**Test Criteria**
- sealed class when 분기 exhaustive 확인

---

### TASK-014 `v0`
**ApprovalRequest / UserProfile / SearchRequest 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 승인/프로필 모델을 정의하고 v1 검색 요청 계약을 준비한다 |
| **Scope** | `domain/model/` 내 3개 파일 |
| **Files to Create** | `domain/model/ApprovalRequest.kt` `domain/model/SearchRequest.kt` `domain/model/UserProfile.kt` |
| **Dependencies** | TASK-011 |

**Done Criteria**
- [ ] `ApprovalRequest` 정의
- [ ] `UserProfile` 정의
- [ ] `SearchRequest` v1 계약 정의
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

### TASK-015 `v0`
**ChatRequest / SearchResultItem / ModelInfo / InferenceMetrics 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Orchestrator 및 Runtime에서 사용하는 내부 계약 타입을 정의한다 |
| **Scope** | `domain/model/` 및 `domain/modelrunner/` 하위 4개 파일 |
| **Files to Create** | `domain/model/ChatRequest.kt` `domain/model/SearchResultItem.kt` `domain/modelrunner/ModelInfo.kt` `domain/modelrunner/InferenceMetrics.kt` |
| **Dependencies** | TASK-010, TASK-011, TASK-014 |

**구현 원칙**
- `ChatRequest`는 Orchestrator 내부 입력 모델이다
- API 요청 DTO와 동일시하지 않는다
- 시간 필드는 contract 계층에서 ISO 8601 string으로 변환될 수 있음을 주석으로 명시한다

**Done Criteria**
- [ ] `ChatRequest` — content, inputType, imageBytes?, sessionId, source 포함
- [ ] `SearchResultItem` — title, url, snippet 포함
- [ ] `ModelInfo` — modelId, modelPath, modelVersion, quantization, lastLoadedAt 포함
- [ ] `InferenceMetrics` — api_spec.yaml 기준 필드 포함
- [ ] Android import 없음
- [ ] 시간/직렬화 표현 원칙 주석 포함

**Test Criteria**
- 인스턴스 생성 및 컴파일 확인

---

## Phase 3. Domain 인터페이스 정의

---

### TASK-016 `v0`
**ModelRunner 인터페이스 및 ModelLoadState 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 추론 추상화 인터페이스를 정의한다 |
| **Scope** | `domain/modelrunner/` 2개 파일 |
| **Files to Create** | `domain/modelrunner/ModelRunner.kt` `domain/modelrunner/ModelLoadState.kt` |
| **Dependencies** | TASK-005, TASK-015 |

**구현 내용**
```kotlin
sealed class ModelLoadState {
    object Loading : ModelLoadState()
    data class Ready(val modelInfo: ModelInfo) : ModelLoadState()
    data class Error(val error: AppError) : ModelLoadState()
    data class NotFound(val expectedPath: String) : ModelLoadState()
}

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

**Done Criteria**
- [ ] `ModelLoadState` sealed class 4가지 상태
- [ ] `ModelRunner` 인터페이스 메서드 완성
- [ ] Android import 없음

**Test Criteria**
- 인터페이스 컴파일 확인

---

### TASK-017 `v0`
**Repository 인터페이스 5개 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 메모리 저장소 인터페이스와 v1 확장 저장소 계약을 정의한다 |
| **Scope** | `domain/memory/` 5개 파일 |
| **Files to Create** | `domain/memory/ProfileRepository.kt` `domain/memory/ConversationRepository.kt` `domain/memory/TaskRepository.kt` `domain/memory/KnowledgeRepository.kt` `domain/memory/AuditRepository.kt` |
| **Dependencies** | TASK-010, TASK-011, TASK-012 |

**구현 내용**
```kotlin
interface ConversationRepository {
    suspend fun save(message: ChatMessage): AppResult<Unit>
    suspend fun getRecentBySession(
        sessionId: String,
        limit: Int = Constants.MAX_CONVERSATION_TURNS
    ): AppResult<List<ChatMessage>>
    fun getPagedBySession(sessionId: String): PagingSource<Int, ChatMessage>
}
```

**버전 범위**
- v0 구현 대상: `ProfileRepository`, `ConversationRepository`, `AuditRepository`
- v1 확장 대상: `TaskRepository`, `KnowledgeRepository`

**Done Criteria**
- [ ] 5개 인터페이스 정의
- [ ] v0/v1 저장소 범위 주석 포함
- [ ] mapper 책임이 필요한 필드(시간/enum 등)는 구현체에서 일관 변환하도록 주석 명시
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

### TASK-018 `v0`
**Tool 인터페이스 4개 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 플랫폼 도구 추상화와 v1 확장 도구 계약을 정의한다 |
| **Scope** | `domain/tool/` 4개 파일 |
| **Files to Create** | `domain/tool/CalendarTool.kt` `domain/tool/SpeechToTextTool.kt` `domain/tool/WebSearchTool.kt` `domain/tool/FileTool.kt` |
| **Dependencies** | TASK-011, TASK-014, TASK-015 |

**구현 내용**
```kotlin
interface CalendarTool {
    suspend fun readEvents(startMs: Long, endMs: Long): AppResult<List<CalendarEvent>>
    suspend fun insert(draft: CalendarDraft): AppResult<Long>
}

interface SpeechToTextTool {
    val state: StateFlow<SttState>
    val transcript: StateFlow<String?>
    fun start(preferOffline: Boolean = true, language: String = "ko-KR")
    fun stop()
    fun cancel()
}

enum class SttState { IDLE, LISTENING, TRANSCRIBING, DONE, ERROR }

interface WebSearchTool {
    suspend fun search(request: SearchRequest): AppResult<List<SearchResultItem>>
}
```

**Done Criteria**
- [ ] 4개 인터페이스 정의
- [ ] `SttState` enum 5가지 상태
- [ ] STT 결과 전달은 `state + transcript`
- [ ] `WebSearchTool`, `FileTool`은 v1 전용 주석 표시
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

### TASK-019 `v0`
**Agent 인터페이스 및 Policy 인터페이스 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Agent와 Policy 추상화 인터페이스를 정의한다 |
| **Scope** | `domain/agent/` `domain/policy/` 합계 4개 파일 |
| **Files to Create** | `domain/agent/Agent.kt` `domain/agent/AgentResult.kt` `domain/policy/ExecutionPolicy.kt` `domain/policy/ApprovalPolicy.kt` |
| **Dependencies** | TASK-005, TASK-013 |

**구현 내용**
```kotlin
interface Agent {
    suspend fun execute(input: String, context: Map<String, Any>): AgentResult
}

sealed class AgentResult {
    data class Text(val content: String) : AgentResult()
    data class ActionRequired(val output: ModelOutput) : AgentResult()
    data class Error(val error: AppError) : AgentResult()
}

/**
 * domain/policy/ApprovalPolicy:
 * - core/security/ApprovalRules 를 사용하는 도메인 정책 인터페이스
 * - 실제 비즈니스 컨텍스트에서 승인 필요 여부를 결정하는 확장 지점
 */
interface ExecutionPolicy {
    fun canExecute(actionType: ApprovalRules.ActionType): Boolean
    fun requiresApproval(actionType: ApprovalRules.ActionType): Boolean
}
```

**Done Criteria**
- [ ] `Agent` 인터페이스 정의
- [ ] `AgentResult` sealed class 정의
- [ ] `ApprovalRules`와 `ApprovalPolicy` 역할 구분 주석 명시
- [ ] Android import 없음

**Test Criteria**
- 컴파일 확인

---

## Phase 4. 로컬 모델 런타임

---

### TASK-020 `v0`
**FakeModelRunner 구현 (테스트용 Stub)**

| 항목 | 내용 |
|---|---|
| **Goal** | 실제 모델 없이 상위 레이어를 테스트하기 위한 Fake 구현을 만든다 |
| **Scope** | `runtime/gemma/FakeModelRunner.kt` 1개 파일 |
| **Files to Create** | `runtime/gemma/FakeModelRunner.kt` |
| **Dependencies** | TASK-016 |

**구현 내용**
```kotlin
class FakeModelRunner : ModelRunner {
    private val _loadState = MutableStateFlow<ModelLoadState>(
        ModelLoadState.Ready(
            ModelInfo(
                modelId = "fake-model",
                modelPath = "/fake/path",
                modelVersion = "1.0",
                quantization = "INT4",
                lastLoadedAt = null
            )
        )
    )
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    var fakeResponse: String = """{"action":"text","content":"테스트 응답입니다."}"""

    override suspend fun generate(prompt: String, onToken: ((String) -> Unit)?): AppResult<String> {
        delay(100)
        return AppResult.Success(fakeResponse)
    }

    override suspend fun generateWithImage(prompt: String, imageBytes: ByteArray): AppResult<String> =
        AppResult.Success(fakeResponse)

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
assert(result is AppResult.Success)
```

---

### TASK-021 `v0`
**GemmaRuntimeManager — 모델 로드/언로드 관리**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 파일 존재 확인 및 LiteRT-LM 로드/언로드를 관리한다 |
| **Scope** | `runtime/gemma/GemmaRuntimeManager.kt` 1개 파일 |
| **Files to Create** | `runtime/gemma/GemmaRuntimeManager.kt` |
| **Dependencies** | TASK-016, TASK-006 |

**Done Criteria**
- [ ] 모델 파일 존재 확인 로직
- [ ] NotFound 상태 처리
- [ ] `getExternalFilesDir("models")` 경로 사용

**Test Criteria**
- 모델 파일 없을 때 `ModelLoadState.NotFound` 반환 확인

---

### TASK-022 `v0`
**GemmaModelRunner — LiteRT-LM 추론 연결**

| 항목 | 내용 |
|---|---|
| **Goal** | `ModelRunner` 인터페이스의 실제 LiteRT-LM 구현을 작성한다 |
| **Scope** | `runtime/gemma/GemmaModelRunner.kt` 1개 파일 |
| **Files to Create** | `runtime/gemma/GemmaModelRunner.kt` |
| **Dependencies** | TASK-020, TASK-021 |

**Done Criteria**
- [ ] `ModelRunner` 인터페이스 완전 구현
- [ ] 모델 미준비 시 `ModelNotReady` 오류 반환
- [ ] Dispatchers.Default에서 추론 실행
- [ ] v0에서는 비스트리밍 경로 유지

**Test Criteria**
- `ModelNotReady` 상태에서 Failure 반환 확인

---

### TASK-023 `v0`
**ImageInputAdapter — 이미지 전처리**

| 항목 | 내용 |
|---|---|
| **Goal** | 이미지 URI를 받아 유효성 검사 후 리사이즈하여 ByteArray를 반환한다 |
| **Scope** | `runtime/multimodal/ImageInputAdapter.kt` 1개 파일 |
| **Files to Create** | `runtime/multimodal/ImageInputAdapter.kt` |
| **Dependencies** | TASK-005 |

**Done Criteria**
- [ ] MIME 타입 검사 (JPEG/PNG/WEBP만 허용)
- [ ] 10MB 크기 제한
- [ ] 장축 1024px 리사이즈
- [ ] 오류별 AppError 반환

**Test Criteria**
- 크기 초과 → `ImageTooLarge`
- 미지원 형식 → `UnsupportedImageFormat`

---

## Phase 5. 데이터 레이어 — DB

---

### TASK-024 `v0`
**Room Entity 정의 — v0 필수 + v1 확장 스키마**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 로컬 저장소 Entity를 정의하고 v1 Task/Knowledge 스키마를 확장 계약으로 준비한다 |
| **Scope** | `data/local/db/entity/` 5개 파일 |
| **Files to Create** | `ProfileEntity.kt` `ConversationEntity.kt` `AuditEntity.kt` `TaskEntity.kt` `KnowledgeEntity.kt` |
| **Dependencies** | TASK-010, TASK-011, TASK-012 |

**Done Criteria**
- [ ] v0 Entity 3개 정의
- [ ] v1 Entity 2개 생성
- [ ] 인덱스 설정 (api_spec.yaml 기준)
- [ ] Primary Key UUID String 타입

**Test Criteria**
- Room 컴파일 타임 스키마 검증 통과

---

### TASK-025 `v0`
**Room DAO 정의 — v0 필수 + v1 확장 DAO**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 저장소 DAO를 정의하고 v1 Task/Knowledge DAO 계약을 준비한다 |
| **Scope** | `data/local/db/dao/` 5개 파일 |
| **Files to Create** | `ProfileDao.kt` `ConversationDao.kt` `TaskDao.kt` `KnowledgeDao.kt` `AuditDao.kt` |
| **Dependencies** | TASK-024 |

**Done Criteria**
- [ ] v0 DAO 3개 정의
- [ ] v1 DAO 2개 생성
- [ ] PagingSource 메서드 포함
- [ ] Suspend 함수 적용

**Test Criteria**
- Room 컴파일 타임 SQL 검증 통과

---

### TASK-026 `v0`
**LocalFridayDatabase 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Room DB 진입점을 생성하고 버전/AutoMigration을 설정한다 |
| **Scope** | `data/local/db/LocalFridayDatabase.kt` 1개 파일 |
| **Files to Create** | `data/local/db/LocalFridayDatabase.kt` |
| **Dependencies** | TASK-024, TASK-025 |

**Done Criteria**
- [ ] `exportSchema = true`
- [ ] v0 Entity 포함
- [ ] v1 Entity는 스키마 안정성 위해 version 1 포함 가능
- [ ] 개발용 `fallbackToDestructiveMigration` 분리

**Test Criteria**
- DB 생성 후 테이블 존재 확인

---

### TASK-027 `v0`
**DataStore — SettingsDataStore / ModelRegistryStore / SessionStore**

| 항목 | 내용 |
|---|---|
| **Goal** | 앱 설정, 모델 정보, 활성 세션 정보를 DataStore에 저장/조회한다 |
| **Scope** | `data/local/prefs/` 3개 파일 |
| **Files to Create** | `SettingsDataStore.kt` `ModelRegistryStore.kt` `SessionStore.kt` |
| **Dependencies** | TASK-005, TASK-015 |

**Done Criteria**
- [ ] `ModelRegistryStore` — 모델 경로/버전 저장
- [ ] `SettingsDataStore` — 응답 스타일 저장
- [ ] `SessionStore` — 활성 sessionId 저장/복원
- [ ] Flow 기반 관찰 가능

**Test Criteria**
- 저장 후 읽기 값 일치 확인

---

## Phase 6. 데이터 레이어 — Repository 구현

---

### TASK-028 `v0`
**ProfileRepositoryImpl / ConversationRepositoryImpl 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Profile과 Conversation 메모리 저장소 구현체를 작성한다 |
| **Scope** | `data/local/repository/` 2개 파일 + 테스트용 Fake 1개 |
| **Files to Create** | `ProfileRepositoryImpl.kt` `ConversationRepositoryImpl.kt` `data/local/repository/fake/FakeConversationRepository.kt` |
| **Dependencies** | TASK-017, TASK-025, TASK-026 |

**구현 원칙**
- Entity ↔ Domain 변환 함수 포함
- 시간 표현은 DB(Long ms) ↔ Domain 규칙을 mapper 함수로 일관되게 처리
- v0에서는 실시간 DB observe를 source of truth로 사용하지 않는다
- UI의 optimistic append와 DB 저장은 분리한다

**Done Criteria**
- [ ] Entity ↔ Domain 변환 함수 포함
- [ ] try-catch → AppError 변환
- [ ] `@Inject constructor` 적용
- [ ] `FakeConversationRepository` 제공
- [ ] 시간 변환 규칙 주석 또는 mapper 함수 포함

**Test Criteria**
```kotlin
// save 후 getRecentBySession 결과 확인
// FakeConversationRepository로 ContextBuilder 테스트 가능
```

---

### TASK-029 `v0`
**AuditRepositoryImpl 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 필수 감사 이벤트 저장소 구현체를 작성한다 |
| **Scope** | `data/local/repository/AuditRepositoryImpl.kt` 1개 파일 + 테스트용 Fake 1개 |
| **Files to Create** | `AuditRepositoryImpl.kt` `data/local/repository/fake/FakeAuditRepository.kt` |
| **Dependencies** | TASK-017, TASK-025, TASK-026 |

**구현 원칙**
- v0 필수 이벤트 저장 가능
- v1 확장 이벤트 타입도 저장 가능한 구조를 유지
- 민감 정보는 저장 전 Redaction 가능 구조 유지

**Done Criteria**
- [ ] `AuditRepositoryImpl.save()` 구현
- [ ] v0 필수 이벤트 저장 가능
- [ ] Entity ↔ Domain 변환 포함
- [ ] `FakeAuditRepository` 제공

**Test Criteria**
- save → getPaged 확인
- FakeAuditRepository로 AuditTrailService 테스트 가능

---

### TASK-030 `v0`
**DI 모듈 바인딩 — v0 MemoryModule / AppModule**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 Repository 인터페이스와 구현체를 Hilt에 바인딩한다 |
| **Scope** | `app/di/MemoryModule.kt` `app/di/AppModule.kt` |
| **Files to Modify** | `app/di/MemoryModule.kt` `app/di/AppModule.kt` |
| **Dependencies** | TASK-026, TASK-027, TASK-028, TASK-029 |

**Done Criteria**
- [ ] v0 Repository 3개 바인딩
- [ ] DataStore 제공
- [ ] `SessionStore` 제공
- [ ] Hilt 컴파일 성공

**Test Criteria**
- Hilt 그래프 컴파일 오류 없음

---

## Phase 7. Assistant Core — Context

---

### TASK-031 `v0`
**ContextBuilder — v0 대화 컨텍스트 조합**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 프롬프트 조립을 위한 최근 대화 컨텍스트를 구성한다 |
| **Scope** | `assistant/context/ContextBuilder.kt` 1개 파일 |
| **Files to Create** | `assistant/context/ContextBuilder.kt` |
| **Dependencies** | TASK-017, TASK-028 |

**구현 내용**
```kotlin
class ContextBuilder @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    data class Context(
        val recentConversations: List<ChatMessage>,
        val sessionId: String
    )

    suspend fun build(sessionId: String): AppResult<Context> {
        val conversationsResult = conversationRepository.getRecentBySession(
            sessionId,
            Constants.MAX_CONVERSATION_TURNS
        )

        return when (conversationsResult) {
            is AppResult.Success -> AppResult.Success(
                Context(conversationsResult.data, sessionId)
            )
            is AppResult.Failure -> AppResult.Failure(conversationsResult.error)
        }
    }
}
```

**Done Criteria**
- [ ] 대화 최근 5턴 로드
- [ ] v0에서는 KnowledgeRepository 호출 안 함
- [ ] TODO(v1) 주석 위치 명시

**Test Criteria**
- FakeRepository 사용 → build 결과 확인

---

### TASK-032 `v0`
**PromptAssembler — v0 프롬프트 조립**

| 항목 | 내용 |
|---|---|
| **Goal** | 시스템/대화/현재 입력/출력 형식 블록으로 v0 프롬프트를 조립한다 |
| **Scope** | `assistant/context/PromptAssembler.kt` 1개 파일 |
| **Files to Create** | `assistant/context/PromptAssembler.kt` |
| **Dependencies** | TASK-031, TASK-027 |

**Done Criteria**
- [ ] v0 블록 구조 구현
- [ ] 검색 허용 여부는 항상 false
- [ ] `search`, `save_knowledge` 출력 형식은 v0 기본 프롬프트에 강제하지 않음
- [ ] 대화 시간순 정렬

**Test Criteria**
- prompt 문자열 블록 포함 확인

---

### TASK-033 `v0`
**ResponseParser — 모델 출력 JSON 파싱**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 출력 JSON을 `ModelOutput` sealed class로 파싱한다 |
| **Scope** | `assistant/context/ResponseParser.kt` 1개 파일 |
| **Files to Create** | `assistant/context/ResponseParser.kt` |
| **Dependencies** | TASK-013 |

**Done Criteria**
- [ ] 4가지 action 타입 파싱
- [ ] 파싱 실패 시 `TextOutput` fallback
- [ ] `confidence` 필드 파싱 포함
- [ ] v0 실행 경로는 `TextOutput`, `CalendarDraftOutput`만 처리

**Test Criteria**
- 잘못된 JSON → `TextOutput` fallback

---

## Phase 8. Assistant Core — Orchestration

---

### TASK-034 `v0`
**PreExecutionGuard — 실행 전 정책 검사**

| 항목 | 내용 |
|---|---|
| **Goal** | v0 모델 실행 전 승인 필요 여부를 검사한다 |
| **Scope** | `assistant/guard/PreExecutionGuard.kt` 1개 파일 |
| **Files to Create** | `assistant/guard/PreExecutionGuard.kt` |
| **Dependencies** | TASK-008, TASK-019 |

**Done Criteria**
- [ ] SearchOutput / KnowledgeSaveOutput → v1 안내 Blocked
- [ ] CalendarDraftOutput → RequiresApproval
- [ ] TextOutput → Allowed

**Test Criteria**
- 각 타입별 분기 확인

---

### TASK-035 `v0`
**AuditTrailService — 감사 이벤트 기록**

| 항목 | 내용 |
|---|---|
| **Goal** | 모든 주요 이벤트를 AuditRepository에 기록하는 서비스를 구현한다 |
| **Scope** | `assistant/audit/AuditTrailService.kt` 1개 파일 |
| **Files to Create** | `assistant/audit/AuditTrailService.kt` |
| **Dependencies** | TASK-007, TASK-017, TASK-029 |

**Done Criteria**
- [ ] v0 필수 AuditEventType에 대한 편의 메서드
- [ ] 저장 실패 시 앱 크래시 없이 로그만 출력
- [ ] `AuditLogger` 구현
- [ ] 민감 텍스트 Redaction 적용

**Test Criteria**
- `logModelRun()` 후 저장 확인

---

### TASK-036 `v0`
**IntentClassifier — 입력 의도 분류**

| 항목 | 내용 |
|---|---|
| **Goal** | 사용자 입력의 의도를 분류하여 라우팅 힌트를 제공한다 |
| **Scope** | `assistant/orchestrator/IntentClassifier.kt` 1개 파일 |
| **Files to Create** | `assistant/orchestrator/IntentClassifier.kt` |
| **Dependencies** | TASK-010 |

**Done Criteria**
- [ ] v0 Intent 분류: CHAT / CALENDAR_READ / CALENDAR_WRITE
- [ ] KNOWLEDGE / SEARCH는 TODO(v1)
- [ ] 키워드 기반 구현
- [ ] 기본값 CHAT

**Test Criteria**
- 입력별 의도 분류 확인

---

### TASK-037 `v0`
**AssistantOrchestrator — 전체 흐름 총괄**

| 항목 | 내용 |
|---|---|
| **Goal** | 사용자 입력부터 응답 생성까지 전체 흐름을 조율한다 |
| **Scope** | `assistant/orchestrator/AssistantOrchestrator.kt` 1개 파일 |
| **Files to Create** | `assistant/orchestrator/AssistantOrchestrator.kt` |
| **Dependencies** | TASK-031, TASK-032, TASK-033, TASK-034, TASK-035, TASK-036, TASK-015 |

**구현 내용**
- `ChatRequest` 내부 입력 모델 사용
- 사용자 메시지 저장
- assistant 응답 확정 후 assistant 메시지 저장
- v0에서 `SearchOutput`, `KnowledgeSaveOutput` 실행 금지

**Done Criteria**
- [ ] v0 8단계 흐름 완성
- [ ] 각 단계 실패 시 오류 전파
- [ ] user/assistant 메시지 저장 포함
- [ ] 검색/지식 저장 출력은 v1 안내로 처리

**Test Criteria**
- FakeModelRunner, FakeRepository 사용 Success 확인

---

### TASK-038 `v0`
**ApprovalCoordinator — 승인 대기 상태 관리**

| 항목 | 내용 |
|---|---|
| **Goal** | 승인이 필요한 액션의 pending state를 관리한다 |
| **Scope** | `assistant/approval/ApprovalCoordinator.kt` 1개 파일 |
| **Files to Create** | `assistant/approval/ApprovalCoordinator.kt` |
| **Dependencies** | TASK-014 |

**구현 내용**
```kotlin
class ApprovalCoordinator @Inject constructor() {
    private val _pendingRequest = MutableStateFlow<ApprovalRequest?>(null)
    val pendingRequest: StateFlow<ApprovalRequest?> = _pendingRequest.asStateFlow()

    fun requestApproval(request: ApprovalRequest) {
        _pendingRequest.value = request
    }

    fun consumePending(): ApprovalRequest? {
        val current = _pendingRequest.value
        _pendingRequest.value = null
        return current
    }

    fun clearPending() {
        _pendingRequest.value = null
    }
}
```

**Done Criteria**
- [ ] `pendingRequest` StateFlow 노출
- [ ] 동시에 1개 요청만 허용
- [ ] 승인/거부 Audit 기록은 하지 않음

**Test Criteria**
- pending 값 설정/소비 확인

---

## Phase 9. 채팅 Feature

---

### TASK-039 `v0`
**SendChatMessageUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 채팅 메시지 전송 비즈니스 로직을 UseCase로 구현한다 |
| **Scope** | `domain/usecase/SendChatMessageUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/SendChatMessageUseCase.kt` |
| **Dependencies** | TASK-037 |

**구현 원칙**
- v0 Request에는 `searchEnabled` 필드 없음
- 입력 길이 제한은 문자 수 기준 (`MAX_INPUT_CHARS`)

**Done Criteria**
- [ ] 빈 입력 ValidationError 반환
- [ ] v0 문자 길이 제한(8192자) 검사
- [ ] Orchestrator 위임
- [ ] operator fun invoke 패턴
- [ ] TODO(v1): searchEnabled 필드 주석만 남김

**Test Criteria**
- 빈 문자열 → Failure 확인

---

### TASK-040 `v0`
**ChatUiState 및 ChatViewModel 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 채팅 화면의 상태 관리와 이벤트 처리를 구현한다 |
| **Scope** | `feature/chat/ChatUiState.kt` `feature/chat/ChatViewModel.kt` |
| **Files to Create** | `feature/chat/ChatUiState.kt` `feature/chat/ChatViewModel.kt` |
| **Dependencies** | TASK-039, TASK-027, TASK-038, TASK-016 |

**구현 원칙**
- `SavedStateHandle` 기반 sessionId 유지
- 필요 시 `SessionStore`와 동기화
- sessionId의 source of truth는 `SavedStateHandle` 우선, `SessionStore`는 앱 재생성 복원 보조 역할
- 사용자 메시지 optimistic append
- assistant 응답 append
- `finally`에서 `isInFlight` 해제
- v0에서는 UI 메시지 리스트와 DB 저장은 분리하며, 실시간 DB observe는 도입하지 않는다

**Done Criteria**
- [ ] 중복 전송 차단
- [ ] 세션 ID 복원
- [ ] 사용자 메시지 optimistic append
- [ ] 성공 시 assistant 메시지 append
- [ ] v0에서는 검색 토글 상태 없음
- [ ] sessionId source of truth 원칙 주석 명시
- [ ] UI append와 DB 저장이 중복 source of truth가 되지 않음

**Test Criteria**
- 동시 전송 2회 → 두 번째 무시 확인
- SavedStateHandle 복원 시 동일 sessionId 유지 확인

---

### TASK-041 `v0`
**ChatScreen Composable 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 메인 채팅 화면 UI를 구현한다 |
| **Scope** | `feature/chat/ChatScreen.kt` 1개 파일 |
| **Files to Create** | `feature/chat/ChatScreen.kt` |
| **Dependencies** | TASK-040 |

**포함 UI 요소**
- 상태 카드 (오프라인/모델 상태)
- 메시지 리스트
- 이미지 첨부 프리뷰
- 입력창 + 이미지 버튼 + 전송 버튼
- 음성 FAB
- 로딩 인디케이터
- 오류 시 재시도 버튼

**Done Criteria**
- [ ] 5가지 UiState 표현
- [ ] v0 검색 토글 UI 없음
- [ ] TODO(v1): 검색 토글 위치 주석
- [ ] 음성 FAB 포함
- [ ] empty state 표시

**Test Criteria**
- 로딩/오류/빈 상태 확인

---

### TASK-042 `v0`
**DI 바인딩 — AgentModule / ModelModule**

| 항목 | 내용 |
|---|---|
| **Goal** | Orchestrator, UseCase, ModelRunner를 Hilt에 바인딩한다 |
| **Scope** | `app/di/AgentModule.kt` `app/di/ModelModule.kt` |
| **Files to Modify** | `app/di/AgentModule.kt` `app/di/ModelModule.kt` |
| **Dependencies** | TASK-037, TASK-038, TASK-039, TASK-022 |

**Done Criteria**
- [ ] `ModelRunner` → `GemmaModelRunner` 바인딩
- [ ] `AuditLogger` → `AuditTrailService` 바인딩
- [ ] Hilt 그래프 컴파일 성공

**Test Criteria**
- ChatViewModel 주입 시 크래시 없음

---

## Phase 10. 음성 입력 Feature

---

### TASK-043 `v0`
**AndroidSpeechToTextTool 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Android SpeechRecognizer를 사용하는 STT Tool을 구현한다 |
| **Scope** | `platform/speech/AndroidSpeechToTextTool.kt` 1개 파일 + 테스트용 Fake 1개 |
| **Files to Create** | `platform/speech/AndroidSpeechToTextTool.kt` `platform/speech/FakeSpeechToTextTool.kt` |
| **Dependencies** | TASK-018 |

**구현 원칙**
- `state + transcript` 계약 구현
- `EXTRA_PREFER_OFFLINE = true`
- transcript는 final result만 저장
- 테스트를 위해 Fake 구현 제공

**Done Criteria**
- [ ] 5가지 SttState 전환
- [ ] transcript StateFlow 구현
- [ ] 실패 시 ERROR 상태 전환
- [ ] 오프라인 우선 설정
- [ ] `FakeSpeechToTextTool` 제공

**Test Criteria**
- 권한 없을 때 ERROR 상태 확인
- FakeSpeechToTextTool로 VoiceViewModel 테스트 가능

---

### TASK-044 `v0`
**ProcessVoiceInputUseCase / VoiceViewModel / VoiceOverlay 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 음성 입력 UseCase와 오버레이 UI를 구현한다 |
| **Scope** | UseCase 1개 + ViewModel 1개 + Composable 1개 |
| **Files to Create** | `domain/usecase/ProcessVoiceInputUseCase.kt` `feature/voice/VoiceViewModel.kt` `feature/voice/VoiceOverlay.kt` `feature/voice/VoiceUiState.kt` |
| **Dependencies** | TASK-039, TASK-043 |

**Done Criteria**
- [ ] `SpeechToTextTool.transcript` 구독 후 SendChatMessageUseCase 위임
- [ ] transcript 자동 전송하지 않고 사용자 확인 후 전송
- [ ] 오버레이 상태: idle / listening / transcribing / success / error
- [ ] 취소/전송 버튼 포함
- [ ] 빈 STT 결과 처리

**Test Criteria**
- STT 결과 → sendMessage 호출 확인

---

## Phase 11. 이미지 입력 Feature

---

### TASK-045 `v0`
**ProcessImageInputUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 이미지 입력 처리 UseCase를 구현한다 |
| **Scope** | `domain/usecase/ProcessImageInputUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/ProcessImageInputUseCase.kt` |
| **Dependencies** | TASK-023, TASK-039 |

**구현 원칙**
- v0 이미지 입력 표준 경로는 이 UseCase
- `SendChatMessageUseCase.imageBytes` 경로는 내부 재사용용

**Done Criteria**
- [ ] ImageInputAdapter 전처리 후 UseCase 위임
- [ ] 이미지 유효성 오류 그대로 전파

**Test Criteria**
- 10MB 초과 이미지 → `ImageTooLarge`

---

## Phase 12. 캘린더 Feature

---

### TASK-046 `v0`
**AndroidCalendarTool 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Android Calendar Provider에서 일정을 읽고 저장한다 |
| **Scope** | `platform/calendar/AndroidCalendarTool.kt` 1개 파일 + 테스트용 Fake 1개 |
| **Files to Create** | `platform/calendar/AndroidCalendarTool.kt` `platform/calendar/FakeCalendarTool.kt` |
| **Dependencies** | TASK-018 |

**구현 원칙**
- `createDraft` 책임 금지
- Tool은 read/insert만 담당
- 중복 일정 감지는 hard fail보다 warning 우선 정책을 고려한다
- v0 기본 UX는 사용자 확인 우선이다
- 실제 경고 표시는 ApprovalSheet 또는 CalendarScreen 상위 레이어에서 담당한다

**Done Criteria**
- [ ] `readEvents` 구현
- [ ] `insert` 구현
- [ ] SecurityException → PermissionDenied 변환
- [ ] Dispatchers.IO 사용
- [ ] `FakeCalendarTool` 제공
- [ ] 중복/겹침 일정 감지 시 경고 우선 정책 주석 명시

**Test Criteria**
- 권한 없을 때 PermissionDenied 반환
- FakeCalendarTool로 ApproveAndSaveEventUseCase 테스트 가능

---

### TASK-047 `v0`
**GetTodayScheduleUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 오늘/주간 일정을 조회하고 AI 요약을 포함하여 반환한다 |
| **Scope** | `domain/usecase/GetTodayScheduleUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/GetTodayScheduleUseCase.kt` |
| **Dependencies** | TASK-046, TASK-016, TASK-011 |

**구현 원칙**
- 반환 타입은 `ScheduleData`
- AI 요약은 plain text 응답으로 취급
- 구조화 JSON 파서와 분리된 프롬프트 정책 허용

**Done Criteria**
- [ ] TODAY / WEEK 범위 지원
- [ ] 빈 일정 시 empty 반환
- [ ] AI 요약 실패 시 요약 없이 일정만 반환
- [ ] `ScheduleData` 도메인 타입 반환

**Test Criteria**
- 빈 캘린더 → 오류 없음 확인

---

### TASK-048 `v0`
**CreateCalendarDraftUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 자연어 일정 요청을 CalendarDraft로 변환한다 |
| **Scope** | `domain/usecase/CreateCalendarDraftUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/CreateCalendarDraftUseCase.kt` |
| **Dependencies** | TASK-033, TASK-016 |

**구현 원칙**
- 내부 계약 상 시간 누락은 ValidationError 반환 가능
- 대화 UX에서는 재확인 질문으로 변환 가능함을 주석으로 명시
- 상위 레이어가 재질문으로 바꿀 수 있도록 `reason`은 구조적으로 구분 가능해야 한다
  - 예: `"missing_time"`, `"missing_end_time"`, `"ambiguous_date"`

**Done Criteria**
- [ ] `CalendarDraftOutput` 파싱 및 `CalendarDraft` 변환
- [ ] `confidence < 0.7` → `isConfident = false`
- [ ] 시간 정보 누락 시 `ValidationError`
- [ ] 저장 동작 없음
- [ ] 내부 UseCase 실패를 Chat UX에서 재확인 질문으로 변환할 수 있도록 error reason 명시

**Test Criteria**
- confidence 0.6 → `isConfident = false`
- missing_time reason 분기 확인

---

### TASK-049 `v0`
**ApproveAndSaveEventUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 사용자 승인 후 캘린더에 일정을 저장하고 Audit을 기록한다 |
| **Scope** | `domain/usecase/ApproveAndSaveEventUseCase.kt` 1개 파일 |
| **Files to Create** | `domain/usecase/ApproveAndSaveEventUseCase.kt` |
| **Dependencies** | TASK-046, TASK-035 |

**Done Criteria**
- [ ] 승인 시 `CalendarTool.insert()` 실행
- [ ] 거부 시 저장 없이 Audit만 기록
- [ ] 승인/거부 Audit 기록의 단일 책임 주체
- [ ] 결과와 무관하게 Audit 항상 기록

**Test Criteria**
- 승인 → insert 호출, 거부 → insert 미호출

---

### TASK-050 `v0`
**CalendarUiState / CalendarViewModel / CalendarScreen 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 일정 화면 UI와 상태 관리를 구현한다 |
| **Scope** | `feature/calendar/` 3개 파일 |
| **Files to Create** | `CalendarUiState.kt` `CalendarViewModel.kt` `CalendarScreen.kt` |
| **Dependencies** | TASK-047, TASK-048 |

**Done Criteria**
- [ ] 오늘/이번 주/초안 세그먼트 탭
- [ ] 권한 거부 시 안내 카드
- [ ] empty state 표시

**Test Criteria**
- 캘린더 권한 없을 때 안내 카드 확인

---

## Phase 13. 승인 플로우 Feature

---

### TASK-051 `v0`
**ApprovalUiState / ApprovalViewModel / ApprovalSheet 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 승인 바텀시트 UI와 상태 관리를 구현한다 |
| **Scope** | `feature/approval/` 3개 파일 |
| **Files to Create** | `ApprovalUiState.kt` `ApprovalViewModel.kt` `ApprovalSheet.kt` |
| **Dependencies** | TASK-038, TASK-049 |

**Done Criteria**
- [ ] CalendarDraft 내용 표시
- [ ] `isConfident = false` 시 경고 메시지
- [ ] 승인/거부 콜백 연결
- [ ] 승인 후 ApproveAndSaveEventUseCase 호출

**Test Criteria**
- 승인/거부 시 Audit 기록 확인

---

## Phase 14. 메모리 관리 Feature

---

### TASK-052 `v1`
**Task / Knowledge Memory 저장소 및 UseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | v1 Task / Knowledge Memory 저장, 조회, 검색 흐름을 구현한다 |
| **Scope** | `data/local/repository/` 2개 파일 + `domain/usecase/` 1개 파일 + DI 수정 |
| **Files to Create/Modify** | `KnowledgeRepositoryImpl.kt` `TaskRepositoryImpl.kt` `SearchKnowledgeUseCase.kt` `app/di/MemoryModule.kt` |
| **Dependencies** | TASK-017, TASK-024, TASK-025, TASK-026 |

**Done Criteria**
- [ ] `KnowledgeRepositoryImpl.searchRecent()` 구현
- [ ] `KnowledgeRepositoryImpl.searchByTags()` 구현
- [ ] `TaskRepositoryImpl` 구현
- [ ] `SearchKnowledgeUseCase` 구현
- [ ] `limit` 파라미터 지원
- [ ] DI 바인딩 추가

**Test Criteria**
- save 후 search 결과 포함 확인

---

### TASK-053 `v1`
**MemoryUiState / MemoryViewModel / MemoryScreen 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 5계층 메모리 조회 화면을 구현한다 |
| **Scope** | `feature/memory/` 3개 파일 |
| **Files to Create** | `MemoryUiState.kt` `MemoryViewModel.kt` `MemoryScreen.kt` |
| **Dependencies** | TASK-052 |

**Done Criteria**
- [ ] 메모리 유형 필터 칩
- [ ] export/import 버튼 위치 포함
- [ ] Paging 3 연결

**Test Criteria**
- 필터 변경 시 해당 유형만 표시

---

## Phase 15. 설정 Feature

---

### TASK-054 `v0`
**SettingsUiState / SettingsViewModel / SettingsScreen 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 정보, 응답 스타일, 정책 설정 화면을 구현한다 |
| **Scope** | `feature/settings/` 3개 파일 |
| **Files to Create** | `SettingsUiState.kt` `SettingsViewModel.kt` `SettingsScreen.kt` |
| **Dependencies** | TASK-027, TASK-021 |

**Done Criteria**
- [ ] 모델 파일 경로 및 상태 표시
- [ ] 응답 스타일 선택
- [ ] 모델 파일 없을 때 경로 안내
- [ ] 감사 로그 목록 보기 연결

**Test Criteria**
- 모델 NotFound 상태 → 경로 안내 확인

---

## Phase 16. Export / Import

---

### TASK-055 `v1`
**ExportManifest 데이터 클래스 정의**

| 항목 | 내용 |
|---|---|
| **Goal** | Export 파일의 메타 정보 스키마를 정의한다 |
| **Scope** | `data/local/file/ExportManifest.kt` 1개 파일 |
| **Files to Create** | `ExportManifest.kt` |
| **Dependencies** | TASK-005 |

**Done Criteria**
- [ ] `version = "1.0"` 기본값
- [ ] `encrypted = false`
- [ ] `@Serializable` 적용

**Test Criteria**
- JSON 직렬화/역직렬화 확인

---

### TASK-056 `v1`
**ExportImportManager — Export 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 5계층 메모리와 설정을 ZIP 파일로 export한다 |
| **Scope** | `ExportImportManager.kt` export 부분 |
| **Files to Create** | `ExportImportManager.kt` |
| **Dependencies** | TASK-055, TASK-026, TASK-027 |

**Done Criteria**
- [ ] ZIP 파일 생성
- [ ] manifest 포함
- [ ] DB 파일 복사 포함
- [ ] 저장 공간 부족 시 오류

**Test Criteria**
- ZIP 파일 존재 및 manifest 파싱 확인

---

### TASK-057 `v1`
**ExportImportManager — Import 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Export ZIP 파일을 검증하고 복원한다 |
| **Scope** | `ExportImportManager.kt` import 부분 추가 |
| **Files to Modify** | `ExportImportManager.kt` |
| **Dependencies** | TASK-056 |

**Done Criteria**
- [ ] manifest 버전 불일치 처리
- [ ] DB 스키마 불일치 처리
- [ ] 복원 완료 후 Audit 기록

**Test Criteria**
- export → import 왕복 확인

---

### TASK-058 `v1`
**ExportMemoryUseCase / ImportMemoryUseCase 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | Export/Import UseCase를 구현한다 |
| **Scope** | `domain/usecase/` 2개 파일 |
| **Files to Create** | `ExportMemoryUseCase.kt` `ImportMemoryUseCase.kt` |
| **Dependencies** | TASK-056, TASK-057, TASK-035 |

**Done Criteria**
- [ ] Export 시 개인정보 포함 경고 반환
- [ ] Import 시 overwriteExisting 지원
- [ ] 완료 후 Audit 기록

**Test Criteria**
- export 성공 응답에 warning 필드 포함 확인

---

## Phase 17. 웹 검색

---

### TASK-059 `v1`
**검색 전 사용자 확인 및 쿼리 미리보기 정책 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 질문 단위 검색 실행 전 사용자 확인과 쿼리 미리보기를 강제한다 |
| **Scope** | `assistant/guard/PreExecutionGuard.kt` `feature/chat/ChatUiState.kt` `feature/chat/ChatViewModel.kt` |
| **Files to Modify** | 위 3개 파일 |
| **Dependencies** | TASK-034, TASK-040 |

**Done Criteria**
- [ ] 검색 쿼리 미리보기 상태 정의
- [ ] 토글 ON 확정 시 해당 질문만 승인
- [ ] 민감 정보 포함 가능 시 확인 문구 표시
- [ ] 1회 전송 후 자동 초기화

**Test Criteria**
- 승인 없이 WebSearchTool 호출되지 않음

---

### TASK-060 `v1`
**WebSearchGateway 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | WebSearchTool 계약을 만족하는 v1 검색 Gateway를 구현한다 |
| **Scope** | `platform/network/WebSearchGateway.kt` |
| **Files to Create** | `WebSearchGateway.kt` |
| **Dependencies** | TASK-018, TASK-059 |

**구현 정책**
- 민감 정보 포함 가능 쿼리는 호출 전 차단 가능
- 대화 전문/일정 전문/메모 전문 그대로 전송 금지
- 실패 시 로컬 fallback 가능한 오류 반환
- 클라우드 LLM API 호출 금지

**Done Criteria**
- [ ] `WebSearchTool.search()` 구현
- [ ] Dispatchers.IO 사용
- [ ] 실패 시 `AppError.SearchError` 또는 `NetworkUnavailable`

**Test Criteria**
- 네트워크 없음 → 오류 반환
- 결과 없음 → Success(emptyList())

---

### TASK-061 `v1`
**SearchAgent 및 Orchestrator 검색 보강 연결**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델의 SearchOutput을 실제 검색 보강 응답 흐름에 연결한다 |
| **Scope** | `assistant/agent/SearchAgent.kt` `assistant/orchestrator/AssistantOrchestrator.kt` |
| **Files to Create/Modify** | `SearchAgent.kt` 및 Orchestrator |
| **Dependencies** | TASK-037, TASK-060 |

**Done Criteria**
- [ ] SearchOutput → SearchAgent 라우팅
- [ ] 검색 성공 시 응답 보강
- [ ] 실패 시 로컬 응답 유지 + 안내
- [ ] `logSearchUsed()` 호출

**Test Criteria**
- SearchOutput + 승인 true → WebSearchTool 호출 확인

---

### TASK-062 `v1`
**Chat 검색 토글 UI 및 검색 결과 표시**

| 항목 | 내용 |
|---|---|
| **Goal** | 질문 단위 검색 토글과 검색 보강 상태를 Chat UI에 표시한다 |
| **Scope** | `feature/chat/ChatScreen.kt` `feature/chat/ChatViewModel.kt` `feature/chat/ChatUiState.kt` |
| **Files to Modify** | 위 3개 파일 |
| **Dependencies** | TASK-059, TASK-061 |

**Done Criteria**
- [ ] 검색 토글 기본 OFF
- [ ] 확인 문구와 쿼리 미리보기 표시
- [ ] 질문 1회에만 검색 적용
- [ ] 검색 보강 여부 표시

**Test Criteria**
- 첫 질문 검색 ON → 다음 질문 OFF 확인

---

## Phase 18. 공유 인텐트

---

### TASK-063 `v1`
**ShareIntentHandler 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 외부 앱에서 공유된 텍스트 / 이미지를 Local Friday 입력으로 변환한다 |
| **Scope** | `platform/share/ShareIntentHandler.kt` |
| **Files to Create** | `ShareIntentHandler.kt` |
| **Dependencies** | TASK-018, TASK-045 |

**Done Criteria**
- [ ] `ACTION_SEND` 텍스트 처리
- [ ] `ACTION_SEND` 이미지 URI 처리
- [ ] 미지원 MIME 타입 → ValidationError
- [ ] 원본 앱 데이터 전문 로그 금지

**Test Criteria**
- 텍스트/이미지 공유 처리 확인

---

### TASK-064 `v1`
**MainActivity 공유 인텐트 수신 연결**

| 항목 | 내용 |
|---|---|
| **Goal** | Android manifest / MainActivity에서 공유 인텐트를 수신해 Chat 화면으로 연결한다 |
| **Scope** | `AndroidManifest.xml` `app/MainActivity.kt` `navigation/AppNavHost.kt` |
| **Files to Modify** | 위 3개 파일 |
| **Dependencies** | TASK-004, TASK-063 |

**Done Criteria**
- [ ] 텍스트 공유 intent-filter 등록
- [ ] 이미지 공유 intent-filter 등록
- [ ] 공유 수신 시 Chat 화면으로 이동
- [ ] 기존 흐름과 충돌 없음

**Test Criteria**
- 브라우저/갤러리 공유 확인

---

### TASK-065 `v1`
**공유 입력 ChatViewModel 연결**

| 항목 | 내용 |
|---|---|
| **Goal** | 공유된 텍스트 / 이미지가 기존 채팅 전송 흐름을 재사용하도록 연결한다 |
| **Scope** | `feature/chat/ChatViewModel.kt` `feature/chat/ChatUiState.kt` |
| **Files to Modify** | 위 2개 파일 |
| **Dependencies** | TASK-040, TASK-064 |

**Done Criteria**
- [ ] 공유 텍스트를 입력 draft로 표시
- [ ] 공유 이미지 URI를 프리뷰 상태로 표시
- [ ] 자동 전송하지 않음
- [ ] 취소 시 draft 초기화

**Test Criteria**
- 공유 수신 직후 자동 전송되지 않음

---

### TASK-066 `v1`
**공유 인텐트 오류 / 권한 처리**

| 항목 | 내용 |
|---|---|
| **Goal** | 공유 URI 접근 실패와 권한 문제를 사용자에게 안전하게 안내한다 |
| **Scope** | `platform/share/ShareIntentHandler.kt` `platform/storage/AndroidFileTool.kt` |
| **Files to Create/Modify** | `AndroidFileTool.kt` `ShareIntentHandler.kt` |
| **Dependencies** | TASK-063 |

**Done Criteria**
- [ ] URI 접근 실패 → 한국어 오류 메시지
- [ ] 10MB 초과 이미지 → `ImageTooLarge`
- [ ] 미지원 형식 → `UnsupportedImageFormat`
- [ ] 실패 시 앱 크래시 없음

**Test Criteria**
- 권한 없는 URI 공유 → 오류 안내 확인

---

## Phase 19. 운영 품질

---

### TASK-067 `v1`
**RuntimeMetricsCollector 구현**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 추론 시간, 온도, 연속 추론 횟수를 수집한다 |
| **Scope** | `runtime/metrics/RuntimeMetricsCollector.kt` |
| **Files to Create** | `RuntimeMetricsCollector.kt` |
| **Dependencies** | TASK-015, TASK-035 |

**Done Criteria**
- [ ] `InferenceMetrics` 기록
- [ ] 연속 추론 횟수 관리
- [ ] 43°C 이상 경고 이벤트 기록
- [ ] 48°C 이상 중단 이벤트 기록

**Test Criteria**
- Fake temperature provider로 threshold 분기 확인

---

### TASK-068 `v1`
**GemmaModelRunner 성능 / 발열 정책 연결**

| 항목 | 내용 |
|---|---|
| **Goal** | 모델 추론 전후 RuntimeMetricsCollector와 쿨다운 정책을 연결한다 |
| **Scope** | `runtime/gemma/GemmaModelRunner.kt` |
| **Files to Modify** | `GemmaModelRunner.kt` |
| **Dependencies** | TASK-022, TASK-067 |

**Done Criteria**
- [ ] 추론 전 48°C 이상이면 실행 중단
- [ ] 43°C 이상이면 경고 Audit 기록
- [ ] 연속 5회 추론 후 1초 쿨다운
- [ ] Main thread 추론 금지

**Test Criteria**
- 연속 5회 호출 → delay 확인

---

### TASK-069 `v1`
**스트리밍 응답 활성화**

| 항목 | 내용 |
|---|---|
| **Goal** | v1에서 ModelRunner onToken 콜백 기반 스트리밍 응답을 선택적으로 활성화한다 |
| **Scope** | `runtime/gemma/GemmaModelRunner.kt` `feature/chat/ChatViewModel.kt` |
| **Files to Modify** | 위 2개 파일 |
| **Dependencies** | TASK-022, TASK-040 |

**Done Criteria**
- [ ] FeatureFlag true일 때만 onToken 사용
- [ ] 기본값 false 유지
- [ ] 토큰 수신 중 UI 부분 업데이트
- [ ] 실패 시 최종 오류 상태 표시

**Test Criteria**
- FeatureFlag false → 비스트리밍 유지

---

### TASK-070 `v1`
**전체 Audit 이벤트 확장**

| 항목 | 내용 |
|---|---|
| **Goal** | v1 기능의 검색/export/import/발열 이벤트를 감사 로그에 기록한다 |
| **Scope** | `assistant/audit/AuditTrailService.kt` |
| **Files to Modify** | `AuditTrailService.kt` |
| **Dependencies** | TASK-035, TASK-058, TASK-061, TASK-067 |

**Done Criteria**
- [ ] `SEARCH_USED` 기록
- [ ] `EXPORT` / `IMPORT` 기록
- [ ] `THERMAL_WARNING` / `THERMAL_SHUTDOWN` 기록
- [ ] 민감 본문 로그 금지

**Test Criteria**
- 각 v1 이벤트 저장 확인

---

### TASK-071 `v1`
**오류 메시지 / 문자열 리소스 정리**

| 항목 | 내용 |
|---|---|
| **Goal** | 사용자 표시 오류 메시지를 한국어 strings.xml 리소스로 통일한다 |
| **Scope** | `res/values/strings.xml` 및 각 Feature Screen |
| **Files to Modify** | `res/values/strings.xml` `feature/*/*Screen.kt` |
| **Dependencies** | TASK-041, TASK-050, TASK-051, TASK-053, TASK-054 |

**Done Criteria**
- [ ] 사용자 표시 문자열은 `strings.xml` 사용
- [ ] 권한 / 모델 / 캘린더 / 이미지 / 검색 / export 오류 메시지 포함
- [ ] Composable에 한국어 문자열 직접 하드코딩 없음

**Test Criteria**
- 주요 오류 상태별 stringResource 사용 확인

---

### TASK-072 `v0`
**계약 정합성 테스트 추가**

| 항목 | 내용 |
|---|---|
| **Goal** | api_spec와 Kotlin 모델/매퍼의 필드 및 enum 정합성을 검증한다 |
| **Scope** | 단위 테스트 / 매퍼 테스트 |
| **Files to Create** | `test/contract/ContractConsistencyTest.kt` |
| **Dependencies** | TASK-009 ~ TASK-030 |

**구현 원칙**
- TASK-009는 매핑 정의
- TASK-072는 매핑 검증
- 계약 테스트는 구현 세부보다 직렬화 값과 shape 정합성에 집중한다

**Done Criteria**
- [ ] ErrorCode 매핑 테스트
- [ ] enum 직렬화 값 테스트 (`SttState`, `AuditEventType` 등)
- [ ] Entity ↔ Domain 매핑 테스트
- [ ] ActionCard typed payload 테스트
- [ ] ModelLoadState 상태 표현 정합성 테스트
- [ ] ChatRequest 필수 필드 정합성 테스트
- [ ] ScheduleData contract shape 테스트

**Test Criteria**
- 테스트 전부 통과

---

### TASK-073 `v0`
**v0 Acceptance Criteria 점검**

| 항목 | 내용 |
|---|---|
| **Goal** | PRD v0 AC1~AC8을 기준으로 완료 여부를 검증한다 |
| **Scope** | 문서화된 수동/자동 테스트 체크리스트 |
| **Files to Create** | `checklists/v0_acceptance.md` |
| **Dependencies** | TASK-001 ~ TASK-051, TASK-054, TASK-072 |

**Done Criteria**
- [ ] AC1 텍스트 채팅 표시/저장 검증
- [ ] AC2 음성 입력 채팅 흐름 검증
- [ ] AC3 이미지 응답 표시/저장 검증
- [ ] AC4 오늘 일정 표시 검증
- [ ] AC5 일정 초안 승인 저장 검증
- [ ] AC6 프로필/대화/필수 Audit 저장 검증
- [ ] AC7 권한 거부 안내 검증
- [ ] AC8 모델 파일 없음 설정 이동 검증
- [ ] sessionId 유지/복원 시나리오 검증
- [ ] Approval audit 중복 기록 없음 검증
- [ ] STT transcript 사용자 확인 후 전송 UX 검증

**Test Criteria**
- 체크리스트 Pass/Fail 기록 가능

---

### TASK-074 `v1`
**v1 Acceptance Criteria 점검**

| 항목 | 내용 |
|---|---|
| **Goal** | PRD v1 기능 완료 기준을 검증한다 |
| **Scope** | 문서화된 수동/자동 테스트 체크리스트 |
| **Files to Create** | `checklists/v1_acceptance.md` |
| **Dependencies** | TASK-055 ~ TASK-071 |

**Done Criteria**
- [ ] 검색 기본 OFF / 질문 단위 ON 검증
- [ ] 검색 전 확인 문구와 쿼리 미리보기 검증
- [ ] 검색 실패 fallback 검증
- [ ] Export / Import 왕복 검증
- [ ] 공유 인텐트 텍스트 / 이미지 연결 검증
- [ ] 스트리밍 FeatureFlag false 기본값 검증
- [ ] 검색 승인 상태가 질문 1회 후 초기화되는지 검증
- [ ] 검색 쿼리 미리보기와 실제 호출 쿼리 일치 검증
- [ ] Export/Import 후 모델/설정/session 복원 정책 검증 범위 명시

**Test Criteria**
- 체크리스트 Pass/Fail 기록 가능
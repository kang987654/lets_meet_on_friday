# agent_rules.md — Local Friday
## AI Coding Agent Rules

**문서 버전**: 1.0
**기준 문서**: PRD.md v1.0 / architecture.md v1.0 / api_spec.yaml v1.0 / tasks.md v1.0
**적용 대상**: 이 프로젝트의 모든 코드 생성 AI agent
**우선순위**: 이 문서의 규칙이 다른 일반 관행보다 항상 우선한다

---

## 목차

1. [General Principles](#1-general-principles)
2. [Coding Style Rules](#2-coding-style-rules)
3. [Architecture Rules](#3-architecture-rules)
4. [Naming Conventions](#4-naming-conventions)
5. [File Organization Rules](#5-file-organization-rules)
6. [API / Interface Rules](#6-api--interface-rules)
7. [Database Rules](#7-database-rules)
8. [Testing Rules](#8-testing-rules)
9. [Security Rules](#9-security-rules)
10. [Performance Rules](#10-performance-rules)
11. [Logging Rules](#11-logging-rules)
12. [MVP Scope Rules](#12-mvp-scope-rules)
13. [Error Handling Rules](#13-error-handling-rules)
14. [Dependency Injection Rules](#14-dependency-injection-rules)
15. [Coroutine & Flow Rules](#15-coroutine--flow-rules)
16. [UI / Compose Rules](#16-ui--compose-rules)

---

## 1. General Principles

### GP-001
**MUST** 코드를 생성하기 전에 해당 Task의 `Files to Create/Modify`, `Dependencies`, `Done Criteria`를 확인한다.
지정된 파일 외의 파일을 수정해서는 **MUST NOT**.

### GP-002
**MUST** 각 파일은 단일 책임을 가진다.
하나의 파일이 두 개 이상의 도메인 책임을 동시에 가져서는 **MUST NOT**.

### GP-003
**MUST** `tasks.md`에서 명시한 의존 순서를 지킨다.
상위 Phase의 파일이 완성되지 않은 상태에서 하위 Phase 파일을 구현해서는 **MUST NOT**.

### GP-004
**MUST NOT** 문서에 정의되지 않은 새로운 패턴, 라이브러리, 추상화를 임의로 도입한다.
새로운 기술 도입이 필요하다고 판단될 경우 구현을 중단하고 필요한 결정을 명시적으로 요청한다.

### GP-005
**MUST NOT** MVP 범위를 초과하는 코드를 생성한다.
`tasks.md`의 `P0`, `P1` 태스크만 구현한다.
`P2` 또는 `v2` 범위의 코드는 `// TODO(v1):` 또는 `// TODO(v2):` 주석만 남긴다.

### GP-006
**MUST** 생성한 모든 코드가 `tasks.md`의 `Done Criteria`를 100% 충족하는지 확인 후 완료로 표시한다.

---

## 2. Coding Style Rules

### CS-001 — 언어 및 포맷
**MUST** 모든 코드는 Kotlin으로 작성한다.
Java 코드를 생성해서는 **MUST NOT**.

### CS-002 — 들여쓰기
**MUST** 들여쓰기는 스페이스 4칸을 사용한다.
탭 문자를 사용해서는 **MUST NOT**.

### CS-003 — 줄 길이
**MUST** 한 줄의 길이는 120자를 초과하지 않는다.
120자를 초과하는 경우 반드시 줄 바꿈한다.

### CS-004 — 세미콜론
**MUST NOT** 줄 끝에 세미콜론을 사용한다.
Kotlin 표준을 따른다.

### CS-005 — 표현식 함수
**MUST** 함수 본문이 단일 표현식이면 표현식 함수로 작성한다.

```kotlin
// MUST
fun isReady(): Boolean = loadState.value is ModelLoadState.Ready

// MUST NOT
fun isReady(): Boolean {
    return loadState.value is ModelLoadState.Ready
}
```

### CS-006 — data class
**MUST** 상태를 표현하는 불변 객체는 `data class`로 정의한다.

```kotlin
// MUST
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false
)
```

### CS-007 — sealed class
**MUST** 닫힌 타입 계층(상태, 결과, 출력 타입)은 `sealed class` 또는 `sealed interface`로 정의한다.

```kotlin
// MUST
sealed class ModelLoadState {
    object Loading : ModelLoadState()
    data class Ready(val info: ModelInfo) : ModelLoadState()
    data class Error(val error: AppError) : ModelLoadState()
    data class NotFound(val path: String) : ModelLoadState()
}
```

### CS-008 — when 표현식
**MUST** `sealed class`에 대한 `when` 분기는 `else` 브랜치 없이 exhaustive하게 작성한다.
`else`를 사용하면 새로운 케이스 추가 시 컴파일 오류가 발생하지 않아 버그가 생긴다.

```kotlin
// MUST
when (output) {
    is ModelOutput.TextOutput -> { ... }
    is ModelOutput.CalendarDraftOutput -> { ... }
    is ModelOutput.SearchOutput -> { ... }
    is ModelOutput.KnowledgeSaveOutput -> { ... }
}

// MUST NOT
when (output) {
    is ModelOutput.TextOutput -> { ... }
    else -> { ... }  // 금지
}
```

### CS-009 — nullable 처리
**MUST** nullable 타입에 대해 `!!` 연산자를 사용해서는 **MUST NOT**.
대신 `?.let`, `?:`, `requireNotNull()`, 또는 명시적 null 체크를 사용한다.

```kotlin
// MUST NOT
val name = user!!.name

// MUST
val name = user?.name ?: "Unknown"
```

### CS-010 — 문자열 템플릿
**MUST** 문자열 연결에 `+` 연산자 대신 문자열 템플릿을 사용한다.

```kotlin
// MUST
val message = "안녕하세요, ${user.name}님"

// MUST NOT
val message = "안녕하세요, " + user.name + "님"
```

### CS-011 — 컬렉션 빌더
**MUST** 불변 컬렉션은 `listOf()`, `mapOf()`, `setOf()`를 사용한다.
가변 컬렉션이 필요한 경우에만 `mutableListOf()` 등을 사용한다.

### CS-012 — buildString
**MUST** 복잡한 문자열 조합에는 `buildString { }` 블록을 사용한다.
`StringBuilder`를 직접 사용해서는 **MUST NOT**.

### CS-013 — object vs companion object
**MUST** 상수 및 팩토리 메서드는 `companion object`가 아닌 최상위 수준 또는 별도 `object`에 정의한다.
단, Android 생태계 관례(예: Room Database)에서 `companion object`가 표준인 경우 예외를 허용한다.

### CS-014 — import
**MUST NOT** 와일드카드 import(`import com.localfriday.app.*`)를 사용한다.
각 import를 명시적으로 나열한다.

---

## 3. Architecture Rules

### AR-001 — 레이어 의존 방향
**MUST** 레이어 간 의존은 단방향 하향만 허용한다.

```
허용:
  Presentation → Domain
  Assistant Core → Domain
  Data → Domain
  Runtime → Domain
  Platform → Domain

금지 (MUST NOT):
  Domain → Presentation
  Domain → Data
  Domain → Android Framework
  Data → Presentation
```

### AR-002 — Domain 레이어 순수성
**MUST** `domain/` 패키지의 모든 파일에서 `android.*` import를 금지한다.
`android.*`가 포함된 파일을 `domain/` 패키지에 생성해서는 **MUST NOT**.

유일한 예외:
```kotlin
// 허용: kotlin.coroutines, kotlinx.coroutines만 허용
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
```

### AR-003 — Platform 레이어 격리
**MUST** `android.*` 의존성을 가진 코드는 반드시 `platform/` 패키지에만 위치한다.
`domain/`, `assistant/`, `data/` 패키지에서 Android SDK를 직접 참조해서는 **MUST NOT**.

```kotlin
// MUST NOT: domain/ 패키지에서 Android 직접 사용
class CalendarAgent {
    fun readEvents() {
        val cursor = context.contentResolver.query(...)  // 금지
    }
}

// MUST: CalendarTool 인터페이스 사용
class CalendarAgent(private val calendarTool: CalendarTool) {
    suspend fun readEvents() = calendarTool.readEvents(startMs, endMs)
}
```

### AR-004 — 인터페이스 우선
**MUST** 레이어 간 통신은 인터페이스를 통해서만 한다.
구현체 클래스를 다른 레이어에서 직접 참조해서는 **MUST NOT**.

```kotlin
// MUST NOT
class AssistantOrchestrator(
    private val runner: GemmaModelRunner  // 구현체 직접 참조 금지
)

// MUST
class AssistantOrchestrator(
    private val runner: ModelRunner  // 인터페이스 참조
)
```

### AR-005 — 쓰기 작업 경로
**MUST** 캘린더 쓰기는 반드시 아래 경로만을 통해 실행한다.
다른 경로로 `CalendarTool.insert()`를 호출해서는 **MUST NOT**.

```
ApprovalCoordinator
    → ApproveAndSaveEventUseCase
        → AndroidCalendarTool.insert()
```

### AR-006 — 검색 실행 경로
**MUST** `WebSearchTool.search()`는 반드시 `PreExecutionGuard`의 검사를 통과한 후에만 호출한다.
`PreExecutionGuard`를 우회하는 코드를 작성해서는 **MUST NOT**.

### AR-007 — ViewModel 책임
**MUST** ViewModel은 UI 상태 관리와 UseCase 호출만 담당한다.
ViewModel에서 비즈니스 로직을 직접 구현해서는 **MUST NOT**.
모든 비즈니스 로직은 UseCase에 위임한다.

```kotlin
// MUST NOT
class ChatViewModel {
    fun sendMessage(content: String) {
        // 비즈니스 로직 직접 구현 금지
        val prompt = buildPrompt(content)  // 금지
        val response = modelRunner.generate(prompt)  // 금지
    }
}

// MUST
class ChatViewModel(private val useCase: SendChatMessageUseCase) {
    fun sendMessage(content: String) {
        viewModelScope.launch {
            useCase(Request(content = content, ...))
        }
    }
}
```

### AR-008 — UseCase 단일 책임
**MUST** 각 UseCase는 하나의 비즈니스 동작만 수행한다.
하나의 UseCase에 두 개 이상의 비즈니스 시나리오를 구현해서는 **MUST NOT**.

### AR-009 — Repository 직접 접근
**MUST NOT** ViewModel이 Repository를 직접 주입받아 사용한다.
ViewModel은 반드시 UseCase를 통해서만 데이터에 접근한다.

### AR-010 — Orchestrator 단독 진입
**MUST** 모델 추론(`ModelRunner.generate()`)은 반드시 `AssistantOrchestrator`를 통해서만 호출한다.
ViewModel, UseCase, Agent가 `ModelRunner`를 직접 호출해서는 **MUST NOT**.

유일한 예외:
```kotlin
// 허용: GetTodayScheduleUseCase의 AI 요약 (선택적, 실패 허용)
modelRunner.generate(summaryPrompt).getOrNull()
```

---

## 4. Naming Conventions

### NC-001 — 파일명
**MUST** 파일명은 해당 파일의 주요 클래스/인터페이스 이름과 완전히 일치한다.

```
ChatViewModel.kt → class ChatViewModel
CalendarTool.kt  → interface CalendarTool
```

### NC-002 — 클래스명
**MUST** 클래스명은 PascalCase를 사용한다.

| 유형 | 패턴 | 예시 |
|---|---|---|
| UseCase | `{동사}{명사}UseCase` | `SendChatMessageUseCase` |
| ViewModel | `{화면명}ViewModel` | `ChatViewModel` |
| Repository Interface | `{도메인}Repository` | `ConversationRepository` |
| Repository Impl | `{도메인}RepositoryImpl` | `ConversationRepositoryImpl` |
| Tool Interface | `{도메인}Tool` | `CalendarTool` |
| Tool Impl | `{플랫폼}{도메인}Tool` | `AndroidCalendarTool` |
| Fake/Stub | `Fake{원본명}` / `Stub{원본명}` | `FakeModelRunner` |
| Entity | `{도메인}Entity` | `ConversationEntity` |
| DAO | `{도메인}Dao` | `ConversationDao` |
| Agent | `{도메인}Agent` | `CalendarAgent` |
| Screen | `{화면명}Screen` | `ChatScreen` |
| UiState | `{화면명}UiState` | `ChatUiState` |

### NC-003 — 함수명
**MUST** 함수명은 camelCase를 사용한다.

| 유형 | 패턴 | 예시 |
|---|---|---|
| suspend 함수 | 동사로 시작 | `save()`, `generate()`, `process()` |
| 반환 함수 | `get{명사}` | `getRecentBySession()` |
| 변환 함수 | `to{타입}` | `toEntity()`, `toDomain()` |
| 검사 함수 | `is{조건}`, `has{조건}` | `isConfident()`, `isInFlight()` |
| UseCase invoke | `operator fun invoke` | `operator fun invoke(request: Request)` |

### NC-004 — 변수명
**MUST** 변수명은 camelCase를 사용한다.
축약어는 사용해서는 **MUST NOT**.

```kotlin
// MUST
val sessionId = UUID.randomUUID().toString()
val maxContextTokens = Constants.MAX_CONTEXT_TOKENS

// MUST NOT
val sId = UUID.randomUUID().toString()
val mct = Constants.MAX_CONTEXT_TOKENS
```

### NC-005 — 상수명
**MUST** `object` 내 상수는 SCREAMING_SNAKE_CASE를 사용한다.

```kotlin
object Constants {
    const val MAX_CONTEXT_TOKENS = 4096         // MUST
    const val maxContextTokens = 4096           // MUST NOT
}
```

### NC-006 — StateFlow / MutableStateFlow
**MUST** `MutableStateFlow`는 `_` 접두사를 붙이고 `private`으로 선언한다.
외부에 노출하는 `StateFlow`는 접두사 없이 선언한다.

```kotlin
// MUST
private val _uiState = MutableStateFlow(ChatUiState())
val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

// MUST NOT
val uiState = MutableStateFlow(ChatUiState())  // public MutableStateFlow 금지
```

### NC-007 — 파라미터명
**MUST** 함수 파라미터가 3개 이상이면 named argument를 사용한다.

```kotlin
// MUST
assembler.assemble(
    context = contextData,
    userInput = input,
    searchEnabled = false
)

// MUST NOT
assembler.assemble(contextData, input, false)
```

### NC-008 — Boolean 변수
**MUST** Boolean 변수는 `is`, `has`, `can`, `should` 중 하나로 시작한다.

```kotlin
// MUST
val isLoading: Boolean
val hasError: Boolean
val canExecute: Boolean
val searchEnabled: Boolean  // 예외: UI 바인딩 목적의 경우 허용

// MUST NOT
val loading: Boolean
val error: Boolean
```

### NC-009 — Test 클래스명
**MUST** 테스트 클래스명은 `{테스트대상클래스명}Test`로 지정한다.

```
SendChatMessageUseCaseTest.kt
ResponseParserTest.kt
CalendarDraftTest.kt
```

---

## 5. File Organization Rules

### FO-001 — 파일 위치
**MUST** 모든 파일은 `architecture.md` Directory Structure에 정의된 위치에 생성한다.
임의로 새로운 패키지를 생성해서는 **MUST NOT**.

### FO-002 — 파일 크기
**MUST NOT** 하나의 파일이 300줄을 초과하게 작성한다.
300줄 초과 시 책임을 분리하여 별도 파일로 추출한다.

### FO-003 — 파일 내 선언 순서
**MUST** 파일 내 선언 순서를 아래 순서로 유지한다.

```kotlin
// 1. 패키지 선언
package com.localfriday.app.domain.usecase

// 2. import (알파벳 순서)
import ...

// 3. 최상위 상수 (있는 경우)
private const val TAG = "SendChatMessageUseCase"

// 4. 주요 클래스/인터페이스
class SendChatMessageUseCase @Inject constructor(...) {

    // 5. 중첩 클래스/데이터 클래스 (companion 제외)
    data class Request(...)

    // 6. 프로퍼티
    private val tag = TAG

    // 7. 초기화 블록 (있는 경우)
    init { ... }

    // 8. 공개 함수
    suspend operator fun invoke(request: Request): Result<...>

    // 9. 비공개 함수
    private fun validate(request: Request): Result<Unit, AppError>

    // 10. companion object (있는 경우)
    companion object { ... }
}
```

### FO-004 — 변환 함수 위치
**MUST** Entity ↔ Domain 변환 함수는 해당 Entity 파일 내 확장 함수로 정의한다.
별도 Mapper 클래스를 생성해서는 **MUST NOT**.

```kotlin
// ConversationEntity.kt 내에 정의 (MUST)
fun ConversationEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    sessionId = sessionId,
    ...
)

fun ChatMessage.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    sessionId = sessionId,
    ...
)
```

### FO-005 — Feature 파일 구성
**MUST** 각 `feature/` 디렉토리는 정확히 3개의 파일로 구성한다.

```
feature/{name}/
├── {Name}Screen.kt      // Composable only
├── {Name}ViewModel.kt   // ViewModel only
└── {Name}UiState.kt     // State data class only
```

추가 Composable이 필요한 경우에만 `feature/{name}/components/` 하위에 추가한다.

### FO-006 — TODO 주석 형식
**MUST** 미구현 항목은 아래 형식의 TODO 주석으로 표시한다.

```kotlin
// TODO(v1): LiteRT-LM 실제 SDK 연결
// TODO(v2): 벡터 유사도 검색 추가
// TODO(TASK-020): GemmaModelRunner 구현 후 교체
```

`// TODO:` 단독 사용은 **MUST NOT**.
어떤 버전 또는 태스크에서 구현할지 반드시 명시한다.

---

## 6. API / Interface Rules

### API-001 — Result 래퍼 필수
**MUST** 모든 suspend 함수의 반환 타입은 `Result<T, AppError>`를 사용한다.
`Exception`을 직접 throw해서는 **MUST NOT**.

```kotlin
// MUST
suspend fun save(message: ChatMessage): Result<Unit, AppError>
suspend fun generate(prompt: String): Result<String, AppError>

// MUST NOT
suspend fun save(message: ChatMessage)         // 반환 타입 없음 금지
suspend fun generate(prompt: String): String   // 예외 던질 수 있는 반환 금지
```

### API-002 — AppError 변환
**MUST** 외부 예외(`Exception`)는 반드시 catch 후 `AppError`로 변환한다.
catch 블록에서 다시 throw해서는 **MUST NOT**.

```kotlin
// MUST
} catch (e: SecurityException) {
    Result.Failure(AppError.PermissionDenied(permission))
} catch (e: Exception) {
    Result.Failure(AppError.DbWriteError("conversations"))
}

// MUST NOT
} catch (e: Exception) {
    throw e  // 금지
}
```

### API-003 — onSuccess / onFailure 체이닝
**MUST** `Result`를 소비할 때 `when` 분기 또는 `onSuccess`/`onFailure` 확장 함수를 사용한다.

```kotlin
// MUST
result
    .onSuccess { data -> _uiState.update { it.copy(data = data) } }
    .onFailure { error -> _uiState.update { it.copy(error = error) } }

// 또는 MUST
when (result) {
    is Result.Success -> ...
    is Result.Failure -> ...
}
```

### API-004 — getOrElse 패턴
**MUST** 함수 내에서 Result 실패 시 조기 반환할 때 `getOrElse` 패턴을 사용한다.

```kotlin
// MUST
val context = contextBuilder.build(sessionId)
    .getOrElse { return Result.Failure(it) }

// MUST NOT
val contextResult = contextBuilder.build(sessionId)
if (contextResult is Result.Failure) return Result.Failure(contextResult.error)
val context = (contextResult as Result.Success).data
```

### API-005 — 인터페이스 변경 금지
**MUST NOT** `domain/` 패키지의 인터페이스 시그니처를 태스크 범위 밖에서 변경한다.
인터페이스 변경이 필요한 경우 해당 인터페이스를 정의한 Task를 기준으로 변경한다.

### API-006 — Fake 구현체 필수
**MUST** 새로운 `domain/` 인터페이스를 구현할 때 반드시 `Fake{이름}` 테스트용 구현체를 함께 생성한다.
Fake 없이 인터페이스만 생성해서는 **MUST NOT**.

### API-007 — 모델 출력 파싱 Fallback
**MUST** `ResponseParser`는 JSON 파싱 실패 시 절대 예외를 전파하지 않는다.
파싱 실패 시 반드시 `ModelOutput.TextOutput(rawOutput)`으로 fallback한다.

```kotlin
// MUST
return try {
    val json = JSONObject(rawOutput)
    // ...파싱 시도
} catch (e: Exception) {
    ModelOutput.TextOutput(rawOutput)  // 항상 fallback
}
```

---

## 7. Database Rules

### DB-001 — exportSchema 필수
**MUST** `@Database` 애노테이션에 `exportSchema = true`를 설정한다.
`exportSchema = false`를 사용해서는 **MUST NOT**.

```kotlin
// MUST
@Database(version = 1, entities = [...], exportSchema = true)
```

### DB-002 — 버전 증가 규칙
**MUST** 스키마 변경 시 반드시 DB version을 +1 증가시킨다.
스키마를 변경하고 version을 유지해서는 **MUST NOT**.

### DB-003 — AutoMigration 우선
**MUST** 컬럼 추가 등 단순 변경은 `@AutoMigration`을 사용한다.
테이블명/컬럼명 변경 또는 삭제는 `AutoMigrationSpec`과 수동 `Migration`을 함께 사용한다.

### DB-004 — fallbackToDestructiveMigration 제한
**MUST** `fallbackToDestructiveMigration()`은 개발 중에만 사용한다.
실사용이 시작된 후에는 반드시 제거한다.
프로덕션 빌드에 `fallbackToDestructiveMigration()`이 포함되어서는 **MUST NOT**.

### DB-005 — DAO 메서드 규칙
**MUST** DAO의 모든 데이터 변경 메서드(`@Insert`, `@Update`, `@Delete`)는 `suspend` 함수로 선언한다.
Flow를 반환하는 쿼리 메서드만 non-suspend로 선언할 수 있다.

```kotlin
// MUST
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insert(entity: ConversationEntity)

@Query("SELECT * FROM conversations ORDER BY created_at DESC")
fun getPaged(): PagingSource<Int, ConversationEntity>  // Flow/PagingSource는 suspend 없음
```

### DB-006 — Primary Key 타입
**MUST** 모든 Entity의 Primary Key는 `String` 타입의 UUID를 사용한다.
Auto-increment Integer를 Primary Key로 사용해서는 **MUST NOT**.

```kotlin
// MUST
@PrimaryKey val id: String  // UUID String

// MUST NOT
@PrimaryKey(autoGenerate = true) val id: Int
```

### DB-007 — Boolean 저장
**MUST** SQLite에 Boolean 값을 저장할 때 `Int` 타입(`0` = false, `1` = true)으로 저장한다.
`Boolean` 타입을 SQLite에 직접 저장해서는 **MUST NOT**.

```kotlin
// MUST (Entity에서)
@ColumnInfo(name = "search_used") val searchUsed: Int = 0

// 변환 함수에서
val searchUsed = if (message.searchUsed) 1 else 0
```

### DB-008 — 인덱스 필수
**MUST** 조회 쿼리에서 `WHERE` 또는 `ORDER BY`에 사용되는 컬럼에는 `@Index`를 설정한다.

```kotlin
// MUST
@Entity(
    tableName = "conversations",
    indices = [Index("session_id"), Index("created_at")]
)
```

### DB-009 — 타임스탬프 타입
**MUST** 모든 타임스탬프는 `Long` 타입의 Unix timestamp(milliseconds)로 저장한다.
`String` 또는 `Date` 타입을 DB에 저장해서는 **MUST NOT**.

```kotlin
// MUST
val createdAt: Long = System.currentTimeMillis()

// MUST NOT
val createdAt: String = "2025-05-11T10:00:00"
val createdAt: Date = Date()
```

### DB-010 — Dispatcher 규칙
**MUST** Room DAO 호출은 반드시 `Dispatchers.IO`에서 실행한다.
`Dispatchers.Main`에서 직접 DB 접근해서는 **MUST NOT**.
단, Room의 `suspend` 함수는 자동으로 IO 스레드에서 실행되므로 `withContext(Dispatchers.IO)`가 추가로 필요하지 않다.

---

## 8. Testing Rules

### TR-001 — UseCase 단위 테스트 필수
**MUST** 모든 UseCase 파일 생성 시 동일 이름의 `*Test.kt` 파일을 함께 생성한다.

```
SendChatMessageUseCase.kt → SendChatMessageUseCaseTest.kt
CreateCalendarDraftUseCase.kt → CreateCalendarDraftUseCaseTest.kt
```

### TR-002 — Fake 사용 필수
**MUST** UseCase 테스트에서 실제 구현체(Android 의존) 대신 Fake 구현체를 사용한다.

```kotlin
// MUST
class SendChatMessageUseCaseTest {
    private val fakeModelRunner = FakeModelRunner()
    private val fakeRepository = FakeConversationRepository()
    // ...
}

// MUST NOT
class SendChatMessageUseCaseTest {
    @Inject lateinit var useCase: SendChatMessageUseCase  // 실제 구현체 주입 금지
}
```

### TR-003 — 테스트 케이스 필수 포함
**MUST** 각 UseCase 테스트는 최소 아래 케이스를 포함한다.

```
1. 정상 케이스 (Happy Path) — 기대 출력 확인
2. 빈 입력 케이스 — ValidationError 반환 확인
3. 의존 레이어 실패 케이스 — 오류 전파 확인
4. Edge Case — 도메인 특수 케이스
```

### TR-004 — 테스트 함수 네이밍
**MUST** 테스트 함수명은 아래 형식을 따른다.

```kotlin
// MUST: given_when_then 또는 should{동작}When{조건}
@Test
fun `빈 메시지 입력 시 ValidationError를 반환한다`()

@Test
fun `모델이 준비되지 않은 상태에서 ModelNotReady 오류를 반환한다`()
```

### TR-005 — Coroutine 테스트
**MUST** suspend 함수 테스트에는 `runTest`를 사용한다.
`runBlocking`을 사용해서는 **MUST NOT**.

```kotlin
// MUST
@Test
fun `메시지 전송 성공 테스트`() = runTest {
    val result = useCase(request)
    assert(result is Result.Success)
}

// MUST NOT
@Test
fun `메시지 전송 성공 테스트`() = runBlocking { ... }
```

### TR-006 — StateFlow 테스트
**MUST** `StateFlow` 값 검증에는 `.value`를 직접 확인하거나 `turbine` 라이브러리를 사용한다.

```kotlin
// MUST
viewModel.sendMessage("test")
advanceUntilIdle()
assertEquals(false, viewModel.uiState.value.isLoading)
```

### TR-007 — Parser 테스트 필수
**MUST** `ResponseParser`는 반드시 아래 케이스를 테스트한다.

```
1. 각 action 타입 정상 파싱 (4가지)
2. 잘못된 JSON → TextOutput fallback 확인
3. action 필드 없음 → TextOutput fallback 확인
4. confidence 경계값 (0.7 미만/이상)
```

---

## 9. Security Rules

### SEC-001 — 쓰기 작업 승인 강제
**MUST** `CalendarTool.insert()`는 반드시 사용자 승인 후에만 호출한다.
`ApproveAndSaveEventUseCase` 외의 경로로 `insert()`를 호출하는 코드를 작성해서는 **MUST NOT**.

### SEC-002 — 검색 정책 준수
**MUST** `WebSearchTool.search()`는 `PreExecutionGuard`의 `searchEnabled = true` 확인 후에만 호출한다.
`searchEnabled` 검사를 우회하는 코드를 작성해서는 **MUST NOT**.

### SEC-003 — 외부 네트워크 호출 제한
**MUST** MVP에서 외부 네트워크 호출은 `WebSearchGateway`만 허용한다.
다른 위치에서 HTTP 클라이언트를 생성하거나 외부 서버에 접근하는 코드를 작성해서는 **MUST NOT**.

### SEC-004 — 감사 로그 필수
**MUST** 아래 이벤트 발생 시 `AuditTrailService`를 통해 반드시 기록한다.

```
- 모델 추론 실행       → AuditEventType.MODEL_RUN
- 도구 호출           → AuditEventType.TOOL_CALL
- 사용자 승인         → AuditEventType.APPROVAL_GRANTED
- 사용자 거부         → AuditEventType.APPROVAL_REJECTED
- 웹 검색 실행        → AuditEventType.SEARCH_USED
- Export 실행         → AuditEventType.EXPORT
- Import 실행         → AuditEventType.IMPORT
- 오류 발생           → AuditEventType.ERROR
```

감사 로그를 기록하지 않고 위 이벤트를 처리해서는 **MUST NOT**.

### SEC-005 — 민감 정보 마스킹
**MUST** 감사 로그의 `eventDetail` 필드에 사용자 입력 전문을 저장할 때는 `Redaction.sanitize()`를 통과시킨다.
사용자 메시지 원문을 `eventDetail`에 직접 저장해서는 **MUST NOT**.

### SEC-006 — 권한 확인 선행
**MUST** Android 권한이 필요한 모든 기능 진입 전에 `PermissionManager`로 권한 상태를 확인한다.
권한 확인 없이 Calendar Provider, Microphone 등에 접근하는 코드를 작성해서는 **MUST NOT**.

### SEC-007 — Export 암호화 상태 명시
**MUST** Export 파일 생성 시 `ExportManifest.encrypted = false`를 명시적으로 설정한다.
MVP에서 암호화 로직을 구현해서는 **MUST NOT**.

```kotlin
// MUST
val manifest = ExportManifest(
    ...
    encrypted = false  // 명시적 선언
)
```

### SEC-008 — 개인정보 외부 전송 금지
**MUST NOT** 사용자 대화 내용, 캘린더 정보, 지식 메모를 외부 서버로 전송하는 코드를 작성한다.
이는 검색 기능의 `WebSearchTool`에도 적용된다.
검색 쿼리에 사용자 개인정보를 포함해서는 **MUST NOT**.

---

## 10. Performance Rules

### PR-001 — 모델 추론 스레드
**MUST** `ModelRunner.generate()` 및 `generateWithImage()` 구현에서 `Dispatchers.Default`를 사용한다.
`Dispatchers.Main`에서 모델 추론을 실행해서는 **MUST NOT**.

```kotlin
// MUST
override suspend fun generate(prompt: String, ...): Result<String, AppError> =
    withContext(Dispatchers.Default) {
        // 모델 추론
    }
```

### PR-002 — UI 블로킹 금지
**MUST** 모든 I/O 작업(DB, 파일, 네트워크)은 `Dispatchers.IO`에서 실행한다.
Main 스레드에서 직접 I/O 작업을 수행해서는 **MUST NOT**.

### PR-003 — 중복 요청 차단
**MUST** ViewModel에서 `isInFlight` 플래그로 중복 전송을 차단한다.

```kotlin
// MUST
private var isInFlight = false

fun sendMessage(content: String) {
    if (isInFlight) return  // 반드시 체크
    isInFlight = true
    viewModelScope.launch {
        try { useCase(request) }
        finally { isInFlight = false }  // 반드시 해제
    }
}
```

### PR-004 — 이미지 전처리 필수
**MUST** 이미지를 모델에 입력하기 전에 반드시 `ImageInputAdapter.process()`를 통해 전처리한다.
원본 크기 이미지를 모델에 직접 전달해서는 **MUST NOT**.

### PR-005 — 컨텍스트 토큰 제한
**MUST** `PromptAssembler`에서 조립하는 총 프롬프트 길이가 `Constants.MAX_CONTEXT_TOKENS`를 초과하지 않도록 한다.
대화 히스토리는 `Constants.MAX_CONVERSATION_TURNS` 이내로 제한한다.

### PR-006 — 발열 임계값 준수
**MUST** `RuntimeMetricsCollector`에서 온도를 모니터링하고 임계값 초과 시 아래 정책을 적용한다.

```kotlin
// MUST
when {
    temperature >= Constants.THERMAL_SHUTDOWN_CELSIUS -> {
        auditTrailService.logThermalShutdown()
        // 추론 중단
    }
    temperature >= Constants.THERMAL_WARNING_CELSIUS -> {
        auditTrailService.logThermalWarning()
        // 경고 표시
    }
}
```

### PR-007 — 쿨다운 강제
**MUST** 연속 추론 횟수가 `Constants.THERMAL_COOLDOWN_INFERENCE_COUNT`(5회)를 초과하면 1초 쿨다운을 적용한다.

```kotlin
// MUST
if (consecutiveInferenceCount >= Constants.THERMAL_COOLDOWN_INFERENCE_COUNT) {
    delay(1000L)
    consecutiveInferenceCount = 0
}
```

### PR-008 — StateFlow 불필요 재발행 금지
**MUST** `StateFlow` 업데이트는 실제 상태가 변경된 경우에만 수행한다.
동일한 값으로 `StateFlow`를 반복 업데이트해서는 **MUST NOT**.
`update { }` 블록 내에서 조건 확인 후 상태를 변경한다.

### PR-009 — Lazy Loading
**MUST** 목록 화면(Memory, Calendar, Audit)은 전체 데이터를 한 번에 로드하지 않는다.
`PagingSource`를 사용하여 필요한 만큼만 로드한다.
단, MVP 초기에는 `LazyColumn` + 단순 목록으로 시작하고 데이터가 100건 초과 시 Paging 3으로 전환한다.

---

## 11. Logging Rules

### LOG-001 — AppLogger 사용
**MUST** 모든 개발 로그는 `AppLogger`를 통해 출력한다.
`println()`, `System.out.println()`, `Log.d()` 직접 호출을 사용해서는 **MUST NOT**.

```kotlin
// MUST
AppLogger.d("ChatViewModel", "메시지 전송 시작")
AppLogger.e("GemmaModelRunner", "추론 실패", exception)

// MUST NOT
println("메시지 전송 시작")
Log.d("ChatViewModel", "메시지 전송 시작")
```

### LOG-002 — 태그 명명
**MUST** `AppLogger` 태그는 해당 클래스명을 사용한다.

```kotlin
// MUST
private const val TAG = "ChatViewModel"
AppLogger.d(TAG, "...")

// MUST NOT
AppLogger.d("TAG", "...")
AppLogger.d("chat", "...")
```

### LOG-003 — 감사 로그 vs 개발 로그 구분
**MUST** 비즈니스 이벤트(모델 실행, 승인, 검색)는 `AuditTrailService`를 사용한다.
디버깅 정보는 `AppLogger`를 사용한다.
두 목적을 혼용해서는 **MUST NOT**.

```kotlin
// MUST
auditTrailService.logModelRun(sessionId)  // 비즈니스 이벤트
AppLogger.d(TAG, "모델 추론 완료: ${metrics.totalInferenceMs}ms")  // 디버그

// MUST NOT
AppLogger.d(TAG, "모델 실행됨")  // 감사 목적으로 AppLogger 사용 금지
```

### LOG-004 — 오류 로그 필수
**MUST** `Result.Failure`를 반환하는 모든 지점에서 `AppLogger.e()`로 오류를 기록한다.
오류를 기록하지 않고 silently 실패해서는 **MUST NOT**.

```kotlin
// MUST
} catch (e: Exception) {
    AppLogger.e(TAG, "DB 쓰기 실패: ${e.message}", e)
    Result.Failure(AppError.DbWriteError("conversations"))
}
```

### LOG-005 — 민감 정보 로그 금지
**MUST NOT** 사용자 메시지 내용, 개인정보, 모델 응답 전문을 `AppLogger`로 출력한다.
디버그 로그에는 내용 요약 또는 길이만 표시한다.

```kotlin
// MUST
AppLogger.d(TAG, "메시지 전송: ${content.length}자")

// MUST NOT
AppLogger.d(TAG, "메시지 전송: $content")  // 내용 전체 출력 금지
```

### LOG-006 — 성능 메트릭 기록
**MUST** 모델 추론 완료 시 `RuntimeMetricsCollector`를 통해 아래 메트릭을 기록한다.

```kotlin
// MUST (모델 추론 후)
metricsCollector.record(InferenceMetrics(
    totalInferenceMs = elapsed,
    deviceTemperatureCelsius = currentTemperature,
    consecutiveInferenceCount = count
))
```

---

## 12. MVP Scope Rules

### MVP-001 — 범위 초과 구현 금지
**MUST NOT** `tasks.md`에 `P2`, `v1`, `v2`로 표시된 기능을 구현한다.
해당 위치에 `// TODO(v1):` 주석만 남긴다.

**v2 금지 목록**:
```
- Windows 앱 관련 코드
- 벡터 DB / 임베딩 모델
- 클라우드 동기화
- 다중 사용자 지원
- 음성 출력 (TTS)
```

**MVP에서 Stub으로만 구현**:
```
- WebSearchTool → StubWebSearchGateway (빈 결과만 반환)
- StreamingResponse → onToken 콜백 null 처리만 구현
```

### MVP-002 — 삭제 기능 금지
**MUST NOT** 어떤 데이터 삭제 기능도 구현한다.
`@Delete` DAO 메서드를 생성해서는 **MUST NOT**.
UI에 삭제 버튼을 추가해서는 **MUST NOT**.

### MVP-003 — 클라우드 모델 금지
**MUST NOT** GPT, Claude, Gemini API 등 클라우드 기반 모델 호출 코드를 작성한다.
`ModelRunner` 구현체에 외부 API 호출을 포함해서는 **MUST NOT**.

### MVP-004 — 세션 단위 검색 금지
**MUST NOT** 세션 전체에 걸쳐 검색을 활성화하는 로직을 구현한다.
검색 토글은 반드시 질문 단위(`sendMessage` 호출 1회)로 초기화되어야 한다.

```kotlin
// MUST: 메시지 전송 후 검색 토글 초기화
.onSuccess {
    _uiState.update { it.copy(searchEnabled = false) }  // 반드시 초기화
}
```

### MVP-005 — 스트리밍 기본 비활성
**MUST** `FeatureFlags.STREAMING_RESPONSE_ENABLED = false` 상태를 기본으로 유지한다.
스트리밍을 기본 활성화하는 코드를 작성해서는 **MUST NOT**.

### MVP-006 — Export 암호화 금지
**MUST NOT** Export 파일 암호화 로직을 구현한다.
`// TODO(v1): AES-256-GCM 암호화 추가` 주석만 남긴다.

### MVP-007 — 복잡한 Orchestration 금지
**MUST NOT** MVP에서 `assistant/harness/`, `assistant/planner/`, `assistant/session/` 하위 파일을 생성한다.
이 디렉토리들은 빈 상태로 유지한다.

---

## 13. Error Handling Rules

### EH-001 — 오류 전파 책임
**MUST** 각 레이어의 오류 처리 책임을 준수한다.

| 레이어 | 책임 |
|---|---|
| Platform / Data | try-catch → AppError 변환 후 Result.Failure 반환 |
| Repository / Tool 구현체 | 모든 예외를 AppError로 변환 |
| UseCase | Result 그대로 상위 전달 (변환 금지) |
| Orchestrator | 일부 오류에 대한 fallback 처리 허용 |
| ViewModel | Result → UiState 변환 |
| Screen | UiState.Error → 사용자 메시지 표시 |

### EH-002 — Fallback 정책
**MUST** 아래 오류는 앱을 중단하지 않고 fallback으로 처리한다.

```
검색 실패       → 로컬 응답 유지 + 안내 메시지
JSON 파싱 실패  → TextOutput fallback
STT 실패        → 텍스트 입력 유도
지식 검색 실패  → 빈 리스트로 계속 진행
AI 요약 실패    → 요약 없이 일정 목록만 반환
```

### EH-003 — 치명적 오류 처리
**MUST** 아래 오류는 사용자에게 명확한 안내와 함께 해당 기능을 비활성화한다.

```
ModelNotFound           → 설정 화면으로 이동 + 경로 안내
PermissionDenied        → 기능 비활성 + 설정 이동 버튼
ImportSchemaMismatch    → import 중단 + 오류 메시지
InsufficientStorage     → export 중단 + 필요 공간 안내
```

### EH-004 — 오류 메시지 언어
**MUST** 사용자에게 표시하는 오류 메시지는 한국어로 작성한다.
개발자용 `detail` 필드는 영어로 작성해도 된다.

```kotlin
// MUST
AppError.ValidationError("content", "메시지를 입력해주세요")  // 한국어
AppError.ModelInferenceError("OOM: insufficient memory")     // detail은 영어 허용
```

### EH-005 — 오류 상태 초기화
**MUST** 새로운 요청이 시작될 때 이전 오류 상태를 초기화한다.

```kotlin
// MUST
fun sendMessage(content: String) {
    _uiState.update { it.copy(isLoading = true, error = null) }  // error 초기화 필수
    ...
}
```

---

## 14. Dependency Injection Rules

### DI-001 — Hilt 사용 강제
**MUST** 모든 의존성 주입은 Hilt를 사용한다.
수동으로 인스턴스를 생성하거나 전달해서는 **MUST NOT**.

```kotlin
// MUST
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val useCase: SendChatMessageUseCase
) : ViewModel()

// MUST NOT
class ChatViewModel : ViewModel() {
    private val useCase = SendChatMessageUseCase(...)  // 수동 생성 금지
}
```

### DI-002 — Scope 규칙
**MUST** 아래 Scope 규칙을 준수한다.

| 컴포넌트 유형 | Scope |
|---|---|
| Repository 구현체 | `@Singleton` |
| ModelRunner 구현체 | `@Singleton` |
| Orchestrator / Service | `@Singleton` |
| UseCase | Scope 없음 (매번 생성) |
| ViewModel | `@HiltViewModel` |
| Tool 구현체 | `@Singleton` |

### DI-003 — 모듈 파일 위치
**MUST** 모든 Hilt 모듈은 `app/di/` 패키지에 위치한다.
다른 패키지에 `@Module` 클래스를 생성해서는 **MUST NOT**.

### DI-004 — @Binds vs @Provides
**MUST** 인터페이스와 구현체 바인딩에는 `@Binds`를 사용한다.
객체 생성이 필요한 경우에만 `@Provides`를 사용한다.

```kotlin
// MUST: 인터페이스 바인딩
@Binds @Singleton
abstract fun bindModelRunner(impl: GemmaModelRunner): ModelRunner

// MUST: 외부 객체 제공
@Provides @Singleton
fun provideDatabase(@ApplicationContext context: Context): LocalFridayDatabase =
    LocalFridayDatabase.buildDevelopment(context)
```

### DI-005 — Context 사용
**MUST** `Context`가 필요한 경우 `@ApplicationContext`를 사용한다.
`Activity` Context를 `@Singleton` 스코프 클래스에 주입해서는 **MUST NOT**.

---

## 15. Coroutine & Flow Rules

### CF-001 — viewModelScope 필수
**MUST** ViewModel에서 코루틴 실행은 반드시 `viewModelScope`를 사용한다.
`GlobalScope`를 사용해서는 **MUST NOT**.

```kotlin
// MUST
viewModelScope.launch { useCase(request) }

// MUST NOT
GlobalScope.launch { useCase(request) }
CoroutineScope(Dispatchers.Main).launch { ... }
```

### CF-002 — Dispatcher 기본 규칙
**MUST** 아래 Dispatcher 규칙을 준수한다.

| 작업 유형 | Dispatcher |
|---|---|
| UI 업데이트 | `Dispatchers.Main` (ViewModel에서 자동) |
| DB 접근, 파일 I/O | `Dispatchers.IO` |
| 모델 추론, 이미지 처리 | `Dispatchers.Default` |
| 단순 연산 | 기본 (Dispatcher 명시 불필요) |

### CF-003 — StateFlow 변환
**MUST** UI에 노출하는 모든 `MutableStateFlow`는 `asStateFlow()`로 읽기 전용으로 변환한다.

```kotlin
// MUST
private val _state = MutableStateFlow(ChatUiState())
val state: StateFlow<ChatUiState> = _state.asStateFlow()
```

### CF-004 — update 함수 사용
**MUST** `MutableStateFlow` 값 변경에는 `.value =` 대신 `.update { }` 함수를 사용한다.
단, 단순 교체 (`loading → idle`) 시에는 `.value =`도 허용한다.

```kotlin
// MUST: 기존 상태 기반 업데이트
_uiState.update { it.copy(isLoading = true, error = null) }

// 허용: 완전 교체
_loadState.value = ModelLoadState.Ready(modelInfo)
```

### CF-005 — finally 블록 정리
**MUST** `isInFlight` 같은 플래그는 반드시 `finally` 블록에서 초기화한다.

```kotlin
// MUST
viewModelScope.launch {
    isInFlight = true
    try {
        useCase(request)
    } finally {
        isInFlight = false  // 예외 발생해도 반드시 해제
    }
}
```

### CF-006 — Flow collect
**MUST** Composable에서 Flow를 구독할 때는 `collectAsStateWithLifecycle()`을 사용한다.
`collectAsState()`를 직접 사용해서는 **MUST NOT**.

```kotlin
// MUST
val uiState by viewModel.uiState.collectAsStateWithLifecycle()

// MUST NOT
val uiState by viewModel.uiState.collectAsState()
```

---

## 16. UI / Compose Rules

### UI-001 — 상태 호이스팅
**MUST** Composable은 상태를 직접 보유하지 않는다.
모든 상태는 ViewModel에서 관리하고 Composable은 상태를 표시만 한다.

```kotlin
// MUST
@Composable
fun ChatScreen(
    uiState: ChatUiState,          // 상태를 파라미터로 받음
    onSendMessage: (String) -> Unit // 이벤트를 람다로 받음
)

// MUST NOT
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val message = remember { mutableStateOf("") }  // Screen 내 상태 직접 보유 금지
}
```

### UI-002 — ViewModel 직접 참조 제한
**MUST** Screen Composable이 ViewModel을 직접 참조하는 것을 최소화한다.
`hiltViewModel()`은 최상위 Screen Composable에서만 호출한다.
중첩 Composable에는 필요한 데이터와 콜백만 전달한다.

### UI-003 — 5상태 표현 필수
**MUST** 모든 Screen Composable은 UiState의 5가지 상태를 명시적으로 처리한다.

```kotlin
// MUST
@Composable
fun ChatScreen(uiState: ChatUiState) {
    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorView(error = uiState.error, onRetry = onRetry)
        uiState.messages.isEmpty() -> EmptyState()
        else -> MessageList(messages = uiState.messages)
    }
}
```

### UI-004 — 하드코딩 색상 금지
**MUST** 색상 값을 Composable에 직접 하드코딩해서는 **MUST NOT**.
반드시 `MaterialTheme.colorScheme` 또는 정의된 색상 상수를 사용한다.

```kotlin
// MUST
Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary))

// MUST NOT
Box(modifier = Modifier.background(Color(0xFF5bc2e7)))
```

### UI-005 — 문자열 리소스
**MUST** 사용자에게 표시되는 모든 문자열은 `strings.xml`에 정의한다.
Composable에 한국어 문자열을 직접 작성해서는 **MUST NOT**.

```kotlin
// MUST
Text(text = stringResource(R.string.chat_empty_state))

// MUST NOT
Text(text = "대화를 시작해보세요")
```

### UI-006 — 접근성
**MUST** 모든 이미지, 아이콘 Composable에 `contentDescription`을 제공한다.
장식용 요소에만 `contentDescription = null`을 허용한다.

```kotlin
// MUST
Icon(
    imageVector = Icons.Default.Mic,
    contentDescription = stringResource(R.string.voice_input_button)
)
```

### UI-007 — 애니메이션 수치
**MUST** 화면 전환 애니메이션은 아래 수치를 준수한다.

```kotlin
// architecture.md 기준
탭 전환:    CrossFade + HorizontalSlide, duration = 220..280ms
바텀시트:   SlideIn,                     duration = 250..300ms
카드 등장:  Fade + 8..12dp Upward,       duration = 160..220ms
```

---

## Appendix A. 규칙 위반 감지 체크리스트

AI agent가 코드 생성 후 제출 전에 반드시 확인해야 할 항목이다.

### A-1. Architecture 위반 체크
```
[ ] domain/ 파일에 android.* import 없음
[ ] ViewModel에서 Repository 직접 주입 없음
[ ] 구현체 클래스를 다른 레이어에서 직접 참조 없음
[ ] CalendarTool.insert() 호출이 ApproveAndSaveEventUseCase에만 있음
[ ] ModelRunner.generate() 호출이 AssistantOrchestrator에만 있음 (요약 예외 제외)
```

### A-2. 필수 패턴 체크
```
[ ] 모든 suspend 함수가 Result<T, AppError> 반환
[ ] catch 블록이 AppError로 변환
[ ] isInFlight가 finally에서 해제
[ ] MutableStateFlow가 asStateFlow()로 노출
[ ] SearchEnabled가 메시지 전송 후 false로 초기화
```

### A-3. 보안 체크
```
[ ] 쓰기 작업 전 ApprovalCoordinator 확인
[ ] 검색 전 searchEnabled 확인
[ ] 감사 로그 기록 (MODEL_RUN, APPROVAL, SEARCH_USED)
[ ] 민감 정보가 AppLogger에 출력되지 않음
[ ] Export encrypted = false 명시
```

### A-4. MVP 범위 체크
```
[ ] P2/v2 기능 구현 없음
[ ] TODO 주석 형식 준수 (TODO(v1): 또는 TODO(v2):)
[ ] 삭제 기능 없음
[ ] 클라우드 모델 호출 없음
[ ] 세션 단위 검색 활성화 없음
```

### A-5. 테스트 체크
```
[ ] UseCase 파일에 대응하는 *Test.kt 파일 존재
[ ] Fake 구현체 사용
[ ] runTest 사용 (runBlocking 없음)
[ ] Happy Path + 오류 케이스 포함
```

---

## Appendix B. 빠른 참조 — 금지 목록

아래 코드 패턴은 **어떠한 경우에도 허용하지 않는다**.

```kotlin
// 1. android.* in domain/
import android.content.Context  // domain/ 패키지에서 금지

// 2. !! 연산자
user!!.name  // 금지

// 3. GlobalScope
GlobalScope.launch { }  // 금지

// 4. 직접 Exception throw
throw IllegalStateException("...")  // 금지, Result.Failure 사용

// 5. runBlocking in tests
runBlocking { useCase() }  // 금지, runTest 사용

// 6. 와일드카드 import
import com.localfriday.app.*  // 금지

// 7. else in sealed class when
when (output) { else -> }  // 금지

// 8. Public MutableStateFlow
val state = MutableStateFlow(...)  // public 금지

// 9. @Delete DAO
@Delete suspend fun delete(entity: ConversationEntity)  // MVP에서 금지

// 10. 하드코딩 색상
Color(0xFF5bc2e7)  // Composable에서 직접 사용 금지

// 11. println / Log.d 직접 사용
println("debug")   // 금지, AppLogger 사용
Log.d("tag", "")   // 금지, AppLogger 사용

// 12. 내용 전체 로그 출력
AppLogger.d(TAG, "content: $userMessage")  // 금지, 길이만 출력

// 13. MVP 범위 초과 구현
class VectorSearchEngine { }  // v2 범위, 구현 금지

// 14. SearchEnabled 초기화 누락
.onSuccess { }  // searchEnabled = false 초기화 필수
```
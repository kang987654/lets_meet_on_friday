# Trouble Shooting Log (v1)
> **문서 버전**: v1.0 | **최종 수정일**: 2026-07-31 | **상태**: Approved (승인)
> **관련 모듈**: `:app`, `:core`, `:domain`, `:data`

이 문서는 Local Friday 앱 개발 과정에서 발생한 주요 문제점(Trouble)과 이를 해결한 과정(Shooting)을 기록합니다.
유사한 문제가 다시 발생했을 때 참고하기 위한 목적입니다.

---

## 1. Room DB KSP 컴파일 오류 (Phase 1~2)
- **증상**: Room 라이브러리를 사용하여 Entity와 Dao를 구성한 후 빌드 시 KSP(Kotlin Symbol Processing) 관련 컴파일 오류가 발생함. (스키마 디렉토리 지정 문제 등)
- **원인**: Room KSP 설정 시 `room.schemaLocation` 인수가 올바르게 전달되지 않아 컴파일 타임에 스키마 파일을 생성할 수 없었음.
- **해결 방안**: `app/build.gradle.kts` 파일의 `ksp` 블록에 아래와 같이 스키마 내보내기 경로를 명시하여 해결함.
  ```kotlin
  ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
  }
  ```

## 2. AuditEvent와 AuditEntity 모델 속성 불일치 (Phase 5~6)
- **증상**: Domain Layer의 `AuditEvent`와 Data Layer의 `AuditEntity`를 매핑하는 과정에서 프로퍼티(Property)가 맞지 않아 매핑 에러가 발생.
- **원인**: 초기 설계 시 도메인 모델과 DB 엔티티 간에 `sessionId` 등의 공통 식별자가 누락되거나 이름이 다르게 설정되어 있었음.
- **해결 방안**: 두 모델 모두 `sessionId`를 기준으로 데이터를 동기화할 수 있도록 속성명을 맞추고, Mapper(확장 함수)를 수정하여 `toEntity()`와 `toDomain()`이 완벽하게 1:1로 대응되도록 수정함.

## 3. ChatMessage 도메인 모델 파라미터 불일치로 인한 빌드 에러 (Phase 8, TASK-037)
- **증상**: `AssistantOrchestrator` 클래스를 구현하고 `./gradlew build`를 수행했을 때 다음과 같은 컴파일 에러 발생.
  - `Unresolved reference 'ChatRole'`
  - `No parameter with name 'timestamp' found.`
  - `No value passed for parameter 'inputType'`
- **원인**: `AssistantOrchestrator` 구현 시 작성한 더미 파라미터가 실제 Phase 2에서 정의된 `ChatMessage` 도메인 모델 스펙과 일치하지 않았음. `ChatRole`은 `ChatMessage.Role` 내부에 위치했으며, `timestamp`가 아닌 `createdAt`을 사용하고 있었고, `inputType` 값이 필수로 지정되어 있었음.
- **해결 방안**:
  - `ChatRole` 임포트를 제거하고 `ChatMessage.Role.USER`, `ChatMessage.Role.ASSISTANT`로 수정.
  - `InputType.TEXT`를 넘겨주는 파라미터 `inputType = InputType.TEXT` 추가.
  - `timestamp` 변수명을 `createdAt`으로 변경.
  - 수정 후 빌드 정상 통과(`BUILD SUCCESSFUL`).

---

## 4. AppError 생성자 및 클래스 누락에 따른 빌드 에러 (Phase 9, TASK-039)
- **증상**: `SendChatMessageUseCase` 구현 중 `ValidationError`와 `UnknownError`를 반환하려 했으나 빌드 에러 발생. (`No value passed for parameter 'reason'`, `Unresolved reference 'UnknownError'`)
- **원인**: 작업 지침서(`tasks.md`)에는 "빈 입력 ValidationError 반환"이라고 추상적(High-level)으로만 명시되어 있었습니다. 기존 베이스 코드(`AppError.kt`)에 정의된 `ValidationError`는 `(field, reason)` 두 개의 인자를 받도록 되어 있었고, `UnknownError`라는 타입은 존재하지 않았으나, 이를 사전 확인하지 않고 임의의 생성자(`reason` 단일 인자)로 호출한 것이 원인이었습니다.
- **해결 방안**: 
  - 코드를 확인하여 `ValidationError("message", "...")` 형태로 인자를 모두 채워주었습니다.
  - 존재하지 않는 `UnknownError` 대신, 기존에 존재하는 범용적 에러인 `ModelInferenceError`로 대체하여 에러 매핑.
  - (교훈): `tasks.md`는 큰 틀의 방향과 완료 조건을 제시하는 문서이므로, 세부 도메인 모델이나 에러 클래스를 사용할 때는 반드시 기존에 구현된 코드를 미리 조회(View)하여 스펙을 맞추는 것이 중요합니다.

## 5. 뷰모델 구현 시 DI 인터페이스 바인딩 누락 및 프로퍼티명 불일치 (Phase 9, TASK-040)
- **증상**: `ChatViewModel` 작성 후 빌드 시 다음 오류들이 연쇄적으로 발생.
  - `SessionStore` unresolved reference 및 `getActiveSessionId` 미존재
  - `AgentResult.Text` 모델의 `text` 속성 없음 (Unresolved reference 'text')
  - `ModelRunner` DI 바인딩 누락 (`MissingBinding` 에러)
- **원인**: 
  - `SessionStore` 패키지 경로를 도메인 계층(`domain.memory`)으로 임의 추측했으나 실제로는 데이터 계층(`data.local.prefs`)에 있었음.
  - 메서드명(Flow인 `activeSessionIdFlow` 대신 `getActiveSessionId` 호출 등)이나 프로퍼티명(`content` 대신 `text`)을 소스 코드 조회 없이 직감에 의존해 작성함.
  - `ModelRunner` 인터페이스와 `GemmaModelRunner` 구현체를 이어주는 Hilt `@Binds` 설정을 깜빡함.
- **해결 방안**:
  - `SessionStore` 임포트 경로를 수정하고, 동기적 처리가 불가능한 Flow 수집 대신 `SavedStateHandle`을 1차 기준으로 Session ID를 즉각 유지하도록 `ChatViewModel` 로직 재설계.
  - `agentResult.text` -> `agentResult.content`로 프로퍼티명 교정.
  - `ModelModule`에 `@Binds` 코드를 추가하여 `ModelRunner` 구현체 제공.

## 6. VoiceOverlay 컴파일 중 캐시 문제 (Unresolved reference)

**문제 현상:**
`VoiceOverlay`를 포함하여 UI 상태에 `sessionId`를 추가하고 접근하려 할 때, `ChatScreen.kt`에서 다음과 같은 컴파일 에러 발생:
```text
Unresolved reference 'sessionId'.
```

**원인:**
`ChatUiState.kt` 파일에 `sessionId` 속성을 추가한 코드 갱신 내용이 빌드 툴(Kotlin/KSP/Hilt)의 기존 캐시와 엉키면서, IDE나 빌드 도구가 수정된 구조체를 올바르게 인식하지 못해 발생함. 부분 증분 빌드(Incremental Build) 과정에서 발생하기 쉬운 캐시 불일치.

**해결 방안:**
`./gradlew clean build` 명령어를 실행하여 기존 `/build` 폴더의 중간 컴파일 산출물과 Hilt/KSP가 생성한 코드를 전부 삭제(Clean)한 뒤, 처음부터 새로 빌드하여 해결.

---

## 7. UI Integration (MainActivity 연동 이슈)
- **증상**: `MainActivity`를 `AppNavHost`에 연결하는 과정에서 패키지 임포트 에러 및 빌드 실패 발생.
- **원인**: `material-icons-extended` 라이브러리가 존재하지 않는 상태에서 아이콘을 쓰려 했고, `MainActivity`가 엉뚱한 폴더(`com.kosmos.app.app`)에 위치해 있었음.
- **해결 방안**: 아이콘을 텍스트로 대체하고 패키지 경로를 올바르게 수정함.

## 8. Memory Data Layer (TASK-052)
- **증상 1**: `KnowledgeEntity`에 `tags`가 없어 검색이 불가능했음.
  - **해결 방안**: DB 클리어를 전제로 `tags`와 `updatedAt` 필드를 추가함.
- **증상 2**: Repository에서 반환할 때 `runCatching`의 `onFailure`에 `Throwable`을 던져서 타입 미스매치 발생.
  - **해결 방안**: `AppError.DbWriteError`, `AppError.SearchError` 등으로 매핑.
- **증상 3**: Hilt 컴파일 중 `AuditRepository`, `ConversationRepository` 의존성 주입 에러.
  - **해결 방안**: `MemoryModule`에 누락된 `@Binds`를 전부 추가함.

## 9. Memory UI & Paging 3 (TASK-053)
- **증상**: Compose에서 `PagingData`를 표시하기 위한 `collectAsLazyPagingItems()`가 동작하지 않음.
- **원인**: `androidx.paging:paging-compose` 의존성이 없었음.
- **해결 방안**: `build.gradle.kts`에 의존성을 추가하고, Windows `v2`(KMP) 확장에 대비해 Repository의 `PagingSource` 반환을 `Flow<PagingData<T>>` 래핑 구조로 선제적으로 개편함.

## 10. Settings UI (TASK-054)
- **증상**: `SettingsScreen`에서 `ModelLoadState.Error` 분기가 없어 `when` 문 컴파일 에러가 나고, `NotFound`에서 `message` 변수를 찾지 못함.
- **원인**: sealed class의 프로퍼티 구조를 착각함(`expectedPath`인데 `message`로 호출).
- **해결 방안**: 클래스 선언부를 조회(view)한 뒤 분기문과 변수명을 올바르게 고쳐 통과.

## 11. AppError 확장 및 ErrorCodeMapper 누락 (Phase 16, Import 기능)
- **증상**: `ImportFailed` 예외를 `AppError`에 새로 추가한 뒤 빌드 시, `ErrorCodeMapper` 내부의 `when` 구문에서 `exhaustive`(분기 누락) 에러 발생.
- **원인**: `AppError`가 `sealed class`로 선언되어 있어, 하위 클래스가 추가되면 이를 처리하는 모든 `when` 구문에서 해당 케이스(`is AppError.ImportFailed`)를 반드시 명시해야 함. 그러나 `ErrorCode` 및 `ErrorCodeMapper` 업데이트를 깜빡함.
- **해결 방안**: `ErrorCode` enum에 `IMPORT_FAILED`를 추가하고, `ErrorCodeMapper`에 `is AppError.ImportFailed -> ErrorCode.IMPORT_FAILED` 매핑을 추가하여 해결.

## 12. Room Database 덮어쓰기 복원 시 Lock 에러 (Phase 16)
- **증상**: `Room` 데이터베이스 파일(`local_friday.db`, `-shm`, `-wal`)을 ZIP으로 묶어 내보낸 후 덮어쓰기 복원 시, 곧바로 다시 조회를 시도하면 "database is locked" 등 무결성 깨짐 에러 발생.
- **원인**: Room 데이터베이스 엔진이 아직 파일을 잡고 있거나 WAL(Write-Ahead Logging) 모드 동기화가 제대로 끝나지 않은 상태에서 OS 레벨로 파일을 강제 덮어쓰기 했기 때문.
- **해결 방안**: 데이터베이스 파일을 복원(덮어쓰기)한 직후 앱의 프로세스를 강제로 완전히 종료(`exitProcess(0)`)하여 시스템이 앱을 처음부터 다시 띄우고 DB를 새롭게 Open하게 함으로써 무결성 이슈를 해결함.

## 13. Hilt `@Binds` 누락 에러 (Phase 17)
- **증상**: `[Dagger/MissingBinding] com.kosmos.app.domain.tool.WebSearchTool cannot be provided without an @Provides-annotated method.`
- **원인**: `WebSearchGateway` 구현체를 새로 만들고 `SearchAgent`에 주입하려 했으나, 인터페이스인 `WebSearchTool`과 구현체 `WebSearchGateway`를 매핑해주는 DI 설정(`PlatformModule.kt`)을 누락함.
- **해결 방안**: `PlatformModule.kt`에 `@Binds` 어노테이션을 사용하여 `bindWebSearchTool` 매핑 함수 추가.

## 14. `AppError` 타입 불일치 및 파라미터 누락 에러 (Phase 18)
- **증상**: `Argument type mismatch: actual type is 'Unit', but 'AppError' was expected. Classifier 'data class ImageTooLarge : AppError' does not have a companion object...`
- **원인**: `AppError.ImageTooLarge(val sizeBytes: Long)` 처럼 인자가 필요한 데이터 클래스인데 인자 없이 `AppError.ImageTooLarge` 형태로 객체를 반환하려 했거나, 존재하지 않는 `EmptyInput`을 쓰려 함.
- **해결 방안**: 정해진 도메인 에러 규격에 맞게 `AppError.ValidationError("Share", "Empty text")`나 `AppError.ImageTooLarge(sizeBytes)` 처럼 적절한 파라미터를 담아 에러 인스턴스를 생성하도록 수정.

## 15. Kotlin 파일 내 import 구문 위치 에러 (Phase 19)
- **증상**: `RuntimeMetricsCollector.kt:13:2 Syntax error: imports are only allowed in the beginning of file.`
- **원인**: 기존 클래스 코드 중간에 새로운 라이브러리/클래스(`AuditTrailService` 등)를 주입하기 위해 관련 `import` 구문을 파일의 최상단 패키지 선언부가 아닌 중간 라인에 추가했기 때문.
- **해결 방안**: 해당 `import` 구문들을 `import javax.inject.Inject` 등이 선언된 파일 최상단의 기존 `import` 블록 위치로 모두 이동시켜 문법 오류를 해결.

## 16. JUnit 의존성 누락으로 인한 Unresolved reference 에러 (Phase 20)
- **증상**: `ContractConsistencyTest.kt` 컴파일 시 `Unresolved reference 'junit'`, `Unresolved 일치` 등의 에러와 함께 `compileDebugUnitTestKotlin` 실패.
- **원인**: 단위 테스트 파일을 작성했으나 `app/build.gradle.kts`에 기본 테스트 프레임워크인 `junit:junit` 라이브러리 의존성이 빠져있어 어노테이션과 단언문을 인식하지 못함.
- **해결 방안**: `app/build.gradle.kts` 내 `dependencies` 블록 하단에 `testImplementation("junit:junit:4.13.2")` 의존성을 추가하고 다시 빌드하여 해결.

## 17. Sealed Class 분기 처리 누락 에러 (Phase 26)
- **증상**: 컴파일 과정에서 `when' expression must be exhaustive. Add the 'is DbReadError' branch or an 'else' branch.` 에러 발생.
- **원인**: Domain 계층의 `AppError` sealed class에 `DbReadError`를 추가한 후, 이를 매핑하는 `ErrorCodeMapper.kt`의 `when` 구문과 `ErrorCode` enum에 신규 타입을 반영하지 않음. Kotlin 컴파일러는 sealed class의 모든 분기가 처리되었는지 엄격하게 검사하기 때문에 빌드에 실패함.
- **해결 방안**: 
  - `ErrorCode`에 `DB_READ_ERROR` 항목 추가.
  - `ErrorCodeMapper`의 `when` 문에 `is AppError.DbReadError -> ErrorCode.DB_READ_ERROR` 분기를 추가하여 모든 케이스를 포괄하게 함.

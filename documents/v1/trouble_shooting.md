# Trouble Shooting Log (v1)

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

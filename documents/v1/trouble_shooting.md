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

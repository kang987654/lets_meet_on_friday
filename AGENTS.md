# Kotlin & Android 프로젝트 전역 AI 에이전트 지침 (AGENTS.md)
> **적용 레포지토리**: `lets_meet_on_friday` (KOSMOS)
> **최종 개정일**: 2026-07-31

이 문서는 Google Antigravity 2.0 및 AI 코딩 에이전트를 위한 `lets_meet_on_friday` 프로젝트 전역 지침입니다.
모든 리팩터링, 신규 기능 구현 및 코드 수정 작업 시작 전 아래 지침을 반드시 준수해야 합니다.

---

## 1. 프로젝트 빌드 및 검증 명령어 (Commands)
코드 수정 완료 후, 터미널에서 다음 명령어를 실행하여 자율 검증을 완료할 것.

- **프로젝트 전체 빌드**: `./gradlew build -x test`
- **단위 및 Robolectric E2E 통합 테스트 실행**: `./gradlew test` (또는 특정 모듈 `./gradlew :app:testDebugUnitTest`)
- **안드로이드 정적 분석**: `./gradlew lintDebug`

---

## 2. 안전 수칙 및 단계별 절차 (Safety & Phases)

### ① 단계별 실행 절차 (Phase-by-Phase)
작업은 반드시 다음 단계를 거쳐 순차적으로 진행하며, 각 단계마다 보고 후 진행한다.
- **Phase 1 (탐색/분석)**: 소스 코드 수정 없이 레포 구조, 빈 파일, 하드코딩/목업, 영향도 분석 보고서 제출
- **Phase 2 (구조 정돈)**: 빈 파일 정돈, 하드코딩 상수 추출, 목업 코드 삭제 또는 `src/test/` 격리
- **Phase 3 (코어 구현/리팩터링)**: 비즈니스 로직 개선, KDoc 작성 및 자동 테스트 최종 검증

### ② 테스트 코드 보호 및 Robolectric 수칙 (Strict Rule)
- **기존 테스트 코드 수정 금지**: `src/test/` 하위의 기존 테스트 검증 로직(`@Test`)을 임의로 삭제하거나 오버라이딩하지 말 것. 테스트 실패 시 **구현 코드를 수정하여 통과**시켜야 한다.
- **Robolectric + Compose E2E 제약**:
  - `hiltViewModel()` 크래시 방지를 위해 Compose Screen 테스트 시 ViewModel을 수동으로 인스턴스화하여 주입한다.
  - Background 코루틴 검증 시 `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()` Polling 루프를 사용한다.
  - TextField 검색 시 Hint 텍스트(`onNodeWithText`) 대신 `hasSetTextAction()` 또는 `testTag`를 사용하며, 버튼 클릭 시 `onNodeWithContentDescription("Attach")` / `"Send"`를 사용한다.

---

## 3. KDoc 및 주석 작성 컨벤션

### ① KDoc 작성 (클래스, 인터페이스, 주요 함수)
- 모든 Public 함수와 클래스에는 KDoc(`/** ... */`)을 필수 작성한다.
- Core 클래스(Orchestrator, Agent, UseCase 등) 작성 시 아래 필수 항목을 포함한다:
  - **Role**: 클래스의 핵심 역할
  - **Architecture Context**: 소속 레이어 및 주요 의존성
  - **Key Flow**: 주요 동작 흐름

### ② 인라인 주석 (`//`)
- 코드가 *어떻게(How)* 동작하는지는 타입 시스템과 변수명으로 나타내고 주석을 생략한다.
- **왜(Why)** 이렇게 작성했는지에 대한 비즈니스 이유, 프레임워크 제약사항, 우회(Workaround) 로직에만 주석을 남긴다 (`// [WHY] ...`).

---

## 4. Kotlin & Android 개발 가이드라인

1. **AGP 9.0+ 라이브러리 모듈 수칙**:
   - 신규 모듈 생성 시 `build.gradle.kts`에 `id("org.jetbrains.kotlin.android")`를 **절대 재선언하지 않는다** (AGP 9.0+ 내장 플러그인 충돌 방지).
2. **Strict Type & Null Safety**:
   - `Any` 및 강제 언패킹(`!!`) 사용을 금지한다. `?` 연산자, `let`, `runCatching`, `elvis(?:)` 연산자를 활용한다.
3. **불변성(Immutability) 지향**:
   - `var` 대신 `val`을 사용하고, `MutableList`를 외부로 노출할 때는 `List` 또는 Immutable Collection 인터페이스로 래핑한다.
4. **비동기(Coroutines/Flow) 및 Hilt DI**:
   - Blocking 코드가 메인 스레드나 Coroutine 내부에서 실행되지 않도록 Dispatcher 분리(`Dispatchers.IO`)를 확인한다.
   - 클래스 분리/생성 시 Hilt DI 모듈(`@Provides`, `@Binds` 등)의 객체 등록 여부를 함께 체크한다.

---
name: local-friday-dev
description: Local Friday Android 앱 개발을 보조합니다. 오프라인 우선(Offline-First) 아키텍처, 레이어 의존성 규칙, 지정된 v1 문서(documents/v1/), 디자인 시스템(DESIGN.md) 및 Task 기반 Git 브랜치 전략을 준수하여 코드를 작성할 때 사용하세요.
---

# Local Friday Development Skill

이 스킬은 외부 통신 없이 기기 내부에서 동작하는 오프라인 우선(Offline-First) 개인 AI 비서 'Local Friday'의 코드를 작성하고 아키텍처 및 UI/UX 일관성을 유지하기 위한 구체적인 지침을 제공합니다.

## 이 스킬을 사용해야 할 때 (When to use this skill)

- Local Friday 프로젝트의 새로운 Task(`tasks.md` 기준)를 시작하거나 진행할 때
- 도메인 모델, UseCase, UI State, Repository 또는 모델 런타임 코드를 작성하거나 수정할 때
- `api_spec.yaml`에 정의된 스키마를 Kotlin Data Class로 구현할 때
- Jetpack Compose를 이용해 UI 화면과 컴포넌트를 설계하고 `DESIGN.md`의 디자인 토큰을 적용할 때
- Git 브랜치를 생성하고 작업한 코드를 커밋/병합(Merge)할 때

## 문서 우선순위 및 참조 경로 (Source of Truth)

이 프로젝트는 `documents/` 폴더 내에 문서를 관리합니다. 코드를 작성하거나 구조를 설계할 때 충돌이 발생하면 다음의 역할과 우선순위를 따릅니다.

**[중요] 참조 경로 제한 규칙:**
반드시 **`documents/v1/`** 경로 아래의 문서만 참조해야 합니다. 과거 기록이나 논의 내용이 담긴 `documents/project_journey/` 및 `documents/v0/` 경로는 절대 참조하지 마세요.

1. **제품 정책 및 범위:** `documents/v1/PRD.md` (최상위 비즈니스 룰)
2. **구조 및 책임:** `documents/v1/architecture.md`
3. **내부 계약:** `documents/v1/api_spec.yaml` (코드 구현의 절대 기준, 타입 충돌 시 최우선)
4. **구현 순서 및 작업 단위:** `documents/v1/tasks.md`
5. **시각 언어 및 컴포넌트 사양:** `documents/v1/DESIGN.md` (UI 구현 기준)

## 개발 규칙 및 컨벤션 (How to use it)

### 1. Git 및 브랜치 전략 (Git Workflow)
- **Task 단위 독립 작업:** 코드를 작성할 때는 `master` 브랜치에 직접 커밋하지 않고 반드시 Task 단위의 브랜치를 생성하여 작업합니다.
- **브랜치 네이밍 규칙:** `task/task-XXX-description` 형식을 사용합니다. 
  - *예시: `task/task-001-project-bootstrap`, `task/task-005-app-result-and-error`*
- **병합(Merge) 규칙:** Task 작업이 끝난 후 `master` 브랜치로 병합하기 전에, 반드시 `tasks.md`에 명시된 해당 Task의 **Done Criteria**와 **Test Criteria**를 모두 충족했는지 점검해야 합니다.

### 2. 레이어 분리 및 의존성 규칙 (Strict Layering)
- **단방향 하향 의존성:** `Presentation` → `Assistant Core` → `Domain` ← `Data`/`Runtime`/`Platform`
- **경고 (가장 중요):** `Domain` 레이어(`domain/` 패키지 하위)의 모든 파일에는 절대 `android.*` import가 포함되어서는 안 됩니다.

### 3. 타입 및 데이터 처리 패턴
- **결과 반환 (Result Wrapper):** Kotlin 표준 `Result<T>` 사용을 피하고, 반드시 프로젝트 공통 래퍼인 `AppResult<T>`(Success/Failure)를 사용합니다.
- **Payload 타입:** `ActionCard.payload` 등은 임의 타입(`Any`)이 아닌 액션에 따라 결정되는 Typed Payload(Sealed Class 등)로 명확히 정의합니다.
- **시간(Time) 표현:**
  - DB / 내부 저장: `Long` (Unix timestamp ms)
  - 도메인 로직 / 내부 모델: `Long` (ms) 또는 명시적 ISO 문자열
  - 경계 / 계약(Contract): `ISO 8601 String`을 우선하며, 변환은 Mapper 계층에서만 수행합니다.

### 4. UI 및 디자인 시스템 구현 (DESIGN.md 준수)
- **토큰 기반 UI 구현:** Compose UI 구현 시 하드코딩을 피하고 `DESIGN.md`에 명시된 토큰(`colors`, `typography`, `spacing`, `rounded`)을 `CompositionLocal` 또는 `MaterialTheme`에 매핑하여 사용합니다.
- **Elevation 제어:** 모바일 렌더링 성능 최적화를 위해 강한 그림자(Shadow) 사용을 지양하고, `hairline` 테두리와 `spacing`(여백)을 통해 레이어 계층(Depth)을 표현합니다.

### 5. 에러 처리 (Error Handling)
- 로직 내부에서는 구체화된 `AppError` (Sealed class)를 사용합니다.
- 직렬화 및 외부 표현(계약)으로는 `ErrorCode` (Enum)를 사용하며, 이 둘 간의 변환은 오직 `ErrorCodeMapper`에서 단일 책임으로 관리합니다.

### 6. UI 상태 관리 (State Management)
- ViewModel과 Compose UI 간의 통신은 항상 5가지 명시적 상태(`Idle`, `Loading`, `Success`, `Empty`, `Error`)를 가진 `UiState` Sealed Class를 통해 이루어져야 합니다.
- STT(음성 인식) 등 비동기 상태 전달은 Callback 대신 `StateFlow` 기반 상태 관찰을 기본으로 합니다.

### 7. 버전 통제 (Version Control Scoping)
- `tasks.md`에 명시된 버전(`v0`, `v1`, `v2`) 경계를 엄격히 지킵니다. `v0`(MVP) 작업을 진행할 때 `v1` 전용 기능을 미리 구현하지 말고 `// TODO(v1):` 주석이나 빈 확장 지점만 남겨둡니다.

## 실행 체크리스트 (Execution Checklist)

코드를 생성하거나 병합(Merge)하기 전에 다음 질문에 답하세요:

1. **문서 경로:** 참조한 기획/설계 문서가 `documents/v1/` 경로의 최신 문서가 맞는가?
2. **Git 브랜치:** `task/task-XXX-...` 명명 규칙을 준수한 브랜치에서 작업 중이며, 병합 전 `Done Criteria`와 `Test Criteria`를 완벽히 통과했는가?
3. **계약 확인:** 새로 만든 모델이 `api_spec.yaml`의 스키마 구조(타입, 필수값)와 완벽히 일치하는가?
4. **의존성 확인:** 이 파일이 속한 폴더가 아키텍처 다이어그램에서 허용된 의존성 방향을 따르는가? (Domain에 Android 의존성이 없는가?)
5. **디자인 확인:** Compose 컴포넌트가 `DESIGN.md`의 토큰을 사용하며, 무의미한 그림자나 브랜드 컬러 남용이 없는가?

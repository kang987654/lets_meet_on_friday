# [Architecture Refactoring] 최신 안드로이드 가이드라인 기반 구조 불일치 해소

기존 코드베이스에서 관성적으로 작성되었거나, 초기 설계 문서(`architecture.md`)와 불일치하는 디렉토리 구조를 최신 가이드라인(Now in Android 등)에 맞게 교정합니다. 본 계획은 새로운 기능 구현에 앞서 유지보수성과 확장성을 극대화하기 위한 기반 작업입니다.

## User Review Required

> [!WARNING]
> 본 리팩토링은 파일의 물리적 이동 및 패키지 선언(`package com.kosmos.app...`), 수백 개의 `import` 문 변경을 동반합니다. 
> AGENTS.md 규칙(규칙 2)에 따라 `assistant/assistant/` 같은 폴더 중첩을 막기 위해 `git mv` 대신 에이전트 편집 도구를 이용해 정확히 파일을 옮기고, 꼼꼼하게 컴파일을 확인하겠습니다. 진행을 승인해 주시면 실행하겠습니다.

## Open Questions

- `KosmosApp.kt`와 `MainActivity.kt`가 현재 `com.kosmos.app.app` 이라는 중복된 패키지명 아래에 있습니다. 이를 프로젝트 최상단 루트인 `com.kosmos.app`으로 끌어올리는 것도 본 리팩토링에 포함하는 것이 좋은지 의견 부탁드립니다. (본 계획서에는 포함시켰습니다.)

## Proposed Changes

### 1. Feature Directory Standardization (UI 도메인 종속 탈피)

최신 안드로이드 구조에서는 화면 요소와 UI 로직(ViewModel, State)을 묶어 최상단 `feature/` 패키지로 관리합니다. 단순 시각적 계층(`ui/`) 아래에 가둬두지 않습니다.
기존의 비어있는 `.gitkeep` 폴더들을 제거하고, `ui/feature/`의 모든 내용을 이동합니다.

#### [DELETE] `app/src/main/java/com/kosmos/app/feature/*` (빈 폴더들 정리)
#### [MODIFY] `app/src/main/java/com/kosmos/app/ui/feature/chat/` ➡️ `app/src/main/java/com/kosmos/app/feature/chat/`
#### [MODIFY] `app/src/main/java/com/kosmos/app/ui/feature/voice/` ➡️ `app/src/main/java/com/kosmos/app/feature/voice/`
#### [MODIFY] `app/src/main/java/com/kosmos/app/ui/feature/memory/` ➡️ `app/src/main/java/com/kosmos/app/feature/memory/`
#### [MODIFY] `app/src/main/java/com/kosmos/app/ui/feature/settings/` ➡️ `app/src/main/java/com/kosmos/app/feature/settings/`
#### [MODIFY] `app/src/main/java/com/kosmos/app/ui/feature/approval/` ➡️ `app/src/main/java/com/kosmos/app/feature/approval/`
#### [MODIFY] `app/src/main/java/com/kosmos/app/ui/feature/calendar/` ➡️ `app/src/main/java/com/kosmos/app/feature/calendar/`

*이후 `ui/` 폴더 내에는 `ui/theme/` 및 `ui/MainScreen.kt`(필요시 이동) 같은 공통 요소만 남습니다.*

---

### 2. DI(의존성 주입) 패키지 파편화 단일화

현재 Hilt DI 모듈들이 `com.kosmos.app.di`와 `com.kosmos.app.app.di` 두 곳으로 나뉘어 관리되고 있습니다. 이를 `architecture.md`의 본래 목적과 관리 편의성에 맞게 단일화합니다.

#### [DELETE] `app/src/main/java/com/kosmos/app/app/di/MemoryModule.kt` (중복 생성된 빈 파일)
#### [MODIFY] `app/src/main/java/com/kosmos/app/app/di/AppModule.kt` ➡️ `app/src/main/java/com/kosmos/app/di/AppModule.kt`
#### [MODIFY] `app/src/main/java/com/kosmos/app/app/di/AgentModule.kt` ➡️ `app/src/main/java/com/kosmos/app/di/AgentModule.kt`
#### [MODIFY] `app/src/main/java/com/kosmos/app/app/di/ModelModule.kt` ➡️ `app/src/main/java/com/kosmos/app/di/ModelModule.kt`
#### [MODIFY] `app/src/main/java/com/kosmos/app/app/di/PlatformModule.kt` ➡️ `app/src/main/java/com/kosmos/app/di/PlatformModule.kt`

---

### 3. App Entry Point 격상 (중복 네이밍 해소)

진입점이 되는 Application 클래스와 MainActivity를 `app.app`에서 루트 패키지로 끌어올립니다.

#### [MODIFY] `app/src/main/java/com/kosmos/app/app/KosmosApp.kt` ➡️ `app/src/main/java/com/kosmos/app/KosmosApp.kt`
#### [MODIFY] `app/src/main/java/com/kosmos/app/app/MainActivity.kt` ➡️ `app/src/main/java/com/kosmos/app/MainActivity.kt`

---

## Verification Plan

### Automated Tests
- 전체 이동 및 패키지/import 일괄 변경 후 `./gradlew assembleDebug` 명령을 실행합니다.
- 단방향 하향 의존성 규칙이나 DI 바인딩이 깨진 곳이 없는지 컴파일 성공 여부로 100% 검증할 수 있습니다.

### Manual Verification
- 구조가 변경되었으므로 `docs/architecture.md`의 구조도와 현재 구현된 실제 파일 트리가 완벽히 일치하는지 최종 대조합니다.

# Architecture Refactoring Tasks

- `[x]` **1. Feature Directory Standardization**
  - `[x]` `app/src/main/java/com/kosmos/app/feature/*` 하위 빈 폴더(`.gitkeep`) 전체 삭제
  - `[x]` `ui/feature/chat` 폴더 및 내부 파일 `feature/chat`으로 이동
  - `[x]` `ui/feature/calendar` 폴더 및 내부 파일 `feature/calendar`로 이동 (이미 이동되어 있던 파일 확인)
  - `[x]` `ui/feature/approval`, `memory`, `settings`, `voice` 폴더 및 내부 파일 `feature/` 하위로 이동
  - `[x]` 이동된 파일 내의 `package com.kosmos.app.ui.feature...` 선언을 `package com.kosmos.app.feature...`로 일괄 변경
  - `[x]` 이동된 패키지를 참조하는 타 파일들의 `import com.kosmos.app.ui.feature...` 구문 일괄 업데이트

- `[x]` **2. DI Module Consolidation**
  - `[x]` `app/src/main/java/com/kosmos/app/app/di/MemoryModule.kt` (중복 빈 파일) 삭제
  - `[x]` `app/src/main/java/com/kosmos/app/app/di/` 내부의 모듈들(`AppModule.kt`, `ModelModule.kt`, `AgentModule.kt`, `PlatformModule.kt`)을 `com.kosmos.app.di` 로 이동
  - `[x]` 이동된 DI 모듈들의 `package com.kosmos.app.app.di` 선언을 `package com.kosmos.app.di`로 변경

- `[x]` **3. App Entry Point Reorganization**
  - `[x]` `KosmosApp.kt`를 `com.kosmos.app.app`에서 `com.kosmos.app` 루트 패키지로 이동
  - `[x]` `MainActivity.kt`를 `com.kosmos.app.app`에서 `com.kosmos.app` 루트 패키지로 이동
  - `[x]` `AndroidManifest.xml` 내의 Application name 및 Activity name 속성 경로 업데이트
  - `[x]` 패키지 선언 변경 및 관련 import 업데이트

- `[x]` **4. Verification**
  - `[x]` `./gradlew clean assembleDebug` 명령을 통해 전체 빌드 성공 여부 확인 (성공)
  - `[x]` `docs/architecture.md` 문서 내용과 현재 디렉토리 구조 일치 여부 재확인


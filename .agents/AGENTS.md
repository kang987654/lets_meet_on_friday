# Workspace Rules

## 1. UTF-8 인코딩 보장 및 에이전트 도구 우선 사용
PowerShell 명령어를 통해 파일 내용을 직접 수정하거나 생성할 때, 기본 인코딩(Windows-1252/ANSI)으로 인한 한글 깨짐 현상이 발생할 수 있습니다. 이를 방지하기 위해 파일 수정 시 반드시 에이전트 전용 편집 도구(`multi_replace_file_content`, `replace_file_content`, `write_to_file`)를 우선적으로 사용합니다. 불가피하게 PowerShell 스크립트로 텍스트를 수정할 경우, 반드시 UTF-8(No BOM)을 명시(`[System.Text.UTF8Encoding] $False`)해야 합니다.

## 2. 패키지 리팩토링 및 폴더 구조 이동 시 주의점
`git mv` 등을 사용해 디렉터리를 이동할 때, 목적지에 동일한 이름의 폴더나 `.gitkeep`이 존재할 경우 `assistant/assistant/` 와 같은 원치 않는 중첩 구조가 생길 수 있습니다. 대규모 이동 시에는 가급적 대상 폴더의 상태를 `list_dir` 등으로 꼼꼼히 확인하고, 에이전트 편집 도구로 직접 파일을 이동/생성한 뒤 `git add`와 `git rm`을 명시적으로 사용하여 처리하는 것을 권장합니다.

## 3. Docs-as-code 문서화 전략
기존 기획/설계 문서를 업데이트해야 할 때, 원본 문서를 덮어쓰지 않고 보호합니다. 대신 `docs/` 폴더 내에 변경된 문서를 버전별 또는 진화형태로 새로 작성/복사하여 관리합니다. 에이전트 작업 내역(`task.md`, `implementation_plan.md`) 역시 `docs/agent/` 경로에 저장하고 커밋하여 프로젝트 팀원(또는 다른 기기)과 Git을 통해 공유되도록 합니다.

## 4. 안드로이드 통합 및 E2E 테스트 지향 (Robolectric + Compose UI Test)
통합(Integration) 및 E2E 테스트 작성을 최우선으로 하며 다음 핵심 원칙을 준수합니다:
- **수동 ViewModel 주입**: Robolectric 환경의 `hiltViewModel()` 크래시(Activity Hilt 주입 누락)를 피하기 위해, 테스트 클래스에서 의존성을 주입받아 ViewModel을 직접 생성하고 Compose 스크린에 전달(`ChatScreen(viewModel = vm)`)합니다.
- **Hilt 의존성 보존**: `@UninstallModules`로 특정 모듈 제거 시 연쇄 삭제되는 다른 의존성들(예: Tokenizer)은 반드시 `@BindValue`로 명시적 재정의(Mocking)해야 합니다.
- **비동기 사이드 이펙트 대기**: 백그라운드 코루틴 작업은 `waitForIdle()`로 동기화되지 않으므로, `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()`를 포함한 `while` 루프(Polling)로 상태 변경을 명시적으로 기다립니다.
- **TextField 안전 검색**: 힌트(`onNodeWithText`) 기반 검색은 구조에 따라 실패할 수 있으므로, 텍스트 입력창은 `hasSetTextAction()` 또는 `testTag`로 찾습니다.

## 5. 최신 AGP(9.0+) Kotlin 플러그인 주의점
프로젝트가 최신 Gradle 및 AGP 9.0 이상을 사용 중이므로, 새로운 안드로이드 라이브러리 모듈(예: core, domain 등)을 생성할 때 `build.gradle.kts` 파일에 더 이상 `id("org.jetbrains.kotlin.android")` 플러그인을 명시하지 마십시오. AGP 9.0부터는 코틀린 지원이 내장되어 있어 해당 플러그인을 중복 선언하면 빌드 에러(Crash)가 발생합니다.

## 6. Git Hard Reset 및 Clean 수행 시 주의
로컬 에이전트나 사용자가 생성했지만 아직 커밋(add/commit)하지 않은 폴더/파일들이 존재할 수 있습니다. 문제를 해결하기 위해 `git reset --hard` 또는 `git clean -fd`를 수행하기 전에는 반드시 `git status`를 통해 유실될 우려가 있는 **Untracked files** 가 있는지 먼저 확인하고 사용자에게 의도를 물어야 합니다.

## 7. 핵심 클래스 KDoc 헤더 작성 (Docs-as-code)
새로운 핵심 클래스(Orchestrator, Agent, UseCase 등)를 생성하거나 대규모 구조 변경을 할 때는 클래스 상단에 KDoc(`/** ... */`) 형식으로 헤더 주석을 작성합니다.
헤더에는 **클래스의 핵심 역할, Architecture Context(Layer 및 주요 의존성), Key Flow(동작 흐름)** 을 포함하여 AI 에이전트와 다른 개발자가 파일의 목적을 즉시 파악할 수 있도록 해야 합니다.

## 8. 에이전트 내장 도구(Built-in Tools) 절대 우선 사용 (PowerShell 파일 제어 금지)
PowerShell 명령어를 통한 파일 시스템 제어(탐색, 조회, 수정, 추가 등)는 권한 에러, 인코딩 깨짐, 프로세스 오버헤드를 유발하므로 사용을 엄격히 금지합니다.
- **조회/검색**: `Get-ChildItem`, `Get-Content`, `Select-String` 대신 반드시 `list_dir`, `view_file`, `grep_search` 사용
- **생성/수정/추가**: `Add-Content`, `Set-Content`, `echo`, `>` 등 셸 리다이렉션 대신 반드시 파일 편집 전용 API인 `write_to_file`, `replace_file_content`, `multi_replace_file_content` 사용

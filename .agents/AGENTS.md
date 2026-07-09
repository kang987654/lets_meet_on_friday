# Workspace Rules

## 1. UTF-8 인코딩 보장 및 에이전트 도구 우선 사용
PowerShell 명령어를 통해 파일 내용을 직접 수정하거나 생성할 때, 기본 인코딩(Windows-1252/ANSI)으로 인한 한글 깨짐 현상이 발생할 수 있습니다. 이를 방지하기 위해 파일 수정 시 반드시 에이전트 전용 편집 도구(`multi_replace_file_content`, `replace_file_content`, `write_to_file`)를 우선적으로 사용합니다. 불가피하게 PowerShell 스크립트로 텍스트를 수정할 경우, 반드시 UTF-8(No BOM)을 명시(`[System.Text.UTF8Encoding] $False`)해야 합니다.

## 2. 패키지 리팩토링 및 폴더 구조 이동 시 주의점
`git mv` 등을 사용해 디렉터리를 이동할 때, 목적지에 동일한 이름의 폴더나 `.gitkeep`이 존재할 경우 `assistant/assistant/` 와 같은 원치 않는 중첩 구조가 생길 수 있습니다. 대규모 이동 시에는 가급적 대상 폴더의 상태를 `list_dir` 등으로 꼼꼼히 확인하고, 에이전트 편집 도구로 직접 파일을 이동/생성한 뒤 `git add`와 `git rm`을 명시적으로 사용하여 처리하는 것을 권장합니다.

## 3. Docs-as-code 문서화 전략
기존 기획/설계 문서를 업데이트해야 할 때, 원본 문서를 덮어쓰지 않고 보호합니다. 대신 `docs/` 폴더 내에 변경된 문서를 버전별 또는 진화형태로 새로 작성/복사하여 관리합니다. 에이전트 작업 내역(`task.md`, `implementation_plan.md`) 역시 `docs/agent/` 경로에 저장하고 커밋하여 프로젝트 팀원(또는 다른 기기)과 Git을 통해 공유되도록 합니다.

## 4. 안드로이드 통합 및 E2E 테스트 지향
테스트 작성 시 단순 구문 검증 위주의 유닛 테스트를 피합니다. 대신 `Robolectric`과 `Compose Test Rule`을 활용하여 UI - ViewModel - Orchestrator - DB로 이어지는 데이터 파이프라인의 통합(Integration) 및 E2E 테스트 작성을 최우선으로 합니다.

# AGENTS.md

## 작업 기준 문서

이 저장소에서 **반드시 `documents/v1/` 아래 문서만 참고**한다.

- `documents/v1/PRD.md` → 제품 정책 / 범위 / UX 기준
- `documents/v1/architecture.md` → 구조 / 책임 / 레이어 / 의존 방향
- `documents/v1/api_spec.yaml` → 내부 계약의 source of truth
- `documents/v1/tasks.md` → 구현 계획 / 작업 단위 / 완료 기준
- `documents/v1/DESIGN.md` → UI 디자인 시스템 / 시각 언어 / 컴포넌트 스타일 기준

`documents/project_journey`, `documents/v0/`는 참고하지 않는다.

### UI 작업 추가 규칙
- UI를 작성하거나 수정할 때는 반드시 `documents/v1/DESIGN.md`를 먼저 참고한다.
- UI 구조와 동작은 `PRD.md`, `tasks.md`를 따르고,
  시각 표현과 컴포넌트 스타일은 `DESIGN.md`를 따른다.

---

## 문서 우선순위

충돌 시 아래 순서로 판단한다.

1. 정책 / 범위 / UX → `PRD.md`
2. 타입 / 필드 / enum / 계약 → `api_spec.yaml`
3. 구조 / 책임 / 레이어 → `architecture.md`
4. 구현 순서 / 완료 기준 → `tasks.md`

---

## 개발 방식

- 한 번에 **하나의 TASK만** 구현한다
- 현재 브랜치의 task 범위를 벗어나는 기능은 구현하지 않는다
- v0 작업 중 v1 기능은 `TODO(v1)`까지만 허용한다
- 관련 없는 파일은 수정하지 않는다
- 과잉 설계 / 불필요한 리팩터링 금지

---

## 브랜치 규칙

- task 단위 브랜치에서 작업한다
- 브랜치명은 아래 형식을 권장한다

```text
task/task-001-project-bootstrap
task/task-005-app-result-and-error
task/task-040-chat-viewmodel
```

- task 완료 후 기본 브랜치로 병합한다
- 병합 전 반드시 Done Criteria / Test Criteria를 다시 확인한다

---

## 구현 규칙

- Kotlin 표준 `Result<T>` 대신 `AppResult<T>`를 사용한다
- 타입 / 필드 / enum은 반드시 `api_spec.yaml` 기준
- `domain/`, `core/`에는 `android.*` import 금지
- `ActionCard.payload`를 `Any`로 구현하지 않는다
- 시간은 다음 원칙을 따른다:
  - DB 저장: `Long(ms)`
  - 경계 계약: `ISO 8601 string`
  - 변환은 mapper 계층에서 수행

---

## UI 구현 규칙

- UI를 구현할 때는 `documents/v1/DESIGN.md`를 시각적 기준으로 사용한다.
- 화면의 기능, 상태, 흐름은 `PRD.md`와 `tasks.md`를 따른다.
- 타입/상태/계약은 `api_spec.yaml`을 따른다.
- 구조/레이어 책임은 `architecture.md`를 따른다.

특히 아래 화면/컴포넌트는 `DESIGN.md` 기준을 우선 반영한다.

- `ChatScreen`
- `ApprovalSheet`
- `VoiceOverlay`
- `CalendarScreen`
- `SettingsScreen`
- `MemoryScreen` (v1)

다음 항목은 `DESIGN.md`에 맞춰 구현한다.

- 컬러 사용 방식
- 타이포그래피 계층
- spacing / padding / radius
- 카드 / 버튼 / 입력창 스타일
- 상태 카드 / 경고 배지 / 승인 시트 스타일
- chat bubble 스타일
- 그림자보다 border 중심의 depth 표현

---

## 책임 분리 규칙

- `ApprovalCoordinator`는 승인 대기 상태만 관리
- `ApproveAndSaveEventUseCase`는 실제 저장 + Audit 기록 담당
- `CalendarTool`은 read / insert만 담당
- `SpeechToTextTool`은 `state + transcript` 계약을 따른다
- transcript는 자동 전송하지 않는다
- 중복 일정은 기본적으로 차단보다 경고 우선이다

---

## 세션 / 검색 정책

- v0에서는 하나의 활성 세션(sessionId)을 유지한다
- 사용자가 “새 대화 시작”을 하기 전까지 동일 세션을 사용한다
- v1 검색은 질문 단위 ON만 허용
- 세션 전체 검색 ON은 금지

---

## 테스트 규칙

구현 후 반드시 아래를 점검한다.

- `tasks.md`의 Done Criteria 충족 여부
- `tasks.md`의 Test Criteria 충족 여부
- 문서 위반 여부
- 가능한 경우 테스트 코드 또는 테스트 포인트 제시

우선 검증 대상:
- `ErrorCodeMapper`
- `ModelLoadState`
- `SttState` / transcript 흐름
- Approval 흐름
- sessionId 유지
- 이미지 validation

---

## 출력 형식

가능하면 아래 형식으로 답한다.

1. 작업 범위 요약
2. 수정할 파일 목록
3. 코드 또는 패치
4. Done Criteria 체크
5. 테스트 포인트
6. 남은 TODO / 리스크

---

## 금지 사항

- `documents/v0/` 기준으로 구현
- 문서에 없는 기능 추가
- v0 작업 중 v1 기능 실제 구현
- domain/core에 Android 의존성 추가
- 관련 없는 파일 대규모 수정
- 자동 승인 쓰기 작업
- 세션 전체 검색 허용
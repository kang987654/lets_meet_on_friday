# 교차 검토 및 정렬 작업 수행

## 개요

이 문서는 Local Friday 프로젝트의 핵심 설계 문서들:

- `PRD.md`
- `architecture.md`
- `api_spec.yaml`
- `tasks.md`

에 대해 수행한 **교차 검토 및 정렬 작업**의 결과를 요약한 문서입니다.

목표는 다음과 같았습니다.

1. 문서 작성 순서인  
   **PRD → architecture → api_spec → tasks**  
   에 맞춰 내용이 자연스럽게 내려가도록 정렬
2. 문서 간 **누락 / 충돌 / 중복 / 보완점** 식별
3. 수정 권장안을 반영해 각 문서를 재정비
4. 최종적으로 **정책 / 구조 / 계약 / 구현 계획**이 일관되게 연결되도록 정리

---

## 작업 배경

초기 문서 세트는 전체적인 방향은 잘 잡혀 있었지만, 실제 구현 관점에서는 다음 문제가 있었습니다.

- PRD 정책이 architecture / api_spec / tasks까지 완전히 내려오지 않은 부분
- api_spec과 architecture/tasks 사이의 타입 및 책임 불일치
- v0 / v1 경계는 명시돼 있었지만, 일부 태스크/계약이 이를 흐리는 부분
- QA와 구현 검증 관점에서 부족한 부분
- 책임이 겹치거나 모호한 컴포넌트 존재

대표적인 문제 예시는 다음과 같았습니다.

- `ModelLoadState`가 문서마다 다르게 표현됨
- `SttState` enum 값이 문서마다 다름
- `ChatRequest`, `ScheduleData`, `SearchResultItem`, `ModelInfo` 등 핵심 타입 일부 누락
- `ApprovalCoordinator`와 `ApproveAndSaveEventUseCase`의 책임이 겹침
- `CalendarTool`에 초안 생성 책임이 섞여 있음
- `Result<T>` 사용으로 Kotlin 표준 `Result`와 혼동 가능
- 세션(`sessionId`) 유지 정책이 문서 전반에 충분히 드러나지 않음
- 에러 타입(`AppError`)과 계약용 코드(`ErrorCode`)의 매핑 규칙 부재
- 이미지 입력 경로와 문서 요약 범위가 사용자 기대치와 다르게 해석될 여지 존재

---

## 사용자 요청 흐름 요약

이번 작업은 아래 흐름으로 진행되었습니다.

### 1. 초기 요청
사용자는 다음과 같은 역할/상황을 제시했습니다.

- 이 프로젝트는 **앱 개발을 위한 PM, 기획자, 개발자, QA 팀** 관점에서 검토해야 함
- 문서는 `PRD → architecture → api_spec → tasks` 순서로 작성됨
- 각 문서의 작성 순서와 특성을 고려해  
  **보완 / 누락 / 충돌 / 중복**을 검토해달라는 요청

### 2. 1차 검토 수행
초기 문서 세트를 기준으로 다음을 검토했습니다.

- 문서 간 역할과 범위 정합성
- 정책과 구현 세부 간 충돌 여부
- 타입 누락 및 책임 중복
- v0/v1 경계가 실제 문서 구조에 잘 반영되어 있는지 여부

### 3. 문서 수정 방향 제안
이후 사용자는 각 문서를 수정 반영해 다시 정리해달라고 요청했고,
옵션 중 **패치형 개정안(Option B)** 방식이 선택되었습니다.

이에 따라:
- `PRD.md`
- `architecture.md`
- `api_spec.yaml`
- `tasks.md`

각 문서에서 **바꿔야 할 섹션만 교체/추가**하는 방식으로 수정 권장안을 제시했습니다.

### 4. api_spec / architecture / tasks / PRD 순차 재작성
이후 사용자는 순차적으로:

- `api_spec.yaml`
- `architecture.md`
- `tasks.md`
- `PRD.md`

각 문서의 **최종 개정본**을 요청했고,
이 과정에서 1차 검토안뿐 아니라 2차 검토에서 나온 보완 사항까지 반영했습니다.

### 5. 의존성 버전 업데이트
사용자는 직접 하나씩 확인한 최신 버전 정보를 제공했고,
이를 `tasks.md`와 Gradle 설정 기준에 반영하도록 요청했습니다.

최종적으로 반영한 주요 버전은 다음과 같습니다.

```toml
kotlin = "2.3.21"
compose-bom = "2026.04.01"
hilt = "2.59"
ksp = "2.3.7"
room = "2.8.4"
datastore = "1.2.1"
navigation = "2.9.7"
kotlin-serialization-plugin = "2.3.21"
kotlinx-serialization-json = "1.9.0"
paging = "3.4.2"
coroutines = "1.11.0"
androidx-hilt-navigation-compose = "1.3.0"
agp = "9.2.0"
```

또한 `libs.versions.toml` 예시와,
`TASK-001`의 Gradle wrapper / JDK / serialization 명확화 패치도 함께 정리했습니다.

### 6. 최종 교차 검토
모든 수정 사항 반영 후, 최종적으로 다시 한 번
**PRD / architecture / api_spec / tasks 4문서 교차 검토**를 수행했고,
문서 세트가 v0 구현 착수 가능한 수준인지 점검했습니다.

---

## 핵심 변경사항 요약

### 1. PRD.md

#### 주요 수정 내용
- v0 문서 요약은 **문서 파일 직접 파싱이 아니라 문서 이미지 기반 요약**으로 명확화
- v0 필수 Audit 범위에 `TOOL_CALL` 포함
- 일정 시간 누락 시:
  - 내부 처리 상 validation 실패 가능
  - 사용자 대화 UX에서는 **재확인 질문**으로 처리
- `sessionId` 기반 **단일 활성 세션 정책** 추가

#### 효과
제품 정책과 실제 구조/계약 문서 간 해석 차이를 줄였고,
사용자 기대치와 구현 범위를 맞췄습니다.

---

### 2. architecture.md

#### 주요 수정 내용
- `api_spec.yaml`을 **내부 계약의 source of truth**로 명시
- `CalendarTool`은 **read / insert만 담당**하도록 수정
- `ApprovalCoordinator`는 **pending state 관리만 담당**
- `ApproveAndSaveEventUseCase`가 **실제 저장 + Audit 기록**을 담당하도록 정리
- `SpeechToTextTool`을 `state + transcript` 계약으로 정리
- `ModelLoadState`, `ChatRequest`, `ScheduleData`, `ModelInfo`, `InferenceMetrics` 등 타입 구조 보강
- sessionId 유지 정책, Fake/Stub 정책, mapper 책임(`ErrorCodeMapper`, `TimeMapper`) 반영

#### 효과
구조 문서가 api_spec과 실제 구현 책임에 맞게 정렬되었고,
역할이 겹치던 컴포넌트들이 분리되었습니다.

---

### 3. api_spec.yaml

#### 주요 수정 내용
- `ModelLoadState`를 단순 문자열 enum이 아니라 **상태 + 부가정보 object**로 변경
- `SttState`를 `IDLE / LISTENING / TRANSCRIBING / DONE / ERROR`로 통일
- 누락 타입 추가:
  - `ChatRequest`
  - `ScheduleData`
  - `SearchResultItem`
  - `ModelInfo`
  - `InferenceMetrics`
- `ActionCard.payload`를 `oneOf` 기반 **typed payload**로 명시
- `AppError ↔ ErrorCode` 매핑 원칙 문서화
- `DuplicateEvent`를 **경고 우선** 정책으로 완화
- 이미지 입력의 표준 경로를 `/usecase/chat/image` 중심으로 정리
- STT 결과를 `state + transcript` 계약으로 정리
- 시간 표현 원칙:
  - DB/저장소: `Long(ms)`
  - 경계 계약: `ISO 8601 string`
  - 변환은 mapper 계층

#### 효과
이 문서가 실제로 **내부 계약의 기준 문서** 역할을 하도록 정리되었고,
architecture/tasks가 이를 참조해 정렬될 수 있는 상태가 되었습니다.

---

### 4. tasks.md

#### 주요 수정 내용
- Kotlin 표준 `Result<T>`와 혼동을 피하기 위해 **`AppResult<T>`** 로 통일
- 누락 타입 태스크 추가:
  - `ChatRequest`
  - `ScheduleData`
  - `SearchResultItem`
  - `ModelInfo`
  - `InferenceMetrics`
- `ApprovalCoordinator` / `ApproveAndSaveEventUseCase` 책임 분리 반영
- STT를 `state + transcript` 기반으로 구현하도록 조정
- `SessionStore`, `SavedStateHandle` 기반 sessionId 유지 정책 반영
- `ErrorCodeMapper` 태스크 추가 및 fallback 매핑 오류 수정
- Fake 구현 보강:
  - `FakeConversationRepository`
  - `FakeAuditRepository`
  - `FakeSpeechToTextTool`
  - `FakeCalendarTool`
  - `FakeModelRunner`
- 계약 정합성 테스트(`TASK-072`) 추가
- 의존성 버전과 `libs.versions.toml` 기준 정리
- Gradle wrapper / JDK / AGP / serialization plugin/library 구분 반영

#### 효과
`tasks.md`가 단순 작업 목록이 아니라,
실제로 구현 가능한 수준의 실행 계획 문서로 정리되었습니다.

---

## 공통 정렬 포인트

| 주제 | 최종 정리 내용 |
|---|---|
| 버전 범위 | v0: 오프라인 핵심 기능 / v1: 검색·Export/Import·공유 / v2: Windows·고도화 |
| 검색 정책 | 질문 단위 ON, 쿼리 미리보기 + 사용자 확인 필요 |
| 쓰기 작업 | 반드시 사용자 승인 후 실행 |
| STT 계약 | callback 중심이 아니라 `state + transcript` 기반 |
| 세션 정책 | 사용자가 새 대화를 시작하기 전까지 단일 활성 sessionId 유지 |
| 문서 요약 범위 | v0에서는 문서 이미지 기반 요약, 파일 직접 파싱 아님 |
| 중복 일정 처리 | 기본적으로 차단보다 경고 우선 |
| 에러 처리 | 내부는 `AppError`, 계약은 `ErrorCode`, `ErrorCodeMapper`로 단일 매핑 |
| 계약 기준 | `api_spec.yaml`을 내부 계약의 source of truth로 사용 |
| 구현 기준 | `tasks.md`는 api_spec + architecture 기준으로 재정렬 |

---

## 문서별 최종 역할

| 문서 | 최종 역할 |
|---|---|
| `PRD.md` | 제품 정책 / 범위 / UX 기준 |
| `architecture.md` | 구조 / 책임 / 레이어 / 의존 방향 |
| `api_spec.yaml` | 내부 계약의 source of truth |
| `tasks.md` | 구현 순서 / 작업 단위 / 검증 계획 |

---

## 결과

최종적으로 문서 세트는 다음 상태에 도달했습니다.

- **정책(PRD)** 이 **구조(architecture)** 로 자연스럽게 내려감
- **구조(architecture)** 가 **계약(api_spec)** 과 충돌하지 않음
- **계약(api_spec)** 을 기준으로 **구현 계획(tasks)** 이 정렬됨
- v0 구현에 필요한 범위와 책임이 명확해짐
- 남은 리스크는 주로 문서가 아니라 **실제 빌드 툴체인 검증**과
  소규모 구현 디테일 수준으로 축소됨

즉, 이번 정리는 단순 문서 다듬기가 아니라:

> **PRD → architecture → api_spec → tasks** 전 과정을  
> 정책 / 구조 / 계약 / 구현 계획 측면에서 정합화한 작업입니다.
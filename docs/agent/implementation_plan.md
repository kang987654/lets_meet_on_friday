# Implementation Plan: QA Debugging & Feature Enhancements

사용자 피드백 및 스크린샷 리뷰를 바탕으로 발견된 문제들을 "기획 변경/추가"와 "QA 디버그(오류 수정)"로 분류하고 해결 방안을 설계합니다.

## User Review Required
> [!IMPORTANT]
> **캘린더 일정 시간 단일화 반영 (사용자 피드백 반영)**
> - 일정 등록 시 사용성 개선을 위해 종료 시간(`endIso`)을 완전히 배제하고, 시작 시간(`startIso`)만 **단일 "일정 시간(dueDateIso)"**으로 취급하여 로컬 DB에 저장하도록 구현하겠습니다.
> - 이에 따라 기존 `TaskEntity`에 `dueDateIso` 컬럼을 정식 추가하고 `TaskRepository`와 연동합니다.

---

## 1. 기획 변경 및 추가 내용 (Design Changes & Additions)

### 1-1. 상단 머릿글 (TopAppBar) 공간 절약 (채팅 UI)
- **원인**: 기본 안드로이드 Scaffold 템플릿(TopAppBar)을 관성적으로 사용하여 귀중한 세로 화면 공간을 낭비함.
- **해결**: `ChatScreen.kt`에서 `TopAppBar("Local Friday")` 영역을 제거하고, 상태바 공간만 여백으로 남기도록 리팩토링.

### 1-2. 자체 인앱 캘린더 / To-Do 기능 구현
- **원인**: 외부 앱 연동의 한계를 극복하고 매끄러운 UX를 위해 내부 DB를 직접 활용하는 것으로 기획 변경.
- **해결**:
  - **DB 스키마 확장**: `TaskEntity`에 `dueDateIso: String?` 필드를 추가하여 캘린더 일정이 생성될 때 시작일시(`startIso`)를 여기에 단일 저장합니다. (Room DB 버전을 `2`로 업그레이드하고 `destructiveMigration` 처리)
  - **CalendarAgent 수정**: `ModelOutput.CalendarDraftOutput` 수신 시 `CalendarTool` 대신 `TaskRepository.save`를 호출하여 로컬 DB에 새로운 완료되지 않은 Task(일정)로 저장합니다. 이 때 `dueDateIso`에 `startIso` 값을 바인딩하고 `endIso` 값은 무시합니다.
  - **CalendarScreen & ViewModel 개편**:
    - 월간 달력 뷰 및 일별 일정 리스트 UI를 `feature/calendar/` 하위에 새로 개발합니다.
    - 기존 외부 캘린더 조회 방식 대신, `TaskRepository`에서 `dueDateIso`가 존재하는 일정들을 가져와 날짜별로 매핑하여 달력에 표시합니다.
  - **GetTodayScheduleUseCase 수정**: 내부 DB(`TaskRepository`)를 조회하여 지정된 날짜 범위(오늘, 이번 주) 내의 일정들을 수집하고 LLM 요약을 수행하도록 수정합니다.

### 1-3. 메모리(Memory) 기능 구성 명확화
- **기획 구성**: `MemoryScreen`은 다음 세 가지 정보를 관리합니다.
  - **Pending Tasks (Tasks)**: 캘린더나 To-do 등 실행 대기 중이거나 저장된 액션.
  - **Knowledge Notes (Knowledge)**: 대화 과정에서 LLM이 추출한 사용자 개인정보/취향 (예: "트와이스를 좋아함").
  - **All**: 과거의 전체 채팅 로그 보관함.

### 1-4. 컨텍스트 윈도우 한계 명시 및 프롬프트 개선 (시간/오타 환각)
- **원인**: 
  1. 현재 로컬 모델의 RAM 메모리 한계로 인해 `ContextBuilder.kt`에서 `Sliding Window(3000토큰)`가 적용되어 있어 과거 대화가 잘립니다.
  2. 시스템이 LLM에게 현재 '시간/날짜'를 명시적으로 주입하지 않았음에도 모델이 억측(Hallucination)하여 답변하고, 오타라는 핑계를 댔습니다.
- **해결**: 
  - **사용자 기기(S25 Ultra, 12GB RAM) 스펙을 고려하여 `ContextBuilder.kt`의 Sliding Window 토큰 제한을 기존 3,000에서 8,000 토큰으로 대폭 상향 조정 (메모리 제약 완화).**
  - `PromptAssembler.kt`에 시스템 현재 시간(`System.currentTimeMillis()`)을 동적으로 주입하는 포맷 추가.
  - "당신은 내부 시스템 시계에 접근할 수 있습니다"라는 명시적 프롬프트를 추가하여 환각 방지.

---

## 2. QA를 통한 디버그 및 오류 수정 (QA Debug Issues)

### 2-1. LLM JSON 구조 노출 오류 (채팅)
- **원인**: `Gemma` 모델이 응답할 때 쉼표를 두 번 찍거나(`,,`) 잘못된 JSON 문법을 출력하는 경우가 있음. `ResponseParser.kt`가 이 에러를 처리하지 못하고 원시 문자열 전체를 예외(Fallback)로 화면에 던져버림 (관성적 예외 처리).
- **해결**: `ResponseParser.kt` 내부에 정규표현식(Regex)을 이용한 강력한 "JSON Sanitizer(정제기)" 및 텍스트 추출 Fallback 로직 도입. `,,` 를 `,` 로 치환하는 등 구조적 복구 처리.

### 2-2. View Audit Logs 무반응 및 화면 누락 (Security & Log)
- **원인**: `SettingsScreen.kt`에 버튼만 만들어져 있고, 정작 로그를 보여줄 `AuditScreen.kt`와 `AuditViewModel.kt` 등 UI/Presentation 계층의 코드가 아예 구현되지 않은 상태(목업 버튼)입니다.
- **해결**: 
  - `feature/settings/` 하위에 `AuditScreen.kt`와 `AuditViewModel.kt`를 새로 생성하여, 기존에 존재하는 `AuditRepository`를 통해 DB의 로그를 불러와 리스트로 보여주도록 구현합니다.
  - 생성 후 `AppNavHost.kt`에 라우트(`AppDestination.Audit`)를 안전하게 연결합니다.

---

## Verification Plan

### Automated Tests
- `DatabaseModule` 변경에 따른 DB 재생성 정상 작동 확인 (`./gradlew testDebugUnitTest`)

### Manual Verification
- 채팅창에 `{"type":"text", , "text":"안녕"}` 등 고의로 오염된 형태를 입력하여 파서가 정상적으로 "안녕"만 추출하는지 확인.
- 설정 화면에서 "View Audit Logs"를 눌렀을 때 오류 없이 새 화면으로 진입하는지 확인.
- 일정 추가 후 달력 화면에서 등록한 일정이 정상적으로 조회되는지 확인.

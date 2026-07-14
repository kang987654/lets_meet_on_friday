# QA Debugging & Feature Enhancements Task List

- `[x]` **1. 채팅 (Chat) 영역 개선**
  - `[x]` `ChatScreen.kt`에서 불필요한 `TopAppBar` 제거 및 Layout 여백 조절
  - `[x]` `ResponseParser.kt`의 파싱 로직 정규식 추가 (오염된 쉼표 `,,` 전처리 및 예외 발생 시 강제 Regex Text 추출 등 Fallback 고도화)
  - `[x]` `ContextBuilder.kt`의 Sliding Window 제한을 3,000에서 8,000 토큰으로 상향
  - `[x]` `PromptAssembler.kt`에 시스템 현재 시간 동적 주입 로직 추가
  - `[x]` `PromptAssembler.kt`의 시스템 프롬프트에 "당신은 현재 시간을 알고 있다"는 명시적 지시문 추가

- `[x]` **2. 일정 추가 (Calendar) 인앱 기능 구현**
  - `[x]` `TaskEntity.kt` 및 `TaskRepositoryImpl.kt`에 `dueDateIso` 필드(컬럼) 반영 및 매핑 구현
  - `[x]` `KosmosDatabase.kt` 데이터베이스 버전을 1에서 2로 마이그레이션 상향 조정
  - `[x]` `CalendarAgent.kt`가 기존 `CalendarTool` 대신 `TaskRepository`를 이용해 내부 DB로 일정을 삽입하도록 로직 변경 (`endIso`는 완전히 생략하고 시작시간 `startIso`만 단일 "일정 시간"으로 저장)
  - `[x]` `GetTodayScheduleUseCase.kt`가 외부 캘린더 대신 `TaskRepository`에서 일정을 읽어와 오늘/이번 주 범위에 맞게 필터링 및 요약하도록 로직 연동
  - `[x]` `CalendarScreen.kt` 및 `CalendarViewModel.kt` 개편 (내부 DB의 일정들을 조회하여 표시하는 월간 달력 및 일정 리스트 UI 연동)

- `[x]` **3. Security & Log 화면 신규 구현**
  - `[x]` `feature/settings/` 패키지 하위에 `AuditViewModel.kt` 및 `AuditScreen.kt` 신규 생성
  - `[x]` `AuditViewModel`에서 `AuditRepository.getPaged()` (Paging 3)를 연동하여 감사 로그 페이징 흐름 구현
  - `[x]` `AppDestination.kt`에 `Audit` 목적지 추가 및 `AppNavHost.kt`에 네비게이션 연결 구성

- `[x]` **4. Verification**
  - `[x]` `./gradlew clean assembleDebug` 빌드 정상 수행 검증
  - `[x]` `./gradlew testDebugUnitTest` 테스트 코드 실행 및 통과 여부 확인

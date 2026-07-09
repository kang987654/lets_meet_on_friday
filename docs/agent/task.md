# Local Friday 재작성 및 리팩토링 진행 상황

- [x] **단계 1: Docs-as-code 마이그레이션**
  - [x] `docs/` 디렉토리 생성
  - [x] `documents/v1/` 하위의 문서(`PRD.md`, `architecture.md`, `tasks.md`, `api_spec.yaml` 등)를 `docs/`로 복사

- [x] **단계 2: E2E / 통합 테스트 환경 구축**
  - [x] `app/src/test/` 및 `app/src/androidTest/` 디렉토리 설정
  - [x] `app/build.gradle.kts`에 테스트 의존성(Compose Test, Hilt Test, Robolectric 등) 추가
  - [x] 데이터 파이프라인 테스트용 기반 클래스 작성

- [x] **단계 3: Core Chatbot 파이프라인 수정 (The "Brain")**
  - [x] `AssistantOrchestrator.kt` 연결부 에러 수정 (패키지 구조 원상 복구)
  - [x] `PromptAssembler.kt` 컨텍스트 누락 수정 (SYSTEM 메시지 파싱 분리 적용)
  - [x] **Phase 3: 파이프라인 통합 및 프롬프트/파싱 계층 고도화**
  - [x] `AssistantPipelineTest.kt` 파이프라인 통합 테스트 환경 구축
  - [x] `MockModelRunner` 등을 이용해 UI-ViewModel-Orchestrator-DB 연동 테스트 작성
  - [x] `PromptAssembler`에서 SYSTEM 텍스트 분리 전처리
  - [x] `ResponseParser`에서 ` ```json` 마크다운 래퍼 제거 로직 추가

- [x] **Phase 4: 목업 기능 제거 및 실제 도구 연동**
  - [x] `CalendarAgent.kt` & `AndroidCalendarTool.kt` 실제 구현 연결
  - [x] `AndroidSpeechToTextTool.kt` 연동 및 AndroidManifest.xml 권한 (RECORD_AUDIO, INTERNET, CALENDAR) 추가
  - [x] 런타임 권한 요청 로직 추가 (MainActivity)실제 연동
  - [ ] `ToolIntegrationTest.kt` 테스트 작성

- [ ] **단계 5: DB 연동 및 메모리 영속화**
  - [ ] `LocalFridayDatabase.kt` 및 DAO 트랜잭션 오류 픽스
  - [ ] `SendChatMessageUseCase.kt` 응답 완료 후 메모리 저장 보장

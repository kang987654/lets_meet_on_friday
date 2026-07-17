## [0.3.5] - 2026-07-17
- **[Architecture/Refactoring]** 에이전트 구조 분리 (Multi-Agent Refactoring) 구현 완료
  - `AssistantOrchestrator`의 비대해진 모델 추론(재귀 루프) 및 툴 파싱 로직을 `BaseAgent` 추상 클래스로 분리 및 캡슐화
  - `IntentClassifier`를 통한 사용자 의도(Intent) 기반 라우팅을 담당하는 `TaskRouter` 도입 및 `CalendarAgent`, `DefaultAgent`로 책임 분할
  - `AudioRecorder`와 `ChatViewModel`의 예외 처리를 공통 `AppResult` 래퍼로 통합하여 안정성 강화 및 리팩토링
  - `PromptAssembler`를 개선하여 각 에이전트의 역할(SystemRole)과 사용 가능한 툴(AvailableTools)을 동적으로 주입하도록 스펙 변경
  - 변경된 구조에 맞추어 통합 테스트(`MultimodalChatE2ETest`, `ToolApprovalE2ETest`, `VoiceChatIntegrationTest`, `PromptAssemblerTest`) 코드 모의 객체 및 반환 타입 일괄 업데이트 후 전체 TC 통과
- **[Prompt/Optimization]** 온디바이스 소형 LLM(Gemma 4 e4b) 프롬프트 최적화 (상용 앱 수준 리팩토링)
  - 서비스명 강제(`named Kosmos`) 및 하드코딩된 스타일 부정어 제거 후 `[Style: Concise]` 변수로 동적 주입하여 지시어 충돌 차단
  - 시스템 시간을 `[System Data] Current Time: ...` 단일 문장으로 압축하여 토큰 낭비 제거
  - `Always respond in Korean...` 추가로 다국어 이탈(Language Drift) 현상 차단
  - Tool 인자 부족 시 짐작하지 않고 되묻도록 Fallback 로직 추가하여 환각 캘린더 생성 억제
  - 턴 마커(`User:`, `Assistant:`) 이중 표기 제거 및 Tool 설명 인자 스키마를 JSON 형태로 포맷 변경하여 Syntax Error 예방

## [0.3.4] - 2026-07-16
- **[Persona/SystemPrompt]** 사용자 선호 응답 스타일(Profile Memory) 시스템 지시문 동적 주입 구현 완료
  - `ContextBuilder`가 `SettingsDataStore`를 주입받아 대화 컨텍스트 구성 시 현재 저장된 선호 응답 스타일 상태(`settingsDataStore.responseStyleFlow`)를 코루틴 `.first()` 연산자로 1회성 로드 연동
  - `PromptAssembler`에서 시스템 지시문 구성 시 `Context` 내 `responseStyle`이 `"DEFAULT"`가 아닐 경우 "User's preferred response style: [스타일]" 지시어 블록을 동적 주입하도록 로직 보완
  - 생성자 파급 경로를 모두 검토하여 단위 테스트 및 Hilt 그래프 빌드에 부작용이 없음을 검증 완료
- **[Architecture/Refactoring]** 레거시 툴 실행 아키텍처 제거 및 승인 로직 간소화 이후 발생한 빌드/동기화 오류 수정
  - `ToolApprovalE2ETest` 내에서 Robolectric의 `ShadowLooper`가 Compose의 `StandardTestDispatcher` UI 큐를 펌핑하지 못해 발생하는 Timeout 타임아웃 문제를 확인하고, 테스트 코드가 `ChatViewModel`을 직접 호출하도록 우회하여 승인(ApprovalCoordinator) 흐름 검증 성공
  - 불필요한 중간 계층인 `ResumeActionUseCase` 완전 제거로 인한 패키지 간 순환 의존성 및 복잡도(Early Return) 개선 완료 (SRP, DRY, 가독성 준수 확인)
- **[QA/Test]** 수명이 다한 컨텍스트(과거 기획/디버깅 내역) 청소 원칙에 따라 `docs/agent/qa_plan.md`에 새롭게 구현할 '프로필 메모리 연동' 기능에 대한 QA 계획(수동/자동화 테스트 시나리오) 신규 작성 및 파일 정리

## [0.3.3] - 2026-07-15
- **[Approval/ToolCall]** Gemma 4 Tool Call 일정 쓰기(AddSchedule) 승인 관리 로직 구현 및 E2E 테스트(`ToolApprovalE2ETest`) 검증 완료
- `ApprovalRequest` 구조를 개선하여 `title`, `description` 필드를 추가하고 `action`을 Nullable로 처리하여 Tool Call 승인과 Guard 정책 승인을 동시 지원
- `ApprovalCoordinator`에 `CompletableDeferred<Boolean>`을 도입하여 LLM 툴 루프 도중 비동기적으로 사용자의 승인/거절 입력을 동기적 대기(Suspending)할 수 있도록 구조 설계 및 구현
- `ChatViewModel`에서 `request.action == null`인 새로운 Tool Call 승인에 대해 coordinator의 `approve()` / `reject()`를 실행하여 deferred 완료 처리 연동
- `ChatScreen`에서 `action == null`인 일반 툴 콜 승인 요청에 대해서도 동적으로 Alert Dialog를 렌더링하도록 UI 보완
- **[QA/Test]** Hilt 테스트 환경 내 `ModelRunner` Mock 인터페이스를 최신 스펙(`ChatPrompt` 지원, `suspend` 시그니처)에 맞게 갱신하여 빌드 에러 해결 및 테스트 버튼 클릭을 통한 롤백 시나리오 검증 완료

## [0.3.2] - 2026-07-14
- **[Architecture/Refactoring]** `ChatViewModel`, `AssistantOrchestrator` 과도한 책임 및 복잡도 리팩토링 (SRP, DRY 원칙 적용)
- `ChatViewModel`에서 수행되던 Tool Execution 및 재귀 추론 로직을 `AssistantOrchestrator`의 `processRequest` 내부 캡슐화로 회수
- 뷰모델 내 Android 특화 로직(`Uri` -> `Bitmap` -> `ByteArray`)을 프라이빗 헬퍼 함수로 분리하여 가독성 강화
- `AssistantOrchestrator` 내 반복되는 `ChatMessage` 데이터베이스 저장 코드를 단일 헬퍼(`createAndSaveMessage`)로 병합(DRY)
- 여러 `AppResult` 분기와 거대한 `when` 블록을 별도의 `handleXXXAction` 메서드로 평탄화하여 복잡도 감소
- Hilt `@UninstallModules` 사용 테스트 환경(`MultimodalChatE2ETest`, `VoiceChatIntegrationTest`)에서 누락된 `ImageProcessor` Mock 의존성 주입 복구 및 검증 완료

## [0.3.0] - 2026-07-14
- `:app` 모듈에 밀집된 코드를 `:core`, `:domain`, `:data` 3개 모듈로 물리적/논리적 분리 및 컴파일 연동 완료
- `:domain` 모듈을 Pure Kotlin JVM 모듈로 구성하고, Android SDK 종속성을 차단하기 위해 `ImageProcessor`, `ModelDownloader`, `ModelLoadManager`, `MemoryBackupManager` 인터페이스 추상화 도입
- `:core` 모듈 또한 Pure Kotlin JVM 모듈로 전환하고, Android `Manifest` 상수 및 `AppLogger` 리플렉션 폴백 처리를 적용하여 의존성 격리
- 메인 코디네이터(`AssistantOrchestrator`)를 참조하여 컴파일 순환 참조를 일으키던 4개의 앱 오케스트레이션 유스케이스를 `:app` 모듈로 재배치
- Room 데이터베이스, DataStore, 메모리 Repository에 관한 Hilt 모듈(`DatabaseModule`, `DataStoreModule`, `MemoryModule`)을 `:data` 모듈로 물리적 이관
- **[QA/Test]** 멀티 모듈 리팩토링 검증 완료: 순환 참조 및 Hilt DI 에러 점검 완료 (`MultimodalChatE2ETest`, `VoiceChatIntegrationTest` 내 `ModelLoadManager` Mock 주입 픽스 및 전체 단위 테스트 성공)

## [0.2.1] - 2026-07-14
- Gemma 4 Advanced Features (MTP, Tool Calling, Vision, Thinking Process) 구현 및 E2E 테스트 통합 완료
- `ToolParser.kt`를 통한 스트리밍 출력 중 `<|think|>` 블록 및 `<tool_call>` JSON 정규식 기반 실시간 파싱 적용
- `ChatViewModel`에서 Tool Call을 인터셉트하여 `GetTodayScheduleUseCase`, `AddScheduleUseCase` 실행 후 `tool_response`로 컨텍스트 재개 구현
- `ChatScreen.kt` 내 첨부 이미지(Vision) 및 `thinkingProcess` 아코디언 UI 연동 검증
- `RobolectricTestRunner` 환경에서 `org.json.JSONObject` 종속성 문제 해결 및 `ChatViewModel` 생성자 주입 최신화

## [0.2.0] - 2026-07-14
- Gemma 4 MTP 옵션 검증 및 GPU 백엔드 초기화 로직 보완
- 스트리밍 응답 텍스트에 포함된 <|think|> 태그 분리 및 ChatScreen 아코디언 UI 연동
- 캘린더 조회(get_today_schedule) Tool Call 파싱 구현 및 승인 시나리오(CalendarAgent) 추가

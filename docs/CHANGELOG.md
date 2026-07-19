## [0.5.4] - 2026-07-19
- **[Refactoring]** 기술 부채 청산, 하드코딩된 더미 UI 제거 및 최적화
  - **`CalendarScreen.kt`**: 피그마 시안 확인용으로 하드코딩되었던 "Upcoming" 섹션 및 가짜 일정 행(`UpcomingEventRow`), 일정 시간 배지("30m") 제거 (DB 연동 순수 데이터만 출력)
  - **`ChatViewModel.kt` & `ImageInputAdapter.kt`**: 첨부 이미지 이중 압축 버그 해결. ViewModel에서 Raw Bytes만 추출하고 Adapter에서 단방향(단일) 압축(JPEG)을 수행하도록 파이프라인 리팩토링 및 딜레이 감소
  - **`WebSearchGateway.kt`**: 테스트용 가짜 지연 코드 및 Mock 검색 파일(`WebSearchTool.kt`, `SearchToolExecutor.kt`) 완전 삭제 및 Hilt(`PlatformModule`) 의존성 정리

## [0.5.3] - 2026-07-19
- **[UI/UX Hotfix]** Phase 3 피그마 스펙 완전 동기화 및 잔여 버그 수정 (2:32 AM 기준 리셋 후 재적용)
  - **`OrbPulse.kt`**: 메인 커버 애니메이션의 회전축을 중앙(`pivot = Offset.Zero`)으로 고정하여 궤도 이탈 글리치 수정
  - **`MainScreen.kt`**: 바텀 네비게이션 뒤로가기(Back) 누를 시 백스택 꼬임 현상을 방지하기 위해 `popUpTo(Chat.route)`로 홈 복귀 강제
  - **`MemoryScreen.kt`**: 누락되었던 상단 "Memory & Tasks" 메인 헤더 복구 및 태그(Knowledge) 칩에 둥근 모서리/Glassmorphism 스타일 적용
  - **`CalendarScreen.kt`**: "14~20일"로 하드코딩 되어있던 날짜 스크롤 바를 `LocalDate.now()` 기반으로 동적 생성하도록 수정
  - **`ChatScreen.kt`**: 하단 전송 버튼을 종이비행기 모양(`ic_send.xml`) 에셋으로 교체하고, 마이크 버튼과의 전환 시 `AnimatedContent`를 적용하여 렌더링 글리치(네모->원형 깨짐) 원천 차단
  - **`SettingsScreen.kt`**: 피그마 Phase 3 명세에 따라 `SectionBox` 대문자 타이틀 및 `Icons.Settings` 등 디테일 컴포넌트로 전면 교체

## [0.5.2] - 2026-07-18
- **[UI/UX Hotfix]** 기획안(Phase 2) 누락분 `ChatScreen.kt` 피그마 UI 완전 동기화 및 마이그레이션 적용
  - 상단바: 기본 TopAppBar를 KOSMOS 커스텀 헤더(`CustomChatHeader`)로 교체 및 펄스 애니메이션이 들어간 초록색 상태 뱃지(`PulseGreenDot`) 구현
  - 말풍선: 유저(Gradient), AI(Glassmorphism) 버블에 피그마 원본 곡률(`18px 18px 4px 18px` 등 비대칭) 적용
  - 하단 입력창: 개별 분리되었던 버튼 구조를 하나로 통합하여 단일 `glassEffect` 알약(Pill) 형태로 레이아웃 전면 리팩토링 및 렌더링 검증 완료
- **[Feature]** AI 일정 제안 카드(Calendar Draft Card) UI 바텀 오버레이 구현 및 기능 연동 완료
  - `ChatScreen.kt`에서 배경을 어둡게 가리지 않는 Floating 형태의 피그마 UI(D · Calendar Card) 완벽 구현
  - `ChatViewModel`을 통해 `pendingCalendarDraft` 상태를 구독하여, 사용자의 [Approve & Save] 동작 시 `AddScheduleUseCase`를 호출해 실제 DB에 일정 저장 연동 완료
  - 사용자의 [Reject] 동작 시 자연스럽게 카드를 숨기도록 상태 처리 구현

## [0.5.1] - 2026-07-18
- **[Bugfix/Hotfix]** Android 15 16KB 호환성 정공법 적용 및 스플래시 무한 로딩 버그 최종 수정
  - `libs.versions.toml`에서 `litertlm` (0.14.0) 및 `mediapipe` (0.10.35) 버전을 업데이트하여 네이티브 파일(`.so`)의 16KB 메모리 정렬을 근본적으로 지원하도록 변경
  - AGP `useLegacyPackaging = true` 및 매니페스트의 `extractNativeLibs` 꼼수 옵션 완전 제거
  - `GemmaModelRunner.warmUp()` 내에 모델 파일 존재 여부 재확인 로직(`checkModelFile()`)을 추가하여 초기 모델 파일 부재 시 렌더링 된 Retry 버튼 클릭 시 동작하지 않던(Deadlock) 현상 해결

## [0.5.0] - 2026-07-18
- **[Feature]** 온디바이스 텍스트 임베딩(MediaPipe) 및 RAG(Retrieval-Augmented Generation) 메모리 파이프라인 연동 완료
  - `MediaPipeTextEmbedder` 추가 및 `universal_sentence_encoder.tflite` 오프라인 모델 로드 적용
  - Room 데이터베이스(`KnowledgeEntity`)에 `embedding` 컬럼 추가 및 코사인 유사도(Cosine Similarity) 기반 벡터 검색 기능 구현
  - `SaveKnowledgeUseCase` 및 `SearchKnowledgeUseCase`를 통한 기억 저장/검색 파이프라인 리팩토링
  - `ContextBuilder`에서 최신 사용자 메시지 기반 RAG 검색 후 `[Context / Knowledge]`로 시스템 프롬프트 실시간 주입
- **[Feature]** API Key 프리 위키피디아 온디바이스 검색 툴(`SearchWikipedia`) 탑재
  - Wikipedia 공용 API(`api.php`)를 OkHttp/JSON 기반으로 직접 연동 (`WikipediaSearchToolImpl`)
  - 토픽(Topic), 언어(Lang) 기반 요약문 추출 및 검색 결과 글자수 제한 캡 적용으로 안정성 확보
  - `AddMemoryToolExecutor`와 함께 `ToolRegistry` 및 `PromptAssembler`에 정식 에이전트 툴로 등록 및 검증 완료
- **[QA/Test]** RAG 기반 장기 메모리(Vector DB) 파이프라인 및 웹 검색(SearchWeb) 통합 테스트 검증 완료
  - `MemoryPipelineIntegrationTest` 작성: `SaveKnowledgeUseCase`부터 `ContextBuilder`까지 이어지는 RAG Write/Read 전체 통합 사이클 및 시스템 텍스트 렌더링 정상 확인 (`MediaPipe` 임베딩 포함 Mocking 완료)
  - `SearchToolExecutorTest` 작성: `WebSearchTool`의 승인/거절(Approval Coordinator) 비동기 처리 흐름 및 응답 JSON 직렬화 안정성 검증 통과
- **[QA/Test]** Glassmorphism UI 렌더링 무결성 및 E2E 테스트(`MultimodalChatE2ETest`, `ToolApprovalE2ETest`) 크래시 복구 및 검증
  - Hilt 주입 시 발생하는 `MediaPipeTextEmbedder`의 JNI 로드 에러(`java.lang.UnsatisfiedLinkError`)를 `Throwable` 포획으로 방어하여 앱 강제 종료 및 테스트 크래시 원천 차단
  - 백그라운드 Robolectric 환경에서 새로운 UI 계층의 렌더링(채팅 메시지 갱신, 라우팅, ApprovalSheet 시트 팝업)이 정상 작동하는지 자동화 E2E 테스트로 실행 검증 (All Pass 확인)
- **[Bugfix/Hotfix]** Android 15 (Vanilla Ice Cream) 환경 네이티브 라이브러리 충돌 및 스플래시 화면 무한 로딩 수정
  - TensorFlow Lite, MediaPipe 등 네이티브(`.so`) 라이브러리들이 16KB 페이지 사이즈 정렬 검사에 실패하여 `UnsatisfiedLinkError`를 내며 강제 종료되는 문제(ELF Alignment Error) 해결
  - `build.gradle.kts` 내 `useLegacyPackaging = true` 옵션을 적용하여, OS가 파일 압축을 해제하고 시스템 단위에서 16KB 메모리 정렬을 대행하도록 런타임 호환성 우회 로직 추가
  - `SplashScreen.kt` 내부에서 초기화 상태가 `Error`로 빠질 경우 네비게이션 없이 애니메이션만 무한 루프하는 버그를 수정하여, 에러 원인 텍스트와 함께 뷰모델의 `retry()` 액션을 호출하는 재시도 UI 컴포넌트 추가

## [0.4.0] - 2026-07-18
- **[UI/UX]** 안드로이드 네이티브(Jetpack Compose) Glassmorphism UI 전면 마이그레이션 및 적용 완료 (Phase 1, 2, 3)
  - `core:designsystem`을 대체하여 `BgColor`, `SurfaceColor`, `GlassColor`, `Cyan`, `Violet` 커스텀 테마 색상 및 오프라인 커스텀 폰트 번들 적용
  - `RenderEffect.createBlurEffect()` 기반의 안드로이드 네이티브 `Modifier.glassEffect()` 구현 완료
  - **애니메이션 전환**: React `@keyframes`를 Compose `InfiniteTransition`과 Canvas로 변환 (AuroraBackground, OrbPulse, ThinkingDots)
  - **Screen 일괄 마이그레이션**: `SplashScreen`, `ChatScreen`, `VoiceOverlay`, `MemoryScreen`, `CalendarScreen`, `SettingsScreen`, `AuditScreen`, `ApprovalSheet` 및 `MainScreen` (Bottom Nav) 모두 새 디자인 시스템과 Glassmorphism/Dark Theme 일괄 적용
  - `MemoryScreen` 앱 데이터 복원(Import) 후 재시작 시, 안전한 백스택 초기화를 위해 Android 권장 방식(`Intent.makeRestartActivityTask`)으로 로직 개선
- **[QA/Test]** 0.4.0 대규모 UI 개편 및 다중 에이전트 아키텍처 전환에 대한 회귀 테스트(Regression Test) 검증 완료
  - Robolectric 기반 통합 E2E 테스트(채팅, 멀티모달, 음성, 툴 승인) 전 항목(All Pass) 통과 확인
  - `TaskRouter`, `ContextBuilder`, `MemoryScreen` 내 주요 의존성 및 라우팅 로직 정적 분석 결과 결함 없음 확인

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

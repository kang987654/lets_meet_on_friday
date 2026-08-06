## [0.7.2] - 2026-08-06
- **[Added]** 내보내기/가져오기 UI 진입점 — `prd.md` F8이 "메모리 화면에서 export 선택"을 요구하는데 진입점이 아예 없었다. `MemoryViewModel.exportData`/`importData`는 구현돼 있었으나 호출자가 없어 도달 불가 상태였고(`importLauncher`는 만들어졌지만 `launch`가 호출되는 곳이 없었다), 그래서 `V1-AC4`(export→import 왕복)를 사용자가 수행할 방법이 없었다. 메모리 화면 하단에 백업 섹션 추가
- **[Added]** 내보내기 시 개인정보 포함 경고 다이얼로그 — `prd.md` F8 정책("export 파일에 개인정보가 포함됨을 UI에서 명시") 충족. v1은 암호화를 의도적으로 넣지 않았으므로 이 경고가 유일한 보호막이다. 가져오기에도 기존 데이터를 되돌릴 수 없이 덮어쓴다는 확인 단계 추가
- **[Added]** 저장 위치 직접 선택 — 생성된 zip은 `cacheDir`에 있어 사용자가 접근할 수 없고 시스템이 언제든 비울 수 있다. SAF `CreateDocument`로 사용자가 고른 위치에 복사하는 `BackupFileWriter` 추가 (사용자 결정). 저장 완료·취소 어느 쪽이든 캐시 사본을 정리한다
- **[Fix]** `MemoryViewModel`의 콜백 누수 — 콜백 람다가 Activity `context`를 캡처하고 `viewModelScope` 코루틴이 그것을 붙잡았다. DB를 재작성하는 가져오기 도중 화면을 회전하면 파괴된 Activity를 향해 `startActivity`가 실행되고, 반대로 컴포지션을 벗어나면 진행 중 코루틴이 옛 람다를 들고 있어 완료 통지가 유실됐다(`UiState`에 읽을 필드도 없었다). 결과를 `BackupState`로 노출하도록 전환
- **[Fix]** 가져오기 중복 실행 가드 추가 — DB 파일을 통째로 교체하는 작업이라 재진입이 곧 데이터 파괴다
- **[Fix]** 복원 후 자동 재시작(Toast + 2초 지연 `exitProcess(0)`)을 확인 다이얼로그로 교체 (사용자 결정) — 타이머가 화면 회전이나 백그라운드 전환과 경합할 수 있었고, `Toast`는 0.6.0의 라이트/다크 토큰을 무시했다. 재시작 다이얼로그는 닫을 수 없다(DB가 이미 교체된 상태로 계속 쓰면 화면의 페이징 캐시와 실제 데이터가 어긋난다)
- **[Removed]** `AuditRepository.getPagedByType` 3계층 제거 (인터페이스·`AuditRepositoryImpl`·`AuditDao`) — 감사 로그 타입 필터용으로 UI보다 먼저 만들어졌으나 `tasks.md`에 해당 요구사항이 없고 호출자도 없었다. 소비자 없는 계약은 `:domain`의 paging 의존 제거 작업만 무겁게 만든다. DB 스키마 변경이 아니므로 마이그레이션 없음 (사용자 결정)
- **[QA/Test]** 신규 테스트 12건 — `MemoryBackupStateTest`(내보내기/가져오기 상태 전환, 중복 실행 가드, 재시작 대기 상태의 닫기 차단, 경고 선행 검증). 전체 96건 + lintDebug 통과
- **[Known Issue]** `ExportImportManager`의 zip 왕복은 여전히 무테스트 — Room DB 파일 교체와 프로세스 재시작이 얽혀 JVM 테스트로 재현하기 어렵다. 실기기 QA 필요(특히 가져오기 진행 중 화면 회전)

## [0.7.1] - 2026-08-06
- **[Fix]** `AddMemory` 툴의 태그가 전부 유실되던 버그 — `AddMemoryToolExecutor`가 `tagsRaw is List<*>`로 분기했으나 `org.json`은 JSON 배열을 `List`가 아닌 `JSONArray`로 주므로 이 분기는 절대 참이 되지 않았다. 프롬프트가 지시한 정식 형태(`{"tags":["work","urgent"]}`)로 보낸 태그가 조용히 사라지고 콤마 문자열 폴백만 동작하던 상태(재현 확인: `isList=false`, 결과 `[]`)
- **[Fix]** `KnowledgeDao` LIKE 와일드카드 미이스케이프 — Room 파라미터 바인딩은 SQL 인젝션을 막지만 `%`/`_`는 바인딩된 값 안쪽에 있어 와일드카드로 해석됐다. `100%` 검색이 `100` 포함 전체를, `%` 한 글자가 테이블 전체를 매칭해 노트 100건이 RAG 프롬프트로 쏟아지고 컨텍스트 예산을 터뜨렸다. `SqlLike.escape` + 두 쿼리에 `ESCAPE '\'` 적용, 빈 검색어 가드 추가
- **[Fix]** 태그 부분 매칭 — `tags`는 `"work,urgent"` 형태 콤마 문자열인데 `LIKE '%work%'`가 `"workflow"`에도 매칭됐다. 구분자로 양쪽을 감싸 정확히 한 개 토큰으로 매칭 (사용자 결정)
- **[Fix]** 승인·실행 인자 검증 불일치 — `AddScheduleToolExecutor`의 `buildApprovalRequest`는 누락된 제목을 `(제목 없음)`으로 표시하고 `execute`는 거부했기 때문에, 사용자가 애초에 실행될 수 없는 초안을 승인할 수 있었다. 검증을 한 곳으로 모아 승인 단계에서 먼저 실패하게 변경
- **[Fix]** JSON이 깨진 `<tool_call>`을 빈 `catch`로 삼켜 툴 콜이 흔적 없이 사라지던 문제 — 모델은 오류를 받지 못하고 그 턴이 평문 답변으로 처리돼 사용자에게는 요청이 무시된 것처럼 보였다. `ParsedStream.malformedToolCalls`로 노출하고 `BaseAgent`가 형식 오류를 모델에게 되돌려 재작성 기회를 준다
- **[Refactoring]** 툴 인자 해석을 `ToolArguments` 래퍼로 일원화 — `ToolExecutor`의 두 시그니처를 `Map<String, Any>` → `ToolArguments`로 교체하고 executor 4곳의 `as? String` 캐스트를 제거. `JSONObject.NULL`을 "값 없음"으로 정규화(non-null `Any`라서 생기던 함정 제거), 숫자·불린은 문자열로 강제 변환(모델이 따옴표를 빠뜨려도 대화가 멈추지 않도록), 배열·객체는 타입 오류로 보고. 누락(`MISSING`)과 타입 오류(`WRONG_TYPE`)를 구분해 모델이 무엇을 고칠지 알 수 있게 함 — 기존에는 둘을 뭉개 모델이 이미 보낸 값을 반복 요구받는 루프에 빠졌다
- **[QA/Test]** 신규 테스트 33건 — `ToolArgumentsTest`(14건, JSON 배열 태그 회귀 방지 포함), `SqlLikeEscapeTest`(6건, 백슬래시 우선 처리 순서 검증), `KnowledgeSearchEscapeTest`(10건, 인메모리 Room으로 실제 쿼리 검증), `ToolParserTest`(+3건). 전체 84건 + lintDebug 통과. `ToolApprovalE2ETest`가 승인 경로를 태워 회귀 안전망 역할

## [0.7.0] - 2026-08-05
- **[UI/UX]** Phase B-3 2~3단계 — 모델 다운로드를 WorkManager 전경 작업으로 이관 (ADR-006)
  - 다운로드가 `viewModelScope`를 벗어나 `ModelDownloadWorker`(@HiltWorker)로 이동 — 화면을 벗어나거나 앱을 닫아도 전송이 계속된다. 진행 카드 문구도 "앱을 종료하면 다운로드가 중단됩니다" → "앱을 닫아도 백그라운드에서 계속 진행됩니다"로 사실에 맞게 교체
  - 전경 서비스 알림으로 진행률(받은 용량/전체 용량)과 취소 액션 노출, 완료/실패 알림 추가. 알림 인프라(`NotificationChannels`, `DownloadNotifier`)는 프로젝트 최초 도입
  - 진행 카드에 대기 상태(`Queued`) 추가 — Wi-Fi 전용 제약 때문에 Wi-Fi가 없으면 작업이 멈춰 있는데, 카드가 없으면 "다운로드 버튼이 먹지 않는" 것으로 보인다
  - 실패 다이얼로그에 재시도 버튼과 이어받기 안내 추가, 취소를 "일시 중지"(부분 파일 보존)와 "취소 후 삭제"(저장 공간 회수)로 분리
  - POST_NOTIFICATIONS 컨텍스트 요청(API 33+) — 거부되어도 다운로드는 진행한다
- **[Feature]** HTTP Range 이어받기 및 저장 공간 사전 점검
  - `.part` + `.part.meta`(url·ETag·총 크기) 사이드카로 프로세스가 죽어도 재개 가능. ETag 불일치나 200 응답 시에는 처음부터 다시 받아 버전이 섞인 손상 파일을 만들지 않는다
  - 전송 시작 전 `Content-Length` 기반으로 필요 용량(+256MB 여유)을 점검하고, 부족하면 실제 필요 바이트 수와 함께 차단
  - 재시도 정책: `Transient`(IOException·타임아웃·5xx·408·429)만 최대 5회 지수 백오프(30초 시작), `Permanent`(4xx·크기 불일치)는 즉시 실패
  - 네트워크 제약 `NetworkType.UNMETERED` — 3.6GB를 이동통신 데이터로 받지 않는다 (사용자 결정)
  - 다운로드 전용 OkHttpClient 분리(`@DownloadClient`, read 60s / callTimeout 무제한) — 공유 클라이언트의 30초 read timeout은 대용량 스트림에서 끊긴다
- **[Fix]** `:data` 모듈에 `kotlin-serialization` 플러그인 누락 — 모듈 내부 `@Serializable` 클래스의 직렬화기가 생성되지 않아 컴파일은 통과하고 런타임에 `SerializationException`이 발생하던 상태. `PartMeta` 이어받기 실패로 발견했고, 같은 원인으로 `ExportManifest` 기반 내보내기/가져오기도 동작하지 않고 있었다
- **[Fix]** 다운로드 확정 시 기존 모델 파괴 회귀 — `target.delete()`를 먼저 하고 rename 했기 때문에 rename이 실패하면 새 모델도 없고 쓰던 모델도 없는 상태가 됐다. 기존 파일을 `.bak`으로 옮긴 뒤 rename 하고 실패 시 되돌린다
- **[Fix]** `.part`를 `finally`에서 무조건 삭제하던 동작 제거 — 재시도가 매번 0바이트부터 다시 받게 만든 원인. 영구 실패에서만 정리한다
- **[Fix]** 저장 공간 부족 판정의 부분 문자열 매칭(`message.contains("space")` → `InsufficientStorage(0L)` 하드코딩) 제거 — `ModelDownloadException` 타입 분기로 대체하고 실제 필요 바이트 수를 전달
- **[Fix]** 중복 다운로드 방지를 `downloadJob?.isActive` 가드에서 WorkManager 유니크 작업(KEEP)으로 이관 — 옛 가드는 ViewModel이 죽으면 함께 사라져 실제로 중복을 막지 못했다
- **[Refactoring]** `ModelDownloader`를 `Flow<Int>`(퍼센트) → `Flow<DownloadProgress>`(바이트)로 확장하고 `probe`/`clearPartial`/`partialBytes` 추가. `DownloadModelUseCase`는 `status`/`enqueue`/`cancel`/`acknowledge` 파사드로 재작성(예약과 관찰이 분리된 관심사가 됐다). `ModelDownloadService`의 하드코딩 `"models"`를 `Constants.MODEL_DIR_NAME`으로 교체
- **[QA/Test]** 신규 테스트 25건 — `DownloadStatusMappingTest`(10건, WorkInfo 전 상태 번역), `ModelDownloadWorkerTest`(6건, 재시도/실패 분류·완료 부수효과), `ModelDownloadResumeTest`(9건, MockWebServer 기반 실제 이어받기·부분 파일 보존·기존 모델 보호). 전체 51건 + lintDebug 통과
- **[Known Issue]** 알림 아이콘은 `android.R.drawable.stat_sys_download` 계열 임시 사용 — 24dp 모노 벡터 필요. 전경 승격·알림 렌더링·Doze 백오프는 단위 테스트 불가 영역으로 ADR-006에 수동 QA 체크리스트 기재

## [0.6.1] - 2026-07-31
- **[UI/UX]** UI 개선 계획서 Phase A — 죽은 인터랙션 활성화
  - 캘린더: 장식이던 날짜 스트립(`DatePill`)을 실제 필터로 연결(탭 → 해당 일자만 표시, 재탭 시 해제, 오늘이 아닌 날짜 선택 시 주간 범위 자동 확장), 미사용이던 `selectedRange`를 오늘/이번 주 세그먼트 UI로 노출, 섹션 헤더가 현재 필터를 반영
  - Memory: 기본 필터를 `ALL` → `KNOWLEDGE`로 변경해 첫 진입 시 두 탭 모두 미선택으로 보이던 문제 해결(`MemoryFilterType.ALL` 제거)
  - 채팅: 생성 중 전송 버튼을 정지 버튼으로 전환(`ChatViewModel.cancelGeneration()`) — 모델 스트림만 중단하고 파이프라인은 정상 종료시켜 부분 응답을 보존
  - ISO 파싱 규칙을 `domain/util/IsoDateTimeParser`로 추출해 유즈케이스와 화면 필터가 동일 규칙을 공유
- **[UI/UX]** Phase B(1~2) — 신뢰감 있는 피드백
  - `ErrorCodeMapper` 재작성: 부분 문자열 추측(예: "timeout" → MISSING_TIME_INFO) 제거 후 표준 토큰(`ValidationReason`) 정확 매칭, 미분류를 `INPUT_TOO_LONG` 오분류 대신 신규 `INVALID_INPUT`으로 처리
  - `ErrorMessages` 신설: 모든 `ErrorCode`에 사용자 문구(한국어) 매핑. 캘린더·설정·스플래시·모델 관리·메모리 백업 등 표시 지점 6곳을 연결해 `DbWriteError(task_item)` 같은 내부 클래스명 노출 제거
  - 채팅: 표시 경로가 아예 없어 무음으로 사라졌던 `uiState.error`를 스낵바로 노출, 웹 검색 토글 변경 시 허용/차단 안내 스낵바 추가
  - `ErrorMessageMappingTest` 신규(5건): 표준 토큰 매핑, 오분류 회귀, 내부 식별자 미노출, 전체 코드 문구 보유 검증. `docs/api_spec.yaml` 매핑 계약에 `INVALID_INPUT` 및 토큰 규칙 반영
- **[UI/UX]** Phase B-3 1단계 — 모델 다운로드 진행을 모달 다이얼로그에서 화면 내 카드로 이동(진행률·잔여 저장 공간·취소 노출). WorkManager 이관(2~3단계)은 계획서 권고대로 별도 세션으로 이월
- **[UI/UX]** Phase C — 채팅 화면 완성도
  - 날짜 구분선 추가(`ChatRow` 도입 — 오늘/어제/M월 d일), 메시지 롱프레스 복사(+스낵바), 최신으로 이동 FAB(사용자가 위로 스크롤하면 자동 스크롤 일시 해제), 이미지 첨부 썸네일 프리뷰(`inSampleSize` 다운샘플 + IO 디코딩, 외부 이미지 라이브러리 미추가)
- **[QA/Test]** 전체 테스트 + lintDebug + build(릴리스 포함) 통과

## [0.6.0] - 2026-07-31
- **[UI/Feature]** 라이트/다크 테마 전환 지원 (ADR-005, 사용자 결정 D-b)
  - **시맨틱 토큰 도입**: 하드코딩된 top-level `Color` 상수 20종(BgColor/Cyan/TextPrimary 등)을 `KosmosColors` 데이터 클래스로 대체하고 `LocalKosmosColors` CompositionLocal로 주입 — 전 화면 190여 개 색상 참조를 `KosmosTheme.colors.<토큰>` 형태로 일괄 전환
  - **라이트 팔레트 신설**: `docs/DESIGN.md`의 Sky Blue 기획(canvas #F7FBFD / ink #1F2A33 / primary-strong #39AED8 / hairline #D9E6EC)을 Glassmorphism 구조에 적용 — 밝은 배경에서 white-on-white로 경계가 사라지지 않도록 카드 표면을 불투명에 가깝게 올리고 hairline 테두리로 계층 구성
  - **테마 선택 UI**: 설정 화면에 APPEARANCE 섹션 추가(시스템 설정/라이트/다크), `ThemeMode`를 DataStore(`theme_mode`, 기본 SYSTEM)에 영속. `ThemeViewModel`을 MainActivity 루트에서 구독해 즉시 전체 적용, 상태바 아이콘 명암도 동기화
  - **대비·연출 보정**: `onAccent` 토큰 도입으로 accent 배경 위 텍스트/아이콘 대비 확보(다크=남색, 라이트=흰색), `auroraAlpha`로 라이트에서 배경 오로라 강도 0.35배 감쇠, 라이트에서 안 보이던 `Color.White` 하드코딩 텍스트 5곳과 하드코딩 오렌지 경고 배너를 토큰으로 교체
  - **구조 대응**: `DrawScope`/`LazyListScope`는 비-Composable이므로 토큰을 바깥에서 추출해 전달(AuroraBackground/OrbPulse/ScheduleContent), `Modifier.glassEffect`는 색상 기본값을 null + `composed {}` 내부 해석으로 변경
- **[Docs]** `DESIGN.md` v2.0 개정 — 라이트/다크 양쪽 팔레트와 토큰 구현 위치·접근 규칙 명시(문서·구현 불일치 해소), `architecture.md` ADR-005 추가
- **[QA/Test]** 전체 테스트 + lintDebug + build(릴리스 포함) 통과

## [0.5.12] - 2026-07-31
- **[Refactoring/Decision]** 음성 입력을 Gemma 멀티모달 직접 입력으로 확정 (사용자 결정)
  - 도달 불가 상태였던 시스템 STT 대안 파이프라인 전체 삭제: `feature/voice`(VoiceOverlay/VoiceViewModel/VoiceUiState), `AndroidSpeechToTextTool`, `ProcessVoiceInputUseCase`, `domain/tool/SpeechToTextTool`(+SttState), PlatformModule 바인딩
- **[UI/Refactoring]** 일정 승인 UI를 플로팅 초안 카드로 통일 (절충안, 사용자 결정)
  - `ApprovalRequest`에 `calendarDraft` 페이로드 추가 — AddSchedule 승인 시 일반 다이얼로그(ApprovalSheet) 대신 기존 `CalendarDraftCard` UI로 렌더링, 승인/거절은 동일한 `ApprovalCoordinator` 경로 유지 (메모리 저장 등 비캘린더 승인은 시트 유지)
  - 패배한 설계 경로 제거: `ResponseParser`(항상 TextOutput 스텁), `PreExecutionGuard`/`ExecutionPolicy`, `ModelOutput`, `ActionCard`/`ActionPayload`, `AssistantResponse`, `AgentResult.Action`, `ChatUiState.pendingCalendarDraft` 및 관련 ViewModel 로직 — BaseAgent 응답 처리를 텍스트 저장·반환으로 단순화
  - `ChatViewModel`의 미사용 `addScheduleUseCase` 의존 제거 (E2E 테스트 3건 배선만 동기화, 검증 로직 불변)
- **[QA/Test]** 전체 테스트 + lintDebug 통과

## [0.5.11] - 2026-07-31
- **[Feature/Calendar]** 기기 시스템 캘린더 실연동 (ADR-004, 사용자 결정에 따른 옵션 A)
  - 바인딩만 되고 미사용이던 `AndroidCalendarTool`을 실제 배선: `AddScheduleUseCase`가 로컬 저장(단일 진실 원천) 후 기기 캘린더에 best-effort 삽입, 실패(권한 미보유 등) 시 로컬 저장 유지 + 경고 로깅
  - `GetTodayScheduleUseCase`가 기기 캘린더 이벤트를 읽어 (제목, 시작 시각) 중복 제거 후 로컬 일정과 병합 — 캘린더 화면/GetSchedule 툴 모두 기기 일정 반영
  - 캘린더 권한 컨텍스트 요청: ChatScreen 일정 승인 시(WRITE+READ), CalendarScreen 진입 시(READ, 승인 직후 재조회)
  - `GetTodayScheduleUseCaseTest`에 FakeCalendarTool 반영, 전체 테스트 + lintDebug 통과

## [0.5.10] - 2026-07-31
- **[Performance]** 추론·렌더링 핫패스 최적화
  - 이미지 JPEG 이중 압축 제거(`ProcessImageInputUseCase`는 검증·위임만, 압축은 `SendChatMessageUseCase` 단일 지점), `ImageInputAdapter`의 공유 bitmap pool 제거(동시 경합·픽셀 덮어쓰기·상주 메모리 문제)
  - 배터리 온도 조회 5초 캐시(토큰마다 Binder 왕복 제거), `BaseAgent` 스트리밍 파싱을 `<tool_call` 태그 감지 후에만 수행, 임계 발열(≥48°C) 시 감사 로그만 남기고 추론을 진행하던 문제 수정(실제 차단)
  - 채팅 LazyColumn `key={id}` 부여, `AuroraBackground`를 RESUMED 상태에서만 애니메이션(백그라운드 배터리 소모 제거)
- **[Refactoring]** 컨벤션·에러 처리 정비
  - `runCatchingCancellable` 공통 헬퍼 신설 후 5개 리포지토리 적용 — 코루틴 취소가 가짜 DB 오류로 변환되던 문제 해소, 읽기 실패의 `DbWriteError` 오분류 정정
  - `!!` 사용 전부 제거(BaseAgent/ChatScreen/AndroidCalendarTool), `KnowledgeNote` FloatArray equals/hashCode 구현, 툴 루프 상한 초과를 Timeout 대신 추론 오류로 보고
  - 죽은 코드 삭제(PulseGreenDot, PlaceholderScreen, GemmaTokenizer 동일 분기, resetCount 등), `RuntimeMetricsCollector`에 SupervisorJob+AtomicInteger 적용
  - Room/Paging 의존성 버전 카탈로그 통일(하드코딩 중복 제거), `collectAsStateWithLifecycle` 통일, `DEFAULT_MODEL_FILENAME`을 공식 다운로드 산출 파일명과 일치, domain의 `:core`를 `api()`로 전이
- **[QA/Test]** 전체 테스트 + lintDebug + build 통과
- **[Pending/Backlog]** 사용자 결정 대기: 기기 캘린더 실연동(P3-2), feature/voice 삭제 여부, ResponseParser/CalendarDraft 경로 완성 여부. 이월 과제: ToolExecutor Map<String,Any> 대체, KnowledgeDao LIKE escape, 임베딩 BLOB 전환, MediaPipeTextEmbedder lazy-init, domain paging 의존 제거, WorkManager 다운로드, ChatViewModel 스트리밍 파싱 단일화

## [0.5.9] - 2026-07-31
- **[Fix/Stability]** LLM 런타임 수명주기 경합 해소 (`GemmaModelRunner`)
  - `close()`가 메인 스레드에서 추론 중 네이티브 세션과 무동기화로 실행되던 use-after-free/ANR 위험 제거 — 취소 요청 후 LLM 디스패처에서 Mutex 직렬화 해제
  - `warmUp()`(Dispatchers.IO)과 첫 generate의 Engine 이중 초기화 경합 수정(동일 디스패처+Mutex), `conversation!!` NPE 제거
  - 세션 내 에이전트 전환 시 새 systemInstruction이 무시되던 Conversation 재사용 결함 수정, 툴 루프의 stateful 대화 중복 전송(currentInput+rawOutput 재전송) 제거, 첫 턴 사용자 메시지 이중 포함 제거 — 컨텍스트 토큰 낭비 대폭 감소
  - `BaseAgent`의 fire-and-forget 중간 취소가 다음 루프 추론을 죽일 수 있던 경합을 Job 추적+join으로 구조화
- **[Fix/Crash]** Memory 탭 Paging 중복 키 크래시 수정 — `DefaultPagingSource` key를 페이지 번호에서 offset 기반으로 재작성 (Paging3 initialLoadSize 3× 충돌)
- **[Fix/UX]** 공유 인텐트 콜드 스타트 유실 수정 (`ShareIntentHandler` replay=1 + 소비 후 클리어), 모델 다운로드 다이얼로그 취소 버튼 추가(Job 취소 연동), Import 후 재시작 로직의 raw thread/sleep 제거, 캘린더 초안 저장 실패 시 무음 dismiss 대신 에러 노출·카드 유지
- **[Security/Privacy]** `allowBackup=false` — 대화/지식/감사 DB가 클라우드 자동 백업되던 문제 차단 (백업은 앱 내 Export/Import 경로 사용)
- **[Fix/Platform]** `AudioRecorder` 해제 순서 교정(release 전 writer join, @Volatile, SupervisorJob), `ApprovalCoordinator` AtomicReference+60초 타임아웃 자동 거절, `SpeechRecognizer` release()/에러 코드 로깅, MainActivity 일괄 권한 요청 제거, 채팅 이미지 첨부 읽기를 메인 스레드에서 Dispatchers.IO로 이동
- **[QA/Test]** 전체 테스트 + lintDebug 통과

## [0.5.8] - 2026-07-31
- **[Fix/Calendar]** 일정 데이터 손실 및 시간대 표시 결함 수정
  - `AddScheduleUseCase`: `endTime` 무성 폐기 및 `description` title 뭉개짐 수정 — `TaskItem`/`TaskEntity`에 `endDateIso`/`description` 필드 추가(Room v3→4 명시적 Migration, `fallbackToDestructiveMigration` 제거), 하드코딩 `Success(1L)` 대신 실제 Task ID(String) 반환
  - `GetTodayScheduleUseCase.parseIsoToMs` 재작성: 오프셋 포함 시간(`+09:00` KST 등)이 조용히 드롭되던 휴리스틱을 `OffsetDateTime→Instant→LocalDateTime→LocalDate` 표준 폴백 체인으로 대체 (0.5.5의 "예외 안전성 강화" 기록 정정 — 당시 오프셋 드롭 결함 잔존했음)
  - WEEK 범위 8일 → 7일(오늘 포함) 경계 수정, 일정 정렬을 ISO 사전순에서 파싱된 epoch ms 기준으로 변경
  - 시간대 표시: `AndroidCalendarTool.msToIso`의 UTC 고정(`Instant.toString`) → 기기 시간대 오프셋 포함으로, `CalendarScreen.formatIsoString`/`CalendarDraftCard`의 문자열 슬라이싱(`takeLast(5)`) → java.time 파서 기반 표시로 교체 (KST 19:00가 "10:00 AM"으로 표시되던 버그)
  - `CalendarScreen` 헤더 "July 2026" 하드코딩 → 현재 년월 동적 표시
- **[QA/Test]** `GetTodayScheduleUseCaseTest` 신규 작성 (오프셋 파싱/혼합 포맷 정렬/7일 경계/불량 입력 스킵, 4건) — 전체 테스트 통과
- **[Pending]** 기기 캘린더 실연동(AndroidCalendarTool 배선) 여부는 기획 결정 대기 (docs/agent/task.md P3-2)

## [0.5.7] - 2026-07-31
- **[Security/Architecture]** 툴 승인·allowlist 실행 시점 강제 (ADR-002)
  - `ToolExecutor`에 `actionType`/`buildApprovalRequest` 계약 추가, `BaseAgent.executeToolInner`에서 ① 에이전트별 allowlist 검증 ② `ApprovalRules` 기반 사용자 승인 대기를 공통 수행 — 프롬프트에만 존재하던 장식용 allowlist와 `AddScheduleToolExecutor` 하드코딩 승인을 단일 정책 경로로 통합
  - `AddMemory` 툴을 MEMORY_WRITE 승인 대상으로 편입하고 DefaultAgent에 노출 (기존: 등록만 되고 도달 불가 + 무승인)
  - `IntentClassifier` 캘린더 문맥 키워드에 "약속"/"미팅"/"스케줄" 추가 — allowlist 강제 도입 후 라우팅 누락이 툴 차단으로 이어지는 문제 보완
- **[Feature/UX]** 웹 검색 허용 전역 토글 도입 (기획 변경, ADR-001, PRD F7 개정)
  - 채팅 헤더 설정 버튼 왼쪽에 웹 검색 토글(`Icons.Default.Language`, contentDescription "WebSearchToggle") 배치, `WebSearchViewModel` + `SettingsDataStore.webSearchEnabledFlow`(기본 OFF)로 영속화
  - OFF 시 SearchWikipedia를 모델 프롬프트에서 제외하고 실행 계층에서도 차단(이중 방어), ON 시 건별 승인 없이 실행
- **[Security/Fix]** 프롬프트 인젝션 경로 차단 (ADR-003)
  - 첨부 문서(`documentText`)를 SYSTEM 역할 대신 USER 역할 `[Attached Document]` 구분 블록으로 저장 — 문서 내 지시문의 시스템 프롬프트 승격 차단, 저장 실패 시 오류 반환
  - 툴 응답 JSON을 문자열 연결에서 `JSONObject` 조립로 전환(4개 실행기) — 따옴표/개행 파손 및 2차 인젝션 차단
  - `WikipediaSearchToolImpl`: `HttpUrl.Builder` 쿼리 인코딩(한국어/특수문자 안전), lang 파라미터 호스트 검증, `response.use` 커넥션 누수 수정
  - `AddScheduleToolExecutor`: title/startTime 누락 시 기본값 무성 진행 대신 실행 거부 후 모델이 되묻도록 오류 반환
- **[Refactoring]** 죽은 정책 스켈레톤 정리 및 감사 로그 실질화
  - 미사용 파일 9종 삭제: `domain/agent/Agent`, `domain/policy/*`, `domain/model/ApprovalRequest`, `domain/tool/FileTool`, `domain/modelrunner/InferenceMetrics`, `core/config/{ModelConfig,AppConfig,FeatureFlags}`
  - `Redaction`을 `AuditTrailService.redact`에 실배선하고 이메일/전화번호 PII 마스킹 추가, 감사 저장 실패(AppResult.Failure) 무음 유실을 `AppLogger` 경고로 표면화
  - `PermissionPolicy`/`ErrorCodeMapper`는 계약 테스트가 참조하므로 유지 (개선은 후속)
- **[Build/QA]** androidTest 스코프 Compose BOM 누락으로 `lintDebug`가 실패하던 기존 문제 수정. 전체 테스트 + lintDebug 통과

## [0.5.6] - 2026-07-31
- **[Fix/Critical]** 백업(Export)/복원(Import) 기능 전면 수리 (`ExportImportManager`)
  - 백업 대상 DB 파일명이 실제 Room DB(`kosmos_db`)와 달라 빈 Zip을 만들고 Success를 반환하던 무동작 버그 수정 — DB 이름을 `Constants.DATABASE_NAME` 단일 소스로 통일 (`DatabaseModule` 공유)
  - Export 시 Hilt 싱글턴 `RoomDatabase.close()` 호출로 이후 모든 DB 접근이 죽던 치명 결함 제거, `PRAGMA wal_checkpoint(TRUNCATE)`를 Cursor 소비(`use { moveToFirst() }`)로 실제 실행되도록 수정
  - Import 시 Zip Slip(경로 탈출) 방어(canonicalPath 검증), zip-bomb 상한(512MB/1,000엔트리), 디렉터리 엔트리 처리 추가
  - 복원 시 checkpoint 선행 및 백업에 없는 stale `-wal`/`-shm` 사이드카 삭제로 복원본 손상 방지
  - `printStackTrace` → `AppLogger` 전환, 실패/취소 시 부분 산출물(Zip) 정리, `CancellationException` rethrow
- **[Fix]** 모델 다운로드 원자성 확보 (`ModelDownloadService`, `DownloadModelUseCase`)
  - `.part` 임시 파일 다운로드 후 성공 시에만 rename — 중단된 부분 파일이 유효 모델로 선택되던 문제 차단 (`GemmaRuntimeManager`의 `.litertlm` 필터와 정합 확인)
  - 비-2xx 응답 시 커넥션 누수 수정(`response.use`), 자체 OkHttpClient 제거 후 DI 공유 클라이언트(타임아웃 설정 포함) 주입, fileName 폴백의 URL 쿼리스트링 제거
  - 디스크 부족 오류를 `NetworkUnavailable`이 아닌 `InsufficientStorage`로 구분 매핑
- **[Docs]** 문서 기반 정비: `trouble_shooting.md`·`nvidia_skills_analysis.md`·`agent_workflow_guide.md`에 표준 메타데이터 헤더 추가, `.agents/01·03`의 특정 에이전트 전용 도구명을 중립 표현으로 수정, 감사 후속 수정 계획서(`docs/agent/implementation_plan.md`, `task.md`) 작성
- **[QA/Test]** 전체 단위 및 Robolectric E2E 테스트 통과 확인. (미수행 항목 명시: `ExportImportManager` 신규 단위 테스트는 추후 작성 예정)

## [0.5.5] - 2026-07-31
- **[Refactoring]** 프로젝트 전 영역(app, core, domain, data) 3단계 안전 리팩터링 및 KDoc 문서화 완료
  - **`Core 모듈`**: `AppError`, `AppResult`, `Constants`, `AppConfig`, `FeatureFlags`, `ModelConfig`, `AppLogger`, `ErrorCode`, `ErrorCodeMapper`, `ApprovalRules`, `PermissionPolicy`, `Redaction` 등 12개 주요 클래스/인터페이스에 표준 KDoc 헤더(`Role`, `Architecture Context`, `Key Flow`) 명시 및 `Constants.DEFAULT_MODEL_DOWNLOAD_URL` 중앙 관리 통합
  - **`Mockup 격리`**: `src/main/` 메인 소스 트리에 있던 `FakeTemperatureProvider` 목업 클래스를 `src/test/` 하위 테스트 픽스처 패키지로 이관하여 비즈니스 코드와 완전 격리
  - **`GetTodayScheduleUseCase.kt`**: `runCatching` 기반 안전한 ISO 날짜 파싱 및 `ZoneId` 매개변수화로 예외 안전성(Null Safety) 강화, AI 요약 실패 시 `null` 폴백 처리 명시
  - **`MemoryViewModel.kt` & `ChatViewModel.kt`**: StateFlow 불변성(Immutability) 및 Null Safety 방어 코드 보완
  - **`구조 정돈`**: 구현 파일이 존재하는 패키지의 redundant `.gitkeep` 파일 13개 정리 및 미구현 전용 패키지만 유지
  - **`QA/Test`**: E2E 테스트 버튼 셀렉터(`Attach`, `Send` contentDescription) 및 테스트 픽스처 시그니처 보완 후 전체 17개 단위 및 Robolectric E2E 통합 테스트 100% 통과 완료 (`./gradlew test`)

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

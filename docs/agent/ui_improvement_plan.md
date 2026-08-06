# ui_improvement_plan.md — 전체 UI 개선 계획서
> **문서 버전**: v1.4 | **최종 수정일**: 2026-08-06 | **상태**: Done (Phase A·B·C·D 완료. §7 이월 백로그 4건 + 실기기 QA만 잔여)
> **관련 모듈**: `:app` (feature/*, ui/*), `:core` (mapper)
> **기준 문서**: PRD.md v1.2 / architecture.md v1.2 (ADR-001~004) / DESIGN.md / CHANGELOG 0.5.12

---

## 1. 개요 (Overview)

2026-07-31 코드 감사·리팩터링(0.5.6~0.5.12) 완료 후, 사용자 요청으로 수립한 UI 전면 개선 계획이다.
**세션이 바뀌어도 이어받을 수 있도록** 작성한다 — 착수 전 `AGENTS.md`, `.agents/*`, `.agents/skills/android-friday-skill/SKILL.md`를 반드시 읽을 것.

### 작업 수칙 (요약)
- 기존 `src/test/` 검증 로직 수정 금지. `!!`/`Any` 금지. E2E는 `testTag`/`contentDescription` 셀렉터 사용.
- 각 Phase 완료 시 `./gradlew test` + `lintDebug`, `docs/CHANGELOG.md` 기록, 본 문서 체크박스 갱신.
- Glassmorphism 디자인 언어 유지: 기존 `glassEffect()`, `Cyan`/`Violet`/`TextPrimary` 팔레트, 36.dp 원형 아이콘 버튼 규격을 따를 것.

---

## 2. Phase A — 죽은 인터랙션 살리기 (효과 큼 / 비용 작음)

### A-1. 캘린더 날짜 선택 활성화
- **파일**: `feature/calendar/CalendarScreen.kt`, `CalendarViewModel.kt`, (필요시) `GetTodayScheduleUseCase`
- **현황**: 상단 날짜 스트립(`DatePill`)이 순수 장식(탭 무반응), `CalendarViewModel.selectedRange`는 노출만 되고 UI 미사용.
- **작업**:
  1. `DatePill` 탭 → 선택 날짜 상태(`selectedDate: LocalDate`)를 ViewModel에 추가하고 해당 일 일정만 필터 표시
  2. Today/Week 세그먼트 토글 UI 추가(`selectedRange` 실사용) — 선택 시 `loadSchedule(rangeType)` 재호출
  3. 기기 캘린더 병합(ADR-004) 이벤트도 동일 필터 적용 확인
- **완료 기준**: 날짜 탭 시 리스트가 즉시 필터링되고 선택 pill이 하이라이트됨. 미래 6일까지 탐색 가능.

### A-2. Memory 탭 초기 선택 상태
- **파일**: `feature/memory/MemoryUiState.kt`
- **현황**: 기본 필터 `ALL` — 첫 진입 시 "Memory"/"Tasks" 둘 다 미선택으로 보임.
- **작업**: 기본값을 `KNOWLEDGE`로 변경 (한 줄). `MemoryFilterType.ALL`이 다른 곳에서 안 쓰이면 enum에서 제거 검토.

### A-3. 스트리밍 중 정지 버튼
- **파일**: `feature/chat/ChatScreen.kt`(입력바 전송 버튼), `ChatViewModel.kt`
- **현황**: 응답 생성 중 취소 수단이 없음 (온디바이스 LLM 특성상 응답이 느릴 수 있음).
- **작업**:
  1. `isInFlight == true`일 때 전송 버튼을 정지 아이콘(■)으로 스왑, `contentDescription = "Stop"`
  2. `ChatViewModel.cancelGeneration()` 신설 → `modelRunner.cancel()` 호출 (suspend — viewModelScope)
  3. 취소 후 부분 응답 처리: 스트리밍된 내용까지 저장할지/버릴지 — **저장 권장**(BaseAgent가 이미 저장 경로 보유, 취소 시 AppResult 흐름 확인 필요)
- **주의**: `ModelRunner.cancel()`은 뮤텍스를 잡지 않도록 설계됨(GemmaModelRunner 참조) — UI에서 바로 호출 가능. E2E `onNodeWithContentDescription("Send")` 셀렉터가 깨지지 않도록 정지 상태에서만 아이콘 교체.

---

## 3. Phase B — 신뢰감 있는 피드백

### B-1. 오류 메시지 인간화 (`ErrorCodeMapper` 재배선)
- **파일**: `core/mapper/ErrorCodeMapper.kt`, `core/mapper/ErrorCode.kt`, 각 ViewModel(Chat/Memory/Calendar/ModelManagement)
- **현황**: `AppError.toString()`이 그대로 노출되는 경로 존재. `ErrorCodeMapper`는 테스트에서만 사용되며 substring 휴리스틱 오분류 있음("timeout"→MISSING_TIME_INFO, 미분류 ValidationError→INPUT_TOO_LONG 등).
- **작업**:
  1. `ErrorCodeMapper`를 안정적 타입 매칭으로 재작성 — **주의: `ContractConsistencyTest`가 기존 매핑 2건(ImageTooLarge→IMAGE_TOO_LARGE, SearchError→SEARCH_TIMEOUT)을 검증하므로 해당 계약은 유지**
  2. `ErrorCode → 사용자 문구(한국어)` 매핑 함수 추가 (예: DbWriteError→"저장에 실패했어요. 다시 시도해주세요.")
  3. 각 ViewModel의 `error: AppError` 노출부를 사용자 문구로 변환해 표시 (스낵바 또는 기존 배너)
- **완료 기준**: 어떤 화면에서도 raw 에러 클래스명이 노출되지 않음.

### B-2. 웹 검색 토글 피드백
- **파일**: `feature/chat/ChatScreen.kt` (CustomChatHeader 인근), `AppNavHost.kt`
- **작업**: 토글 변경 시 스낵바 1줄 — "웹 검색 허용됨 — 위키피디아 검색을 사용할 수 있어요" / "웹 검색 차단됨". `SnackbarHostState`를 Scaffold에 연결.

### B-3. 모델 다운로드 UX 2단계
- **파일**: `feature/settings/ModelManagementScreen.kt`, `ModelManagementViewModel.kt`, (신규) WorkManager Worker
- **현황**: 취소 버튼은 있으나(0.5.9) 다이얼로그가 화면을 점유하고, 앱 종료 시 다운로드가 중단됨.
- **작업** (규모 큼 — 별도 세션 권장):
  1. 다이얼로그 → 화면 내 진행 카드로 전환 (진행률 + 취소 + 남은 저장 공간 표시)
  2. WorkManager `CoroutineWorker`로 다운로드 이관 + Foreground 알림(진행률) — `.part` 원자화(0.5.6)와 정합 유지
  3. 완료/실패 알림 및 재시도

---

## 4. Phase C — 채팅 화면 완성도

### C-1. 날짜 구분선
- **파일**: `feature/chat/ChatScreen.kt`
- **작업**: LazyColumn에서 `createdAt` 날짜가 바뀌는 지점에 "오늘 / 어제 / M월 d일" 구분선 아이템 삽입. `items(key=id)` 유지 — 구분선 key는 `"date-$date"`.

### C-2. 메시지 롱프레스 복사
- **작업**: `ChatBubbleUser`/`ChatBubbleAssistant`에 `combinedClickable` 롱프레스 → 클립보드 복사 + 스낵바. (선택) 어시스턴트 버블에 복사 아이콘 상시 노출.

### C-3. 최신으로 이동 FAB
- **작업**: `listState.canScrollForward`가 true일 때(위로 스크롤한 상태) 우하단에 ↓ FAB 표시 → `animateScrollToItem(마지막)`. 스트리밍 중 자동 스크롤과 충돌하지 않게 사용자가 위로 스크롤하면 자동 스크롤 일시 해제 로직 포함.

### C-4. 첨부 프리뷰
- **작업**: 이미지 첨부(sharedInput=Image) 시 입력바 위에 썸네일 칩(64.dp, 삭제 X 버튼) 표시. `AsyncImage` 계열 라이브러리 추가 없이 `BitmapFactory` 다운샘플 디코딩(IO) 사용.

---

## 5. Phase D — 디자인 정본 확정 ✅ 완료 (2026-07-31, v0.6.0)

- **결정**: **D-b(라이트/다크 전환 지원)** 채택 — 사용자 결정. ADR-005 기록 완료.
- **구현 완료**: `KosmosColors` 시맨틱 토큰 + `LocalKosmosColors` CompositionLocal, 라이트 팔레트(DESIGN.md Sky Blue 기반) 신설, 설정 APPEARANCE 섹션(SYSTEM/LIGHT/DARK) + DataStore 영속, `onAccent`/`auroraAlpha` 보정 토큰, `DESIGN.md` v2.0 개정.
- **이후 규칙 (신규 UI 작업 시 필수)**:
  - 색상은 반드시 `KosmosTheme.colors.<토큰>` 사용 — `Color(0xFF...)` 리터럴이나 `Color.White` 직접 사용 금지
  - `DrawScope`/`LazyListScope` 람다 안에서는 토큰 접근 불가 → 바깥에서 `val`로 추출해 전달
  - 신규 화면 추가 시 **라이트 모드에서도 반드시 육안 확인** (대비 손실이 다크에서는 드러나지 않음)

---

## 6. 진행 체크리스트

- [x] A-1 캘린더 날짜 선택 + Today/Week 세그먼트 (선택 날짜 필터, 재탭 해제, 오늘 아닌 날짜 선택 시 주간 자동 확장)
- [x] A-2 Memory 기본 필터 KNOWLEDGE (`MemoryFilterType.ALL` 제거)
- [x] A-3 스트리밍 정지 버튼 (`InputAction` 3-상태 전환, `cancelGeneration()` — 부분 응답 보존)
- [x] Phase A 검증 (test/lint 통과)
- [x] B-1 ErrorCodeMapper 재배선 + 사용자 문구 (`ValidationReason` 표준 토큰, `ErrorMessages`, 표시 지점 6곳 연결, 회귀 테스트 5건)
- [x] B-2 웹 검색 토글 스낵바 (+ 표시되지 않던 `uiState.error`도 스낵바로 노출)
- [x] Phase B(1~2) 검증 (test/lint 통과)
- [x] B-3 1단계: 다이얼로그 → 화면 내 진행 카드(진행률·잔여 저장 공간·취소)
- [x] B-3 2~3단계: WorkManager 이관 + 전경 알림 + 재시도 (v0.7.0, ADR-006)
> 이관 시 범위가 확대된 부분: 재시도가 의미를 갖도록 HTTP Range 이어받기(`.part` + `.part.meta`)를 함께 구현했고,
> 저장 공간 사전 점검과 기존 모델 파괴 회귀(`.bak` 롤백)도 같이 수정했다. 네트워크 제약은 Wi-Fi 전용(사용자 결정).
> 부수 발견: `:data`에 `kotlin-serialization` 플러그인이 빠져 있어 모듈 내 `@Serializable`이 런타임에 깨지던 상태였다
> (내보내기/가져오기도 함께 영향). 플러그인 추가로 해소.
> 남은 것: 알림용 24dp 모노 벡터 아이콘, 그리고 ADR-006의 수동 QA 체크리스트(전경 승격·Doze 백오프·실제 3.6GB 전송).
- [x] C-1 날짜 구분선 (`ChatRow` 도입 — 오늘/어제/M월 d일)
- [x] C-2 롱프레스 복사 (양쪽 버블 `combinedClickable` + 스낵바)
- [x] C-3 최신으로 이동 FAB (사용자 스크롤 이탈 시 자동 스크롤 일시 해제)
- [x] C-4 첨부 썸네일 프리뷰 (`inSampleSize` 다운샘플, IO 디코딩, 외부 라이브러리 없음)
- [x] Phase C 검증 (test/lint/build 통과)
- [x] D 디자인 정본 결정(D-b: 라이트/다크 전환) → 토큰화 + 테마 선택 UI + DESIGN.md v2.0 + ADR-005 (v0.6.0)
- [x] 최종: 전체 test/lint 통과(51건), CHANGELOG 0.7.0 기록
- [ ] README 스크린샷 갱신 (라이트/다크 테마 반영 — 실기기 캡처가 필요해 미착수)

## 7. 이월 백로그 (UI 외 — CHANGELOG 0.5.10 Pending 참조)

### 완료
- [x] ToolExecutor `Map<String,Any>` 대체 → `ToolArguments` 래퍼 (v0.7.1). 태그 유실 버그·승인/실행 불일치·깨진 tool_call 침묵도 함께 해소
- [x] KnowledgeDao LIKE escape → `SqlLike.escape` + `ESCAPE '\'` (v0.7.1). 태그 정확 매칭도 함께
- [x] MemoryViewModel 콜백→UiState 이벤트 전환 (v0.7.2). `BackupState` 도입으로 Activity 컨텍스트 누수와 완료 통지 유실을 함께 해소하고 `Toast`도 제거. 내보내기/가져오기 UI 진입점을 붙이는 순간 이 결함이 실제로 발현되므로 함께 처리했다
- [x] 내보내기/가져오기 UI 진입점 신설 (v0.7.2) — `prd.md` F8 미달이었다. 개인정보 경고·덮어쓰기 확인·재시작 확인 3단 다이얼로그와 SAF 저장 위치 선택 포함
- [x] `AuditRepository.getPagedByType` 제거 (v0.7.2) — 스펙에 없는 선행 구현이었고 호출자가 없었다
- [x] 알림용 24dp 모노 벡터 3종 + `colors.xml` accent (v0.7.3). 제네릭 다운로드 화살표로 결정 — 브랜드 마크는 파생시킬 벡터가 없어 앱 아이덴티티 작업까지 번지므로 제외 (사용자 결정)
- [x] 태그에 콤마가 포함된 경우 (v0.7.3). 공백 치환으로 결정 (사용자 결정). 구분자 자체를 바꾸는 근본 해결은 마이그레이션이 필요해 제외
- [x] **MediaPipeTextEmbedder lazy-init** (v0.7.3). `init` 블록 제거 + `Mutex` 지연 초기화로 실패를 기억하지 않게 했고, `TextEmbedder.embed`를 `suspend`로 바꿔 로드·추론을 `Dispatchers.Default`로 옮겼다
  - 조사 중 정정된 사실: "조용한 강등"은 **읽기에만** 해당한다. `SaveKnowledgeUseCase`는 폴백 없이 실패하므로 임베더가 죽으면 메모리 저장이 통째로 막혔다. 이 폴백 부재는 **의도된 동작으로 남긴다** — 임베딩 없이 저장하면 그 노트가 벡터 검색에 영구히 안 잡혀 폴백이 오히려 조용한 데이터 손실이 된다
  - RAG 품질 저하 배너는 만들지 않았다 — 실패가 일시적이면 필요한 것은 재시도이고, 영구적이면 이미 저장 실패로 드러난다
  - `close()`도 추가하지 않았다 — `@Singleton`은 프로세스 수명과 같아 호출 지점이 없다. 소비자 없는 API를 남기지 않는다는 0.7.2의 `getPagedByType` 제거와 같은 기준

### 잔여
성능 (남은 1건)
- [ ] **임베딩 BLOB 전환** — `embedding: String`(콤마 구분 실수)이라 `searchByVector`가 1000행을 `split`+`toFloatOrNull`로 파싱해 RAG 질의 한 번에 30만 개 남짓 단기 할당이 발생한다. `toDomain()`이 같은 문자열을 다시 파싱하는 이중 파싱도 있음. 정밀도 손실은 없으나(`Float.toString`은 라운드트립 보장), `mapNotNull`이 파싱 실패 항목을 조용히 버려 길이 비교에서 탈락한 노트가 벡터 검색에서 영구히 안 보이게 되는 실패 모드가 있다. Room 스키마 마이그레이션(v4→v5) 필요

UI 정확성 (남은 1건)
- [ ] **ChatViewModel 스트리밍 파싱 단일화** — 같은 토큰 스트림을 3곳에서 각자 다른 누적 버퍼로 파싱한다. `BaseAgent`의 버퍼는 툴 루프마다 초기화되는데 `ChatViewModel.accumulatedRaw`는 초기화되지 않아 **툴 턴에서 1턴 텍스트가 2턴에 이어붙어 보인다**. 반쯤 열린 `<tool_call>`은 정규식에 걸리지 않아 **프로토콜 문법이 그대로 노출**되고, 닫히지 않은 `<|think|>`는 뒤 내용을 삼켜 버블이 잠깐 빈다. 0.5.10에서 `BaseAgent`에 적용한 "태그 감지 후에만 파싱" 최적화가 `ChatViewModel`의 동일 루프에는 적용되지 않았다. `ChatRequest.onToken` 계약을 3계층에 걸쳐 변경해야 함

아키텍처
- [ ] **domain paging 의존 제거** — `AuditRepository.getPaged()`, `KnowledgeRepository.getPagedData()`가 `Flow<PagingData<T>>`를 반환해 Pure Kotlin JVM 모듈이 `androidx` 타입을 공개 계약에 노출한다. 소비자가 ViewModel 2곳이고 둘 다 곧바로 `cachedIn`하므로 `Pager` 생성만 올리면 됨. 0.7.2에서 `getPagedByType`을 지워 옮길 계약이 하나 줄었다

세부 항목
- [ ] per-tool `@Serializable` 타입 스키마 — `ToolArguments`로 런타임 결함은 제거했으나 인자 이름이 여전히 프롬프트 텍스트와 호출부에만 존재한다(컴파일 타임 검증 없음). `ToolExecutor` 제네릭화는 `ToolRegistry`/`BaseAgent`에 star projection이 번져 별건으로 남김
- [ ] `content` 검색 FTS4/5 전환 — 와일드카드 접두사 패턴(`'%' || ...`)이라 인덱스를 쓰지 못하는 문제는 이스케이프로 해결되지 않는다
- [ ] adaptive icon(`ic_launcher_foreground`) 및 KOSMOS 브랜드 마크 — 앱 아이콘 디자인 시 착수. 알림 아이콘(v0.7.3)은 제네릭 화살표로 처리했으므로 급하지 않다

### 확인 필요 (임의 판단하지 않음)

결정이 필요한 항목은 현재 없다. 아래는 사용자 실기기가 필요한 검증이다.

**사용자 실기기가 필요한 것**
- ADR-006 수동 QA 체크리스트 — 전경 승격 / Doze 백오프 / 실제 3.6GB 전송
- v0.7.2 백업 왕복 QA — 내보내기→저장→가져오기→재시작, 특히 **가져오기 진행 중 화면 회전**(`prd.md` V1-AC4)
- v0.7.3 알림 아이콘 시각 확인 — 상태바 알파 마스크 결과(실루엣이 뭉개지지 않는지)와 알림 그림자의 accent 틴트. 라이트/다크 양쪽
- README 스크린샷 갱신 (라이트/다크)

# ui_improvement_plan.md — 전체 UI 개선 계획서
> **문서 버전**: v1.1 | **최종 수정일**: 2026-07-31 | **상태**: In Progress (Phase A·B(1~2)·C·D 완료, B-3 2~3단계만 잔여)
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
- [~] B-3 1단계만 완료: 다이얼로그 → 화면 내 진행 카드(진행률·잔여 저장 공간·취소).
> 진행 메모: 2~3단계(WorkManager 이관 + Foreground 알림 + 재시도)는 계획서 권고대로 **별도 세션**으로 남김.
> 착수 시 필요한 것: WorkManager+Hilt(HiltWorker/WorkerFactory), POST_NOTIFICATIONS(API 33+), 알림 채널,
> `DownloadModelUseCase`를 Worker로 이관하되 `.part` 원자화(0.5.6) 정합 유지, WorkInfo 진행률 관찰.
- [x] C-1 날짜 구분선 (`ChatRow` 도입 — 오늘/어제/M월 d일)
- [x] C-2 롱프레스 복사 (양쪽 버블 `combinedClickable` + 스낵바)
- [x] C-3 최신으로 이동 FAB (사용자 스크롤 이탈 시 자동 스크롤 일시 해제)
- [x] C-4 첨부 썸네일 프리뷰 (`inSampleSize` 다운샘플, IO 디코딩, 외부 라이브러리 없음)
- [x] Phase C 검증 (test/lint/build 통과)
- [x] D 디자인 정본 결정(D-b: 라이트/다크 전환) → 토큰화 + 테마 선택 UI + DESIGN.md v2.0 + ADR-005 (v0.6.0)
- [ ] 최종: 전체 test/lint, CHANGELOG, README 스크린샷/기능 서술 갱신

## 7. 이월 백로그 (UI 외 — CHANGELOG 0.5.10 Pending 참조)
ToolExecutor `Map<String,Any>` 대체, KnowledgeDao LIKE escape, 임베딩 BLOB 전환, MediaPipeTextEmbedder lazy-init, domain paging 의존 제거, ChatViewModel 스트리밍 파싱 단일화, MemoryViewModel 콜백→UiState 이벤트 전환

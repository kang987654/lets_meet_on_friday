# Kotlin & Android 프로젝트 전역 AI 에이전트 지침 (AGENTS.md)
> **적용 레포지토리**: `lets_meet_on_friday` (KOSMOS)
> **최종 개정일**: 2026-08-21

이 문서는 이 저장소에서 작업하는 AI 코딩 에이전트를 위한 전역 지침입니다.
모든 리팩터링, 신규 기능 구현 및 코드 수정 작업 시작 전 아래 지침을 반드시 준수해야 합니다.
아키텍처 결정의 맥락은 `docs/architecture.md` 의 ADR, 회차별 이력은 `docs/CHANGELOG.md` 가 진실이다.

---

## 1. 프로젝트 빌드 및 검증 명령어 (Commands)
**회차(마일스톤) 게이트**: 매 회차 종료 시 아래 세 개가 전부 녹색이어야 커밋한다.

```bash
./gradlew test lintDebug assembleDebug
```

- 빠른 반복에는 `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin` 로 컴파일만 먼저 확인.
- 회차마다 `docs/CHANGELOG.md` 에 기록한다 — 무엇을 왜 바꿨는지, 발견한 결함은 원인까지.

### 커밋 규칙
- **커밋 메시지는 한 줄 제목만** — 본문/불릿 없음 (사용자 결정).
- **`git add -A` 전에 staged 목록을 확인할 것** — `./gradlew test` 가 `scratch/lab/fixtures/*` 를
  재생성한다(PromptFixtureExport). 의도치 않은 픽스처 갱신이 기능 커밋에 딸려 들어간 전례가 있다.
- `scratch/` 에서 **실제 대화 파생 데이터**(kosmos_db, exp33 출력 등)는 절대 커밋하지 않는다 —
  `.gitignore` 의 명시 규칙 참조. 스크립트(코드)는 `scratch/lab/` 으로 추적된다.

---

## 2. 안전 수칙 및 단계별 절차 (Safety & Phases)

### ① 작업 절차 (2026-08-21 현행화 — 초기 정리 시대의 3-Phase 절차를 대체)
- **큰 작업(다파일·신규 기능)은 구현 전에 계획을 세워 사용자 승인**을 받는다 — 탐색(코드 수정
  없음) → 설계 → 계획 제시. 결정이 갈리는 지점(생성 시점·기본값 등)은 계획 단계에서 질문한다.
- **구현은 마일스톤 단위**로 쪼갠다 — 각 마일스톤이 독립적으로 컴파일·테스트 녹색이어야 하고,
  게이트(§1) 통과 후 즉시 커밋한다. 회차가 끝나면 문서 갱신(CHANGELOG·관련 설계 문서 상태)이
  마지막 마일스톤이다.
- 계획과 실제가 갈리면(구현 중 발견) 그 이유를 `[WHY]` 주석과 CHANGELOG 에 남긴다 — 계획을
  조용히 덮어쓰지 않는다.

### ② 테스트 코드 보호 및 Robolectric 수칙 (Strict Rule)
- **기존 테스트 단언 수정 0건**: `src/test/` 하위의 기존 테스트 검증 로직(`@Test`)을 임의로 삭제하거나 오버라이딩하지 말 것. 테스트 실패 시 **구현 코드를 수정하여 통과**시켜야 한다. (생성자 인자 추가 등 컴파일을 위한 생성부 수정은 허용 — 단언은 불변.)
- **E2E 계약 표면 (변경·한글화 금지)**: `ChatScreen` 은 기본 인자만으로 단독 compose 된다 —
  신규 파라미터는 반드시 기본값을 가진다. 셀렉터 문자열 `"Attach"`/`"Send"`(contentDescription),
  `"Enter message"`(플레이스홀더), `"Document Attached"`(첨부 프리뷰)는 테스트 계약이다 —
  0.19.2 한글화 회차에서 마지막 것을 바꿨다가 E2E 가 잡아냈다.
- **Robolectric + Compose E2E 제약**:
  - `hiltViewModel()` 크래시 방지를 위해 Compose Screen 테스트 시 ViewModel을 수동으로 인스턴스화하여 주입한다.
  - Background 코루틴 검증 시 `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()` Polling 루프를 사용한다.
  - TextField 검색 시 Hint 텍스트(`onNodeWithText`) 대신 `hasSetTextAction()` 또는 `testTag`를 사용하며, 버튼 클릭 시 `onNodeWithContentDescription("Attach")` / `"Send"`를 사용한다.

### ③ JVM 게이트가 구조적으로 못 잡는 결함 (실기기 확인 목록으로 남길 것)
- **시스템 바 인셋/윈도우 레이아웃**: Robolectric 은 인셋이 전부 0 이다 — M3 Scaffold 는
  topBar/bottomBar 슬롯의 인셋을 슬롯 자신이 처리한다고 가정하므로(TopAppBar/NavigationBar 는
  내장), 평범한 Row/Column 을 슬롯에 넣으면 실기기에서 상태바·제스처 바에 깔린다
  (0.19.1 실기기 결함 — 조작 불능이었다).
- **색 대비·다크/라이트 렌더**: 테스트가 색을 검증하지 못한다 — UI 회차는 라이트 모드 육안
  확인을 게이트에 포함한다.
- UI 셸을 바꾼 회차는 CHANGELOG 에 실기기 확인 목록을 반드시 남긴다.

### ④ 결정성(플레이크 방지) 수칙 — 2026-08-21 브리핑 회차에서 확립
- **벽시계 금지**: 시각 판정 로직은 `now: Long` 을 인자로 받는다(기본값
  `System.currentTimeMillis()`). 판정을 벽시계에 묶으면 테스트가 실행 시각에 따라 갈린다.
  시간대가 걸리면 순수 함수로 분리하고 테스트는 UTC 를 명시한다.
- **@Singleton 백그라운드 구독은 init 금지**: 생성자/init 에서 Flow 구독을 시작하면 Hilt
  테스트가 주입만으로 부수효과를 일으킨다 — 명시적 `start()`(멱등, KosmosApp.onCreate 가
  호출)로 분리하면 HiltTestApplication 에서는 불리지 않아 자동 격리된다.

### ⑤ 내비게이션·수명주기 불변식
- `navController.popBackStack()` 을 화면 콜백에 직접 노출하지 않는다 — 예측 뒤로가기와
  이중 pop 되어 NavHost 가 비면 복구 불가 빈 화면이 된다. `AppNavHost.navigateBack()`
  (루트 가드)을 쓴다.
- **백그라운드에서 모델을 로드하지 않는다** — onStop/onTrimMemory 가 즉시 3.6GB 를 해제한다.
  백그라운드성 추론은 전부 "엔진 Ready 편승" 패턴(EpisodeSummarizeScheduler,
  MorningBriefingGenerator 전례). oneShot 부수 추론은 반드시 `oneShot = true`
  (빼면 채팅 KV 파괴 — ADR-010·014).

---

## 3. KDoc 및 주석 작성 컨벤션

### ① KDoc 작성 (클래스, 인터페이스, 주요 함수)
- 모든 Public 함수와 클래스에는 KDoc(`/** ... */`)을 필수 작성한다.
- Core 클래스(Orchestrator, Agent, UseCase 등) 작성 시 아래 필수 항목을 포함한다:
  - **Role**: 클래스의 핵심 역할
  - **Architecture Context**: 소속 레이어 및 주요 의존성
  - **Key Flow**: 주요 동작 흐름

### ② 인라인 주석 (`//`)
- 코드가 *어떻게(How)* 동작하는지는 타입 시스템과 변수명으로 나타내고 주석을 생략한다.
- **왜(Why)** 이렇게 작성했는지에 대한 비즈니스 이유, 프레임워크 제약사항, 우회(Workaround) 로직에만 주석을 남긴다 (`// [WHY] ...`).

---

## 4. Kotlin & Android 개발 가이드라인

1. **AGP 9.0+ 라이브러리 모듈 수칙**:
   - 신규 모듈 생성 시 `build.gradle.kts`에 `id("org.jetbrains.kotlin.android")`를 **절대 재선언하지 않는다** (AGP 9.0+ 내장 플러그인 충돌 방지).
2. **Strict Type & Null Safety**:
   - `Any` 및 강제 언패킹(`!!`) 사용을 금지한다. `?` 연산자, `let`, `runCatching`, `elvis(?:)` 연산자를 활용한다.
3. **불변성(Immutability) 지향**:
   - `var` 대신 `val`을 사용하고, `MutableList`를 외부로 노출할 때는 `List` 또는 Immutable Collection 인터페이스로 래핑한다.
4. **비동기(Coroutines/Flow) 및 Hilt DI**:
   - Blocking 코드가 메인 스레드나 Coroutine 내부에서 실행되지 않도록 Dispatcher 분리(`Dispatchers.IO`)를 확인한다.
   - 클래스 분리/생성 시 Hilt DI 모듈(`@Provides`, `@Binds` 등)의 객체 등록 여부를 함께 체크한다.
   - `@Singleton` 이 아무에게도 주입되지 않으면 인스턴스화되지 않는다 — 백그라운드 상주
     컴포넌트는 `KosmosApp` 에 `@Inject lateinit var` 로 eager 주입한다 (briefingGenerator 전례).
5. **UI 색상은 `KosmosTheme.colors` 토큰만** 사용한다 — 하드코딩 `Color(...)` 금지. 라이트/다크
   팔레트가 토큰째 바뀌므로 토큰만 쓰면 테마 대응이 자동이다. 시각 언어는 `glassEffect`.
6. **사용자 표기 규칙**: UI 문자열은 한국어(§2-② E2E 계약 문자열 제외), 일시 표기는
   `IsoDateTimeParser.toDisplayKorean` 이 단일 출처다 — ISO 원문을 화면·모델 관측값에 노출하지
   않는다 (0.19.2).

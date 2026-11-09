# 테스트 작성 요령 (Robolectric + Compose)

> AGENTS.md §2-② 에서 옮겼다(2026-11-09 — 테스트를 쓸 때만 필요한 상세라 매 세션 읽는 정본에서 뺐다). 규칙(단언 보호·E2E 계약)은 AGENTS.md 에 그대로 있다.

- `hiltViewModel()` 크래시 방지를 위해 Compose Screen 테스트 시 ViewModel 을 수동으로 인스턴스화하여 주입한다.
- `@UninstallModules` 로 모듈을 떼면 빠진 의존성(예: Tokenizer)은 `@BindValue` 로 다시 묶는다.
- 테스트 mock 이 오버라이드하는 플랫폼 클래스 메서드(예: `AudioRecorder.stopRecording`)는 프로덕션에서 `open suspend fun` 이어야 한다.
- Background 코루틴 검증 시 `ShadowLooper.runUiThreadTasksIncludingDelayedTasks()` Polling 루프를 사용한다.
- TextField 검색 시 Hint 텍스트(`onNodeWithText`) 대신 `hasSetTextAction()` 또는 `testTag` 를 사용하며, 버튼 클릭 시 `onNodeWithContentDescription("Attach")` / `"Send"` 를 사용한다.
  잠긴(비활성) 입력란은 글자 입력 동작을 내놓지 않아 `hasSetTextAction()` 으로 찾을 수 없다 — "없음"을 단언한다(0.41.7 `ChatInputBarTest`).
- mockk 로 모의하는 함수에 기본값 인자를 더하지 않는다 — `coEvery { f("s1") }` 가 기록 순간의 기본값(예: 현재 시각)까지 고정해 실제 호출과 어긋난다. 기본값이 필요하면 인자 수가 다른 함수를 따로 둔다(0.41.7 M4 `ContextBuilder.build`).

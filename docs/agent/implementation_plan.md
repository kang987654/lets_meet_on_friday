# Phase 13: 멀티모달 (이미지, 음성, 문서) E2E 테스트 구현 계획

텍스트 입력 시나리오에 더해, Kosmos 앱의 핵심 기능인 멀티모달(이미지, PDF, 음성) 입력에 대한 전체 파이프라인(UI ➔ ViewModel ➔ Orchestrator ➔ DB) E2E 검증 코드를 작성합니다.

## User Review Required
> [!IMPORTANT]
> 시스템 파일 탐색기(갤러리/파일) 창이나 권한 요청 팝업은 Compose UI 외부에 존재하는 안드로이드 시스템 UI입니다. 이를 Robolectric 테스트 환경에서 검증하기 위해서는 **시스템 팝업을 띄우는 대신 "사용자가 이미지를 선택했다"고 가짜(Mock) 응답을 내려주는 레지스트리(MockActivityResultRegistry)**를 주입하는 기법을 사용합니다. 이 방향성으로 진행하는 것에 동의하시나요?

## Proposed Changes

### 1. `MultimodalChatE2ETest.kt` 신규 작성
- **경로**: `app/src/test/java/com/kosmos/app/MultimodalChatE2ETest.kt`
- **내용**: 
  - `LocalActivityResultRegistryOwner`를 오버라이드하여 파일 선택 및 권한 요청 결과를 모의(Mocking)하는 인프라 구축
  - 시나리오 1: 첨부 버튼(클립 모양) 클릭 ➔ 가짜 이미지 URI 반환 ➔ 뷰모델에 `SharedInput.Image`로 전달되는지 검증
  - 시나리오 2: 첨부 버튼 클릭 ➔ 가짜 PDF 텍스트 반환 ➔ 뷰모델에 `SharedInput.Document`로 전달되는지 검증
  - 시나리오 3: 마이크 버튼 클릭 ➔ 오디오 권한 승인 Mocking ➔ 음성 녹음 상태(`isRecording`)로 전환되는지 UI 검증
  - **Gemma 모델 파이프라인 검증 (사용자 피드백 반영)**: 단순히 UI에 첨부되는 것을 넘어, 전송 버튼을 눌렀을 때 해당 파일(이미지/문서) 데이터가 `Orchestrator`를 거쳐 최종적으로 `ModelRunner(Gemma 모델)`의 입력 프롬프트로 정확히 전달되는지까지 Mock 객체를 통해 추적(Verify)합니다.

### 2. (필요 시) `ChatInputBar.kt` UI Test Tag 추가
- 멀티모달 버튼들을 테스트 환경에서 쉽게 클릭할 수 있도록 첨부 버튼과 마이크 버튼에 `Modifier.testTag("attach_button")`, `Modifier.testTag("mic_button")` 등을 주입할 예정입니다.

## Verification Plan
### Automated Tests
- `./gradlew :app:testDebugUnitTest --tests "*MultimodalChatE2ETest*"` 명령어를 실행하여 작성한 3가지 멀티모달 시나리오가 모두 정상적으로 PASS(초록불)하는지 확인하겠습니다.

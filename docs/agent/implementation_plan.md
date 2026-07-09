# [Phase 10] 음성 입력(ASR) 기능 고도화 계획

사용자가 타이핑뿐만 아니라 목소리로도 편리하게 KOSMOS 에이전트와 대화할 수 있도록, 음성 입력 기능을 도입하려 합니다.

## User Review Required
> [!IMPORTANT]
> - `SKILL.md`에 따르면 Gemma 4 모델은 `Content.AudioFile` 형태로 오디오 데이터를 직접 입력받을 수 있습니다.
> - 따라서 구글 클라우드 STT(Speech-to-Text) API 등을 별도로 사용하지 않고, 안드로이드 기기의 내장 마이크로 녹음한 음성 파일을 그대로 Gemma 모델에 전달하여 **온디바이스 ASR (자동 음성 인식)** 처리를 수행할 수 있습니다.

## Open Questions
> [!QUESTION]
> 마이크 버튼을 눌렀을 때의 동작 방식(UX)에 대한 결정이 필요합니다:
> 1. **버튼 유지(Hold to talk)**: 마이크 버튼을 누르고 있는 동안만 녹음되고, 떼면 전송.
> 2. **토글(Toggle)**: 한 번 누르면 녹음 시작, 다시 누르면 녹음 종료 및 전송.

## Proposed Changes

### UI Layer (`ChatScreen.kt` & `ChatViewModel.kt`)
- 채팅 입력창(TextField) 우측에 **마이크 아이콘 버튼** 추가.
- 마이크 권한(`RECORD_AUDIO`) 요청 로직 추가.
- 사용자의 음성 녹음 시작/종료 상태를 관리하는 UI 로직.

### Data/Device Layer (`AudioRecorder.kt` 추가)
- `MediaRecorder` 또는 `AudioRecord` API를 활용하여 사용자의 음성을 `.wav` 또는 모델이 요구하는 포맷으로 기기 캐시 폴더에 임시 저장하는 유틸리티 클래스 구현.

### Domain / Runtime Layer (`GemmaModelRunner.kt`)
- 기존의 텍스트, 이미지 처리 외에 오디오 파일 경로를 받아 `Content.AudioFile(filePath)` 형태로 묶어서 `sendMessageAsync`로 넘길 수 있도록 오디오 지원 확장.

## Verification Plan
1. 기기에서 마이크 권한 승인 창이 정상적으로 뜨는지 확인.
2. 음성 녹음 후 임시 파일이 정상 생성되는지 로그 확인.
3. 해당 음성 파일을 넣었을 때 KOSMOS가 사용자의 음성 의도를 파악하고 적절한 텍스트 답변을 출력하는지 테스트.

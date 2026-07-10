# Phase 10: Voice Input (ASR)

- `[x]` **Data Layer**: `AudioRecorder` 유틸리티 클래스 생성 (MediaRecorder를 이용해 임시 파일로 녹음)
- `[x]` **UI/ViewModel Layer**: `ChatScreen` 내 마이크 토글 버튼 및 권한(`RECORD_AUDIO`) 요청 처리 구현
- `[x]` **Runtime Layer**: `GemmaModelRunner` 내 오디오 파일 입력을 처리하는 `generateWithAudio` (또는 통합 미디어 처리) 메서드 추가
- `[x]` **테스트**: 빌드 검증 및 녹음-추론 파이프라인 연동 확인

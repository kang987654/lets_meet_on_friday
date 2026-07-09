# Phase 7: Model Performance Optimization

- `[x]` `GemmaModelRunner.kt` 내 GPU 백엔드 초기화 시 `enableSpeculativeDecoding = true` 적용 (MTP 활성화)
- `[x]` `GemmaModelRunner.kt` 의 `generateWithImage` 메서드를 동기식(`sendMessage`)에서 비동기 스트리밍(`sendMessageAsync`) 방식으로 리팩토링
- `[x]` 빌드 및 실행 검증 (MTP 동작 시 크래시 여부, 이미지 추론 스트리밍 동작 여부 확인)

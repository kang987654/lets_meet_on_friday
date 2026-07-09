# [Phase 7] 모델 성능 최적화 구현 계획 (MTP / Speculative Decoding)

현재 Gemma 4 모델의 추론 속도를 더욱 끌어올리고 GPU 백엔드에서 발생할 수 있는 병목을 줄이기 위해, LiteRT-LM이 제공하는 **MTP(Speculative Decoding)** 기능을 엔진 설정에 도입하려 합니다.

## User Review Required
> [!IMPORTANT]
> - `enableSpeculativeDecoding` 옵션은 Gemma 4 모델에서 GPU 백엔드 가속을 크게 향상시켜 생성 속도(Tokens Per Second)를 높여줍니다.
> - 기존 대비 발열 양상이나 메모리 피크치가 달라질 수 있어, `RuntimeMetricsCollector`의 쿨다운 로직이 더 잦게 호출될 가능성도 염두에 두어야 합니다.

## Proposed Changes

### Runtime Layer (`com.localfriday.app.runtime.gemma`)

#### [MODIFY] [GemmaModelRunner.kt](file:///c:/Users/SSAFY/Desktop/dev/lets_meet_on_friday/app/src/main/java/com/localfriday/app/runtime/gemma/GemmaModelRunner.kt)
- `ensureInferenceInitialized` 메서드 내부의 `EngineConfig` 인스턴스화 시 `enableSpeculativeDecoding = true` 플래그를 추가합니다.
- (선택) OOM(Out of Memory) 예방 차원에서 `maxContextSize` 나 `batchSize` 파라미터가 명시적으로 필요하다면 함께 조정합니다.

## Verification Plan

### Automated/Manual Verification
1. 변경 사항 적용 후 프로젝트 빌드.
2. `SettingsScreen`에서 "Refresh" 또는 "Manage Models"를 통해 모델 엔진 재시작 트리거.
3. Chat 화면에서 긴 문장(예: "안드로이드 OS의 아키텍처에 대해 상세히 설명해 줘")을 요청한 뒤, 이전 Phase 대비 응답의 스트리밍 출력 속도(눈으로 체감되는 TPS)가 개선되었는지 확인.
4. 필요시 `Logcat`을 통해 GPU 초기화 및 MTP 관련 로그 확인.

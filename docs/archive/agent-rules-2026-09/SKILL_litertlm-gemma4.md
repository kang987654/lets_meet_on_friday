---
name: litertlm-gemma4
description: Cheatsheet for using LiteRT-LM with Gemma 4 (Multimodality, MTP, Streaming)
---

# LiteRT-LM Gemma 4 Cheatsheet

이 문서는 Android 환경에서 LiteRT-LM 프레임워크와 Gemma 4 모델을 사용할 때의 참고용 중심 가이드입니다.

## 1. Multi-Modality (Vision & Audio) 지원
Gemma 4 E2B/E4B는 텍스트뿐만 아니라 이미지와 음성 입력을 기본 지원합니다. 모델 호출 시 `Contents.of`와 `Content` 빌더를 사용하여 멀티모달 입력을 구성해야 합니다.
- **이미지 입력**: `com.google.ai.edge.litertlm.Content.ImageBytes(imageBytes)` (또는 `ImageFile`)
- **오디오 입력 (ASR)**: `com.google.ai.edge.litertlm.Content.AudioFile(audio_path)`
- **결합 예시**: `com.google.ai.edge.litertlm.Contents.of(Content.ImageBytes(bytes), Content.Text("텍스트 프롬프트"))`

### 1.1 오디오 입력 제약 사항 (Audio Format Constraints)
- Gemma 오디오 모델은 내부적으로 오디오 리샘플링이나 다중 채널 변환 프로세스를 제공하지 않습니다.
- 모델은 최종적으로 **단일 채널 (Mono), 16kHz, float32** 형태의 텐서를 소비합니다.
- `Content.AudioFile`에 전달하는 파일은 `miniaudio` 디코더가 기본 지원하는 **WAV** 또는 **MP3** 포맷이어야 합니다. (안드로이드 기본 `MediaRecorder`의 M4A/AAC 포맷은 `error code:-10` 크래시를 유발합니다.)
- 안드로이드 환경에서는 `AudioRecord` API를 사용하여 **16000Hz, Mono, 16-bit PCM** 데이터로 직접 녹음한 뒤, 44바이트 헤더를 붙인 표준 **WAV 파일(.wav)** 로 저장하여 전달해야 합니다.

## 2. Multi-Token Prediction (MTP) 최적화
- **기능 개요**: 엔진(Engine) 초기화 시 `EngineConfig`에서 `enableSpeculativeDecoding = true` (또는 해당 MTP 옵션)을 설정하면 MTP가 적용되어 토큰 속도가 비약적으로 상승합니다.
- (참고: `litertlm` 버전에 따라 해당 파라미터 이름이 다를 수 있으므로 최신 버전의 API 문서를 확인해야 합니다.)

## 3. System Instruction & Persona
- 시스템 프롬프트(페르소나 설정 등)는 `ConversationConfig`를 통해 설정합니다.
- `ConversationConfig(systemInstruction = Contents.of(Content.Text("시스템 프롬프트 내용")), ...)`

## 4. Streaming & Async
- 긴 대기 시간을 방지하기 위해 단일 응답이 아닌 스트리밍 모드로 처리해야 합니다.
- `conversation.sendMessageAsync(prompt)` Flow를 `.collect { ... }` 하여 실시간 단위로 실시간 UI 업데이트를 구현합니다.
- **주의사항**: 코루틴 내부에서 `yield()`나 `delay()`를 적절히 호출하여 과도한 GPU/CPU 사용으로 인한 드로핑과 UI 프레임 기아 상태를 방지해야 합니다.

## 5. 모델 저장소 위치
- 모델 파일(.litertlm)은 기기 내부 데이터 수동 복사가 아닌 **앱 내부 저장소(context.filesDir)**에 다운로드하여 관리하도록 구성합니다.

## 6. 이미지 처리: 가변 해상도 및 토큰 예산 (Gemma 4 E4B 전용)
- **강제 전처리 금지**: 이미지를 에이전트나 안드로이드 앱 단에서 임의로 자르거나(Crop), 리사이징(Bitmap.createScaledBitmap 등) 하지 마십시오. E4B의 150M 비전 인코더는 원본 비율을 그대로 받아 내부적으로 16x16 패치로 자동 처리합니다.
- **소프트 토큰 예산(Soft Tokens Budget) 동적 할당**: 이미지 입력 시 API 호출 파라미터로 작업 복잡도에 따라 토큰 예산을 선택해야 합니다.
  - 70 Tokens: 단순 분류, 썸네일, 전반적 색상/구도
  - 140 Tokens: 스크린샷, 단순 UI 파악
  - 280 Tokens (기본값): 균형 잡힌 멀티모달, 일반 캡셔닝
  - 560 Tokens: 복잡한 아키텍처 다이어그램, 상세 UI/UX
  - 1120 Tokens: 밀도 높은 코드 OCR, 차트 정밀 분석

## 7. 입출력(I/O) 토큰 여유분 관리 규칙
- **추론(Thinking) 토큰 고려**: E4B는 <|think|> 태그를 통한 내부 추론 출력을 지원합니다. 따라서 답변 생성 전 숨겨진 추론 토큰이 발생하므로, Max Output Tokens 여유분을 항상 넉넉하게(최소 2,000 ~ 4,000 토큰 이상) 확보해야 합니다.

## 8. 프롬프트 포맷팅 규칙 (Prompt Formatting & Chat)
- Gemma 4는 엄격한 제어 토큰(Control Tokens)을 사용하여 대화 턴을 구분합니다.
- **기본 구조**: `<start_of_turn>role\n...<end_of_turn>\n`
- **역할(Role)**: `user`, `model`, `system`
- `LlmChatModelHelper` 등의 모델 래퍼에서 이전 대화 이력을 프롬프트로 직렬화할 때 이 포맷을 엄격하게 준수해야 합니다.

## 9. 함수 호출 및 에이전트 스킬 (Function Calling / Tool Use)
- 모델은 프롬프트에 제공된 함수 스키마(Schema)를 인지하고 `<tool_call>` 형식으로 외부 함수 실행을 요청할 수 있습니다.
- 안드로이드 구현 시 `ToolProvider` 인터페이스와 같은 추상화 계층을 두어:
  1. 모델 응답 중 `<tool_call>`을 가로채 파싱
  2. 네이티브 함수 실행
  3. 실행 결과를 `<tool_response>`로 포맷팅하여 다시 프롬프트에 주입(Injection)하는 흐름을 구성해야 합니다.

## 10. 메모리 관리 (Sliding Window)
- 멀티턴 대화가 지속될 경우 모바일 기기의 제한된 RAM(예: 12GB 등)을 보호하기 위해 메모리 관리가 필수적입니다.
- 채팅 히스토리 전체를 주입하지 말고, 최근 N개의 토큰(예: 3,000 토큰) 범위 내에서만 유지하고 오래된 대화는 잘라내는 **Sliding Window(Truncation)** 방식을 구현해야 OOM(Out of Memory)이나 급격한 성능 저하를 피할 수 있습니다.

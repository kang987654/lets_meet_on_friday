---
name: litertlm-gemma4
description: Cheatsheet for using LiteRT-LM with Gemma 4 (Multimodality, MTP, Streaming)
---

# LiteRT-LM Gemma 4 Cheatsheet

이 스킬은 Android 환경에서 LiteRT-LM 프레임워크와 Gemma 4 모델을 다룰 때 참고할 핵심 가이드입니다.

## 1. Multi-Modality (Vision & Audio) 지원
Gemma 4 E2B/E4B는 텍스트뿐만 아니라 이미지와 음성을 기본 지원합니다. 모델 호출 시 `Contents.of`와 `Content` 빌더를 사용하여 멀티모달 입력을 구성해야 합니다.
- **이미지 입력**: `com.google.ai.edge.litertlm.Content.ImageBytes(imageBytes)` (또는 `ImageFile`)
- **오디오 입력 (ASR)**: `com.google.ai.edge.litertlm.Content.AudioFile(audio_path)`
- **결합 예시**: `com.google.ai.edge.litertlm.Contents.of(Content.ImageBytes(bytes), Content.Text("텍스트 프롬프트"))`

## 2. Multi-Token Prediction (MTP) 최적화
- **성능 가속**: 엔진(Engine) 초기화 시 `EngineConfig`에서 `enableSpeculativeDecoding = true` (또는 해당 MTP 옵션)를 설정하면 MTP가 적용되어 디코딩 속도가 비약적으로 상승합니다.
- (참고: `litertlm` 버전에 따라 해당 플래그 이름이 다를 수 있으므로 최신 버전의 API 문서를 확인해야 합니다.)

## 3. System Instruction & Persona
- 시스템 프롬프트(페르소나 제어 등)는 `ConversationConfig`를 통해 설정합니다.
- `ConversationConfig(systemInstruction = Contents.of(Content.Text("시스템 프롬프트 내용")), ...)`

## 4. Streaming & Async
- 긴 대기 시간을 방지하기 위해 단일 생성이 아닌 스트리밍 모드로 처리해야 합니다.
- `conversation.sendMessageAsync(prompt)` Flow를 `.collect { ... }` 하여 토큰 단위로 실시간 UI 업데이트를 구현합니다.
- **주의사항**: 코루틴 내부에서 `yield()`나 `delay()`를 적절히 호출하여 과도한 GPU/CPU 사용으로 인한 쓰로틀링과 UI 스레드 기아 상태를 방지해야 합니다.

## 5. 모델 저장소 위치
- 모델 파일(.litertlm)은 기기 외부 폴더 수동 복사가 아닌 **앱 내부 저장소(context.filesDir)**에 다운로드하여 관리하도록 구성합니다.

## 6. �̹��� ó��: ���� �ػ� �� ��ū ���� (Gemma 4 E4B ����)
- **���� ��ó�� ����**: �̹����� ������Ʈ�� �ȵ���̵� �� �ܿ��� ���Ƿ� �ڸ��ų�(Crop), ������¡(Bitmap.createScaledBitmap ��) ���� ���ʽÿ�. E4B�� 150M ���� ���ڴ��� ���� ������ �״�� �޾� ���������� 16x16 ��ġ�� �ڵ� ó���մϴ�.
- **����Ʈ ��ū ����(Soft Tokens Budget) ���� �Ҵ�**: �̹��� �Է� �� API ȣ�� �Ķ���ͷ� �۾� ���⵵�� ���� ��ū ������ �����ؾ� �մϴ�.
  - 70 Tokens: �ܼ� �з�, �����, ������ ����/����
  - 140 Tokens: ��ũ����, �ܼ� UI �ľ�
  - 280 Tokens (�⺻��): ���� ���� ��Ƽ���, �Ϲ� ĸ�Ŵ�
  - 560 Tokens: ������ ��Ű��ó ���̾�׷�, �� UI/UX
  - 1120 Tokens: �е� ���� �ڵ� OCR, ��Ʈ ���� �м�

## 7. �����(I/O) ��ū ������ ���� ��Ģ
- **�߷�(Thinking) ��ū ����**: E4B�� <|think|> �±׸� ���� ���� �߷� ����� �����մϴ�. ���� �亯 ���� �� ������ �߷� ��ū�� �߻��ϹǷ�, Max Output Tokens �������� �׻� �˳��ϰ�(�ּ� 2,000 ~ 4,000 ��ū �̻�) Ȯ���ؾ� �մϴ�.

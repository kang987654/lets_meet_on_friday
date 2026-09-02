# docs/archive/c2-mediapipe-2026-09 — MediaPipe 임베더 제거(C2, 0.26.0) 보관물

0.26.0 에서 영어 전용 MediaPipe 텍스트 임베더(`universal_sentence_encoder.tflite`)의 **구현·gradle 의존·자산**을
제거했다(expand.md C2, ADR-026). 이 폴더는 그때 사문이 된 스크립트 둘을 삭제 대신 보관한다 — 이력 보존 원칙.

| 파일 | 원 위치 | 보관 이유 |
|---|---|---|
| `download_model.ps1` | 저장소 루트 | 제거된 tflite 자산만 내려받는 스크립트. 자산이 없으니 사문 — C3(한국어 임베더) 때 같은 형태의 스크립트를 다시 쓸 수 있다 |
| `exp11_embedder.py` | `scratch/lab/` | **ADR-013 의 근거 실험** — 이 임베더의 한국어 분별력이 0(관련쌍·무관쌍 분리도 0.000, top-1 1/7 = 무작위)임을 잰 스크립트. 자산 경로와 `mediapipe.tasks` import 에 묶여 더는 돌지 않지만, 수치의 출처로 남긴다 |

## 무엇을 남기고 무엇을 지웠나
- **지움**: `data/.../embedder/MediaPipeTextEmbedder.kt`, `com.google.mediapipe:tasks-text` 의존(카탈로그 포함),
  `app/src/main/assets/models/universal_sentence_encoder.tflite`(6,120,274 B).
- **남김(계약 층)**: `TextEmbedder` 인터페이스(`DisabledTextEmbedder` 가 항상 Failure), `KnowledgeNote.embedding`,
  `knowledge_note.embedding BLOB` 컬럼, `KnowledgeRepository.searchByVector`, `FloatBytes`, 관련 테스트 25건.
  기존 테스트 단언 수정 0건 규칙과 C3 되살림 자리 때문이다 — 사용자 결정 2026-09-03 "최소 제거".

## 되살리는 법 (C3)
한국어를 다루는 임베더 자산 + `TextEmbedder` 구현 하나를 바인딩하면 저장 시 임베딩이 다시 채워지고
`searchByVector` 가 그대로 동작한다. **배선 전에 반드시 한국어 분별력을 먼저 재라**(exp11 의 방법 그대로).

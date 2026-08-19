"""exp31 — GPU 전용 모델 변형(gemma-4-E4B-it-gpu.litertlm) 판정.

배경: exp27~29 로 원인을 격리했다 — GPU 백엔드 × 깊은 문맥(~2,750토큰)에서 숫자 토큰이
깨지고(FP16 활성화 정밀도), FLOAT32 활성화로 사라진다. 그러나 AAR 0.16.0 에는
activationDataType 레버가 없다(EngineConfig·ExperimentalFlags 모두 확인).

6일 전 HuggingFace 에 GPU 전용 변형(2.77GB, 기존 3.41GB)이 올라왔다 — 이것이 곧
처방인지 같은 재현 조건으로 판정한다.

조건 (전부 새 모델):
  A. GPU + depth 0            — 기본 동작·툴 호출 확인
  B. GPU + 오염 깊은 문맥      — exp28 에서 깨졌던 바로 그 조건 (결정적 재현)
  C. CPU + 오염 깊은 문맥      — 기기 CPU 폴백 경로의 정합성
"""

import pathlib

import litert_lm as llm

import kosmos_lab as lab
from exp27_polluted_history import HISTORY, probe

GPU_MODEL = str(pathlib.Path(lab.MODEL_PATH).parent / "gemma-4-E4B-it-gpu.litertlm")


def engine_for(backend):
    return llm.Engine(
        GPU_MODEL,
        backend=backend,
        max_num_tokens=lab.ENGINE_MAX_TOKENS,
        max_num_images=lab.MAX_IMAGES_PER_TURN,
    )


def main():
    engine = engine_for(llm.Backend.GPU)
    probe(engine, "A (GPU 모델, GPU, depth 0)", history=None)
    probe(engine, "B (GPU 모델, GPU, 오염 깊은 문맥)", history=HISTORY)
    engine.close()

    try:
        engine = engine_for(llm.Backend.CPU)
    except Exception as e:  # noqa: BLE001
        print(f"CPU 엔진 생성 실패(GPU 전용일 수 있음): {type(e).__name__}: {str(e)[:200]}")
        return
    probe(engine, "C (GPU 모델, CPU, 오염 깊은 문맥)", history=HISTORY, reps=1)
    engine.close()


if __name__ == "__main__":
    main()

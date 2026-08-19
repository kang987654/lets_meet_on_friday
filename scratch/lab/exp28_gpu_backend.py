"""exp28 — PC 에서 GPU 백엔드로 exp27 재판정 (백엔드 A/B 의 절반).

exp27: 같은 문맥(오염 히스토리, token_count 2784 ≈ 실기기 2783)에서 PC+CPU 는 온전,
실기기+GPU 는 깨짐. 남은 변수는 GPU 백엔드 vs arm64 빌드.

이 실험: PC 런타임에도 GPU 가속기(WebGPU)가 등록된다 — 같은 문맥을 PC+GPU 로 돌린다.
  - 깨지면: GPU 델리게이트 계열 결함 (PC 재현 확보 — 상류 보고 가능)
  - 온전하면: PC GPU(NVIDIA/WebGPU)와 기기 GPU(Adreno)는 다른 경로라 미확정
    → 기기 CPU A/B 로 진행

주의: PC GPU 는 앱과 델리게이트 구현이 다르다(WebGPU vs OpenCL/Vulkan). 결과 해석에
그 한계를 명기할 것.
"""

import litert_lm as llm

import kosmos_lab as lab
from exp27_polluted_history import HISTORY, probe


def main():
    try:
        engine = lab.create_engine(backend=llm.Backend.GPU)
    except Exception as e:  # noqa: BLE001
        print(f"GPU 엔진 생성 실패: {type(e).__name__}: {str(e)[:300]}")
        print("→ PC GPU 경로 불가. 기기 CPU A/B 로 진행해야 한다.")
        return

    print("GPU engine up")
    probe(engine, "A-GPU (depth 0)", history=None)
    probe(engine, "B-GPU (오염 히스토리 그대로)", history=HISTORY)
    engine.close()


if __name__ == "__main__":
    main()

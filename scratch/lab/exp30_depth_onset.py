"""exp30 — GPU 숫자 깨짐의 발병 깊이 측정 (기존 모델).

exp28/29: GPU 에서 depth0(1398 토큰) 온전, 깊은 문맥(2752·2776·2784) 깨짐.
완화 후보 "재설정 임계값을 발병점 아래로"의 설계 수치를 위해 발병 구간을 좁힌다.

방법: 깨끗한 깊은 문맥(exp29 cleaned_history)을 턴 쌍 단위로 앞에서부터 k쌍 실어
token_count 를 늘려 가며 같은 판정 발화를 보낸다. greedy 라 지점당 1회, 경계에서 2회.

주의: PC GPU(WebGPU/NVIDIA)의 발병점이 기기 GPU(Adreno)와 같다는 보장은 없다 —
근사치 + 여유 마진으로 쓰고, 실기기 재검증으로 확정한다.
"""

import litert_lm as llm

import kosmos_lab as lab
from exp27_polluted_history import probe
from exp29_gpu_characterize import cleaned_history


def main():
    full = cleaned_history()
    pairs = [(full[i], full[i + 1]) for i in range(0, len(full) - 1, 2)]
    print(f"턴 쌍 {len(pairs)}개")

    engine = lab.create_engine(backend=llm.Backend.GPU)
    for k in [4, 8, 10, 12, 14, len(pairs)]:
        history = [msg for pair in pairs[:k] for msg in pair]
        probe(engine, f"k={k}쌍", history=history, reps=1)
    engine.close()


if __name__ == "__main__":
    main()

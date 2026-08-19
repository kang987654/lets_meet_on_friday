"""exp29 — GPU 깨짐의 특성화: 방아쇠 분리 + 완화 레버.

exp28: GPU + 깊은 오염 문맥 = 깨짐(결정적), GPU + depth0 = 온전, CPU + 같은 문맥 = 온전.

조건:
  D. GPU + 깨끗한 깊은 문맥 (조각 제거 + 깨진 숫자를 정상 숫자로 수정, 길이 유사)
     → 깨지면 방아쇠는 '깊이', 온전하면 '오염된 숫자 시범 × GPU' 상호작용
  E. GPU + 오염 문맥 + activation_data_type 조절 (F32 계열이 있으면)
     → 온전해지면 GPU 수치 정밀도 문제로 특정 + 제품 완화 레버 확보
"""

import inspect

import litert_lm as llm

import kosmos_lab as lab
from exp27_polluted_history import HISTORY, FRAGMENT_INDEX, probe

# --- 조건 D: 깨끗한 깊은 문맥 ---
# 조각 턴 쌍 제거 + 깨진 숫자만 정상값으로 치환(그 외 문구·길이 유지)
_FIXES = [
    ("**20817일 01시**", "**2026-08-17 10시**"),
    ("**2023년 111월 1일**", "**2020년 11월 17일**"),
    ("2023년 111월 1일 JYP", "2020년 11월 17일 JYP"),
    ("다음주 월요일 01시 팀 회의", "다음주 월요일 10시 팀 회의"),
    ("**20202년 1월1일**", "**2020년 11월 17일**"),
]


def cleaned_history():
    no_fragment = HISTORY[:FRAGMENT_INDEX - 1] + HISTORY[FRAGMENT_INDEX + 1:]
    out = []
    for role, content in no_fragment:
        for bad, good in _FIXES:
            content = content.replace(bad, good)
        out.append((role, content))
    return out


def main():
    print("ActivationDataType 멤버:", [m for m in dir(llm.ActivationDataType) if not m.startswith("_")])

    engine = lab.create_engine(backend=llm.Backend.GPU)
    probe(engine, "D-GPU (깨끗한 깊은 문맥)", history=cleaned_history())
    engine.close()

    # --- 조건 E: 오염 문맥 + activation_data_type ---
    candidates = [m for m in dir(llm.ActivationDataType) if not m.startswith("_") and "32" in m]
    if not candidates:
        print("F32 계열 ActivationDataType 없음 — 조건 E 생략")
        return
    adt = getattr(llm.ActivationDataType, candidates[0])
    print(f"조건 E: activation_data_type={candidates[0]}")
    try:
        engine = llm.Engine(
            lab.MODEL_PATH,
            backend=llm.Backend.GPU,
            max_num_tokens=lab.ENGINE_MAX_TOKENS,
            max_num_images=lab.MAX_IMAGES_PER_TURN,
            activation_data_type=adt,
        )
    except Exception as e:  # noqa: BLE001
        print(f"E 엔진 생성 실패: {type(e).__name__}: {str(e)[:300]}")
        return
    probe(engine, f"E-GPU-{candidates[0]} (오염 문맥)", history=HISTORY)
    engine.close()


if __name__ == "__main__":
    main()

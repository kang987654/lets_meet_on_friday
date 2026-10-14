"""exp51 — LiteRT-LM 0.16.0 → 0.18.0: GPU 숫자 깨짐 발병점과 FLOAT32 활성화 (0.37.0 M0).

ADR-021: GPU 텍스트 디코더 FP16 활성화 × 문맥 깊이가 숫자를 깨뜨린다(exp30: 1,854 온전 / 2,122 깨짐, 당시 프롬프트).
처방 FLOAT32 는 AAR 0.16.0 에 없어 기각됐는데 0.18.0 AAR 의 EngineConfig 에 activationDataType 이 생겼다.

같은 스크립트를 conda `friday`(0.16.0)·`friday18`(0.18.0)에서 돌린다 — 현 픽스처(오버헤드 1,250)로 발병점을 다시 잰다.
깊은 문맥 = exp29 의 깨끗한 히스토리(오염 없음)를 턴 쌍 단위로 순환해 깊이를 늘린다. greedy 라 지점당 1회.

판정 발화 두 개:
  S 일정 — "다음주 월요일 10시 팀 회의 일정 추가해줘" → add_schedule.start_time == 픽스처의 다음주 월요일 T10:00
  M 메모 — "내 사물함 비밀번호는 507319야, 기억해줘" → add_memory.content 에 507319 가 그대로
사용: python exp51_runtime_018.py [fp16|fp32|both]
"""
import importlib.metadata
import re
import sys
import time

import litert_lm as llm

import kosmos_lab as lab
from exp29_gpu_characterize import cleaned_history

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

NEXT_MONDAY = re.search(r"다음주 월요일=(\d{4}-\d{2}-\d{2})", lab.fixture("system_instruction.txt")).group(1)
EXPECTED_START = re.compile(rf"^{NEXT_MONDAY}T10:00(:00)?$")
SECRET = "507319"
PROBES = [
    ("S", "다음주 월요일 10시 팀 회의 일정 추가해줘"),
    ("M", f"내 사물함 비밀번호는 {SECRET}야, 기억해줘"),
]
DEPTHS = [0, 4, 8, 10, 12, 14, 16, 20, 24, 28, 32, 36]
TOKEN_CEILING = 3_900  # ENGINE_MAX_TOKENS 4,096 안에서 응답 여유


def verdict(tag, calls):
    if not calls:
        return "툴없음", ""
    fn = calls[0]["function"]
    args = fn.get("arguments") or {}
    if tag == "S":
        got = str(args.get("start_time", ""))
        return ("온전" if fn["name"] == "add_schedule" and EXPECTED_START.match(got) else "깨짐"), f"{fn['name']} start={got!r}"
    got = str(args.get("content", ""))
    return ("온전" if fn["name"] == "add_memory" and SECRET in got else "깨짐"), f"{fn['name']} content={got!r}"


def history_of(pairs, k):
    msgs = []
    for i in range(k):
        user, model = pairs[i % len(pairs)]
        msgs += [{"role": user[0], "content": user[1]}, {"role": model[0], "content": model[1]}]
    return msgs


def run(precision):
    full = cleaned_history()
    pairs = [(full[i], full[i + 1]) for i in range(0, len(full) - 1, 2)]
    kwargs = {} if precision == "fp16" else {"activation_data_type": llm.ActivationDataType.FLOAT32}
    t0 = time.perf_counter()
    engine = llm.Engine(lab.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=lab.ENGINE_MAX_TOKENS,
                        max_num_images=lab.MAX_IMAGES_PER_TURN, **kwargs)
    print(f"## litert-lm {importlib.metadata.version('litert-lm')} GPU {precision} — 엔진 {time.perf_counter() - t0:.1f}s", flush=True)
    for k in DEPTHS:
        for tag, utterance in PROBES:
            conv = lab.create_chat_conversation(engine, history=history_of(pairs, k))
            t = time.perf_counter()
            res = conv.send_message(lab.with_turn_reminder(utterance))
            dt = time.perf_counter() - t
            v, detail = verdict(tag, res.get("tool_calls") or [])
            tokens = conv.token_count
            conv.close()
            print(f"{precision} k={k:2} {tag} tokens={tokens:5} {dt:5.1f}s {v} {detail}", flush=True)
            if tokens > TOKEN_CEILING:
                break
        else:
            continue
        break
    engine.close()


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "both"
    for p in (["fp16", "fp32"] if mode == "both" else [mode]):
        run(p)

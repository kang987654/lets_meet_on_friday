"""exp66b — 0.39.2 M0 c: 대화 생성 1.2초(exp66)는 무엇에 드는가 — 히스토리 / 툴 선언 / 문법 강제.

생성 시간이 히스토리 길이에 따라 늘면 생성 때 히스토리를 읽는 것이고, 문법 강제에만 따라 늘면 문법(FST) 준비 비용이다.
다음 턴 시간과 대조(재사용 R)의 차이는 첫 send 에 붙는 프리필이다. 각 조합 3회.
"""
import sys
import time

import litert_lm as llm
import kosmos_lab as K
from exp66_prewarm import HISTORY, NEXT, engine, timed  # noqa: F401 — 엔진 하나를 같이 쓴다

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS = K.fixture("system_instruction.txt")


def make(history, tools, constrained):
    return engine.create_conversation(
        system_message=SYS, messages=history or None, tools=tools, automatic_tool_calling=False,
        thinking_config=llm.ThinkingConfig(enable_thinking=False),
        sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        constrained_decoding_config=llm.ConstrainedDecodingConfig(enable=constrained))


ARMS = [("기록0 툴 문법", 0, True, True), ("기록16 툴 문법", 16, True, True), ("기록16 툴 문법끔", 16, True, False),
        ("기록16 툴없음", 16, False, False), ("기록0 툴없음", 0, False, False)]
for rep in range(3):
    for name, n, with_tools, constrained in ARMS:
        conv, t_make = timed(lambda: make(HISTORY[:n], K.ALL_TOOLS if with_tools else None, constrained))
        _, t_turn = timed(lambda: conv.send_message(K.with_turn_reminder(NEXT)))
        tokens = conv.token_count
        conv.close()
        print(f"[{rep}] {name}: 생성 {t_make:5.2f}s · 다음 턴 {t_turn:5.2f}s · 뒤 KV {tokens}", flush=True)

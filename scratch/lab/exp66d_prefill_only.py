"""exp66d — 0.39.2 M0 c: 모델 역할 메시지 send 에 출력 상한(max_output_tokens 0·1)을 두면 생성 없이 프리필만 하는가.

exp66c: 모델 메시지 send 는 프리필을 치르지만(다음 턴 0.63s ≈ 재사용) 이어서 글을 하나 더 만들었다 — DB 에 없는 말이 KV 에 남는다.
판정: 다음 턴 답이 지금 앱 방식(N — 전체 기록으로 새로 만든 대화의 첫 send)과 글자까지 같으면 KV 가 같다고 본다(greedy).
"""
import sys
import time

import litert_lm as llm
import kosmos_lab as K
from exp66_prewarm import HISTORY, NEXT, engine, timed

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS = K.fixture("system_instruction.txt")
last = HISTORY[-1]["content"]


def text(r):
    c = r.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


n = K.create_chat_conversation(engine, history=HISTORY, system_message=SYS)
ref, t_ref = timed(lambda: n.send_message(K.with_turn_reminder(NEXT)))
ref_kv = n.token_count
n.close()
print(f"N 지금 방식: 다음 턴 {t_ref:4.2f}s · KV {ref_kv} · {text(ref)[:70]}", flush=True)
for cap in (0, 1):
    conv = K.create_chat_conversation(engine, history=HISTORY[:-1], system_message=SYS)
    try:
        got, t_m = timed(lambda: conv.send_message(llm.Message.model(llm.Contents([llm.Content.Text(last)])), max_output_tokens=cap))
        kv_mid = conv.token_count
    except Exception as e:  # noqa: BLE001
        print(f"상한 {cap}: send 실패 {type(e).__name__}: {e}", flush=True)
        conv.close()
        continue
    ans, t_turn = timed(lambda: conv.send_message(K.with_turn_reminder(NEXT)))
    same = text(ans) == text(ref)
    print(f"상한 {cap}: 미리 send {t_m:4.2f}s · 돌려줌 {text(got)[:40]!r} · KV {kv_mid} → 다음 턴 {t_turn:4.2f}s · KV {conv.token_count} · 같은 답 {same} · {text(ans)[:60]}", flush=True)
    conv.close()

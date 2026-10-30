"""exp66c — 0.39.2 M0 c: 히스토리 프리필을 미리 치르는 길 — 마지막 비서 답을 '모델 역할 메시지'로 send 하면 생성 없이 KV 에 들어가는가.

exp66b: 대화 생성 비용은 문법 준비뿐이고 히스토리 프리필은 첫 send 에 붙는다(실기기 재생성 턴의 큰 몫).
  M 모델 메시지 send: 기록[:-1](사용자 말로 끝남)로 생성 → send(Message.model(마지막 답)) — 돌려준 것·시간·KV → 다음 턴
  R 대조(재사용): exp66 과 같음
다음 턴 시간이 R 에 가깝고, 모델 메시지 send 가 새 글을 만들지 않으면(돌려준 내용 확인) 프리필을 미리 치를 수 있다. 다음 턴 답이 R 과 같은지도 본다.
"""
import sys
import time

import litert_lm as llm
import kosmos_lab as K
from exp66_prewarm import HISTORY, NEXT, build_by_send, engine, timed

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS = K.fixture("system_instruction.txt")
last = HISTORY[-1]["content"]
for rep in range(2):
    conv, t_make = timed(lambda: K.create_chat_conversation(engine, history=HISTORY[:-1], system_message=SYS))
    try:
        got, t_model = timed(lambda: conv.send_message(llm.Message.model(llm.Contents([llm.Content.Text(last)]))))
        print(f"[{rep}] M: 생성 {t_make:4.2f}s · 모델 메시지 send {t_model:4.2f}s · 돌려줌 {str(got)[:80]!r} · KV {conv.token_count}", flush=True)
    except Exception as e:  # noqa: BLE001
        print(f"[{rep}] M: 모델 메시지 send 실패 {type(e).__name__}: {e}", flush=True)
        conv.close()
        continue
    ans, t_turn = timed(lambda: conv.send_message(K.with_turn_reminder(NEXT)))
    print(f"[{rep}]    다음 턴 {t_turn:4.2f}s · KV {conv.token_count} · {str(ans.get('content'))[:70]}", flush=True)
    conv.close()
    r = build_by_send()
    print(f"[{rep}] R: KV {r.token_count}", flush=True)
    ans, t_turn = timed(lambda: r.send_message(K.with_turn_reminder(NEXT)))
    print(f"[{rep}]    다음 턴 {t_turn:4.2f}s · {str(ans.get('content'))[:70]}", flush=True)
    r.close()

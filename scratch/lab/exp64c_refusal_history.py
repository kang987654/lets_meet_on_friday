"""exp64c — 0.38.9 M2: 앞에서 한 거절을 따라 하는 문제를 기록 쪽에서 — 거절 답이 든 턴을 모델 입력 기록에서 빼면 줄어드는가.

0.38.8 M1(exp62d)은 프롬프트만 바꿨다(현행 2~3/12, 후보 모두 효과 없음). 앱은 살아 있는 대화(KV)를 이어 쓰므로 기록에서 빼는 안은
거절 직후 대화를 다시 만들어야(invalidateConversation) 효과가 난다 — 여기서는 "다시 만든 기록"을 그대로 넣어 상한을 잰다.
기록 두 가지(짧은: 거절 한 쌍 / 긴: 일상 두 쌍 사이에 거절 한 쌍) × 일상 요청·사실 6(exp62 DRIFT_ASK).
  R0 거절 그대로   R1 거절 쌍(사용자 말 + 거절 답)을 뺌   R2 거절 답만 짧은 중립 답("알겠어요.")으로 바꿈
지표: 거절(exp62 REFUSE), 거짓 약속.
"""
import exp62_trust_order as X

M = X.M
REFUSAL = ("아침 운동 루틴 짜줘", "죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요. 대신 다른 도움이 필요하시면 말씀해 주세요!")
BEFORE = [M("user", "알림 뭐 있어?"), M("model", "현재 등록된 알림은 없어요. 😊")]
AFTER = [M("user", "오늘 날씨 좋다"), M("model", "정말 좋은 날씨네요! 산책하기 딱 좋겠어요. 😊")]


def hists(arm):
    u, a = REFUSAL
    pair = {"R0": [M("user", u), M("model", a)], "R1": [], "R2": [M("user", u), M("model", "알겠어요.")]}[arm]
    return {"짧은": pair, "긴": BEFORE + pair + AFTER}


for arm in ("R0", "R1", "R2"):
    refused = promised = n = 0
    for hname, h in hists(arm).items():
        for q in X.DRIFT_ASK:
            out, tools = X.turn(h or None, q, X.SYS, X.REM)
            r = bool(X.REFUSE.search(out))
            p = bool(X.PROMISE.search(out)) and "add_memory" not in tools
            refused += r
            promised += p
            n += 1
            print(f"[{arm} {hname}] {'거절 ' if r else ''}{'약속 ' if p else ''}{','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    print(f"== {arm}: 거절 {refused}/{n} · 거짓 약속 {promised}/{n}", flush=True)

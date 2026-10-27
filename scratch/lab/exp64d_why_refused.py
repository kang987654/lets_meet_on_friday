"""exp64d — 0.38.9 M2 이어서: 거절 쌍을 기록에서 빼면(M2) "왜 안 된다고 했어?"에 엉뚱하게 답하는가, 남기면 제대로 답하는가.

거절 3가지(운동 루틴·점심 메뉴·김치찌개) × 되묻는 말 4가지. 기록 = 일상 한 쌍 + (거절 쌍) + 되묻는 말.
  DROP 거절 쌍을 뺀 기록(M2 현행)   KEEP 거절 쌍을 남긴 기록(1번 안)
지표: 답이 거절한 주제를 말함(주제 낱말), 모른다는 답("어떤 …", "무엇을", "언제 …그랬", "말씀하시는").
"""
import re

import exp62_trust_order as X

M = X.M
REFUSALS = [
    ("아침 운동 루틴 짜줘", "죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요. 대신 다른 도움이 필요하시면 말씀해 주세요!", "운동"),
    ("점심 메뉴 추천해 줄래?", "점심 메뉴 추천은 제가 직접 해드릴 수 없어요. 혹시 다른 궁금한 점이 있으신가요?", "메뉴"),
    ("김치찌개 끓이는 법 알려줘", "죄송해요, 저는 요리법을 알려드리는 기능은 없어요. 다른 도움이 필요하시면 말씀해 주세요.", "김치찌개|요리"),
]
WHYS = ["왜 안 된다고 했어?", "아까 왜 못 한다고 했어?", "안 된다고 했잖아, 이유가 뭐야?", "그게 왜 안 돼?"]
BEFORE = [M("user", "알림 뭐 있어?"), M("model", "현재 등록된 알림은 없어요. 😊")]
LOST = re.compile(r"(어떤 (요청|부분|것|내용)|무엇을 (말씀|안 된)|언제 .*그랬|말씀하시는|어떤 걸)")

for arm in ("DROP", "KEEP"):
    topic = lost = n = 0
    for u, a, key in REFUSALS:
        h = BEFORE + ([M("user", u), M("model", a)] if arm == "KEEP" else [])
        for q in WHYS:
            out, tools = X.turn(h, q, X.SYS, X.REM)
            t = bool(re.search(key, out))
            lo = bool(LOST.search(out))
            topic += t
            lost += lo
            n += 1
            print(f"[{arm}] {'주제 ' if t else ''}{'모름 ' if lo else ''}{','.join(tools)} | {u} / {q} → {out.replace(chr(10), ' / ')[:90]}", flush=True)
    print(f"== {arm}: 주제를 말함 {topic}/{n} · 모른다 {lost}/{n}", flush=True)

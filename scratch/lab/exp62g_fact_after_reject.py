"""exp62g — 0.38.8 M6: 실기기에서 사실만 말해도 저장 호출 3/3(실험실 exp62e F1 0/8). 앞 기록에 "사실 → 메모 거절 고정 문장"이 있으면 따라 하는지.

기록 h0 없음, h1 사실+거절 1쌍, h3 3쌍(실기기 모양), hq 거절 1쌍 + 일상 질문·답.
지표: 사실 8 — 저장 호출·물어봄.
"""
import exp62_trust_order as X

PAIRS = [("다음 주 금요일 회의실은 5층 C룸이야", X.MEMO_REJECTED), ("내 자전거 비밀번호 4821이야", X.MEMO_REJECTED),
         ("새 와이파이 비번은 sky7788이야", X.MEMO_REJECTED)]


def hist(n, tail=()):
    h = []
    for u, a in PAIRS[:n]:
        h += [X.M("user", u), X.M("model", a)]
    return h + list(tail)


HISTS = {
    "h0": hist(0),
    "h1": hist(1),
    "h3": hist(3),
    "hq": hist(1, [X.M("user", "회의실 어디라고 했지?"), X.M("model", "방금 말씀해주신 대로 5층 C룸이에요.")]),
}
FACTS = [f for f in X.FACTS if "와이파이" not in f]

for name, h in HISTS.items():
    saves = asks = 0
    for f in FACTS:
        out, tools = X.turn(h, f, X.SYS, X.REM)
        saves += "add_memory" in tools
        asks += bool(X.ASK_SAVE.search(out))
    print(f"== {name}: 저장 호출 {saves}/{len(FACTS)} · 물어봄 {asks}/{len(FACTS)}", flush=True)

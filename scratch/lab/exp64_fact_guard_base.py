"""exp64 — 0.38.9 M0 재현 세트·기준값 (계획서 docs/plans/0.38.9-fact-save-guard.md). M1~M3 가 이 세트·지표로 후보를 잰다.

세트(웹 검색 끈 판, 앱 픽스처 그대로 — exp62 의 turn·판정식을 쓴다):
  F  사실만 말하기 × 기록 h0/h1/h3(사실·메모 거절 쌍 0·1·3개) — 저장 호출·물어봄·거짓 약속
  S  저장 부탁 — 목록 안 낱말 6 / 목록 밖 6: 저장 호출(값 포함), 그리고 RepeatRequest.SAVE 가 잡는지(앱 가드가 볼 글자)
  Y  저장 물음 3가지 뒤 긍정 4가지 — 저장 호출(값 포함)
  A  거절 따라 하기(exp62 A, 현행 2~3/12)
  I  일시가 든 말 12 — 답 글자에 ISO 원문
사용: python exp64_fact_guard_base.py [F] [S] [Y] [A] [I]
"""
import re
import sys

import exp62_trust_order as X
import kosmos_lab as K

SETS = [a for a in sys.argv[1:] if a in "FSYAI"] or list("FSYAI")

# 앱 RepeatRequest.SAVE 와 같은 식 — 가드가 볼 글자.
SAVE = re.compile(r"기억해|저장|메모해|잊지\s*마|적어\s*(둬|놔|줘)")
ISO = re.compile(r"\d{4}-\d{2}-\d{2}(T\d{2}:\d{2}(:\d{2})?)?")

PAIRS = [("다음 주 금요일 회의실은 5층 C룸이야", X.MEMO_REJECTED), ("내 자전거 비밀번호 4821이야", X.MEMO_REJECTED),
         ("치과 예약 번호는 A-302야", X.MEMO_REJECTED)]


def hist(n):
    h = []
    for u, a in PAIRS[:n]:
        h += [X.M("user", u), X.M("model", a)]
    return h


FACTS = [f for f in X.FACTS if "치과" not in f]  # 기록 쌍과 겹치는 것 뺌 — 7개

SAVE_IN = [("나 땅콩 알레르기 있어, 잊지 마", "땅콩"), ("내 자전거 비밀번호 4821이야 기억해줘", "4821"),
           ("나는 아메리카노보다 라떼를 좋아해, 기억해 둬", "라떼"), ("우리 집 와이파이 비번은 kosmos2024야 저장해줘", "kosmos2024"),
           ("회사 사번은 K-20391이야 메모해줘", "K-20391"), ("내 생일은 3월 5일이야 적어 둬", "3월 5일")]
SAVE_OUT = [("이거 알아둬, 내 사물함 번호는 214번이야", "214"), ("엄마 생신 11월 7일이야, 까먹으면 안 돼", "11월 7일"),
            ("주차 자리 C-14야, 나중에 물어볼게", "C-14"), ("헬스장 락커 비번 0912야 기록해줘", "0912"),
            ("우리 강아지 이름 콩이야 외워 둬", "콩이"), ("내 혈액형 A형인 거 꼭 챙겨줘", "A형")]

ASKS = ["메모해 둘까요?", "저장해 드릴까요?", "기억해 둘까요?"]
YESES = ["응", "그래", "좋아", "부탁해"]
YES_FACT = ("내 사물함 번호는 214번이야", "사물함 번호 214번 확인했어요.", "214")

TIMED = ["회의는 오후 4시 30분에 시작해", "내일 아침 9시에 치과 예약 있어", "다음 주 금요일 저녁 7시에 엄마랑 저녁 먹기로 했어",
         "모레 오전 10시 회의 잡아줘", "오늘 오후 3시에 약 먹으라고 알려줘", "이번 주 토요일 일정 뭐 있어?",
         "11월 7일이 엄마 생신이야", "내일 몇 시에 일어나야 해?", "다음 달 3일에 병원 가", "금요일 오후 2시 회의 1시간 미뤄줘",
         "지금 몇 시야?", "크리스마스까지 며칠 남았어?"]


def row(name, d):
    print(f"== {name}: " + " | ".join(f"{k} {v}" for k, v in d.items()), flush=True)


def run_F():
    for n in (0, 1, 3):
        saves = asks = promised = 0
        for f in FACTS:
            out, tools = X.turn(hist(n), f, X.SYS, X.REM)
            s = "add_memory" in tools
            saves += s
            asks += bool(X.ASK_SAVE.search(out))
            promised += bool(X.PROMISE.search(out)) and not s
            print(f"[F h{n}] {'저장 ' if s else ''}{'물어봄 ' if X.ASK_SAVE.search(out) else ''}| {f} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
        row(f"F h{n}", {"저장 호출": f"{saves}/{len(FACTS)}", "물어봄": f"{asks}/{len(FACTS)}", "거짓 약속": f"{promised}/{len(FACTS)}"})


def run_S():
    for name, cases in (("목록 안", SAVE_IN), ("목록 밖", SAVE_OUT)):
        called = caught = 0
        for u, key in cases:
            out, tools = X.turn(None, u, X.SYS, X.REM)
            called += "add_memory" in tools
            caught += bool(SAVE.search(u))
            print(f"[S {name}] {'저장 ' if 'add_memory' in tools else ''}{'낱말O' if SAVE.search(u) else '낱말X'} | {u} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
        row(f"S {name}", {"저장 호출": f"{called}/{len(cases)}", "가드 낱말": f"{caught}/{len(cases)}"})


def run_Y():
    ok = 0
    n = 0
    fact, confirm, key = YES_FACT
    for ask in ASKS:
        for yes in YESES:
            h = [X.M("user", fact), X.M("model", f"{confirm} {ask}")]
            with K.create_chat_conversation(X.engine(), history=h, tools=X.TOOLS, system_message=X.SYS) as conv:
                cs = conv.send_message(f"{X.REM}\n\n{yes}").get("tool_calls") or []
            hit = bool(cs) and cs[0]["function"]["name"] == "add_memory" and key in str(cs[0]["function"].get("arguments", {}))
            ok += hit
            n += 1
            if not hit:
                print(f"[Y] 놓침 {ask} / {yes} → {cs[:1]}", flush=True)
    row("Y 물음 뒤 긍정", {"저장": f"{ok}/{n}"})


def run_A():
    row("A", X.run_A(tag="base"))


def run_I():
    hits = 0
    for u in TIMED:
        out, tools = X.turn(None, u, X.SYS, X.REM)
        h = bool(ISO.search(out))
        hits += h
        print(f"[I] {'ISO ' if h else ''}{','.join(tools)} | {u} → {out.replace(chr(10), ' / ')[:90]}", flush=True)
    row("I ISO 노출", {"답에 ISO": f"{hits}/{len(TIMED)}"})


if __name__ == "__main__":
    for s in SETS:
        {"F": run_F, "S": run_S, "Y": run_Y, "A": run_A, "I": run_I}[s]()

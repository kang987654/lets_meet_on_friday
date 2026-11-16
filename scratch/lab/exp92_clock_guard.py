"""exp92 — 몇 시 없는 일정 가드 안내 문구 (0.41.15 M2).

시간대·날짜 낱말만 있고 시계 시각이 없는 일정 부탁에서 add_schedule 을 막고 안내 후보를 돌려준 뒤:
  1턴: 모델이 시각을 지어내 다시 부르지 않고 몇 시인지 묻는가(묻기), 2턴: 사용자가 시각을 답하면 그 날짜·시각으로 넣는가(넣기).
  none = 지금 가드(시간대 낱말 통과) — 지어낸 시각으로 바로 들어가는 비율.
"""
import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
TODAY, TOMORROW = R.TODAY, R.TOMORROW

# 앱 ScheduleTimeIntent.CLOCK 미러(0.41.15 M2 안)
CLOCK = re.compile(
    r"\d+\s*시(?!간)|\d+\s*(분|시간)\s*(뒤|후)|\d{1,2}:\d{2}|(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|열한|열두)\s*시(?!간)|정오|자정|지금|"
    r"\b\d{1,2}\s*(am|pm|a\.m\.|p\.m\.)|\b(noon|midnight|now)\b|o'?clock|\bin\s+\d+\s+(minutes?|hours?)\b",
    re.I,
)

VARIANTS = {
    "K1": ("실행하지 않았습니다. 사용자가 몇 시인지 말하지 않았습니다. 시각을 지어내지 말고 몇 시인지 물어보세요.",
           "Not executed. The user did not say a clock time. Do not guess a time; ask what time."),
    "K2": ("실행하지 않았습니다. 몇 시인지 정해지지 않았습니다. 일정을 몇 시에 잡을지 사용자에게 물어보세요.",
           "Not executed. No clock time was given. Ask the user what time to schedule it."),
    "K3": ("실행하지 않았습니다. 몇 시인지 먼저 물어보세요.",
           "Not executed. Ask what time first."),
}

CASES = [  # (user1, user2, 기대 start_time 앞부분, en)
    ("아침 운동 일정 추가해줘", "내일 아침 7시", f"{TOMORROW}T07:00", False),
    ("저녁 약속 일정 넣어줘", "저녁 7시", f"{TODAY}T19:00", False),
    ("오후 회의 일정 잡아줘", "3시", f"{TODAY}T15:00", False),
    ("내일 저녁 미용실 예약 잡아줘", "7시 반", f"{TOMORROW}T19:30", False),
    ("금요일 오후에 치과 예약 넣어줘", "2시", "2026-11-13T14:00", False),
    ("점심 약속 일정 추가해줘", "12시 반", f"{TODAY}T12:30", False),
    ("Add dinner with friends tonight to my calendar", "7pm", f"{TODAY}T19:00", True),
    ("Schedule a meeting tomorrow afternoon", "3pm", f"{TOMORROW}T15:00", True),
]
ASK = re.compile(r"몇\s*시|시간을|시간이|언제|what time|which time|when", re.I)


def run_turn(hist, user, en, variant, prev_model):
    sysm = R.SYS_EN_OFF if en else R.SYS_OFF
    executed, blocked = [], 0
    with K.create_chat_conversation(R.engine(), history=hist or None, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        for _ in range(3):
            calls = res.get("tool_calls") or []
            if not calls:
                break
            fn = calls[0]["function"]
            name, args = fn["name"], fn.get("arguments") or {}
            has_clock = CLOCK.search(user) or (prev_model and CLOCK.search(prev_model))
            if name == "add_schedule" and variant != "none" and not has_clock:
                body, ok = {"status": "error", "message": VARIANTS[variant][1 if en else 0]}, False
            elif name == "add_schedule":
                body, ok = {"status": "success", "message": "Event added." if en else "일정이 성공적으로 추가되었습니다."}, True
            else:
                body, ok = {"status": "success", "data": "None" if en else "없음"}, True
            (executed.append((name, args)) if ok else None)
            blocked += not ok
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
        return executed, blocked, R.text_of(res)


def main():
    score = {}
    for variant in ("none", "K1", "K2", "K3"):
        for u1, u2, want, en in CASES:
            for rep in (1, 2):
                ex1, bl1, a1 = run_turn(None, u1, en, variant, None)
                s = score.setdefault(variant, {"n": 0, "fab": 0, "ask": 0, "put": 0, "reblock": 0})
                s["n"] += 1
                if ex1:
                    s["fab"] += 1
                    print(f"  [{variant}|{u1}#r{rep}] 1턴 바로 넣음 {ex1[0][1].get('start_time')}", flush=True)
                    continue
                asked = bool(ASK.search(a1))
                s["ask"] += asked
                s["reblock"] += bl1 > 1
                ex2, bl2, a2 = run_turn([R.M("user", u1), R.M("model", a1)], u2, en, variant, a1)
                st = str(ex2[-1][1].get("start_time", "")) if ex2 else ""
                put = st.startswith(want)
                s["put"] += put
                print(f"  [{variant}|{u1}#r{rep}] 막힘={bl1} 묻기={asked} | {a1[:60]!r} -> 2턴 {st or '없음'} ok={put}", flush=True)
    print("\n=== 종합 (n=16: 8문항 × 2회) ===", flush=True)
    for v, s in score.items():
        print(f"  {v:4} 1턴 바로 넣음={s['fab']}/{s['n']} 묻기={s['ask']}/{s['n']} 다시 막힘={s['reblock']} 2턴 바른 시각={s['put']}/{s['n']}", flush=True)
    for v, (ko, en) in VARIANTS.items():
        print(f"  {v} KO {len(R.engine().tokenize(ko))} tok · EN {len(R.engine().tokenize(en))} tok", flush=True)


if __name__ == "__main__":
    main()

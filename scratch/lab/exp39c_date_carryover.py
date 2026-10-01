"""exp39c — 날짜 없는 시각의 "직전 턴 날짜 끌림"을 시스템 지시 한 구절로 막을 수 있는가 (Known Issue ①).

exp39b: "내일 오후 3시 치과" 대화 직후 "오전 7시 4분에 물 마시라고 알려줘" → 3건 중 2건이 내일로 끌려갔다
(히스토리 없으면 3/3 오늘). exp39 는 턴 리마인더 [Now] 시각 주입이 무효임을 보였다.

후보: 상대 날짜 규칙 줄(시스템 지시) 끝에 "날짜 단어가 없는 시각은 오늘 — 앞 턴의 날짜를 이어 쓰지 않는다" 구절.
게이트: 끌림 케이스 정답 ↑, 무히스토리·명시 "내일" 무손상, 툴 스모크 무손상, 추가 토큰 ≤ 예산 여유(23).
"""
import sys
import litert_lm as llm
import kosmos_lab as K
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

TODAY, TOMORROW = "2026-09-30", "2026-10-01"
ANCHOR = "Never ask the user to restate a date you can compute."
CLAUSES = {
    "A_base": "",
    "B_en": " A time with no date word means today; do not carry a date over from earlier turns.",
    "C_short": " A time with no date word means today.",
}
HISTS = {
    "noHist": None,
    "afterTomorrow": [
        llm.Message.user("내일 오후 3시에 치과 예약 일정 추가해줘"),
        llm.Message.model(llm.Contents([llm.Content.Text("네, 알겠습니다. 내일(2026년 10월 1일) 오후 3시에 '치과 예약' 일정을 추가했습니다.")])),
    ],
    "afterMoRe": [
        llm.Message.user("모레 저녁 7시에 가족 식사 일정 넣어줘"),
        llm.Message.model(llm.Contents([llm.Content.Text("모레(2026년 10월 2일) 저녁 7시에 '가족 식사' 일정을 추가했습니다.")])),
    ],
}
ASKS = [
    ("오후 7시 4분에 물 마시라고 알려줘", TODAY),
    ("저녁 8시에 운동하라고 알려줘", TODAY),
    ("3시에 회의 준비하라고 알려줘", TODAY),
    ("내일 오전 9시에 쓰레기 버리라고 알려줘", TOMORROW),
]
SMOKE = [
    ("자전거 비밀번호 1234야 기억해줘", "add_memory"),
    ("내일 3시에 치과 예약해줘", "add_schedule"),
    ("오늘 일정 뭐 있어?", "get_schedule"),
    ("자전거 비밀번호 뭐였지?", "search_memory"),
]

base_sys = K.fixture("system_instruction.txt")
assert ANCHOR in base_sys
engine = K.create_engine()

for label, clause in CLAUSES.items():
    sysmsg = base_sys.replace(ANCHOR, ANCHOR + clause)
    ok = total = 0
    for hname, hist in HISTS.items():
        for u, day in ASKS:
            with K.create_chat_conversation(engine, history=hist, system_message=sysmsg) as conv:
                res = conv.send_message(K.with_turn_reminder(u))
            calls = res.get("tool_calls") or []
            name = calls[0]["function"]["name"] if calls else None
            t = str(calls[0]["function"].get("arguments", {}).get("time", "")) if calls else ""
            hit = name == "add_reminder" and t.startswith(day)
            ok += hit; total += 1
            print(f"{label:8} {hname:14} {'OK' if hit else 'X '} {u[:16]!r} -> {name} {t} (기대 {day})", flush=True)
    smoke = 0
    for u, tool in SMOKE:
        with K.create_chat_conversation(engine, system_message=sysmsg) as conv:
            res = conv.send_message(K.with_turn_reminder(u))
        calls = res.get("tool_calls") or []
        name = calls[0]["function"]["name"] if calls else None
        smoke += name == tool
        print(f"{label:8} smoke {'OK' if name == tool else 'X '} {u[:16]!r} -> {name}", flush=True)
    print(f"== {label}: 날짜 {ok}/{total}  스모크 {smoke}/{len(SMOKE)}  추가토큰 {len(engine.tokenize(clause)) if clause else 0}", flush=True)

# 에뮬레이터 원문 재현(exp39b 조건: "내일 치과" 직후, "오전 7시 4분") — A 와 C 비교
for label in ("A_base", "C_short"):
    sysmsg = base_sys.replace(ANCHOR, ANCHOR + CLAUSES[label])
    for u in ("오전 7시 4분에 물 마시라고 알려줘", "저녁 8시에 운동하라고 알려줘"):
        with K.create_chat_conversation(engine, history=HISTS["afterTomorrow"], system_message=sysmsg) as conv:
            res = conv.send_message(K.with_turn_reminder(u))
        calls = res.get("tool_calls") or []
        t = str(calls[0]["function"].get("arguments", {}).get("time", "")) if calls else ""
        print(f"repro {label:8} {u[:16]!r} -> {t}", flush=True)

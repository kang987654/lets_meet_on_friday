"""exp39 — 턴 리마인더에 현재 시각을 실으면 리마인더 날짜가 맞는가 (0.27.1 Known Issue ①).

## 왜 필요한가

에뮬레이터(2026-09-30 06:54)에서 "오전 7시 4분에 물 마시라고 알려줘" → 모델이 **내일 7:04** 로 잡았다.
시스템 지시에는 날짜만 있고 시각이 없어서(PromptAssembler.buildDateBlock — 분 단위 시계를 넣으면
매 턴 시스템 지시가 바뀌어 대화 전체 재프리필) 오늘 그 시각이 지났는지 모른다.

## 후보

시각을 **턴 리마인더**(사용자 발화 앞에 매 턴 붙는 줄, PromptAssembler.withTurnToolReminder)에 싣는다 —
원래 매 턴 바뀌는 자리라 대화 재사용이 깨지지 않는다. 비용은 턴당 몇 토큰.

## 게이트

B(시각 有)가 기대 날짜·시각 정답률에서 A 이상 AND 툴 선택 스모크 무손상(기억/일정/조회).
"""
import json
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

NOW = "14:10"  # 가정한 현재 시각 — fixtures/system_instruction.txt 의 Today(2026-09-30)와 짝
TODAY, TOMORROW = "2026-09-30", "2026-10-01"

# (발화, 기대 ISO 접두) — 이미 지난 시각은 "다음 발생"(내일)이 정답
REMINDERS = [
    ("오후 2시 30분에 약 먹으라고 알려줘", f"{TODAY}T14:30"),
    ("3시에 회의 준비하라고 알려줘", f"{TODAY}T15:00"),
    ("저녁 8시에 운동하라고 알려줘", f"{TODAY}T20:00"),
    ("오후 1시에 약 먹으라고 알려줘", f"{TOMORROW}T13:00"),   # 이미 지남
    ("오전 11시에 은행 가라고 알려줘", f"{TOMORROW}T11:00"),  # 이미 지남
    ("내일 오전 9시에 쓰레기 버리라고 알려줘", f"{TOMORROW}T09:00"),
]
SMOKE = [
    ("자전거 비밀번호 1234야 기억해줘", "add_memory"),
    ("내일 3시에 치과 예약해줘", "add_schedule"),
    ("오늘 일정 뭐 있어?", "get_schedule"),
    ("자전거 비밀번호 뭐였지?", "search_memory"),
]


def turn(u: str, with_time: bool) -> str:
    base = K.fixture("turn_reminder.txt")
    if with_time:
        # [WHY] 후보 문구 — 짧게, 날짜는 시스템 지시에 이미 있으므로 시각만.
        base = f"{base}\n[Now] {NOW}"
    return f"{base}\n\n{u}"


engine = K.create_engine()


def call(u: str, with_time: bool):
    with K.create_chat_conversation(engine) as conv:
        before = conv.token_count
        res = conv.send_message(turn(u, with_time))
        calls = res.get("tool_calls") or []
        if not calls:
            return None, {}, conv.token_count - before
        fn = calls[0]["function"]
        return fn["name"], fn.get("arguments", {}), conv.token_count - before


for label, with_time in (("A_noTime", False), ("B_turnTime", True)):
    ok = 0
    for u, expected in REMINDERS:
        name, args, _ = call(u, with_time)
        got = str(args.get("time", ""))
        hit = name == "add_reminder" and got.startswith(expected)
        ok += hit
        print(f"{label} {'OK ' if hit else 'X  '} {u[:20]!r} -> {name} {got} (기대 {expected})", flush=True)
    smoke = 0
    for u, tool in SMOKE:
        name, args, _ = call(u, with_time)
        smoke += name == tool
        print(f"{label} smoke {'OK ' if name == tool else 'X  '} {u[:18]!r} -> {name}", flush=True)
    print(f"== {label}: 날짜정답 {ok}/{len(REMINDERS)}  스모크 {smoke}/{len(SMOKE)}", flush=True)

# 토큰 비용
_, _, a = call("안녕", False)
_, _, b = call("안녕", True)
print(f"턴 토큰(발화+응답 포함) A={a} B={b} Δ={b - a}")

"""exp39b — 직전 턴의 "내일" 맥락이 리마인더 날짜를 끌고 가는가 (에뮬레이터 재현 조건).

에뮬레이터: "내일 오후 3시에 치과 예약" 대화 직후 06:54 에 "오전 7시 4분에 물 마시라고 알려줘"
→ 모델이 **내일** 7:04 로 잡았다. exp39(히스토리 없음)에서는 "내일"이 없으면 늘 오늘이었다.
"""
import sys
import litert_lm as llm
import kosmos_lab as K
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

HIST = [
    llm.Message.user("내일 오후 3시에 치과 예약 일정 추가해줘"),
    llm.Message.model(llm.Contents([llm.Content.Text("네, 알겠습니다. 내일(2026년 10월 1일) 오후 3시에 '치과 예약' 일정을 추가했습니다.")])),
]
ASKS = [
    "오전 7시 4분에 물 마시라고 알려줘",
    "저녁 8시에 운동하라고 알려줘",
    "3시에 회의 준비하라고 알려줘",
]
engine = K.create_engine()
for label, hist in (("noHist", None), ("afterTomorrowTurn", HIST)):
    for u in ASKS:
        with K.create_chat_conversation(engine, history=hist) as conv:
            res = conv.send_message(K.with_turn_reminder(u))
            calls = res.get("tool_calls") or []
            t = calls[0]["function"].get("arguments", {}).get("time") if calls else None
            name = calls[0]["function"]["name"] if calls else None
            print(f"{label:18} {u[:18]!r} -> {name} {t}", flush=True)

"""exp40 — 지난 시각 리마인더를 승인 카드 전에 되돌릴 때 모델이 어떻게 반응하나.

지금: 과거 시각 검사가 AddReminderUseCase(승인 뒤 실행)에만 있어, 사용자가 카드를 승인한 다음에야
"이미 지난 시각" 오류가 난다. 승인 요청 단계로 당기면 회신이 BaseAgent 의 인자 오류 봉투
(tool/field/reason 키 추가)로 바뀐다 — 모델 입력이 바뀌므로 반응을 실측한다(§2-⑤).

X = 현행 실행 오류 봉투, Y = 인자 오류 봉투(reason=past), 문구는 둘 다 현행 원문.
판정: 다시 호출했다면 그 시각이 처음과 다른 미래(내일)인지 / 텍스트면 거짓 성공이 없는지.
"""
import json
import re
import sys
import kosmos_lab as K
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

MSG = "이미 지난 시각입니다. 미래의 시각으로 다시 호출하세요."
VARIANTS = {
    "X_exec": {"status": "error", "message": MSG},
    "Y_arg": {"status": "error", "tool": "add_reminder", "field": "time", "reason": "past", "message": MSG},
}
UTTERANCES = [
    "오전 11시에 은행 가라고 알려줘",
    "오전 9시에 약 먹으라고 알려줘",
    "오후 1시에 점심 약속 알려줘",
    "아침 8시에 운동하라고 알려줘",
]
SAVED = re.compile(r"(등록|설정|예약|추가)(했|해\s?(두|뒀|드렸|놓)|되었|됐|완료)")
NEG = re.compile(r"(못|실패|오류|않|없|지난|지났)")

engine = K.create_engine()
for name, payload in VARIANTS.items():
    good = 0
    for u in UTTERANCES:
        with K.create_chat_conversation(engine) as conv:
            r1 = conv.send_message(K.with_turn_reminder(u))
            c1 = r1.get("tool_calls") or []
            if not c1 or c1[0]["function"]["name"] != "add_reminder":
                print(f"{name} SKIP {u!r}", flush=True); continue
            t1 = c1[0]["function"].get("arguments", {}).get("time")
            r2 = conv.send_message(K.tool_response_message("add_reminder", json.dumps(payload, ensure_ascii=False)))
        c2 = r2.get("tool_calls") or []
        if c2:
            t2 = c2[0]["function"].get("arguments", {}).get("time")
            ok = c2[0]["function"]["name"] == "add_reminder" and t2 != t1
            out = f"재호출 {t1} -> {t2}"
        else:
            content = r2.get("content")
            text = "".join(x.get("text", "") for x in content) if isinstance(content, list) else str(content)
            fs = bool(SAVED.search(text)) and not NEG.search(text)
            ok = not fs
            out = f"텍스트{'[거짓성공]' if fs else ''} {text.strip()[:80]!r}"
        good += ok
        print(f"{name:7} {'OK' if ok else 'X '} {u[:16]!r} {out}", flush=True)
    print(f"== {name}: {good}/{len(UTTERANCES)}", flush=True)

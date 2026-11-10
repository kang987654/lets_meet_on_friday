"""exp87 — 시각 없이 일정 부탁 → 모델 물음 → 시각만 답할 때 일정 추가로 이어지는지 (0.41.11 M2).

exp86: "회의 일정 추가해줘" → "모레 오전 10시"에 "회의 제목과 종료 시간을 알려주시면…"으로 또 물었다.
후보: C0 지금 · C1 선언(제목은 사용자 말에서, 종료·메모는 묻지 않음) · C2 시스템 지시 일정 규칙에 한 문장.
첫 턴은 각 후보 그대로 실제로 돌리고(모델의 물음까지 후보의 영향), 둘째 턴에서 add_schedule 호출·시작 시각을 본다.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
llm = R.llm


def add_schedule(title: str, start_time: str, end_time: str | None, memo: str | None) -> dict:
    """사용자의 캘린더에 일정을 추가한다.

    Args:
        title: 일정 제목. 사용자 말에서 정한다(예: 회의).
        start_time: 시작 시각. ISO 8601 형식.
        end_time: 종료 시각. ISO 8601 형식. 묻지 말고, 모르면 시작 1시간 뒤.
        memo: 메모. 묻지 말고, 없으면 빈 문자열.
    """
    raise NotImplementedError


TOOLS_C1 = [add_schedule if t.__name__ == "add_schedule" else t for t in R.TOOLS_OFF]
RULE = next(l for l in R.SYS_OFF.splitlines() if "`add_schedule`" in l and "예약" in l)
SYS_C2 = R.SYS_OFF.replace(RULE, RULE + " Take the title from the user's words and never ask for end_time or memo.")
RULE_EN = next(l for l in R.SYS_EN_OFF.splitlines() if "`add_schedule`" in l)
SYS_EN_C2 = R.SYS_EN_OFF.replace(RULE_EN, RULE_EN + " Take the title from the user's words and never ask for end_time or memo.")

VARIANTS = {
    "C0": (R.TOOLS_OFF, R.SYS_OFF, R.SYS_EN_OFF),
    "C1": (TOOLS_C1, R.SYS_OFF, R.SYS_EN_OFF),
    "C2": (R.TOOLS_OFF, SYS_C2, SYS_EN_C2),
}
CASES = [
    ("회의 일정 추가해줘", "모레 오전 10시", False), ("병원 예약 잡아줘", "다음 주 화요일 2시", False),
    ("저녁 약속 일정 넣어줘", "금요일 저녁 7시", False), ("팀 회의 잡아줘", "내일 오후 4시", False),
    ("치과 예약 잡아줘", "내일 3시", False), ("운동 일정 추가해줘", "토요일 아침 8시", False),
    ("Add a meeting to my calendar", "The day after tomorrow at 10am", True), ("Book a doctor's appointment", "Next Tuesday at 2pm", True),
]


def one(history, user, tools, sysm, en):
    hist = R.model_history((history or []) + [R.M("user", user)])[:-1]
    with K.create_chat_conversation(R.engine(), history=hist or None, tools=tools, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))  # 영어 턴도 회귀 세트 turn_en 과 같은 리마인더
        calls = res.get("tool_calls") or []
        if calls:
            fn = calls[0]["function"]
            res2 = conv.send_message(K.tool_response_message(fn["name"], json.dumps({"status": "success", "message": "일정이 성공적으로 추가되었습니다."}, ensure_ascii=False)))
            return fn, R.text_of(res2)
        return None, R.text_of(res)


if __name__ == "__main__":
    print(f"규칙 원문: {RULE}", flush=True)
    for tag, (tools, sys_ko, sys_en) in VARIANTS.items():
        ok = 0
        print(f"== {tag}", flush=True)
        for first, follow, en in CASES:
            sysm = sys_en if en else sys_ko
            fn1, a1 = one(None, first, tools, sysm, en)
            if fn1 is not None:
                print(f"  [{first}] 첫 턴부터 호출 {json.dumps(fn1.get('arguments'), ensure_ascii=False)}", flush=True)
                continue
            fn2, a2 = one([R.M("user", first), R.M("model", a1)], follow, tools, sysm, en)
            hit = fn2 is not None and fn2["name"] == "add_schedule" and (fn2.get("arguments") or {}).get("start_time")
            ok += bool(hit)
            print(f"  [{first} → {follow}] {'추가' if hit else '안 됨'} | 물음: {a1[:50]} | {json.dumps(fn2.get('arguments'), ensure_ascii=False) if fn2 else a2[:60]}", flush=True)
        print(f"  {tag} 이어서 추가 {ok}/{len(CASES)}", flush=True)

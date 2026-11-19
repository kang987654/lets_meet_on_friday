"""exp104 — 0.42.9: 기억 → 알림 (한국어). 메모에 적어 둔 일의 시각으로 알림을 맞추게 할 처방을 고른다.

앱과 같은 실행: 한 생성의 호출 3개까지 차례 실행(결과 묶음), 생성 상한 3(실행 차례 2) — C 조건만 4(실행 차례 3).
지난 시각 알림은 앱처럼 되돌린다("이미 지난 시각입니다…"). 일정 가드는 회귀 세트 흉내.
조건
  P0  : 지금
  G1/G2/G3 : 시각 근거 없는 add_reminder 를 막고 문구 n 을 돌려준다(근거 = 사용자 말의 시계 시각 · 이번 턴 앞선 툴 결과의 시각)
  G1C/G2C : 위 + 생성 상한 4(막힌 차례가 상한을 먹으므로)
  T   : 시스템 지시에 트리거 한 줄(먼저 search_memory)
  TG1C: T + G1 + 상한 4
지금 = 픽스처 2026-11-09(월) 10:53.
사용: python exp104_memory_to_reminder.py [조건,…] > exp104_out.txt
"""
import json
import pathlib
import re
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
NOW = "2026-11-09T10:53"
PAST_MSG = "이미 지난 시각입니다. 미래의 시각으로 다시 호출하세요."
PAST_MSG_EN = "That time has already passed. Call the tool again with a future time."

G_TEXT = {
    "G1": "실행하지 않았습니다. 몇 시인지 정해지지 않았습니다. 사용자가 전에 알려 준 일이면 search_memory 로 먼저 찾아보고, 없으면 몇 시인지 물어보세요.",
    "G2": "실행하지 않았습니다. 알림 시각의 근거가 없습니다. 사용자가 전에 알려 준 일의 시각이면 search_memory 로 찾은 뒤 다시 부르고, 아니면 몇 시에 알릴지 사용자에게 물어보세요.",
    "G3": "실행하지 않았습니다. 몇 시인지 모릅니다. search_memory 로 찾아보거나 사용자에게 몇 시인지 물어보세요.",
}
G_TEXT_EN = "Not executed. No time was given. If it is something the user told you before, call search_memory first; otherwise ask the user what time."

T_ANCHOR = "- Reminders at a time (\"3시에 알려줘\", \"리마인드\"): call `add_reminder`."
T_LINE = "\n- A reminder at the time of something the user told you before (\"내가 알려준 … 시간에\", \"병원 예약 하루 전에\"): call `search_memory` first, then use the time it returns."
# 짧은 안(0.42.9 사용자 요청) — 키는 조건 이름. T3 는 줄을 더하지 않고 기존 알림 줄 끝에 붙인다.
T_VARIANTS = {
    "T": T_ANCHOR + T_LINE,
    "T1": T_ANCHOR + "\n- A reminder at the time of something the user told you (\"내가 알려준 … 시간에\"): `search_memory` first.",
    "T2": T_ANCHOR + "\n- Reminder timed by something the user told you: `search_memory` first.",
    "T3": T_ANCHOR[:-1] + "; if timed by something the user told you, `search_memory` first.",
    # T2 + 놓친 문항의 예시(짧은 안 셋이 모두 "병원 예약 하루 전에"를 놓쳤다)
    "T4": T_ANCHOR + "\n- Reminder timed by something the user told you (\"병원 예약 하루 전에\"): `search_memory` first.",
    "T5": T_ANCHOR + "\n- Reminder timed by something the user told you (\"내가 알려준\", \"하루 전에\"): `search_memory` first.",
}

MEMOS = [
    ("병원 예약은 11월 12일 오후 2시", ["병원", "예약"]),
    ("팀 회의는 11월 10일 오전 10시", ["회의"]),
    ("치과 예약은 11월 13일 오후 3시 30분", ["치과", "예약"]),
    ("친구 결혼식은 11월 15일 낮 12시", ["결혼식", "약속"]),
    ("미용실 예약은 강남점으로 했어", ["미용실", "예약"]),
]
MEMO_EN = "- Hospital appointment is Nov 12 at 2 PM [hospital, appointment]"
SCHEDULE_KO = "[일정]\n- 팀 회의 (11월 10일 오전 10:00 ~ 오전 11:00)\n- 점심 약속 (11월 10일 오후 1:00 ~ 오후 2:00)"

# (이름, 언어, 사용자 말, 바람) — 바람: "YYYY-MM-DDTHH:MM" 알림 시각 / "ASK" 쓰기 없이 물음 / "LIST" list_reminders / "OBS" 관찰만
CASES = [
    ("M1 알려준 병원 시간", "ko", "내가 알려준 병원 예약 시간에 알림 맞춰줘", "2026-11-12T14:00"),
    ("M2 병원 하루 전", "ko", "병원 예약 하루 전에 알림 맞춰줘", "2026-11-11T14:00"),
    ("M3 메모한 회의 10분 전", "ko", "메모해 둔 회의 시간 10분 전에 알려줘", "2026-11-10T09:50"),
    ("M4 치과 1시간 전", "ko", "내가 말한 치과 예약 1시간 전에 알려줘", "2026-11-13T14:30"),
    ("M5 약속 전날 저녁 8시", "ko", "전에 알려준 약속 전날 저녁 8시에 알림 맞춰줘", "2026-11-14T20:00"),
    ("M6 시각 없는 메모", "ko", "내가 알려준 미용실 예약 시간에 알림 맞춰줘", "ASK"),
    ("M7 없는 기억", "ko", "내가 알려준 은행 방문 시간에 알려줘", "ASK"),
    ("E1 영어", "en", "Set a reminder at the hospital appointment time I told you about", "2026-11-12T14:00"),
    ("X1 오늘 6시", "ko", "오늘 오후 6시에 약 먹으라고 알려줘", "2026-11-09T18:00"),
    ("X2 10분 뒤", "ko", "10분 뒤에 빨래 꺼내라고 알려줘", "2026-11-09T11:0"),
    ("X3 알림 목록", "ko", "지금 등록된 알림 뭐 있어?", "LIST"),
    ("X4 첫 일정 30분 전", "ko", "내일 첫 일정 30분 전에 알려줘", "2026-11-10T09:30"),
    ("X5 내일 아침(시각 없음)", "ko", "내일 아침에 운동하라고 알려줘", "OBS"),
]
# 문구 예시와 겹치지 않는 문장(--held) — T4 의 예시 "병원 예약 하루 전에"가 M2 와 같아 따로 본다.
HELD = [
    ("H1 치과 이틀 전", "ko", "치과 예약 이틀 전에 알림 맞춰줘", "2026-11-11T15:30"),
    ("H2 병원 2시간 전", "ko", "병원 가기 2시간 전에 알려줘", "2026-11-12T12:00"),
    ("H3 결혼식 당일 아침 9시", "ko", "결혼식 날 아침 9시에 알림 맞춰줘", "2026-11-15T09:00"),
]
if "--held" in sys.argv:
    CASES = HELD
WRITE = {"add_schedule", "add_reminder", "add_memory"}
CLAIM = ("맞춰 드렸", "맞췄", "설정했", "등록했", "추가했", "잡았", "알려 드릴게요", "I've set", "I set", "has been set", "set a reminder for")


def search(keyword, en):
    if en:
        return MEMO_EN
    terms = [t for t in re.split(r"\s+", keyword or "") if len(t) >= 2]
    hits = [(c, tg) for c, tg in MEMOS if any(t[:2] in c or any(t[:2] in g for g in tg) for t in terms)]
    fmt = lambda xs: "\n".join(f"- {c} [{', '.join(tg)}]" for c, tg in xs)
    if hits:
        return fmt(hits)
    tags = ", ".join(dict.fromkeys(g for _, tg in MEMOS for g in tg))
    return (f"'{keyword}' 로는 일치하는 기억이 없습니다. 저장된 기억에 붙은 분류는 다음과 같습니다: {tags}.\n"
            "이 중 질문에 해당할 만한 분류가 있으면 그 단어로 `search_memory` 를 한 번 더 호출하세요. 해당하는 분류가 없으면 그런 기억이 없다고 답하세요.\n"
            f"가장 최근에 저장된 기억:\n{fmt(MEMOS)}"
            "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요. 거기에도 없으면 없다고 답하세요. 절대 지어내지 마세요.")


def reminder_guard(mode, user, seen_results, en):
    """G: 사용자 말·이번 턴 앞선 툴 결과 어디에도 시계 시각이 없으면 막는다."""
    if not mode.startswith(("G", "TG")):
        return None
    if R.SCHEDULE_CLOCK.search(user) or any(R.SCHEDULE_CLOCK.search(s) for s in seen_results):
        return None
    key = "G1" if mode.startswith("TG") else mode[:2]
    return G_TEXT_EN if en else G_TEXT[key]


def run_one(mode, name, args, user, sysm, en, seen, log):
    """한 호출 실행 — (결과 body, 실행됐나)."""
    if name == "add_reminder":
        g = reminder_guard(mode, user, seen, en)
        if g:
            log.append(("막힘", name, args))
            return {"status": "error", "message": g}
        t = str(args.get("time", ""))
        if t and t[:16] <= NOW:
            log.append(("지난 시각", name, args))
            return {"status": "error", "message": PAST_MSG_EN if en else PAST_MSG}
        log.append(("실행", name, args))
        return {"status": "success", "message": f"Reminder set: '{args.get('content', '')}'" if en else f"리마인더 등록: '{args.get('content', '')}' 알림"}
    if name == "add_schedule":
        err = None if R.SCHEDULE_TIME.search(user) else R.SCHEDULE_NO_TIME
        err = err or R.past_guidance(user, args.get("start_time"), sysm, en=en)
        log.append(("막힘" if err else "실행", name, args))
        return {"status": "error", "message": err} if err else {"status": "success", "message": "일정이 성공적으로 추가되었습니다."}
    log.append(("실행", name, args))
    if name == "search_memory":
        data = search(args.get("keyword"), en)
    elif name == "get_schedule":
        data = SCHEDULE_KO
    elif name == "list_reminders":
        data = "등록된 알림이 없습니다."
    else:
        data = "없음"
    seen.append(data)
    return {"status": "success", "data": data}


def turn(mode, user, en, sys_t):
    key = mode.rstrip("C").replace("G1", "") or "T"
    sysm = R.SYS_EN_OFF if en else (sys_t[key] if mode.startswith("T") else R.SYS_OFF)
    rounds = 3 if mode.endswith("C") else 2
    log, seen = [], []
    with K.create_chat_conversation(R.engine(), tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        for _ in range(rounds):
            tc = res.get("tool_calls") or []
            if not tc:
                break
            fns = [c["function"] for c in tc][:3]
            bodies = [(f["name"], run_one(mode, f["name"], f.get("arguments") or {}, user, sysm, en, seen, log)) for f in fns]
            if len(bodies) == 1:
                body = bodies[0][1]
            else:
                body = {"status": "success" if all(b["status"] == "success" for _, b in bodies) else "partial",
                        "results": [{"call": n, **b} for n, b in bodies]}
            res = conv.send_message(K.tool_response_message(bodies[0][0], json.dumps(body, ensure_ascii=False)))
        overflow = bool(res.get("tool_calls"))
        return R.text_of(res), log, overflow


def judge(want, ans, log, overflow):
    done = [a for k, n, a in log if k == "실행" and n == "add_reminder"]
    names = [n for k, n, _ in log if k == "실행"]
    claimed = any(w in ans for w in CLAIM)
    if want == "OBS":
        if done:
            return "시각 골라 넣음 " + ",".join(str(a.get("time", ""))[11:16] for a in done)
        return "물음" if not overflow else "상한 초과"
    if want == "LIST":
        return "정답" if "list_reminders" in names and not done else "틀림"
    if overflow:
        return "상한 초과"
    if want == "ASK":
        if done:
            return "지어낸 시각"
        return "거짓 완료" if claimed else "정답(물음)"
    if any(str(a.get("time", "")).startswith(want) for a in done) and len(done) == 1:
        return "정답"
    if done:
        return "지어낸 시각"
    return "거짓 완료" if claimed else "물음"


def main():
    modes = next((a.split(",") for a in sys.argv[1:] if not a.startswith("-")), ["P0", "G1", "G2", "G3", "G1C", "G2C", "T", "TG1C"])
    sys_t = {k: R.SYS_OFF.replace(T_ANCHOR, v) for k, v in T_VARIANTS.items()}
    assert all(v != R.SYS_OFF for v in sys_t.values()), "트리거 줄 자리를 못 찾음"
    try:
        tok = lambda s: len(R.engine().tokenize(s))
        for k, v in sys_t.items():
            print(f"{k} 줄 비용: {tok(v) - tok(R.SYS_OFF)} 토큰(tokenize)", flush=True)
    except Exception as e:  # noqa: BLE001
        print(f"tokenize 실패: {e}", flush=True)
    summary = {}
    for mode in modes:
        print(f"\n######## {mode}", flush=True)
        tally = {}
        for rep in range(2):
            for name, lang, user, want in CASES:
                t0 = time.time()
                ans, log, overflow = turn(mode, user, lang == "en", sys_t)
                v = judge(want, ans, log, overflow)
                tally.setdefault(name, []).append(v)
                steps = "; ".join(f"{k}:{n}({json.dumps(a, ensure_ascii=False)[:70]})" for k, n, a in log)
                print(f"  [{rep}] {name}: {v} | {time.time() - t0:.1f}s | {steps}{' | 상한' if overflow else ''}\n     답: {ans[:120]}", flush=True)
        summary[mode] = tally
        flat = [x for xs in tally.values() for x in xs]
        core = [x for n, xs in tally.items() if n[0] in "ME" for x in xs]
        ctrl = [x for n, xs in tally.items() if n[0] == "X" and not n.startswith("X5") for x in xs]
        print(f"== {mode}: 기억→알림 정답 {sum(x.startswith('정답') for x in core)}/{len(core)} · 지어낸 시각 {core.count('지어낸 시각')} · 거짓 완료 {flat.count('거짓 완료')} · "
              f"상한 초과 {flat.count('상한 초과')} · 대조 정답 {sum(x.startswith('정답') for x in ctrl)}/{len(ctrl)} · X5 {tally.get('X5 내일 아침(시각 없음)')}", flush=True)
    print("\n######## 합계", flush=True)
    for m, t in summary.items():
        print(m, json.dumps(t, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()

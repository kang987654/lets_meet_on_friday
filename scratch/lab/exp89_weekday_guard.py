"""exp89 — 두 턴 요일 일정의 날짜 오류 재현 검증 및 가드 안내 문구 실측 (0.41.13 M1-a).

목적:
  1) 0.41.11 exp88 W1 에서 "저녁 약속 일정 넣어줘 → 금요일 저녁 7시"가 오늘 날짜로 기록된 현상의 실제 원인을 분리 재현한다:
     - (1-A) 1턴에 시간대 낱말이 있는 부탁("저녁 약속 일정 넣어줘" 등 7문항 × 2기준일 × 2회 = 28회):
       앱과 같은 설정에서 1턴 자체가 즉시 add_schedule(오늘)을 부르는지, 되묻는지 확인.
     - (1-B) 실제 앱의 두 턴 경로(1턴 시각 없는 일정 부탁 → 모델이 되묻거나 ScheduleTimeIntent 가드가 막아 되물음 → 2턴 요일+시각 응답,
       8문항[한국어 6 + 영어 2] × 2기준일 × 2회 = 32회)에서 가드 없이 날짜 요일이 틀리는 빈도 측정.
  2) 실시간 대화(live conv)에서 2턴에 요일 불일치 호출이 발생한 모든 회차(및 2턴 호출 전체 대조)에 대해,
     안내 문구 후보 G1·G2·G3 별로 막은 뒤:
       - ① 같은 턴 안 즉시 재호출 수
       - ② 답 원문의 거짓 성공("추가했어요")·자책 사과("제가 잘못 계산했네요") 유무
       - ③ 안내 직후 사용자가 "응"/"Yes"라고 답한 다음 턴(3턴)에서 바른 날짜로 다시 부르는 수
     를 함께 측정한다.
"""
import datetime as dt
import json
import pathlib
import re
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
KO_SHORT = ["월", "화", "수", "목", "금", "토", "일"]
KO_FULL = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"]
EN_SHORT = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
EN_FULL = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
EN_MONTHS = ["", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

KO_WD_RE = re.compile(r"([월화수목금토일])(?:요일|욜)")
EN_WD_RE = re.compile(r"\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b", re.I)
ISO_DATE_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})")

SCHEDULE_NO_TIME_EN = (
    'Not executed. The user\'s message has no date or time, so it is not a scheduling request. '
    'Do not bring up the schedule; confirm what they told you and ask "Would you like me to save this as a note?". '
    'If they did ask to schedule something, ask only when it should be.'
)

SELF_BLAME_RE = re.compile(r"제가\s*(날짜를\s*)?잘못|계산했|착각했|misinterpreted|my mistake", re.I)
FALSE_SAVED_RE = re.compile(
    r"(일정.{0,12}(추가|등록|예약)(했|해\s?(두|뒀|드렸)|되었|됐)|"
    r"i'?ve scheduled|has been (scheduled|added)|added .{0,20}to your calendar)",
    re.I,
)


def date_line(today: dt.date, en: bool) -> str:
    nm = today + dt.timedelta(days=(7 - today.weekday()) or 7)
    wd = today.strftime("%A")
    days = [today + dt.timedelta(i) for i in range(1, 7)]
    if en:
        coming = " ".join(f"{d.strftime('%m-%d')}({EN_SHORT[d.weekday()]})" for d in days)
        return (f"[System Data] Today={today.isoformat()} ({wd}), Tomorrow={(today + dt.timedelta(1)).isoformat()}, "
                f"Day after tomorrow={(today + dt.timedelta(2)).isoformat()}, Next Monday={nm.isoformat()}, Coming days={coming}")
    coming = " ".join(f"{d.strftime('%m-%d')}({KO_SHORT[d.weekday()]})" for d in days)
    return (f"[System Data] 오늘={today.isoformat()} ({wd}), 내일={(today + dt.timedelta(1)).isoformat()}, "
            f"모레={(today + dt.timedelta(2)).isoformat()}, 다음주 월요일={nm.isoformat()}, 요일별={coming}")


def system_for(today: dt.date, en: bool) -> str:
    base = R.SYS_EN_OFF if en else R.SYS_OFF
    old_line = next(l for l in base.splitlines() if l.startswith("[System Data] "))
    return base.replace(old_line, date_line(today, en))


def extract_single_weekday(text: str) -> int | None:
    found = {KO_SHORT.index(m.group(1)) for m in KO_WD_RE.finditer(text)}
    found |= {[x.lower() for x in EN_FULL].index(m.group(1).lower()) for m in EN_WD_RE.finditer(text)}
    return next(iter(found)) if len(found) == 1 else None


def parse_iso_date(start_time: str) -> dt.date | None:
    if not start_time:
        return None
    m = ISO_DATE_RE.match(start_time.strip())
    if not m:
        return None
    try:
        return dt.date.fromisoformat(m.group(1))
    except ValueError:
        return None


def nearest_upcoming(today: dt.date, wd: int) -> dt.date:
    delta = (wd - today.weekday()) % 7
    return today + dt.timedelta(days=delta or 7)


def fmt_month_day(d: dt.date, en: bool) -> str:
    return f"{EN_MONTHS[d.month]} {d.day}" if en else f"{d.month}월 {d.day}일"


def fmt_date_with_wd(d: dt.date, en: bool) -> str:
    if en:
        return f"{EN_MONTHS[d.month]} {d.day} ({EN_SHORT[d.weekday()]})"
    return f"{d.month}월 {d.day}일 ({KO_SHORT[d.weekday()]})"


def guard_message(variant: str, today: dt.date, want_wd: int, en: bool) -> str:
    target = nearest_upcoming(today, want_wd)
    wd_name = EN_FULL[want_wd] if en else KO_FULL[want_wd]
    md = fmt_month_day(target, en)
    md_wd = fmt_date_with_wd(target, en)
    if variant == "G1":
        if en:
            return f"Not executed. {wd_name} is {md}. Add the event again with that date."
        return f"실행하지 않았습니다. {wd_name}은 {md}입니다. 그 날짜로 다시 추가하세요."
    if variant == "G2":
        if en:
            return f"Not executed. {wd_name} is {md}. Ask the user whether to add the event on {md_wd}."
        return f"실행하지 않았습니다. {wd_name}은 {md}입니다. {md_wd}에 일정을 추가할지 사용자에게 확인하세요."
    if variant == "G3":
        if en:
            return f"{wd_name} is {md}. Call `add_schedule` again with {md} (do not ask for confirmation)."
        return f"{wd_name}은 {md}입니다. 확인을 묻지 말고 {md} 날짜로 `add_schedule`을 다시 호출하세요."
    raise ValueError(variant)


# (1-A) 1턴에 시간대 낱말이 있는 부탁 (계획서 예시 "저녁 약속 일정 넣어줘" 등 7개)
TIMED_TURN1_CASES = [
    ("t_ko_금_저녁약속", "저녁 약속 일정 넣어줘", "금요일 저녁 7시", False, 4, "19:00"),
    ("t_ko_토_아침운동", "아침 운동 일정 추가해줘", "토요일 아침 8시", False, 5, "08:00"),
    ("t_ko_화_오후회의", "오후 팀 회의 일정 잡아줘", "화요일 오후 3시", False, 1, "15:00"),
    ("t_ko_일_점심생일", "점심 생일 모임 일정 넣어줘", "일요일 낮 12시", False, 6, "12:00"),
    ("t_ko_화_오전병원", "오전 병원 예약 일정 추가해줘", "화요일 오전 10시", False, 1, "10:00"),
    ("t_ko_금_저녁운동", "저녁 필라테스 일정 잡아줘", "금요일 오후 8시", False, 4, "20:00"),
    ("t_en_fri_dinner", "Add an evening dinner with Sam to my calendar", "Friday at 7pm", True, 4, "19:00"),
]

# (1-B & 2) 실제 두 턴 일정 부탁 8개 (한국어 6: 금·토·화·일·수×2 / 영어 2: 금·수)
TWO_TURN_CASES = [
    ("ko_금_회식", "회식 일정 잡아줘", "금요일 저녁 7시", "회식", False, 4, "19:00", "20:00"),
    ("ko_토_운동", "운동 일정 추가해줘", "토요일 아침 8시", "운동", False, 5, "08:00", "09:00"),
    ("ko_화_회의", "팀 회의 일정 잡아줘", "화요일 오후 3시", "팀 회의", False, 1, "15:00", "16:00"),
    ("ko_일_생일", "생일 모임 일정 넣어줘", "일요일 낮 12시", "생일 모임", False, 6, "12:00", "13:00"),
    ("ko_수_치과", "치과 예약 잡아줘", "수요일 오전 11시", "치과 예약", False, 2, "11:00", "12:00"),
    ("ko_수_검진", "건강검진 예약 넣어줘", "수요일 아침 9시", "건강검진", False, 2, "09:00", "10:00"),
    ("en_fri_dinner", "Schedule dinner with Sam", "Friday at 7pm", "Dinner with Sam", True, 4, "19:00", "20:00"),
    ("en_wed_dentist", "Book a dentist appointment", "Wednesday at 11am", "Dentist appointment", True, 2, "11:00", "12:00"),
]


def app_turn1(sysm: str, user1: str, en: bool):
    executed, blocked_cnt = [], 0
    with K.create_chat_conversation(R.engine(), history=None, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user1))
        for _ in range(3):
            calls = res.get("tool_calls") or []
            if not calls:
                break
            fn = calls[0]["function"]
            name, args = fn["name"], fn.get("arguments") or {}
            if name == "add_schedule" and not R.SCHEDULE_TIME.search(user1):
                msg = SCHEDULE_NO_TIME_EN if en else R.SCHEDULE_NO_TIME
                body, ok = {"status": "error", "message": msg}, False
            elif name == "add_schedule":
                body, ok = {"status": "success", "message": "Event added." if en else "일정이 성공적으로 추가되었습니다."}, True
            else:
                body, ok = {"status": "success", "data": "None" if en else "없음"}, True
            if ok:
                executed.append((name, args))
            else:
                blocked_cnt += 1
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
        return executed, blocked_cnt, R.text_of(res)


def natural_turn2(sysm: str, hist: list, user2: str, en: bool):
    with K.create_chat_conversation(R.engine(), history=hist, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user2))
        calls = res.get("tool_calls") or []
        return (calls[0]["function"] if calls else None), R.text_of(res)


def live_guard_eval(sysm: str, hist: list, user2: str, today: dt.date, want_wd: int, hhmm: str, en: bool, variant: str):
    """실시간 열린 대화에서 2턴 요일 불일치 호출을 가드 문구로 막은 뒤:
    (즉시 바른 날짜 재호출 여부, 답 원문, 거짓 성공 여부, 자책 사과 여부, '응'/'Yes' 후속 턴 바른 날짜 호출 여부)를 측정한다."""
    msg = guard_message(variant, today, want_wd, en)
    err_json = json.dumps({"status": "error", "message": msg}, ensure_ascii=False)
    want_date = nearest_upcoming(today, want_wd).isoformat()
    with K.create_chat_conversation(R.engine(), history=hist, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user2))
        calls1 = res.get("tool_calls") or []
        if not calls1:
            return False, R.text_of(res), False, False, False, ""
        res2 = conv.send_message(K.tool_response_message("add_schedule", err_json))
        calls2 = res2.get("tool_calls") or []
        if calls2:
            fn_re = calls2[0]["function"]
            st_re = str((fn_re.get("arguments") or {}).get("start_time", ""))
            ok_same = fn_re["name"] == "add_schedule" and st_re.startswith(f"{want_date}T{hhmm}")
            ok_msg = json.dumps({"status": "success", "message": "Event added." if en else "일정이 성공적으로 추가되었습니다."}, ensure_ascii=False)
            res3 = conv.send_message(K.tool_response_message(fn_re["name"], ok_msg))
            ans = R.text_of(res3)
            return ok_same, ans, False, bool(SELF_BLAME_RE.search(ans)), ok_same, st_re
        ans = R.text_of(res2)
        false_saved = bool(FALSE_SAVED_RE.search(ans))
        self_blame = bool(SELF_BLAME_RE.search(ans))
        res_yes = conv.send_message(K.with_turn_reminder("Yes" if en else "응"))
        calls_yes = res_yes.get("tool_calls") or []
        fn_yes = calls_yes[0]["function"] if calls_yes else None
        st_yes = str((fn_yes.get("arguments") or {}).get("start_time", "")) if fn_yes else ""
        ok_after_yes = bool(fn_yes and fn_yes["name"] == "add_schedule" and st_yes.startswith(f"{want_date}T{hhmm}"))
        return False, ans, false_saved, self_blame, ok_after_yes, st_yes


def main():
    eng = R.engine()
    sample_today = dt.date(2026, 11, 9)
    print("=== [0] 안내 문구 후보 및 토큰 수 (예시: 금요일 = 11월 13일) ===", flush=True)
    for v in ("G1", "G2", "G3"):
        ko_m = guard_message(v, sample_today, 4, False)
        en_m = guard_message(v, sample_today, 4, True)
        print(f"  {v} KO ({len(eng.tokenize(ko_m))} tok): {ko_m}", flush=True)
        print(f"  {v} EN ({len(eng.tokenize(en_m))} tok): {en_m}", flush=True)

    print("\n=== [1-A] 1턴 시간대 낱말 포함 시 행동 확인 (exp88 W1 원인 분리, 7문항 × 2기준일 × 2회 = 28회) ===", flush=True)
    t1_immediate, t1_total = 0, 0
    for today in (dt.date(2026, 11, 9), dt.date(2026, 11, 12)):
        for tag, u1, u2, en, want_wd, hhmm in TIMED_TURN1_CASES:
            sysm = system_for(today, en)
            for rep in (1, 2):
                t1_total += 1
                ex1, _, text1 = app_turn1(sysm, u1, en)
                if ex1:
                    t1_immediate += 1
                    st1 = ex1[0][1].get("start_time", "")
                    print(f"  [{today} {tag}#r{rep}] 1턴 즉시 add_schedule! start_time={st1} (2턴 미진입)", flush=True)
                else:
                    fn2, text2 = natural_turn2(sysm, [R.M("user", u1), R.M("model", text1)], u2, en)
                    st2 = (fn2 or {}).get("arguments", {}).get("start_time", "")
                    print(f"  [{today} {tag}#r{rep}] 1턴 되물음 -> 2턴 start_time={st2 or 'NO_CALL'} | {text2[:50]}", flush=True)
    print(f"  => 1-A 요약: {t1_total}회 중 {t1_immediate}회가 1턴에서 즉시 오늘 날짜로 add_schedule 호출(2턴 미진입)", flush=True)

    print("\n=== [1-B] 실제 두 턴 경로(1턴 시각 없음 -> 되묻기 -> 2턴 요일 지정) 가드 전 재현 (8문항 × 2기준일 × 2회 = 32회) ===", flush=True)
    nat_total, nat_wrong_wd, nat_no_call, nat_ok = 0, 0, 0, 0
    wrong_runs = []
    for today in (dt.date(2026, 11, 9), dt.date(2026, 11, 12)):
        for case in TWO_TURN_CASES:
            tag, u1, u2, title, en, want_wd, hhmm, end_hhmm = case
            sysm = system_for(today, en)
            for rep in (1, 2):
                nat_total += 1
                ex1, bl1, text1 = app_turn1(sysm, u1, en)
                hist = [R.M("user", u1), R.M("model", text1)]
                fn2, text2 = natural_turn2(sysm, hist, u2, en)
                st2 = str((fn2 or {}).get("arguments", {}).get("start_time", "")) if fn2 and fn2["name"] == "add_schedule" else ""
                dt2 = parse_iso_date(st2)
                if not st2:
                    nat_no_call += 1
                    status = "NO_CALL"
                elif dt2 is not None and dt2.weekday() != want_wd:
                    nat_wrong_wd += 1
                    status = f"WRONG_WD({st2})"
                    wrong_runs.append((today, case, rep, sysm, hist, st2))
                else:
                    nat_ok += 1
                    status = f"OK({st2})"
                print(f"  [{today} {tag}#r{rep}] 1턴막힘={bl1} -> 2턴={status} | {text2[:60]}", flush=True)
    print(f"  => 1-B 요약: 총 {nat_total}회 | 요일일치 호출={nat_ok} | 요일불일치 호출={nat_wrong_wd} | 미호출(되물음)={nat_no_call}", flush=True)

    print(f"\n=== [2] 실시간 요일 불일치 발생 회차({len(wrong_runs)}회)에 대한 후보 G1·G2·G3 비교 ===", flush=True)
    stats = {v: {"same_turn": 0, "after_yes": 0, "false_saved": 0, "self_blame": 0} for v in ("G1", "G2", "G3")}
    for today, case, rep, sysm, hist, bad_st in wrong_runs:
        tag, u1, u2, title, en, want_wd, hhmm, end_hhmm = case
        print(f"  -- [{today} {tag}#r{rep}] 원래 잘못 보낸 start_time={bad_st} --", flush=True)
        for v in ("G1", "G2", "G3"):
            ok_same, ans, fs, sb, ok_yes, st_yes = live_guard_eval(sysm, hist, u2, today, want_wd, hhmm, en, v)
            stats[v]["same_turn"] += int(ok_same)
            stats[v]["after_yes"] += int(ok_yes)
            stats[v]["false_saved"] += int(fs)
            stats[v]["self_blame"] += int(sb)
            clean_ans = ans.replace("\n", " ")
            print(f"    {v}: 즉시재호출={ok_same} | 응뒤재호출={ok_yes}({st_yes or 'NO_CALL'}) | 거짓성공={fs} | 자책={sb} | 답={clean_ans!r}", flush=True)

    n = len(wrong_runs)
    print(f"\n=== [3] 후보별 종합 비교표 (자연 발생 요일 불일치 {n}회 기준) ===", flush=True)
    for v in ("G1", "G2", "G3"):
        s = stats[v]
        print(f"  {v}: 같은턴즉시재호출={s['same_turn']}/{n} | 확인뒤('응'/'Yes')바른날짜호출={s['after_yes']}/{n} | 거짓성공={s['false_saved']}/{n} | 자책사과={s['self_blame']}/{n}", flush=True)


if __name__ == "__main__":
    main()

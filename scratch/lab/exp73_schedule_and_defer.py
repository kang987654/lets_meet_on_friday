"""exp73 — 0.40.6 M0: 일정 조회 중첩 추론 제거·종료 시각·브리핑 재료 스모크 및 연속 oneShot 지연 복원 실측.

계획서 docs/plans/0.40.6-runtime-optimization.md M0 (AGENTS.md §2-⑤):
1. part schedule — SummarizeScheduleUseCase 중첩 추론을 빼고 [일정] / [Schedule] 목록(+종료 시각, 기기 캘린더 실패 주의)만 돌려줄 때
   오늘·내일(주간 혼합 목록에서 골라내기)·내일 빈 일정·주간·빈 일정·캘린더 실패 최종 답 채점(한/영) 및 ISO 노출 0건 검증.
2. part brief    — 아침 브리핑 재료([오늘 일정])에 종료 시각(~ 오후 5:00 / ~ 5:00 PM) 추가 시 말투·후속 질문 1개·시각 보존·캘린더 실패·ISO 0건 대조.
3. part defer    — 채팅 2턴 뒤 연속 oneShot 3회(요약→추출→병합 판정) + 3턴째 채팅에서 매회 token_count 복원(every) vs 끝에 1회 복원(defer) 실측.
"""
import datetime as dt
import json
import re
import sys
import time

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

args = [a for a in sys.argv[1:] if not a.startswith("--")]
PARTS = args or ["schedule", "brief", "defer"]

SYS_KO = K.fixture("system_instruction.txt")
SYS_EN = K.fixture("system_instruction_en.txt")
REMINDER = K.fixture("turn_reminder.txt").strip()
BRIEF_SYS_KO = K.fixture("briefing_system.txt")
SUMMARY_SYS = K.fixture("episode_summary_system.txt")
EXTRACT_SYS = K.fixture("extract_system.txt")
JUDGE_SYS = K.fixture("memory_judge_system.txt")

BRIEF_SYS_EN = """[System]
You are a personal assistant writing a short morning briefing in English. Follow these rules:
- Start with a brief, calm greeting like "Good morning." Avoid exclamation marks or exaggerated cheerfulness.
- Summarize today's schedule and pending tasks concisely in a warm, polite tone. Preserve times, names, and numbers verbatim.
- End with **exactly 1** natural follow-up question based on [Recent Conversation Memories] (check the date prefix on the memory to refer to "yesterday" or "last week" accurately). If there are no memories, ask one light question about today's plans instead.
- Keep the entire briefing to 5-8 sentences. If a section is empty, do not pad it."""

_WIKI_KO = next(l for l in SYS_KO.splitlines() if "search_wikipedia" in l) + "\n"
_WIKI_EN = next(l for l in SYS_EN.splitlines() if "search_wikipedia" in l) + "\n"
SYS_KO_OFF = SYS_KO.replace(_WIKI_KO, "")
SYS_EN_OFF = SYS_EN.replace(_WIKI_EN, "")
TOOLS_OFF = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]

TODAY_STR, TOMORROW_STR, DAY_AFTER_STR, NEXT_MON_STR = re.search(
    r"오늘=(\S+) \(\w+\), 내일=(\S+), 모레=(\S+), 다음주 월요일=(\S+)", SYS_KO
).groups()
TODAY_DATE = dt.date.fromisoformat(TODAY_STR)
TOMORROW_DATE = dt.date.fromisoformat(TOMORROW_STR)
DAY_AFTER_DATE = dt.date.fromisoformat(DAY_AFTER_STR)

MONTH_EN = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
ISO_RE = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}")
EMOJI_RE = re.compile("[\U0001F300-\U0001FAFF☀-➿⭐⬆↔-↪]")
HANGUL_RE = re.compile("[가-힣]")
FORMAL_RE = re.compile(r"(니다|니까|십시오|시오)$")
POLITE_RE = re.compile(r"(요|죠)$")


def fmt_time_ko(h, m):
    ampm = "오후" if h >= 12 else "오전"
    h12 = 12 if h % 12 == 0 else h % 12
    return f"{ampm} {h12}:{m:02d}"


def fmt_time_en(h, m):
    ampm = "PM" if h >= 12 else "AM"
    h12 = 12 if h % 12 == 0 else h % 12
    return f"{h12}:{m:02d} {ampm}"


def fmt_range_ko(d, sh, sm, eh=None, em=None):
    start = f"{d.month}월 {d.day}일 {fmt_time_ko(sh, sm)}"
    if eh is None or (sh == eh and sm == em):
        return start
    return f"{start} ~ {fmt_time_ko(eh, em)}"


def fmt_range_en(d, sh, sm, eh=None, em=None):
    start = f"{MONTH_EN[d.month - 1]} {d.day}, {fmt_time_en(sh, sm)}"
    if eh is None or (sh == eh and sm == em):
        return start
    return f"{start} ~ {fmt_time_en(eh, em)}"


def build_schedule_data(events, en=False, cal_failed=False, is_week=False):
    note_ko = "\n\n(주의: 기기 캘린더를 읽지 못해 앱에 저장된 일정만 조회했습니다. 답변에 기기 캘린더를 확인하지 못했다는 점을 꼭 함께 알려주세요.)"
    note_en = "\n\n(Note: Could not read the device calendar, so only app-saved events were checked. Be sure to mention in your reply that the device calendar could not be checked.)"
    week_tail_ko = "\n이 목록에서 사용자가 물은 날짜와 일치하는 일정만 골라 답하고, 그 날짜에 일정이 없으면 없다고만 답하고 다른 날짜 일정은 말하지 마세요."
    week_tail_en = "\nOnly report events whose date matches the date the user asked about; if there is no event on that date, state only that nothing is scheduled for that date and do not mention events on other dates."
    header = "[Schedule]" if en else "[일정]"
    empty = "No scheduled events." if en else "등록된 일정이 없어요."
    note = (note_en if en else note_ko) if cal_failed else ""
    if not events:
        return empty + note
    lines = [header]
    lines.extend(f"- {title} ({disp})" for title, disp in events)
    tail = (week_tail_en if en else week_tail_ko) if is_week else ""
    return "\n".join(lines) + tail + note


engine = llm.Engine(
    K.MODEL_PATH,
    backend=llm.Backend.GPU(),
    max_num_images=K.MAX_IMAGES_PER_TURN,
    max_num_tokens=K.ENGINE_MAX_TOKENS,
    activation_data_type=llm.ActivationDataType.FLOAT32,
)


def text_of(res):
    c = res.get("content")
    return (c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))).strip()


def run_schedule_turn(user, sysm, schedule_map):
    with K.create_chat_conversation(engine, tools=TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        called = []
        for _ in range(3):
            calls = res.get("tool_calls") or []
            if not calls:
                break
            fn = calls[0]["function"]
            name, a = fn["name"], fn.get("arguments") or {}
            raw_date = str(a.get("date", "")).lower().strip()
            rng = "today" if (not raw_date or "today" in raw_date or "오늘" in raw_date) else "week"
            called.append((name, rng))
            data = schedule_map.get(rng, schedule_map.get("default", "없음"))
            body = json.dumps({"status": "success", "data": data}, ensure_ascii=False)
            res = conv.send_message(K.tool_response_message(name, body))
        return text_of(res), called


if "schedule" in PARTS:
    today_ko = [
        ("팀 회의", fmt_range_ko(TODAY_DATE, 10, 0, 11, 0)),
        ("치과 예약", fmt_range_ko(TODAY_DATE, 16, 30, 17, 30)),
    ]
    week_mixed_ko = [
        ("팀 회의", fmt_range_ko(TODAY_DATE, 10, 0, 11, 0)),
        ("안과 진료", fmt_range_ko(TOMORROW_DATE, 14, 0, 15, 0)),
        ("친구 저녁 약속", fmt_range_ko(TOMORROW_DATE, 19, 0, 21, 0)),
        ("프로젝트 발표", fmt_range_ko(DAY_AFTER_DATE, 15, 0, 16, 0)),
    ]
    week_no_tom_ko = [
        ("팀 회의", fmt_range_ko(TODAY_DATE, 10, 0, 11, 0)),
        ("프로젝트 발표", fmt_range_ko(DAY_AFTER_DATE, 15, 0, 16, 0)),
    ]

    today_en = [
        ("Team meeting", fmt_range_en(TODAY_DATE, 10, 0, 11, 0)),
        ("Dentist appointment", fmt_range_en(TODAY_DATE, 16, 30, 17, 30)),
    ]
    week_mixed_en = [
        ("Team meeting", fmt_range_en(TODAY_DATE, 10, 0, 11, 0)),
        ("Eye clinic", fmt_range_en(TOMORROW_DATE, 14, 0, 15, 0)),
        ("Dinner with friend", fmt_range_en(TOMORROW_DATE, 19, 0, 21, 0)),
        ("Project presentation", fmt_range_en(DAY_AFTER_DATE, 15, 0, 16, 0)),
    ]
    week_no_tom_en = [
        ("Team meeting", fmt_range_en(TODAY_DATE, 10, 0, 11, 0)),
        ("Project presentation", fmt_range_en(DAY_AFTER_DATE, 15, 0, 16, 0)),
    ]

    map_std_ko = {
        "today": build_schedule_data(today_ko, en=False),
        "week": build_schedule_data(week_mixed_ko, en=False, is_week=True),
    }
    map_no_tom_ko = {
        "today": build_schedule_data(today_ko, en=False),
        "week": build_schedule_data(week_no_tom_ko, en=False, is_week=True),
    }
    map_empty_ko = {"default": build_schedule_data([], en=False)}
    map_cal_fail_ko = {"default": build_schedule_data(today_ko[:1], en=False, cal_failed=True)}
    map_cal_fail_empty_ko = {"default": build_schedule_data([], en=False, cal_failed=True)}

    map_std_en = {
        "today": build_schedule_data(today_en, en=True),
        "week": build_schedule_data(week_mixed_en, en=True, is_week=True),
    }
    map_no_tom_en = {
        "today": build_schedule_data(today_en, en=True),
        "week": build_schedule_data(week_no_tom_en, en=True, is_week=True),
    }
    map_empty_en = {"default": build_schedule_data([], en=True)}
    map_cal_fail_en = {"default": build_schedule_data(today_en[:1], en=True, cal_failed=True)}
    map_cal_fail_empty_en = {"default": build_schedule_data([], en=True, cal_failed=True)}

    CASES = [
        # (태그, 발화, 지시문, 맵, 판정 함수)
        ("ko_today_all", "오늘 일정 뭐 있어?", SYS_KO_OFF, map_std_ko,
         lambda a, c: c == [("get_schedule", "today")] and "팀 회의" in a and "치과" in a and ("10:00" in a or "10시" in a)),
        ("ko_today_end1", "오늘 팀 회의 몇 시에 끝나?", SYS_KO_OFF, map_std_ko,
         lambda a, c: c == [("get_schedule", "today")] and ("11:00" in a or "11시" in a)),
        ("ko_today_end2", "오늘 치과 예약 몇 시부터 몇 시까지야?", SYS_KO_OFF, map_std_ko,
         lambda a, c: c == [("get_schedule", "today")] and bool(re.search(r"4:30|4시\s*30", a)) and bool(re.search(r"5:30|5시\s*30", a))),
        ("ko_tom_filter1", "내일 일정 뭐 있어?", SYS_KO_OFF, map_std_ko,
         lambda a, c: c == [("get_schedule", "week")] and "안과" in a and "저녁 약속" in a and "팀 회의" not in a and "프로젝트 발표" not in a),
        ("ko_tom_filter2", "내일 스케줄 알려줘", SYS_KO_OFF, map_std_ko,
         lambda a, c: c == [("get_schedule", "week")] and "안과" in a and "저녁 약속" in a and "팀 회의" not in a and "프로젝트 발표" not in a),
        ("ko_tom_empty", "내일 일정 있어?", SYS_KO_OFF, map_no_tom_ko,
         lambda a, c: c == [("get_schedule", "week")] and bool(re.search(r"없|비어", a)) and "안과" not in a and "프로젝트 발표" not in a),
        ("ko_week_all", "이번 주 스케줄 알려줘", SYS_KO_OFF, map_std_ko,
         lambda a, c: c == [("get_schedule", "week")] and all(k in a for k in ("팀 회의", "안과", "저녁 약속", "프로젝트 발표"))),
        ("ko_empty", "오늘 일정 뭐 있어?", SYS_KO_OFF, map_empty_ko,
         lambda a, c: c == [("get_schedule", "today")] and bool(re.search(r"없|비어", a))),
        ("ko_cal_fail1", "오늘 일정 알려줘", SYS_KO_OFF, map_cal_fail_ko,
         lambda a, c: "팀 회의" in a and ("캘린더" in a or "앱에 저장" in a)),
        ("ko_cal_fail_empty", "오늘 일정 알려줘", SYS_KO_OFF, map_cal_fail_empty_ko,
         lambda a, c: bool(re.search(r"없|비어", a)) and ("캘린더" in a or "앱에 저장" in a)),
        ("en_today_all", "What's on my schedule today?", SYS_EN_OFF, map_std_en,
         lambda a, c: c == [("get_schedule", "today")] and "team meeting" in a.lower() and "dentist" in a.lower()),
        ("en_today_end", "When does my team meeting end today?", SYS_EN_OFF, map_std_en,
         lambda a, c: c == [("get_schedule", "today")] and bool(re.search(r"11:00|11\s*AM", a, re.I))),
        ("en_tom_filter", "What's on my calendar tomorrow?", SYS_EN_OFF, map_std_en,
         lambda a, c: c == [("get_schedule", "week")] and "eye clinic" in a.lower() and "dinner" in a.lower() and "team meeting" not in a.lower() and "project presentation" not in a.lower()),
        ("en_tom_empty", "Do I have anything scheduled tomorrow?", SYS_EN_OFF, map_no_tom_en,
         lambda a, c: c == [("get_schedule", "week")] and bool(re.search(r"\b(no|nothing|don't|do not|free|empty|aren't|isn't)\b", a, re.I)) and "project presentation" not in a.lower()),
        ("en_empty", "What's on my schedule today?", SYS_EN_OFF, map_empty_en,
         lambda a, c: c == [("get_schedule", "today")] and bool(re.search(r"\b(no|nothing|don't|do not|free|empty)\b", a, re.I))),
        ("en_cal_fail", "What's on my schedule today?", SYS_EN_OFF, map_cal_fail_en,
         lambda a, c: "team meeting" in a.lower() and bool(re.search(r"calendar|app-saved|saved in the app", a, re.I))),
        ("en_cal_fail_empty", "What's on my schedule today?", SYS_EN_OFF, map_cal_fail_empty_en,
         lambda a, c: bool(re.search(r"\b(no|nothing|none|empty)\b", a, re.I)) and bool(re.search(r"calendar|app-saved|saved in the app", a, re.I))),
    ]

    ok_cnt = 0
    iso_cnt = 0
    for tag, u, sysm, smap, check in CASES:
        ans, called = run_schedule_turn(u, sysm, smap)
        has_iso = bool(ISO_RE.search(ans))
        iso_cnt += has_iso
        ok = bool(check(ans, called)) and not has_iso
        ok_cnt += ok
        print(f"[schedule] {'OK' if ok else 'X '} {tag:18s} | calls={called} iso={has_iso} | {ans.replace(chr(10), ' / ')[:110]}", flush=True)
    print(f"== schedule 총점: {ok_cnt}/{len(CASES)} (ISO 노출 {iso_cnt}/{len(CASES)})", flush=True)


# ---------------------------------------------------------------- part brief
def brief_input_ko(date_label, events, tasks, episodes, cal_failed=False):
    sched = ["[오늘 일정]"]
    if cal_failed:
        sched.append("(주의: 기기 캘린더를 읽지 못했습니다 — 아래는 앱에 저장된 일정만이며, 브리핑에서 이 사실을 알려야 합니다)")
    sched += [f"- {t} ({w})" for t, w in events] or ["- 없음"]
    body = [f"오늘은 {date_label}입니다. 아래 재료로 아침 브리핑을 작성하세요.", "", *sched, "", "[미완료 할 일]"]
    body += [f"- {t}" for t in tasks] or ["- 없음"]
    body += ["", "[최근 대화 기억]"]
    body += [f"- ({d}) {t}: {s}" for d, t, s in episodes] or ["- 없음"]
    return "\n".join(body)


def brief_input_en(date_label, events, tasks, episodes, cal_failed=False):
    sched = ["[Today's Schedule]"]
    if cal_failed:
        sched.append("(Note: Could not read device calendar — below are app-saved events only; mention this in the briefing)")
    sched += [f"- {t} ({w})" for t, w in events] or ["- None"]
    body = [f"Today is {date_label}. Write a morning briefing using the materials below.", "", *sched, "", "[Pending Tasks]"]
    body += [f"- {t}" for t in tasks] or ["- None"]
    body += ["", "[Recent Conversation Memories]"]
    body += [f"- ({d}) {t}: {s}" for d, t, s in episodes] or ["- None"]
    return "\n".join(body)


def polite_stats(text):
    parts = [p.strip() for p in re.split(r"(?<=[.?!…])\s+|\n+", text) if HANGUL_RE.search(p or "")]
    polite = formal = 0
    for s in parts:
        core = re.sub(r"[\s.?!…~,\"'”’)\]*:]+$", "", EMOJI_RE.sub("", s))
        if FORMAL_RE.search(core):
            formal += 1
        elif POLITE_RE.search(core):
            polite += 1
    return len(parts), polite, formal


if "brief" in PARTS:
    EP_KO = [
        ("10월 21일", "치과 상담 결과 정리", "사용자는 치과에서 충치 치료를 권유받았고 다음 주에 다시 가기로 했습니다."),
        ("10월 18일", "이사 준비 체크리스트", "사용자는 이사 날짜를 11월 2일로 정하고 짐 정리 순서를 비서와 정리했습니다."),
        ("10월 9일", "영어 회화 공부", "사용자는 매일 30분 영어 회화 연습 계획을 세웠습니다."),
    ]
    BRIEFS_RANGE_KO = [
        brief_input_ko("10월 22일 목요일", [], [], []),
        brief_input_ko("10월 22일 목요일", [("팀 회의", "10월 22일 오전 10:00 ~ 오전 11:00"), ("치과 예약", "10월 22일 오후 4:30 ~ 오후 5:30")], ["장보기", "보고서 초안"], EP_KO[:2]),
        brief_input_ko("10월 22일 목요일", [("점심 약속", "10월 22일 오후 12:30 ~ 오후 1:30")], [], EP_KO[2:]),
        brief_input_ko("10월 22일 목요일", [], ["택배 반품", "관리비 납부", "세탁소"], EP_KO[:1]),
        brief_input_ko("10월 23일 금요일", [("부모님 저녁", "10월 23일 오후 7:00 ~ 오후 9:00")], ["선물 사기"], EP_KO, cal_failed=True),
        brief_input_ko("10월 24일 토요일", [], [], EP_KO[1:2]),
        brief_input_ko("10월 26일 월요일", [("주간 회의", "10월 26일 오전 9:30 ~ 오전 10:30"), ("병원", "10월 26일 오후 2:00"), ("헬스", "10월 26일 오후 8:00 ~ 오후 9:00")], ["회의 자료"], []),
        brief_input_ko("10월 27일 화요일", [], ["이사 업체 견적 비교"], EP_KO[1:]),
    ]
    tot_sent = tot_pol = tot_form = q_ok = cal_wrong = cal_missed = iso_leak = 0
    for i, body in enumerate(BRIEFS_RANGE_KO):
        with K.create_chat_conversation(engine, tools=[], system_message=BRIEF_SYS_KO) as conv:
            out = text_of(conv.send_message(body))
        ns, pol, form = polite_stats(out)
        tot_sent += ns
        tot_pol += pol
        tot_form += form
        q_ok += (out.count("?") == 1)
        failed = "(주의: 기기 캘린더" in body
        cal_wrong += (not failed) and ("캘린더" in out)
        cal_missed += failed and ("캘린더" not in out)
        iso_leak += bool(ISO_RE.search(out))
        print(f"[brief_ko {i}] q={out.count('?')} pol={pol}/{ns} | {out.replace(chr(10), ' / ')[:120]}", flush=True)
    print(f"== brief_ko(종료 시각 포함 8세트): 해요 {100 * tot_pol / max(tot_sent, 1):.0f}% 합니다 {100 * tot_form / max(tot_sent, 1):.0f}% | "
          f"질문1개 {q_ok}/{len(BRIEFS_RANGE_KO)} | 캘린더 오언급 {cal_wrong}/7 누락 {cal_missed}/1 | ISO {iso_leak}/8", flush=True)

    EP_EN = [
        ("Oct 21", "Dentist follow-up", "The user was advised to get cavity treatment next week."),
        ("Oct 18", "Moving checklist", "The user set the moving date for Nov 2 and organized packing steps."),
    ]
    BRIEFS_RANGE_EN = [
        brief_input_en("Thursday, Oct 22", [("Team meeting", "Oct 22, 10:00 AM ~ 11:00 AM"), ("Dentist", "Oct 22, 4:30 PM ~ 5:30 PM")], ["Buy groceries"], EP_EN[:1]),
        brief_input_en("Friday, Oct 23", [("Dinner with parents", "Oct 23, 7:00 PM ~ 9:00 PM")], ["Buy gift"], EP_EN, cal_failed=True),
    ]
    en_q_ok = en_cal_ok = 0
    for i, body in enumerate(BRIEFS_RANGE_EN):
        with K.create_chat_conversation(engine, tools=[], system_message=BRIEF_SYS_EN) as conv:
            out = text_of(conv.send_message(body))
        en_q_ok += (out.count("?") == 1)
        failed = "(Note: Could not read device calendar" in body
        en_cal_ok += (failed == bool(re.search(r"calendar", out, re.I)))
        print(f"[brief_en {i}] q={out.count('?')} | {out.replace(chr(10), ' / ')[:120]}", flush=True)
    print(f"== brief_en(종료 시각 포함 2세트): 질문1개 {en_q_ok}/2 | 캘린더 정합 {en_cal_ok}/2", flush=True)


# ---------------------------------------------------------------- part defer
def timed(f):
    t = time.perf_counter()
    r = f()
    return r, time.perf_counter() - t


def run_oneshot(sys_msg, user_msg):
    c = engine.create_conversation(
        system_message=sys_msg,
        automatic_tool_calling=False,
        thinking_config=llm.ThinkingConfig(enable_thinking=False),
        sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
    )
    c.send_message(user_msg)
    c.close()


if "defer" in PARTS:
    TURNS = ["안녕! 한 문장으로만 답해 줘.", "오늘 날씨가 좋네. 한 문장으로만 답해 줘.", "고마워. 한 문장으로만 답해 줘."]
    SIDES = [
        (SUMMARY_SYS, "다음 대화를 요약하세요.\n\n사용자: 내일 치과 예약 있어\n비서: 몇 시에 예약하셨나요?\n사용자: 오후 3시야"),
        (EXTRACT_SYS, "사용자: 내 사물함 번호는 214번이야\n비서: 기억해 둘게요."),
        (JUDGE_SYS, "기억들:\n- 자전거 자물쇠 비밀번호는 4821\n- 자전거 자물쇠 번호 4821"),
    ]
    totals = {"every": [], "defer": []}
    for rep in range(3):
        for arm in ("every", "defer"):
            conv = K.create_chat_conversation(engine, tools=TOOLS_OFF, system_message=SYS_KO_OFF)
            for u in TURNS[:2]:
                conv.send_message(K.with_turn_reminder(u))
            t_side_total = 0.0
            t_restore_total = 0.0
            for idx, (s_sys, s_usr) in enumerate(SIDES):
                _, ts = timed(lambda: run_oneshot(s_sys, s_usr))
                t_side_total += ts
                if arm == "every" or idx == len(SIDES) - 1:
                    _, tr = timed(lambda: conv.token_count)
                    t_restore_total += tr
            _, t_turn3 = timed(lambda: conv.send_message(K.with_turn_reminder(TURNS[2])))
            conv.close()
            bg_total = t_side_total + t_restore_total
            totals[arm].append((t_side_total, t_restore_total, bg_total, t_turn3))
            print(f"[defer rep={rep} {arm:5s}] oneShot 3회={t_side_total:5.2f}s · 복원(token_count)={t_restore_total:5.2f}s · "
                  f"배치합={bg_total:5.2f}s · 3턴째 채팅={t_turn3:5.2f}s", flush=True)
    for arm in ("every", "defer"):
        rows = totals[arm]
        avg_side = sum(r[0] for r in rows) / len(rows)
        avg_rest = sum(r[1] for r in rows) / len(rows)
        avg_bg = sum(r[2] for r in rows) / len(rows)
        avg_t3 = sum(r[3] for r in rows) / len(rows)
        print(f"== defer 평균 [{arm:5s}]: oneShot={avg_side:.2f}s · 복원={avg_rest:.2f}s · 배치합={avg_bg:.2f}s · 3턴째={avg_t3:.2f}s", flush=True)

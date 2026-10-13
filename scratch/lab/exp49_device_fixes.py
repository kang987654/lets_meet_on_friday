"""exp49 — 0.36.0 실기기 결함 수정 M0 실측 (합성 데이터, 계획서 docs/plans/0.36.0-device-fixes.md).

part 1 turn    — 턴 리마인더 보강: A 현행 / N "[Now] HH:mm" / NC 긍정 답 문장 + [Now]
                 (상대 시각 8 · 긍정 답 6형 × 히스토리 2형 · 무호출 긍정 2 · exp41 스모크 23)
part 2 multi   — 한 턴 여러 호출 중 첫 호출만 실행될 때의 거짓 성공(⑦a): 현행 / 미실행 건수 안내 키
part 3 brief   — 아침 브리핑 "어제" 오표기(L3): A 현행 / C 에피소드 날짜 + 예시 교체
part 4 extract — 자동 추출(②⑥): A exp36 v2 원문 / B 리마인더 요청·비서 발언 보강 문장

사용: python exp49_device_fixes.py [part...]  (기본 전부)
"""
import datetime as dt
import pathlib
import re
import sys
import textwrap

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

PARTS = sys.argv[1:] or ["turn", "multi", "brief", "extract"]
REPO = K.LAB.parent.parent
SYS = K.fixture("system_instruction.txt")
R = K.fixture("turn_reminder.txt").strip()
m = re.search(r"오늘=(\d{4}-\d{2}-\d{2})", SYS)
assert m, "픽스처에 날짜 블록이 없다"
D0 = dt.date.fromisoformat(m.group(1))
iso = lambda d: d.isoformat()  # noqa: E731

engine = K.create_engine()


def text_of(res):
    c = res.get("content")
    if isinstance(c, str):
        return c
    return "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    return [(c["function"]["name"], c["function"].get("arguments", {}) or {}) for c in (res.get("tool_calls") or [])]


def M(s):
    return llm.Message.model(llm.Contents([llm.Content.Text(s)]))


def M_call(name, args):
    return llm.Message.model(llm.Contents([llm.Content.Text("")]), [llm.ToolCall(name, args)])


def chat(hist, user_text, system=SYS):
    with K.create_chat_conversation(engine, history=hist, system_message=system) as conv:
        return conv.send_message(user_text)


# ---------------------------------------------------------------- part 1
CONT = " If the user is answering your previous question, carry out what you proposed."


def wrap(variant, u, now):
    hhmm = now.strftime("%H:%M")
    if variant == "A":
        return f"{R}\n\n{u}"
    if variant == "N":
        return f"{R} [Now] {hhmm}\n\n{u}"
    return f"{R}{CONT} [Now] {hhmm}\n\n{u}"


if "turn" in PARTS:
    NOW = dt.datetime.combine(D0, dt.time(14, 49))
    at = lambda h, mi, day=D0: f"{iso(day)}T{h:02d}:{mi:02d}"  # noqa: E731
    RELATIVE = [
        ("지금 테스트 중이라 1분 뒤에 알려줘", at(14, 50)),
        ("10분 있다가 전화하라고 알려줘", at(14, 59)),
        ("30분 뒤에 물 마시라고 알려줘", at(15, 19)),
        ("한 시간 후에 회의 준비하라고 알려줘", at(15, 49)),
        ("2시간 뒤에 빨래 걷으라고 알려줘", at(16, 49)),
        ("오후 5시에 퇴근 준비하라고 알려줘", at(17, 0)),
        ("저녁 8시에 운동하라고 알려줘", at(20, 0)),
        ("내일 오전 9시에 쓰레기 버리라고 알려줘", at(9, 0, D0 + dt.timedelta(days=1))),
    ]
    PROPOSAL = "혹시 오늘 오후 4시 30분에 '산책' 알림도 설정해 드릴까요?"
    HIST_KV = [
        llm.Message.user(wrap("A", "3시에 약 먹으라고 알려줘", NOW)),
        M_call("add_reminder", {"time": f"{iso(D0)}T15:00:00", "content": "약 먹기"}),
        K.tool_response_message("add_reminder", f'{{"status":"success","message":"리마인더 등록: {D0.month}월 {D0.day}일 오후 3:00에 \'약 먹기\' 알림"}}'),
        M("오후 3시에 '약 먹기' 알림을 설정했습니다."),
        llm.Message.user(wrap("A", "요즘 운동을 너무 안 해서 걱정이야", NOW)),
        M(f"가벼운 산책부터 시작해 보시는 건 어떨까요? {PROPOSAL}"),
    ]
    HIST_TEXT = [
        llm.Message.user("3시에 약 먹으라고 알려줘"), M("오후 3시에 '약 먹기' 알림을 설정했습니다."),
        llm.Message.user("요즘 운동을 너무 안 해서 걱정이야"), M(f"가벼운 산책부터 시작해 보시는 건 어떨까요? {PROPOSAL}"),
    ]
    AFFIRM = ["어", "응", "그래", "ㅇㅇ", "해줘", "네"]
    NO_CALL = [
        ([llm.Message.user("요즘 피곤해"), M("많이 피곤하시군요. 요즘 충분히 주무시고 계신가요?")], "응"),
        ([llm.Message.user("주말에 영화 볼까 고민 중이야"), M("좋은 생각이에요! 평소에 어떤 장르를 좋아하세요? 액션 좋아하세요?")], "어"),
    ]
    # exp41 스모크(단일 턴 17 + 긴 잡담 히스토리 6). 날짜는 픽스처 날짜에서 계산한다.
    D1 = D0 + dt.timedelta(days=1)
    SAT = D0 + dt.timedelta(days=(5 - D0.weekday()) % 7 or 7)
    MON = D0 + dt.timedelta(days=(0 - D0.weekday()) % 7 or 7)
    has = lambda key, sub: (lambda a: sub in str(a.get(key, "")))  # noqa: E731
    TOMORROW_HIST = [llm.Message.user("내일 오후 3시에 치과 예약 일정 추가해줘"),
                     M(f"네, 알겠습니다. 내일({D1.year}년 {D1.month}월 {D1.day}일) 오후 3시에 '치과 예약' 일정을 추가했습니다.")]
    CHATS = [("요즘 날씨 너무 덥다", "맞아요, 요즘 정말 덥죠. 물 자주 드시고 시원하게 지내세요."),
             ("주말에 뭐 할지 고민이야", "날씨가 좋으면 가까운 공원 산책은 어떠세요? 실내라면 전시회도 좋아요."),
             ("요즘 읽을 만한 책 추천해줘", "가볍게 읽기 좋은 에세이나 단편 소설을 추천드려요."),
             ("커피 하루에 몇 잔이 적당해?", "보통 성인은 하루 3~4잔 이내가 적당하다고 알려져 있어요."),
             ("오늘 점심 뭐 먹지", "따뜻한 국밥이나 시원한 냉면은 어떠세요?"),
             ("운동 시작하려는데 뭐부터 해?", "걷기나 가벼운 스트레칭부터 시작해 보세요.")]
    LONG = [x for uu, mm in CHATS for x in (llm.Message.user(uu), M(mm))]
    SMOKE = [
        ("내 자물쇠 비밀번호는 4936이야, 기억해줘", "add_memory", has("content", "4936"), None),
        ("와이파이 비번 kosmos123 저장해줘", "add_memory", has("content", "kosmos123"), None),
        ("나 땅콩 알레르기 있어, 잊지 마", "add_memory", None, None),
        ("내 자물쇠 비밀번호 뭐였지?", "search_memory", None, None),
        ("내가 알려준 와이파이 비번 기억나?", "search_memory", None, None),
        ("내일 3시에 치과 예약해줘", "add_schedule", has("start_time", iso(D1)), None),
        ("3시에 회의 일정 잡아줘", "add_schedule", has("start_time", iso(D0)), None),
        ("다음주 월요일 오전 10시에 팀 회의 일정 추가해줘", "add_schedule", has("start_time", iso(MON) + "T10"), None),
        ("이번주 토요일 오후 2시에 결혼식 일정 추가해줘", "add_schedule", has("start_time", iso(SAT) + "T14"), None),
        ("오늘 일정 뭐 있어?", "get_schedule", None, None),
        ("에스파가 뭐야? 검색해줘", "search_wikipedia", None, None),
        ("오후 3시에 약 먹으라고 알려줘", "add_reminder", has("time", iso(D0) + "T15"), None),
        ("내일 아침 9시에 쓰레기 버리라고 리마인드 해줘", "add_reminder", has("time", iso(D1) + "T09"), None),
        ("안녕! 오늘 기분 좋다", None, None, None),
        ("고마워, 덕분에 살았어", None, None, None),
        ("오전 7시 4분에 물 마시라고 알려줘", "add_reminder", has("time", iso(D0)), TOMORROW_HIST),
        ("저녁 8시에 운동하라고 알려줘", "add_reminder", has("time", iso(D0)), TOMORROW_HIST),
        ("에스파가 뭐야? 검색해줘", "search_wikipedia", None, LONG),
        ("내 자물쇠 비밀번호 뭐였지?", "search_memory", None, LONG),
        ("자전거 비밀번호 5521이야 기억해줘", "add_memory", has("content", "5521"), LONG),
        ("내일 3시에 치과 예약해줘", "add_schedule", has("start_time", iso(D1)), LONG),
        ("오후 3시에 약 먹으라고 알려줘", "add_reminder", has("time", iso(D0) + "T15"), LONG),
        ("그렇구나, 고마워", None, None, LONG),
    ]
    # [WHY] 스모크는 아침 6시 발화로 잰다 — "오전 7시 4분"이 이미 지난 시각이 되면 [Now] 조건만 날짜를 내일로 옮겨 오답처리된다.
    SMOKE_NOW = dt.datetime.combine(D0, dt.time(6, 0))
    for v in ("A", "N", "NC"):
        rel = 0
        for u, want in RELATIVE:
            cs = calls_of(chat(None, wrap(v, u, NOW)))
            t = str(cs[0][1].get("time", "")) if cs else ""
            ok = bool(cs) and cs[0][0] == "add_reminder" and t.startswith(want)
            rel += ok
            print(f"[turn] {v:2} rel   {'OK' if ok else 'X '} {u[:18]!r} -> {t or cs} (기대 {want})", flush=True)
        aff = {}
        for hname, hist in (("kv", HIST_KV), ("text", HIST_TEXT)):
            ok_n = 0
            for a in AFFIRM:
                res = chat(hist, wrap(v, a, NOW))
                cs = calls_of(res)
                ok = bool(cs) and cs[0][0] == "add_reminder" and "16:30" in str(cs[0][1].get("time", "")) and "산책" in str(cs[0][1].get("content", ""))
                ok_n += ok
                print(f"[turn] {v:2} {hname:4}  {'OK' if ok else 'X '} {a!r:6} -> {cs} | {text_of(res)[:50]!r}", flush=True)
            aff[hname] = ok_n
        nocall = 0
        for hist, a in NO_CALL:
            res = chat(hist, wrap(v, a, NOW))
            ok = not calls_of(res)
            nocall += ok
            print(f"[turn] {v:2} nocall {'OK' if ok else 'X '} {a!r} -> {calls_of(res)} | {text_of(res)[:50]!r}", flush=True)
        smoke = 0
        for u, tool, check, hist in SMOKE:
            cs = calls_of(chat(hist, wrap(v, u, SMOKE_NOW)))
            got, args = (cs[0] if cs else (None, {}))
            ok = got == tool and (check is None or check(args))
            smoke += ok
            if not ok:
                print(f"[turn] {v:2} smoke X  {u[:18]!r} -> {got} {str(args)[:70]}", flush=True)
        print(f"== [turn] {v}: 상대시각 {rel}/{len(RELATIVE)} 긍정 kv {aff['kv']}/6 text {aff['text']}/6 무호출 {nocall}/{len(NO_CALL)} 스모크 {smoke}/{len(SMOKE)}", flush=True)
    base = len(engine.tokenize(wrap("A", "", NOW)))
    print(f"== [turn] 추가 토큰: N {len(engine.tokenize(wrap('N', '', NOW))) - base}, NC {len(engine.tokenize(wrap('NC', '', NOW))) - base}", flush=True)

# ---------------------------------------------------------------- part 2
if "multi" in PARTS:
    REJECT = "사용자가 승인하지 않아 실행하지 않았습니다. 기억하지 않았다고 알리세요."
    SKIP = "같은 요청에 들어 있던 다른 작업 {n}건은 실행하지 않았습니다. 사용자에게 알리세요."
    ASKS = [
        ("아침에 커피 대신 녹차를 마셔, 기억해줘. 그리고 나를 민준님이라고 불러줘", "reject"),
        ("내 사물함 번호 112번이야 기억해. 그리고 와이파이 비번 kosmos77도 저장해줘", "reject"),
        ("나 땅콩 알레르기 있어 기억해줘, 그리고 회사 주차는 B2 구역이야 그것도", "reject"),
        ("내 사물함 번호 112번이야 기억해. 그리고 와이파이 비번 kosmos77도 저장해줘", "success"),
        ("나 땅콩 알레르기 있어 기억해줘, 그리고 회사 주차는 B2 구역이야 그것도", "success"),
        ("자전거 비밀번호 5521이야 기억해줘", "reject"),
    ]
    CLAIM = re.compile(r"기억해 ?두|기억하겠|기억했|저장했|저장해 ?두|저장하겠|기억해 드|저장해 드")
    for v in ("A", "S"):
        bad = 0
        multi_seen = 0
        for u, kind in ASKS:
            with K.create_chat_conversation(engine, system_message=SYS) as conv:
                res = conv.send_message(K.with_turn_reminder(u))
                cs = calls_of(res)
                if not cs:
                    print(f"[multi] {v} -- 호출 없음 {u[:20]!r} | {text_of(res)[:50]!r}", flush=True)
                    continue
                dropped = len(cs) - 1
                multi_seen += dropped > 0
                body = {"status": "error", "message": REJECT} if kind == "reject" else {"status": "success", "message": "기억에 저장했어요."}
                if v == "S" and dropped > 0:
                    body["skipped"] = SKIP.format(n=dropped)
                import json
                final = conv.send_message(K.tool_response_message(cs[0][0], json.dumps(body, ensure_ascii=False)))
            out = text_of(final)
            # 거짓 성공: 거절이면 어떤 저장 주장이든, 성공이면 미실행 건까지 저장했다고 말하는 것(둘째 대상 단어가 저장 주장과 함께)
            if kind == "reject":
                lie = bool(CLAIM.search(out))
            else:
                second = {"kosmos77": "kosmos77", "B2": "B2"}
                tgt = next((w for w in second if w in u), None)
                lie = dropped > 0 and tgt is not None and tgt in out and bool(CLAIM.search(out)) and "않" not in out
            bad += lie
            print(f"[multi] {v} {'LIE' if lie else 'ok '} {kind:7} calls={len(cs)} {u[:20]!r} -> {out[:90]!r}", flush=True)
        print(f"== [multi] {v}: 거짓 성공 {bad}/{len(ASKS)} (여러 호출 재현 {multi_seen}건)", flush=True)

# ---------------------------------------------------------------- part 2b
# [WHY] part 2 결과: 여러 호출은 모델이 둘째 호출을 다시 내 거짓말이 없었고, **단일 add_memory 거절**에서 거짓 성공이 나왔다.
# exp38d·e 는 리마인더·일정 거절만 쟀다 — 메모 거절 문구는 실측된 적이 없다.
if "memrej" in PARTS:
    import json
    REJ = {
        "A": "사용자가 승인하지 않아 실행하지 않았습니다. 기억하지 않았다고 알리세요.",
        "B": "사용자가 승인하지 않아 기억에 저장하지 않았습니다. 기억해 두겠다고 말하지 말고, 저장하지 않았다고 알리세요.",
        "C": "사용자가 저장을 승인하지 않았습니다. 이 내용은 기억에 없습니다. 저장하지 않았다고 사용자에게 분명히 알리세요.",
    }
    ASKS = ["자전거 비밀번호 5521이야 기억해줘", "내 사물함 번호 112번이야 기억해", "나 땅콩 알레르기 있어, 잊지 마",
            "와이파이 비번 kosmos77 저장해줘", "아침에 커피 대신 녹차를 마셔, 기억해줘", "회사 주차는 B2 구역이야 메모해줘"]
    CLAIM = re.compile(r"기억해 ?두|기억하겠|기억했|저장했|저장해 ?두|저장하겠|기억해 드|저장해 드|기억할게|저장할게")
    for v, msg in REJ.items():
        bad = honest = 0
        for u in ASKS:
            with K.create_chat_conversation(engine, system_message=SYS) as conv:
                cs = calls_of(conv.send_message(K.with_turn_reminder(u)))
                if not cs or cs[0][0] != "add_memory":
                    print(f"[memrej] {v} -- add_memory 아님 {u[:18]!r} -> {cs}", flush=True)
                    continue
                final = conv.send_message(K.tool_response_message("add_memory", json.dumps({"status": "error", "message": msg}, ensure_ascii=False)))
            out = text_of(final)
            lie = bool(CLAIM.search(out))
            said_not = "않" in out or "못" in out
            bad += lie
            honest += said_not and not lie
            print(f"[memrej] {v} {'LIE' if lie else 'ok '} {u[:18]!r} -> {out[:90]!r} {calls_of(final) or ''}", flush=True)
        print(f"== [memrej] {v}: 거짓 성공 {bad}/{len(ASKS)} 저장 안 함을 말함 {honest}/{len(ASKS)}  토큰 {len(engine.tokenize(msg))}", flush=True)

# ---------------------------------------------------------------- part 3
if "brief" in PARTS:
    src = (REPO / "domain/src/main/java/com/kosmos/app/domain/usecase/GenerateBriefingUseCase.kt").read_text(encoding="utf-8")
    sm = re.search(r'SYSTEM_INSTRUCTION = """(.*?)"""\.trimIndent\(\)', src, re.S)
    assert sm, "브리핑 지시를 소스에서 찾지 못했다"
    SYS_A = textwrap.dedent(sm.group(1)).strip("\n")
    OLD_EX = '(예: "어제 말씀하신 ○○은 잘 돼가요?")'
    assert OLD_EX in SYS_A
    SYS_C = SYS_A.replace(OLD_EX, '(예: "지난번에 말씀하신 ○○은 잘 돼가요?" — 기억 앞의 날짜를 보고 "어제"·"지난주"를 맞게 쓰세요)')
    WEEK = "월화수목금토일"

    def label(d):
        return f"{d.month}월 {d.day}일 {WEEK[d.weekday()]}요일"

    SETS = {
        "s1": [(1, "치과 상담 결과 정리", "사용자는 치과에서 충치 치료를 권유받았고 다음 주에 다시 가기로 했습니다.", "치과"),
               (5, "BFS 알고리즘 공부", "사용자는 BFS 알고리즘의 동작 원리와 예시를 물었고 비서가 큐를 이용한 예시를 설명했습니다.", "BFS"),
               (60, "제주도 여행 계획", "사용자는 제주도 2박 3일 여행 일정을 짰고 렌터카 예약을 고민했습니다.", "제주")],
        "s2": [(8, "이사 준비 체크리스트", "사용자는 이사 날짜를 정하고 짐 정리 순서를 비서와 정리했습니다.", "이사"),
               (21, "영어 회화 공부", "사용자는 매일 30분 영어 회화 연습 계획을 세웠습니다.", "영어"),
               (45, "자전거 구매 고민", "사용자는 출퇴근용 자전거 두 모델을 비교했습니다.", "자전거")],
    }
    for v, sysmsg, dated in (("A", SYS_A, False), ("C", SYS_C, True)):
        wrong = 0
        n = 0
        for sname, eps in SETS.items():
            for today in (D0, D0 + dt.timedelta(days=20)):
                lines = []
                for ago, title, summary, _ in eps:
                    d = D0 - dt.timedelta(days=ago)
                    lines.append(f"- ({d.month}월 {d.day}일) {title}: {summary}" if dated else f"- {title}: {summary}")
                body = (f"오늘은 {label(today)}입니다. 아래 재료로 아침 브리핑을 작성하세요.\n\n[오늘 일정]\n- 없음\n\n"
                        "[미완료 할 일]\n- 장보기\n\n[최근 대화 기억]\n" + "\n".join(lines))
                with K.create_chat_conversation(engine, tools=[], system_message=sysmsg) as conv:
                    out = text_of(conv.send_message(body)).strip()
                yesterday_kw = [kw for ago, _, _, kw in eps if (today - (D0 - dt.timedelta(days=ago))).days == 1]
                bad = [s for s in re.split(r"(?<=[.?!])\s+", out) if "어제" in s and not any(k in s for k in yesterday_kw)]
                wrong += bool(bad)
                n += 1
                q = out.count("?")
                print(f"[brief] {v} {sname} {label(today)} {'X ' if bad else 'OK'} 질문{q} | {out.splitlines()[-1][:80] if out else ''}", flush=True)
        print(f"== [brief] {v}: '어제' 오표기 {wrong}/{n}", flush=True)
    print(f"== [brief] 날짜 3줄 추가 토큰 약 {len(engine.tokenize('(10월 1일) (9월 27일) (8월 3일) '))}", flush=True)

# ---------------------------------------------------------------- part 4
if "extract" in PARTS:
    SYS_X = K.fixture("extract_system.txt")
    USER_X = K.fixture("extract_user.txt")
    ANCHOR = "예약·약속처럼 한 번 지나가는 일정은 캘린더가 맡으므로 제외합니다."
    assert ANCHOR in SYS_X
    ADD = " 알림·리마인더 요청도 처리가 끝난 일이므로 제외합니다. 비서가 말한 내용은 사용자가 그렇다고 확인한 것만 씁니다."
    SYS_XB = SYS_X.replace(ANCHOR, ANCHOR + ADD)

    def ep(*pairs):
        return "\n".join(f"{'사용자' if i % 2 == 0 else '비서'}: {t}" for i, t in enumerate(pairs))

    # (전사, 있어야 할 문자열 목록, 없어야 할 문자열 목록)
    EPISODES = [
        (ep("내 자물쇠 비밀번호 4936이야", "네, 자물쇠 비밀번호 4936 기억해 둘게요."), ["4936"], []),
        (ep("나 땅콩 알레르기 있어서 과자 고를 때 조심해야 돼", "알겠어요, 땅콩이 들어간 제품은 피하시는 게 좋겠네요."), ["땅콩"], []),
        (ep("나 매일 아침 7시에 조깅해", "꾸준히 하시는군요! 멋져요."), ["조깅"], []),
        (ep("앞으로 나를 서윤님이라고 불러줘", "네, 서윤님이라고 부를게요."), ["서윤"], []),
        (ep("나 병원에서 간호사로 일해. 야간 근무가 많아", "고생 많으시네요. 야간 근무 후엔 충분히 쉬세요."), ["간호사"], []),
        (ep("집 와이파이 비번은 kosmos77이야", "알겠어요."), ["kosmos77"], []),
        (ep("회사 주차는 항상 B2 구역에 해", "B2 구역이군요."), ["B2"], []),
        (ep("현관 비밀번호 2580으로 바꿨어", "현관 비밀번호가 2580으로 바뀌었군요."), ["2580"], []),
        (ep("3시 40분에 스트레칭하라고 알려줘", "3시 40분에 '스트레칭하기' 알림을 설정했습니다."), [], ["스트레칭"]),
        (ep("내일 아침 9시에 약 먹으라고 알려줘", "내일 오전 9시에 '약 먹기' 알림을 설정했습니다.", "고마워", "천만에요!"), [], ["약 먹"]),
        (ep("자전거 비밀번호 기억나?", "기억에 따르면 자전거 비밀번호는 4321로 변경된 적이 있습니다.", "그렇구나 고마워", "천만에요!"), [], ["4321"]),
        (ep("오늘 날씨 진짜 덥다", "맞아요, 물 자주 드세요."), [], []),
        (ep("점심 뭐 먹지", "국밥 어떠세요?", "좋다", "맛있게 드세요!"), [], []),
    ]
    LABEL = re.compile(r"^\s*[-*•]?\s*(프로필|지식)\s*[:：]\s*(.*)$")

    for v, sysmsg in (("A", SYS_X), ("B", SYS_XB)):
        recall = planted = leaks = fmt = 0
        for transcript, must, must_not in EPISODES:
            with K.create_chat_conversation(engine, tools=[], system_message=sysmsg) as conv:
                out = text_of(conv.send_message(USER_X + transcript)).strip()
            fmt += sum(1 for line in out.splitlines() if LABEL.match(line)) >= 2
            body = " ".join(LABEL.match(l).group(2) if LABEL.match(l) else l for l in out.splitlines())
            body = body.replace("없음", "")
            got = all(s in body for s in must)
            leak = [s for s in must_not if s in body]
            planted += bool(must)
            recall += bool(must) and got
            leaks += bool(leak)
            mark = "OK" if (got and not leak) else "X "
            print(f"[extract] {v} {mark} {transcript.splitlines()[0][5:25]!r} -> {out.replace(chr(10), ' / ')[:110]!r}", flush=True)
        print(f"== [extract] {v}: 회수 {recall}/{planted} 오추출 {leaks} 형식 {fmt}/{len(EPISODES)}  추가 토큰 {len(engine.tokenize(ADD)) if v == 'B' else 0}", flush=True)

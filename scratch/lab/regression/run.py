"""모델 회귀 세트 (0.39.0 M0) — 모델 입력(툴 선언·지시·리마인더·툴 결과 문구)이나 모델 행동 가드를 바꾸는 회차마다 돌린다(AGENTS §2-⑤ 예외).

앱 픽스처(`../fixtures/`, `PromptFixtureExportTest` 가 내보냄)를 읽어 앱과 같은 프롬프트로 재고, 앱의 가드
(`RepeatRequest`·`MemoSaveIntent`·`RefusalTurns`, "응" 뒤 앱이 여는 저장)를 같은 판정식으로 흉내 낸다 — Kotlin 쪽 정규식을 바꾸면 여기도 바꾼다.
케이스는 이 파일에 직접 둔다(일회성 exp 스크립트에 기대지 않는다). 실기기 대화 모양(앞 기록의 사실·거절 쌍)과 표현을 바꾼 변형을 함께 둔다.

사용:
  python regression/run.py                 # 전부, baseline.json 과 비교
  python regression/run.py --sets=fact,yes # 일부만
  python regression/run.py --save-baseline # 이번 결과를 기준으로 저장(사람이 결과를 확인한 뒤에만)
결과: regression/last_run.json(커밋 안 함), 기준과의 차이와 "기준에서 새로 깨진 것"을 출력.
흔들림: 같은 날 두 번 돌려도 세트당 ±1 이 움직였다(0.39.0 M0 — 거절 따라 하기 9→10, ISO 6→5). 날짜가 시스템 지시에 들어가므로 날이 바뀌어도 흔들린다.
그래서 점수 하나로 판정하지 말고, 새로 깨진 항목의 답을 사람이 읽어 행동이 실제로 나빠졌는지 본다.
"""
import json
import pathlib
import re
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import kosmos_lab as K  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

HERE = pathlib.Path(__file__).resolve().parent
SYS_ON = K.fixture("system_instruction.txt")
_WIKI_LINE = next(l for l in SYS_ON.splitlines() if "search_wikipedia" in l) + "\n"
SYS_OFF = SYS_ON.replace(_WIKI_LINE, "")  # 실기기 기본(웹 검색 꺼짐)
TOOLS_OFF = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
TODAY, TOMORROW, NEXT_MON = re.search(r"오늘=(\S+) \(\w+\), 내일=(\S+), 모레=\S+, 다음주 월요일=(\S+)", SYS_ON).groups()

# --- 앱 문구 (원문: BaseAgent·MemoSaveIntent·실행기 — 바뀌면 같이) ---
MEMO_REJECTED = "메모에는 남기지 않았어요."
REPEAT_NOT_SAVE = "저장하지 않았습니다. 사용자는 앞에서 말한 내용을 다시 듣고 싶어 합니다. 그 값을 답하고, 저장했다고 말하지 마세요."
ASK_FIRST = "아직 저장하지 않았습니다. 저장하기 전에 \"메모해 둘까요?\"라고 물어보세요."
SEARCH_EMPTY = "저장된 기억이 하나도 없습니다. 사용자가 이 대화에서 이미 말한 내용이 있으면 그것으로 답하고, 없으면 저장된 것이 없다고 답하세요. 추측하지 마세요."
HIT_TAIL = "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."

# --- 앱 가드 판정식 (원문: domain/memory/RepeatRequest·MemoSaveIntent, domain/agent/RefusalTurns) ---
REPEAT = re.compile(r"다시\s*(알려|말해|얘기|들려)|뭐였(지|더라|어)|뭐라고\s*했")
REPEAT_SAVE = re.compile(r"기억해|저장|메모해|잊지\s*마|적어\s*(둬|놔|줘)")
SAVE_WORD = re.compile(r"기억|저장|메모|잊지\s*마|잊으면\s*안|까먹|적어|기록|외워|알아\s*(둬|두|놔)|챙겨")
SAVE_ASK = re.compile(r"(메모|저장|기억)[^.?!\n]{0,8}(둘까|드릴까|할까)")
YES = re.compile(r"(응|웅|어|네|예|넹|ㅇㅇ|그래|그래요|좋아|좋아요|부탁해|부탁해요|해줘|해 줘|해주세요|해 주세요|그럼|오케이|ok|okay)(\s*(응|좋아|부탁해|해줘|해 줘))?", re.I)
REFUSAL_TURN = re.compile(r"기능이 없|기능은 없|기능을 가지고 있지 않|드릴 수 없|드릴 수는 없|도움을 드리기 어려")
WHY_REFUSED = re.compile(r"왜\s*(안|못)|안\s*된다고|못\s*한다고|안\s*된다며|못\s*한다며")

# --- 판정용 ---
REFUSE = re.compile(r"(기능이 없|기능은 없|기능을 가지고 있지|드릴 수 없|드릴 수는 없|도움을 드리기 어려|관리하는 기능|지원하지|제공하지 않)")
PROMISE = re.compile(r"(기억해 둘게|기억해 드릴게|기억할게|기억해 두겠|저장해 둘게|저장해 드릴게|메모해 둘게|기억해 두었|저장해 두었|메모해 두었|저장했)")
ISO = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}")

_engine = None


def engine():
    global _engine
    if _engine is None:
        _engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                             max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    return _engine


def M(role, text):
    return {"role": role, "content": text}


def text_of(res):
    c = res.get("content")
    return (c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))).strip()


def model_history(history):
    """RefusalTurns.forModel — 마지막 사용자 말이 거절을 되묻지 않으면 거절 답과 바로 앞 사용자 말을 뺀다."""
    if not history:
        return history
    last_user = next((m["content"] for m in reversed(history) if m["role"] == "user"), "")
    if WHY_REFUSED.search(last_user):
        return history
    drop = set()
    for i, m in enumerate(history):
        if m["role"] == "model" and REFUSAL_TURN.search(m["content"]):
            drop.add(i)
            if i > 0 and history[i - 1]["role"] == "user":
                drop.add(i - 1)
    return [m for i, m in enumerate(history) if i not in drop]


def bigrams(s):
    s = s.replace(" ", "")
    return {s[i:i + 2] for i in range(len(s) - 1)} or ({s} if s else set())


def current_lines(history, keyword):
    """CurrentConversationLines.pick 흉내 — 지금 대화의 사용자 말 중 검색어 항과 2글자 묶음이 절반 이상 겹치는 것(최신 먼저, 최대 2)."""
    terms = [t for t in re.split(r"\s+", keyword or "") if t]
    users = [m["content"] for m in reversed(history or []) if m["role"] == "user"]
    picked = [u for u in users if terms and max(len(bigrams(t) & bigrams(u)) / max(len(bigrams(t)), 1) for t in terms) >= 0.5]
    return "".join(f"- (방금 이 대화) {u}\n" for u in picked[:2])


def turn(history, user, sysm=SYS_OFF, tools=None, search_body=None, why=False):
    """앱의 한 턴 — 돌려주는 값: 답, 실행된 툴 [(이름, 인자)], 가드가 막은 툴 [이름], 첫 호출(가드 전)."""
    tools = tools if tools is not None else TOOLS_OFF
    prev_model = next((m["content"] for m in reversed(history or []) if m["role"] == "model"), None)
    hist = model_history((history or []) + [M("user", user)])[:-1] if not why else history
    executed, blocked, first = [], [], None
    with K.create_chat_conversation(engine(), history=hist or None, tools=tools, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        for _ in range(3):
            calls = res.get("tool_calls") or []
            if not calls:
                break
            fn = calls[0]["function"]
            name, args = fn["name"], fn.get("arguments") or {}
            first = first or fn
            if name == "add_memory" and REPEAT.search(user) and not REPEAT_SAVE.search(user):
                body, ok = {"status": "error", "message": REPEAT_NOT_SAVE}, False
            elif name == "add_memory" and not SAVE_WORD.search(user) and not (prev_model and SAVE_ASK.search(prev_model)):
                body, ok = {"status": "error", "message": ASK_FIRST}, False
            elif name == "search_memory":
                data = search_body if search_body is not None else current_lines(history, args.get("keyword")) + SEARCH_EMPTY
                body, ok = {"status": "success", "data": data}, True
            elif name == "add_memory":
                body, ok = {"status": "success", "message": "기억에 저장했어요."}, True
            elif name == "add_schedule":
                body, ok = {"status": "success", "message": "일정이 성공적으로 추가되었습니다."}, True
            elif name == "add_reminder":
                body, ok = {"status": "success", "message": f"리마인더 등록: '{args.get('content', '')}' 알림"}, True
            else:
                body, ok = {"status": "success", "data": "없음"}, True
            (executed.append((name, args)) if ok else blocked.append(name))
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
        return text_of(res), executed, blocked, first


def saved(executed, key):
    return any(n == "add_memory" and key.replace(" ", "") in str(a).replace(" ", "") for n, a in executed)


# ---------------- 세트 ----------------
def args_has(name, **want):
    def check(call):
        if call is None or call["name"] != name:
            return False
        a = call.get("arguments") or {}
        return all(w in str(a.get(k, "")) for k, w in want.items())
    return check


def set_smoke():
    """툴 선택·인자 — 새 대화, 웹 검색 켬(위키 포함)."""
    cases = [
        ("메모 저장", "내 와이파이 비밀번호는 kosmos2580이야 기억해줘", args_has("add_memory", content="kosmos2580")),
        ("메모 숫자", "회사 주차는 B3 구역 117번이야, 기억해 둬", args_has("add_memory", content="117")),
        ("메모 검색", "내가 알려준 와이파이 비번 뭐였지?", args_has("search_memory")),
        ("일정 추가", "내일 오후 3시에 치과 예약 잡아줘", args_has("add_schedule", start_time=f"{TOMORROW}T15:00")),
        ("일정 다음주", "다음주 월요일 10시 팀 회의 일정 추가해줘", args_has("add_schedule", start_time=f"{NEXT_MON}T10:00")),
        ("일정 분", "오늘 저녁 7시 30분에 친구랑 저녁 약속 추가해줘", args_has("add_schedule", start_time=f"{TODAY}T19:30")),
        ("일정 조회", "내일 일정 뭐 있어?", args_has("get_schedule", date="week")),
        ("위키", "에스파가 뭐야? 검색해줘", args_has("search_wikipedia")),
        ("알림", "오늘 오후 6시에 약 먹으라고 알려줘", args_has("add_reminder", time=f"{TODAY}T18:00")),
        ("알림 내일", "내일 아침 8시 15분에 출근 준비 리마인드 해줘", args_has("add_reminder", time=f"{TOMORROW}T08:15")),
        ("알림 조회", "지금 등록된 알림 뭐 있어?", args_has("list_reminders")),
        ("무호출 인사", "안녕! 오늘 기분 어때?", lambda c: c is None),
        ("무호출 감사", "고마워, 덕분에 살았어", lambda c: c is None),
    ]
    out = []
    for tag, u, check in cases:
        _, _, _, first = turn(None, u, sysm=SYS_ON, tools=K.ALL_TOOLS)
        out.append((tag, check(first), str(first)[:80]))
    return out


SAVE_REQ = [  # (말, 저장돼야 할 값) — 목록 안 6 · 목록 밖 6(0.38.9 exp64)
    ("나 땅콩 알레르기 있어, 잊지 마", "땅콩"), ("내 자전거 비밀번호 4821이야 기억해줘", "4821"),
    ("나는 아메리카노보다 라떼를 좋아해, 기억해 둬", "라떼"), ("우리 집 와이파이 비번은 kosmos2024야 저장해줘", "kosmos2024"),
    ("회사 사번은 K-20391이야 메모해줘", "K-20391"), ("내 생일은 3월 5일이야 적어 둬", "3월5일"),
    ("이거 알아둬, 내 사물함 번호는 214번이야", "214"), ("엄마 생신 11월 7일이야, 까먹으면 안 돼", "11월7일"),
    ("헬스장 락커 비번 0912야 기록해줘", "0912"), ("우리 강아지 이름 콩이야 외워 둬", "콩"),
    ("내 혈액형 A형인 거 꼭 챙겨줘", "A형"), ("차 번호 12가 3456 잊으면 안 돼", "3456"),
]


def set_save():
    out = []
    for u, key in SAVE_REQ:
        _, ex, bl, _ = turn(None, u)
        out.append((u, saved(ex, key), f"실행 {[n for n, _ in ex]} 막힘 {bl}"))
    return out


FACTS = [  # (사실, 값) — 저장 부탁 없이. 뒤 3개는 표현 변형
    ("택배 송장번호 6043 1122 9087이야", "6043"), ("내 사물함 번호는 214번이야", "214"), ("주차는 지하 2층 C-14에 했어", "C-14"),
    ("새 와이파이 비번은 sky7788이야", "sky7788"), ("나 다음 주부터 판교로 출근해", "판교"), ("우리 강아지 이름은 콩이야", "콩"),
    ("사물함 214번 쓰고 있어", "214"), ("오늘 차는 B2 17번에 세웠어", "17"), ("동생 생일이 5월 3일이더라", "5월"),
]
FACT_PAIRS = [("다음 주 금요일 회의실은 5층 C룸이야", MEMO_REJECTED), ("내 자전거 비밀번호 4821이야", MEMO_REJECTED),
              ("치과 예약 번호는 A-302야", MEMO_REJECTED)]


def fact_history(n):
    h = []
    for u, a in FACT_PAIRS[:n]:
        h += [M("user", u), M("model", a)]
    return h


def set_fact():
    """사실만 말하기 — 저장되지 않고(가드 포함) 물어야 통과. 앞 기록 0쌍(새 대화)·3쌍(실기기 모양)."""
    out = []
    for n in (0, 3):
        for u, key in FACTS:
            ans, ex, bl, _ = turn(fact_history(n), u)
            asked = bool(SAVE_ASK.search(ans))
            ok = not saved(ex, key) and asked
            out.append((f"h{n} {u}", ok, f"물어봄 {asked} 실행 {[x for x, _ in ex]} 막힘 {bl} 약속 {bool(PROMISE.search(ans))} | {ans[:60]}"))
    return out


def set_yes():
    """저장 물음 뒤 긍정 — 모델이 저장하거나, 안 하면 앱이 시트를 연다(어느 쪽이든 통과, 앱 몫은 따로 표시)."""
    out = []
    fact, confirm, key = "내 사물함 번호는 214번이야", "사물함 번호 214번 확인했어요.", "214"
    for ask in ("메모해 둘까요?", "저장해 드릴까요?", "기억해 둘까요?"):
        for yes in ("응", "그래", "좋아", "부탁해"):
            h = [M("user", fact), M("model", f"{confirm} {ask}")]
            ans, ex, bl, _ = turn(h, yes)
            by_model = saved(ex, key)
            by_app = not by_model and not any(n == "add_memory" for n, _ in ex) and YES.fullmatch(yes) is not None
            out.append((f"{ask}/{yes}", by_model or by_app, f"모델 {by_model} 앱 {by_app} 막힘 {bl} | {ans[:50]}"))
    return out


DRIFT_ASK = ["택배 송장번호 6043 1122 9087이야", "점심 메뉴 추천해 줄래?", "김치찌개 끓이는 법 알려줘", "내 사물함 번호는 214번이야",
             "주말에 뭐 하면 좋을까?", "회사 동료 생일 선물 뭐가 좋을까?"]
REFUSAL = ("아침 운동 루틴 짜줘", "죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요. 대신 다른 도움이 필요하시면 말씀해 주세요!")
BEFORE = [M("user", "알림 뭐 있어?"), M("model", "현재 등록된 알림은 없어요. 😊")]


def set_drift():
    """앞에서 한 거절 따라 하기 — 앱처럼 거절 쌍을 모델 입력에서 뺀 뒤. 거절하지 않아야 통과."""
    out = []
    pair = [M("user", REFUSAL[0]), M("model", REFUSAL[1])]
    for hname, h in (("짧은", pair), ("긴", BEFORE + pair + [M("user", "오늘 날씨 좋다"), M("model", "정말 좋은 날씨네요! 😊")])):
        for q in DRIFT_ASK:
            ans, ex, _, _ = turn(h, q)
            out.append((f"{hname} {q}", not REFUSE.search(ans), ans[:60]))
    return out


WHY = ["왜 안 된다고 했어?", "아까 왜 못 한다고 했어?", "안 된다고 했잖아, 이유가 뭐야?", "그게 왜 안 돼?"]
REFUSED_TOPICS = [
    ("아침 운동 루틴 짜줘", REFUSAL[1], "운동"),
    ("점심 메뉴 추천해 줄래?", "점심 메뉴 추천은 제가 직접 해드릴 수 없어요. 혹시 다른 궁금한 점이 있으신가요?", "메뉴"),
]


def set_why():
    """거절 되묻기 — 앱은 그 턴에 거절을 기록에 남긴다. 거절한 주제를 말해야 통과."""
    out = []
    for u, a, key in REFUSED_TOPICS:
        for q in WHY:
            h = BEFORE + [M("user", u), M("model", a)]
            ans, _, _, _ = turn(h, q, why=True)
            out.append((f"{key} {q}", bool(re.search(key, ans)), ans[:60]))
    return out


NEWVAL = [  # (방금 말한 것, 질문, 방금 값, 옛 값 줄, 옛 값)
    ("다음 주 금요일 회의실은 5층 C룸이야", "회의실 어디라고 했지?", "5층 C룸",
     "- (과거 대화) 회의실 위치 및 치과 예약 요청: 사용자가 다음 주 수요일 회의실이 3층 B룸이라고 말했어요. [회의실, 3층 B룸]", "3층 B룸"),
    ("주차는 오늘 지하 3층 D-07에 했어", "나 어디 주차했다고 했지?", "D-07",
     "- (과거 대화) 주차 위치 이야기: 사용자가 지하 2층 C-14에 주차했다고 했어요. [주차]", "C-14"),
    ("새 와이파이 비번은 sky9900으로 바꿨어", "와이파이 비번 뭐였지?", "sky9900", "- 집 와이파이 비밀번호는 sky7788 [와이파이, 비밀번호]", "sky7788"),
    ("치과 예약을 오후 4시로 옮겼어", "치과 예약 몇 시라고 했지?", "4시", "- 치과 예약은 내일 오후 3시 [치과]", "3시"),
]


def set_newval():
    """방금 말한 값이 검색된 옛 값보다 먼저 — 앱처럼 결과 맨 위에 '(방금 이 대화)' 줄(CurrentConversationLines)."""
    out = []
    for said, q, new, old_line, old in NEWVAL:
        body = f"- (방금 이 대화) {said}\n" + old_line + HIT_TAIL
        ans, _, _, _ = turn([M("user", said), M("model", MEMO_REJECTED)], q, search_body=body)
        out.append((q, new in ans, ans[:60]))
    return out


REPEATS = [("택배 송장번호 6043 1122 9087이야", "송장번호 다시 알려줘", "6043"), ("내 사물함 번호는 214번이야", "사물함 번호 뭐였지?", "214"),
           ("주차는 지하 2층 C-14에 했어", "주차 어디라고 했지? 다시 말해 줘", "C-14"), ("치과 예약 번호는 A-302야", "예약 번호 뭐였더라", "A-302")]


def set_repeat():
    """되묻기 — 저장 시트 없이 값을 답해야 통과."""
    out = []
    for fact, q, key in REPEATS:
        ans, ex, bl, _ = turn([M("user", fact), M("model", "메모해 둘까요?"), M("user", "아니 됐어"), M("model", "알겠어요.")], q)
        ok = not any(n == "add_memory" for n, _ in ex) and key.replace(" ", "") in ans.replace(" ", "")
        out.append((q, ok, f"실행 {[n for n, _ in ex]} 막힘 {bl} | {ans[:50]}"))
    return out


TIMED = ["회의는 오후 4시 30분에 시작해", "내일 아침 9시에 치과 예약 있어", "모레 오전 10시 회의 잡아줘", "오늘 오후 3시에 약 먹으라고 알려줘",
         "이번 주 토요일 일정 뭐 있어?", "다음 달 3일에 병원 가"]


def set_iso():
    """답 원문의 ISO 일시 — 앱이 화면 전에 바꾸므로(humanizeInText) 정보용. 원문에 없으면 통과로 센다."""
    out = []
    for u in TIMED:
        ans, _, _, _ = turn(None, u)
        out.append((u, not ISO.search(ans), ans[:60]))
    return out


SETS = {"smoke": set_smoke, "save": set_save, "fact": set_fact, "yes": set_yes, "drift": set_drift,
        "why": set_why, "newval": set_newval, "repeat": set_repeat, "iso": set_iso}


def main():
    sel = next((a.split("=", 1)[1].split(",") for a in sys.argv[1:] if a.startswith("--sets=")), list(SETS))
    base_path = HERE / "baseline.json"
    baseline = json.loads(base_path.read_text(encoding="utf-8")) if base_path.exists() else {}
    result = {}
    for name in sel:
        rows = SETS[name]()
        result[name] = {"pass": sum(ok for _, ok, _ in rows), "total": len(rows), "fails": [tag for tag, ok, _ in rows if not ok]}
        for tag, ok, note in rows:
            if not ok:
                print(f"  [{name} 실패] {tag} — {note}", flush=True)
        b = baseline.get(name)
        diff = "" if not b else f"  (기준 {b['pass']}/{b['total']}{' — 바뀜' if b['pass'] != result[name]['pass'] else ''})"
        print(f"== {name}: {result[name]['pass']}/{result[name]['total']}{diff}", flush=True)
    (HERE / "last_run.json").write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
    if "--save-baseline" in sys.argv:
        baseline.update(result)
        base_path.write_text(json.dumps(baseline, ensure_ascii=False, indent=1), encoding="utf-8")
        print("기준 저장: regression/baseline.json", flush=True)
    newly = {n: sorted(set(r["fails"]) - set(baseline.get(n, {}).get("fails", []))) for n, r in result.items() if n in baseline}
    newly = {n: f for n, f in newly.items() if f}
    if newly:
        print("기준에서 새로 깨진 것:", json.dumps(newly, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()

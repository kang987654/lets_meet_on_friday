"""exp41 — 시스템 지시·툴 선언 다이어트 후보 실측 (0.27.1 이후 예산 여유 7토큰).

후보마다 (a) 절감 토큰(engine.tokenize 차이 — 응답 길이 변동이 섞이지 않는다)과
(b) 스모크 17건(툴 선택 12 + 무호출 2 + 날짜 끌림 2 + 요일 날짜 1, 인자 보존·날짜 검사 포함)을 잰다.
게이트: 기준선과 같은 통과 수 이상, 기준선이 통과한 항목을 새로 깨지 않을 것.
"""
import sys
import types
import litert_lm as llm
import kosmos_lab as K
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

BASE = K.fixture("system_instruction.txt")
assert "Today: 2026-10-01 (Thursday)" in BASE, "픽스처 날짜가 바뀌었다 — 기대 날짜를 갱신할 것"
D0, D1, SAT, MON = "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-05"


def cut(s, old, new=""):
    assert old in s, old[:50]
    return s.replace(old, new)


def with_doc(fn, old, new=""):
    assert old in fn.__doc__, old
    f2 = types.FunctionType(fn.__code__, fn.__globals__, fn.__name__, fn.__defaults__, fn.__closure__)
    f2.__doc__ = fn.__doc__.replace(old, new)
    f2.__annotations__ = dict(fn.__annotations__)
    return f2


def tools_with(**repl):
    return [repl.get(f.__name__, f) for f in K.ALL_TOOLS]


C5_OLD = " 사용자가 말한 숫자와 고유명사는 절대 바꾸지 말고 그대로 적는다."
C6_OLD = " 추측해서 답하지 말고 이 도구로 확인한다."
VARIANTS = {
    "A_base": (BASE, K.ALL_TOOLS),
    "C1_date": (cut(BASE, "[System Data] Today: 2026-10-01 (Thursday)\n[System Data] 오늘=2026-10-01,",
                    "[System Data] 오늘=2026-10-01 (Thursday),"), K.ALL_TOOLS),
    "C2_must": (cut(BASE, "For EVERY user request, first decide if one of your tools applies. If it does, you MUST call the tool. "
                          "Do NOT merely promise or pretend — promising without calling is a failure.\n"), K.ALL_TOOLS),
    "C3_label": (BASE.replace(" — Korean triggers: ", ": "), K.ALL_TOOLS),
    "C4_plain": (cut(BASE, "\nIf you do not need a tool, simply provide your final response in plain text."), K.ALL_TOOLS),
    "C5_memdoc": (BASE, tools_with(add_memory=with_doc(K.add_memory, C5_OLD))),
    "C6_srchdoc": (BASE, tools_with(search_memory=with_doc(K.search_memory, C6_OLD))),
}
assert VARIANTS["C3_label"][0].count(": you MUST") >= 5

# exp41b — 단독 통과한 C1~C5 를 합친 조합(C6 은 단독에서 요일 날짜를 깨 기각).
if len(sys.argv) > 1 and sys.argv[1] == "combo":
    keep_must = VARIANTS["C1_date"][0].replace(" — Korean triggers: ", ": ").replace(
        "\nIf you do not need a tool, simply provide your final response in plain text.", "")
    combo = cut(keep_must, "For EVERY user request, first decide if one of your tools applies. If it does, you MUST call the tool. "
                           "Do NOT merely promise or pretend — promising without calling is a failure.\n")
    mem = tools_with(add_memory=with_doc(K.add_memory, C5_OLD))
    VARIANTS = {"A_base": (BASE, K.ALL_TOOLS), "ALL5": (combo, mem), "ALL4_keepMust": (keep_must, mem)}

TOMORROW_HIST = [
    llm.Message.user("내일 오후 3시에 치과 예약 일정 추가해줘"),
    llm.Message.model(llm.Contents([llm.Content.Text("네, 알겠습니다. 내일(2026년 10월 2일) 오후 3시에 '치과 예약' 일정을 추가했습니다.")])),
]
# (발화, 기대 툴 또는 None, 인자 검사 함수 또는 None, 히스토리)
def has(key, sub):
    return lambda a: sub in str(a.get(key, ""))
CASES = [
    ("내 자물쇠 비밀번호는 4936이야, 기억해줘", "add_memory", has("content", "4936"), None),
    ("와이파이 비번 kosmos123 저장해줘", "add_memory", has("content", "kosmos123"), None),
    ("나 땅콩 알레르기 있어, 잊지 마", "add_memory", None, None),
    ("내 자물쇠 비밀번호 뭐였지?", "search_memory", None, None),
    ("내가 알려준 와이파이 비번 기억나?", "search_memory", None, None),
    ("내일 3시에 치과 예약해줘", "add_schedule", has("start_time", D1), None),
    ("3시에 회의 일정 잡아줘", "add_schedule", has("start_time", D0), None),
    ("다음주 월요일 오전 10시에 팀 회의 일정 추가해줘", "add_schedule", has("start_time", MON + "T10"), None),
    ("이번주 토요일 오후 2시에 결혼식 일정 추가해줘", "add_schedule", has("start_time", SAT + "T14"), None),
    ("오늘 일정 뭐 있어?", "get_schedule", None, None),
    ("에스파가 뭐야? 검색해줘", "search_wikipedia", None, None),
    ("오후 3시에 약 먹으라고 알려줘", "add_reminder", has("time", D0 + "T15"), None),
    ("내일 아침 9시에 쓰레기 버리라고 리마인드 해줘", "add_reminder", has("time", D1 + "T09"), None),
    ("안녕! 오늘 기분 좋다", None, None, None),
    ("고마워, 덕분에 살았어", None, None, None),
    ("오전 7시 4분에 물 마시라고 알려줘", "add_reminder", has("time", D0), TOMORROW_HIST),
    ("저녁 8시에 운동하라고 알려줘", "add_reminder", has("time", D0), TOMORROW_HIST),
]

# 긴 히스토리: 툴 없는 잡담 6왕복 뒤 — 지침이 멀어지는 조건(턴 리마인더 도입 사유, 2026-08-12)
CHAT = [("요즘 날씨 너무 덥다", "맞아요, 요즘 정말 덥죠. 물 자주 드시고 시원하게 지내세요."),
        ("주말에 뭐 할지 고민이야", "날씨가 좋으면 가까운 공원 산책은 어떠세요? 실내라면 전시회도 좋아요."),
        ("요즘 읽을 만한 책 추천해줘", "가볍게 읽기 좋은 에세이나 단편 소설을 추천드려요."),
        ("커피 하루에 몇 잔이 적당해?", "보통 성인은 하루 3~4잔 이내가 적당하다고 알려져 있어요."),
        ("오늘 점심 뭐 먹지", "따뜻한 국밥이나 시원한 냉면은 어떠세요?"),
        ("운동 시작하려는데 뭐부터 해?", "걷기나 가벼운 스트레칭부터 시작해 보세요.")]
LONG = []
for uu, mm in CHAT:
    LONG += [llm.Message.user(uu), llm.Message.model(llm.Contents([llm.Content.Text(mm)]))]
if len(sys.argv) > 1 and sys.argv[1] == "combo":
    CASES += [
        ("에스파가 뭐야? 검색해줘", "search_wikipedia", None, LONG),
        ("내 자물쇠 비밀번호 뭐였지?", "search_memory", None, LONG),
        ("자전거 비밀번호 5521이야 기억해줘", "add_memory", has("content", "5521"), LONG),
        ("내일 3시에 치과 예약해줘", "add_schedule", has("start_time", D1), LONG),
        ("오후 3시에 약 먹으라고 알려줘", "add_reminder", has("time", D0 + "T15"), LONG),
        ("그렇구나, 고마워", None, None, LONG),
    ]

engine = K.create_engine()
base_tok = len(engine.tokenize(BASE))
base_doc_tok = {n: len(engine.tokenize(getattr(K, n).__doc__)) for n in ("add_memory", "search_memory")}
results = {}
for name, (sysmsg, tools) in VARIANTS.items():
    saved = base_tok - len(engine.tokenize(sysmsg))
    for f in tools:
        if f.__name__ in base_doc_tok:
            saved += base_doc_tok[f.__name__] - len(engine.tokenize(f.__doc__))
    oks = []
    for u, tool, check, hist in CASES:
        with K.create_chat_conversation(engine, history=hist, tools=tools, system_message=sysmsg) as conv:
            res = conv.send_message(K.with_turn_reminder(u))
        calls = res.get("tool_calls") or []
        got = calls[0]["function"]["name"] if calls else None
        args = calls[0]["function"].get("arguments", {}) if calls else {}
        ok = got == tool and (check is None or check(args))
        oks.append(ok)
        print(f"{name:10} {'OK' if ok else 'X '} {u[:18]!r} -> {got} {str(args)[:70] if not ok else ''}", flush=True)
    results[name] = oks
    print(f"== {name}: 스모크 {sum(oks)}/{len(CASES)}  절감 {saved}토큰", flush=True)

base = results["A_base"]
for name, oks in results.items():
    broke = [CASES[i][0][:14] for i, (b, o) in enumerate(zip(base, oks)) if b and not o]
    print(f"## {name}: {sum(oks)}/{len(CASES)}  새로 깬 항목 {broke or '없음'}", flush=True)

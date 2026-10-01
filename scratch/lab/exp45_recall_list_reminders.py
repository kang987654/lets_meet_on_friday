"""exp45 — 0.32.0 M0: 약속 문장 제거(exp43 통과) 위에 과거 대화 회상 트리거 · list_reminders 툴을 얹을 수 있는가.

## 예산
여유 74토큰(0.27.1 실측 1,226 + 프로필 100 / 예약 1,400). 약속 문장(C2) 제거로 약 +39.
각 변형의 추가 비용을 `engine.tokenize` 차이(시스템 지시 + 툴 선언 docstring)로 잰다.

## 변형
- A 현행(앱 그대로)
- B C2 제거
- C B + 회상 트리거 예시("전에", "그때", "지난번", "있었지")
- D C + list_reminders 선언 + 트리거 줄

## 게이트
B·C·D 스모크(exp41 23건) 무손상, C 의 회상 호출 ≥ A + 2 이고 저장 의도가 search 로 새지 않음, D 의 목록 조회 ≥ 5/6 이고
add_reminder 혼동 0, 최종 추가 비용 ≤ 예산.
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
C2 = ("For EVERY user request, first decide if one of your tools applies. If it does, you MUST call the tool. "
      "Do NOT merely promise or pretend — promising without calling is a failure.\n")
RECALL_OLD = '"뭐였지", "뭐라고 했지", "내가 알려준", "기억나", "저장한 거": you MUST call `search_memory`'
RECALL_NEW = '"뭐였지", "뭐라고 했지", "내가 알려준", "기억나", "저장한 거", "전에", "그때", "지난번", "있었지": you MUST call `search_memory`'
REMINDER_RULE = '- The user asks to be reminded at a specific time: "3시에 알려줘", "리마인드": you MUST call `add_reminder`.\n'
LIST_RULE = '- The user asks which reminders are set: "알림 뭐 있어", "리마인더 목록": you MUST call `list_reminders`.\n'
for needle in (C2, RECALL_OLD, REMINDER_RULE):
    assert needle in BASE, needle[:40]


def list_reminders() -> dict:
    """등록해 둔 알림 목록을 시각순으로 조회한다."""
    raise NotImplementedError


B = BASE.replace(C2, "")
C = B.replace(RECALL_OLD, RECALL_NEW)
D = C.replace(REMINDER_RULE, REMINDER_RULE + LIST_RULE)
TOOLS_D = K.ALL_TOOLS + [list_reminders]
E = BASE.replace(REMINDER_RULE, REMINDER_RULE + LIST_RULE)  # exp45b: 현행(약속 문장 유지) + list_reminders 만
VARIANTS = {"A": (BASE, K.ALL_TOOLS), "B": (B, K.ALL_TOOLS), "C": (C, K.ALL_TOOLS), "D": (D, TOOLS_D), "E": (E, TOOLS_D)}

engine = K.create_engine()
base_cost = len(engine.tokenize(BASE))
for name, (system, tools) in VARIANTS.items():
    extra = len(engine.tokenize(system)) - base_cost
    if list_reminders in tools:
        extra += len(engine.tokenize("list_reminders " + list_reminders.__doc__))
    print(f"비용 {name}: {extra:+d} 토큰(tokenize, 툴 선언 템플릿 오버헤드 제외 — 아래 token_count 로 보정)", flush=True)


def count(system, tools):
    with K.create_chat_conversation(engine, system_message=system, tools=tools) as conv:
        conv.send_message("안녕")
        return conv.token_count


a_count = count(BASE, K.ALL_TOOLS)
for name, (system, tools) in VARIANTS.items():
    print(f"token_count {name}: {count(system, tools) - a_count:+d} (응답 길이 변동 포함)", flush=True)


def msg(role, text):
    return llm.Message.user(text) if role == "u" else llm.Message.model(llm.Contents([llm.Content.Text(text)]))


CHAT = [("요즘 날씨 너무 덥다", "맞아요, 요즘 정말 덥죠."), ("주말에 뭐 할지 고민이야", "공원 산책은 어떠세요?"),
        ("커피 하루에 몇 잔이 적당해?", "보통 3~4잔 이내가 적당해요."), ("오늘 점심 뭐 먹지", "국밥은 어떠세요?")]
LONG = [m for u, a in CHAT for m in (msg("u", u), msg("m", a))]

RECALL = [
    ("네가 지어내서 답한 적 있었지?", "search_memory"),
    ("지난번에 내가 말한 식당 이름이 뭐였더라", "search_memory"),
    ("그때 얘기한 여행 계획 기억나?", "search_memory"),
    ("전에 알려준 와이파이 비번 알려줘", "search_memory"),
    ("저번에 추천해 준 책 뭐였지", "search_memory"),
    ("예전에 내가 싫어한다고 한 음식 있었잖아", "search_memory"),
    # 저장 의도 — 회상 단어가 섞여도 add_memory 여야 한다
    ("전에 말했는데 나 땅콩 알레르기 있어, 기억해 둬", "add_memory"),
    ("지난번이랑 바뀌었어, 새 와이파이 비번 abc123 저장해줘", "add_memory"),
    ("엄마 생신 5월 3일이야 기억해줘", "add_memory"),
    ("그때 말한 거 말고 새로 메모해줘: 차 번호 12가3456", "add_memory"),
]
LIST = [
    ("내가 설정한 알림 뭐 있어?", "list_reminders"), ("리마인더 목록 보여줘", "list_reminders"),
    ("알림 몇 개 걸려 있어?", "list_reminders"), ("내일 알림 잡힌 거 있어?", "list_reminders"),
    ("등록된 리마인더 알려줘", "list_reminders"), ("오늘 무슨 알림 남았어?", "list_reminders"),
]
# exp41 스모크(23) — 날짜는 픽스처(Today)에 맞춘다
TODAY = BASE.split("[System Data] 오늘=")[1][:10]
import datetime as _dt
_d0 = _dt.date.fromisoformat(TODAY)
D0, D1 = TODAY, str(_d0 + _dt.timedelta(days=1))
SMOKE = [
    ("내 자물쇠 비밀번호는 4936이야, 기억해줘", "add_memory"), ("와이파이 비번 kosmos123 저장해줘", "add_memory"),
    ("나 땅콩 알레르기 있어, 잊지 마", "add_memory"), ("내 자물쇠 비밀번호 뭐였지?", "search_memory"),
    ("내가 알려준 와이파이 비번 기억나?", "search_memory"), ("내일 3시에 치과 예약해줘", "add_schedule"),
    ("3시에 회의 일정 잡아줘", "add_schedule"), ("다음주 월요일 오전 10시에 팀 회의 일정 추가해줘", "add_schedule"),
    ("이번주 토요일 오후 2시에 결혼식 일정 추가해줘", "add_schedule"), ("오늘 일정 뭐 있어?", "get_schedule"),
    ("에스파가 뭐야? 검색해줘", "search_wikipedia"), ("오후 3시에 약 먹으라고 알려줘", "add_reminder"),
    ("내일 아침 9시에 쓰레기 버리라고 리마인드 해줘", "add_reminder"), ("안녕! 오늘 기분 좋다", None),
    ("고마워, 덕분에 살았어", None),
]


def run(name, system, tools, cases, hist):
    ok = 0
    for u, tool in cases:
        with K.create_chat_conversation(engine, history=hist, tools=tools, system_message=system) as conv:
            res = conv.send_message(K.with_turn_reminder(u))
        calls = res.get("tool_calls") or []
        got = calls[0]["function"]["name"] if calls else None
        ok += got == tool
        if got != tool:
            print(f"  {name} X {u[:22]!r} -> {got} (기대 {tool})", flush=True)
    return ok


plan = {"A": ["recall"], "B": ["smoke", "recall"], "C": ["smoke", "recall"], "D": ["smoke", "recall", "list"]}
if len(sys.argv) > 1 and sys.argv[1] == "E":
    plan = {"E": ["smoke", "recall", "list"]}  # exp45b — B 위가 아니라 현행 위에 얹은 ListReminders
for name, parts in plan.items():
    system, tools = VARIANTS[name]
    out = []
    if "smoke" in parts:
        out.append(f"스모크 {run(name, system, tools, SMOKE, None) + run(name, system, tools, SMOKE[:6], LONG)}/{len(SMOKE) + 6}")
    if "recall" in parts:
        out.append(f"회상·저장 {run(name, system, tools, RECALL, None) + run(name, system, tools, RECALL, LONG)}/{2 * len(RECALL)}")
    if "list" in parts:
        out.append(f"목록 {run(name, system, tools, LIST, None)}/{len(LIST)}")
    print(f"== {name}: " + "  ".join(out), flush=True)
print("끝")

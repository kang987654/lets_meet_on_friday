"""exp46b — 0.35.0 M0 후속: 첨부 문서 질문이 툴로 새는 결함의 대책 비교.

## 발견 (exp46, 2026-10-02)
첨부 표에 대한 질문 20건 중 15건이 문서를 읽지 않고 `search_memory`·`get_schedule`·`search_wikipedia` 를 불렀다(형식 M).
턴 리마인더("For THIS request … you MUST call the tool")와 트리거 규칙("~가 뭐야?", "몇 시" 류)이 문서 질문을 툴 질문으로 끌어간다.
현행 앱에도 있는 결함이다(웹 검색은 기본 꺼짐이라 위키는 빠지지만 search_memory·get_schedule 은 늘 있다).

## 조건 (모두 앱 기본 = 웹 검색 꺼짐: 툴 5개, 시스템 지시에서 위키 줄 제거)
- A 현행: 툴 5개 + 표준 턴 리마인더
- B 문서 턴은 툴 없음: 선언 0 + 시스템 지시의 툴 규칙을 "You have no tools available in this turn." 으로(PromptAssembler 빈 목록 분기)
  + 리마인더 없음 — 코드만 바꾸는 안. 단 문서 턴과 다음 턴에서 시스템 지시가 바뀌어 대화를 다시 프리필한다
- C 문서 턴 리마인더(엄격): 툴·시스템 지시는 그대로, 턴 리마인더만 DOC_STRICT 로 교체 — 프리픽스 불변
- D 문서 턴 리마인더(행동 예외): DOC_ACTION — 문서에서 답하되, 저장·일정·알림을 **명시적으로** 요청하면 툴

## 채점
표 질문 20(형식 M, exp46 과 같은 표·질문·정답 규칙) + 행동 요청 3(문서를 보고 일정 추가·기억·알림 → 해당 툴) + 문단 스모크 3(눈으로).
리마인더 비용은 tokenize 차이로 함께 잰다(문서 턴 입력만 늘어난다 — 시스템 지시·MEASURED_OVERHEAD 무관).
"""
import sys

import litert_lm as llm
import kosmos_lab as K
from exp46_attached_table import TABLES, render, questions, correct, CAP

PARA = ("회의록 2026-10-02\n참석: 김민수, 이서연, 박지훈\n안건 1. 4분기 예산 — 마케팅 예산을 10% 늘리기로 했다.\n"
        "안건 2. 신규 채용 — 개발자 2명을 11월까지 뽑는다.\n다음 회의는 10월 16일 오후 2시.")

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

FULL = K.fixture("system_instruction.txt")
WIKI_LINE = next(l for l in FULL.splitlines() if "search_wikipedia" in l) + "\n"
SYSTEM_APP = FULL.replace(WIKI_LINE, "")
TOOLS_APP = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]

# PromptAssembler.buildFormatBlock 빈 목록 분기 — 헤더 다음 줄부터 "Resolve relative dates" 직전까지를 한 줄로 바꾼다
lines = SYSTEM_APP.splitlines(keepends=True)
head = next(i for i, l in enumerate(lines) if l.startswith("[Tool Usage Guidelines]"))
dates = next(i for i, l in enumerate(lines) if l.startswith("Resolve relative dates"))
SYSTEM_NOTOOLS = "".join(lines[:head + 1] + ["You have no tools available in this turn.\n"] + lines[dates:])

STANDARD = K.fixture("turn_reminder.txt")
DOC_STRICT = "[Attached Document] The user attached a document above. Answer from that document only. Do not call any tool."
DOC_ACTION = ("[Attached Document] The user attached a document above. Answer questions about it from the document itself, "
              "not with tools. Call a tool only if the user explicitly asks to save, schedule, or remind something.")

ACTIONS = [
    ("일정.xlsx", "여기 10월 3일 저녁 약속을 내 일정에 추가해줘", "add_schedule"),
    ("연락처.xlsx", "박민준 내선 번호 기억해줘", "add_memory"),
    ("일정.xlsx", "10월 2일 고객 미팅 30분 전에 알려줘", "add_reminder"),
]

CONDITIONS = {
    "A": (SYSTEM_APP, TOOLS_APP, STANDARD),
    "B": (SYSTEM_NOTOOLS, [], None),
    "C": (SYSTEM_APP, TOOLS_APP, DOC_STRICT),
    "D": (SYSTEM_APP, TOOLS_APP, DOC_ACTION),
}

engine = K.create_engine()
base = len(engine.tokenize(STANDARD))
for name, text in (("DOC_STRICT", DOC_STRICT), ("DOC_ACTION", DOC_ACTION)):
    print(f"비용 {name}: 리마인더 {len(engine.tokenize(text))} 토큰(표준 {base}) → 문서 턴 입력 {len(engine.tokenize(text)) - base:+d}", flush=True)


def doc_block(name, body):
    return f'[Attached Document]\n"""\n첨부된 문서 내용({name}):\n{body}\n"""'


def ask(system, tools, reminder, question, doc):
    history = [llm.Message.user(question), llm.Message.user(doc)]
    turn = f"{reminder}\n\n{question}" if reminder else question
    with K.create_chat_conversation(engine, history=history, tools=tools, system_message=system) as conv:
        res = conv.send_message(turn)
    calls = res.get("tool_calls") or []
    content = res.get("content")
    text = content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))
    return (calls[0]["function"]["name"] if calls else None), text


which = sys.argv[1:] or list(CONDITIONS)
for cond in which:
    system, tools, reminder = CONDITIONS[cond]
    ok = leaks = 0
    for name, (header, rows) in TABLES.items():
        body, used = render("M", header, rows)
        for q, expected in questions(name, used):
            tool, text = ask(system, tools, reminder, q, doc_block(name, body))
            hit = tool is None and correct(text, expected)
            ok += hit
            leaks += tool is not None
            if not hit:
                print(f"  {cond} X {name} {q!r} 기대 {expected!r} -> {('툴 ' + tool) if tool else text.replace(chr(10), ' ')[:70]}", flush=True)
    act = 0
    for name, q, expected_tool in ACTIONS:
        header, rows = TABLES[name]
        body, _ = render("M", header, rows)
        tool, text = ask(system, tools, reminder, q, doc_block(name, body))
        act += tool == expected_tool
        if tool != expected_tool:
            print(f"  {cond} 행동 X {q!r} 기대 {expected_tool} -> {tool or text.replace(chr(10), ' ')[:60]}", flush=True)
    print(f"== {cond}: 표 질문 {ok}/20 (툴 샘 {leaks}) · 행동 요청 {act}/{len(ACTIONS)}", flush=True)
    for q in ["이 회의록 요약해줘", "다음 회의 언제야?"]:
        tool, text = ask(system, tools, reminder, q, doc_block("회의록.docx", PARA[:CAP]))
        print(f"  {cond} 문단 {q!r} -> {('툴 ' + tool) if tool else text.replace(chr(10), ' ')[:110]}", flush=True)
print("끝")

"""exp46c — 0.35.0 M0 마지막: 문서 턴 리마인더(exp46b 채택안)를 붙인 상태에서 첨부 표 형식 3종(M·T·K)을 비교한다.

exp46 은 표준 리마인더라 질문 대부분이 툴로 새서 형식 차이를 볼 수 없었다. 리마인더는 앱이 내보낸 픽스처
`fixtures/doc_turn_reminder.txt`(DocumentTurnReminderTest)를 그대로 읽는다 — 앱과 같은 문구를 잰다.
툴·시스템 지시는 앱 기본(웹 검색 꺼짐: 위키 줄·툴 제외).

판정: 정답 수 우선, 동률이면 300자에 든 평균 행이 많은 형식(같은 상한에 더 많은 행).
"""
import sys

import litert_lm as llm
import kosmos_lab as K
from exp46_attached_table import TABLES, render, questions, correct

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

FULL = K.fixture("system_instruction.txt")
WIKI_LINE = next(l for l in FULL.splitlines() if "search_wikipedia" in l) + "\n"
SYSTEM_APP = FULL.replace(WIKI_LINE, "")
TOOLS_APP = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
REMINDER = K.fixture("doc_turn_reminder.txt")

engine = K.create_engine()
for fmt in sys.argv[1:] or ["M", "T", "K"]:
    ok = leaks = 0
    rows_seen = []
    for name, (header, rows) in TABLES.items():
        body, used = render(fmt, header, rows)
        rows_seen.append(len(used))
        doc = f'[Attached Document]\n"""\n첨부된 문서 내용({name}):\n{body}\n"""'
        for q, expected in questions(name, used):
            with K.create_chat_conversation(engine, history=[llm.Message.user(q), llm.Message.user(doc)], tools=TOOLS_APP, system_message=SYSTEM_APP) as conv:
                res = conv.send_message(f"{REMINDER}\n\n{q}")
            calls = res.get("tool_calls") or []
            content = res.get("content")
            text = content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))
            hit = not calls and correct(text, expected)
            ok += hit
            leaks += bool(calls)
            if not hit:
                print(f"  {fmt} X {name} {q!r} 기대 {expected!r} -> {('툴 ' + calls[0]['function']['name']) if calls else text.replace(chr(10), ' ')[:70]}", flush=True)
    print(f"== {fmt}: 정답 {ok}/20 (툴 샘 {leaks}), 300자에 든 평균 행 {sum(rows_seen) / len(rows_seen):.1f}", flush=True)
print("끝")

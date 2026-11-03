"""exp75 — 문서 보관함 검색어를 모델이 바꿔 주는가 (0.41.0 M0 후속, 0.41.1 사전 실측).

배경: exp74 에서 어휘(바이그램) 검색은 바꿔 말한 질문을 1/10 만 찾았다("몰래 가져가면" ↔ "절도"). 채팅에서는 사용자 질문이 아니라
모델이 뽑은 검색어가 툴로 넘어가므로, 모델이 문서 용어로 바꿔 주면 간극이 메워질 수 있다.

조건 (앱 기본 = 웹 검색 꺼짐, 앱 시스템 지시 픽스처 + 표준 턴 리마인더, 빈 기록)
- K 키워드: `search_documents(query)` — query 설명 "찾을 핵심 키워드. 짧은 명사 위주"(search_memory 와 같은 결)
- R 바꿔 쓰기: query 설명 "사용자 말 그대로가 아니라 법령·규정·교재에 쓰였을 법한 용어로 바꾼 짧은 검색어"
둘 다 시스템 지시 [Tool Usage Guidelines] 에 트리거 한 줄을 더한다(같은 문구).
질문: exp74 의 같은 용어 10 + 바꿔 말한 질문 10, 각각 그대로 / 끝에 "등록한 문서에서 찾아줘" 를 붙인 판.
채점: ① 툴 호출(search_documents) ② 뽑은 검색어 ③ 그 검색어로 exp74 색인(FTS4 후보 100·df≤0.2 → 재순위)과 전수 스캔의 @1·@3.
실행: %USERPROFILE%\\miniforge3\\envs\\friday\\python.exe -I scratch/lab/exp75_library_query.py > scratch/lab/exp75_out.txt
"""
import json
import os
import sys
import tempfile
from pathlib import Path

import litert_lm as llm

# [WHY] -I 로 실행하면 스크립트 폴더가 sys.path 에 없다 — 실험실 모듈을 명시적으로 잇는다.
sys.path.insert(0, str(Path(__file__).parent))

import kosmos_lab as K
import exp74_library_index as E

sys.stdout.reconfigure(encoding="utf-8")

TRIGGER = ("- The user asks about rules, laws, regulations, procedures, or the contents of their registered documents: "
           "call `search_documents`. Answer only from what it returns and name the document and article.\n")


def search_documents_k(query: str) -> dict:
    """사용자가 등록한 문서 보관함(규정·법령·교재)에서 관련 부분을 찾는다.

    Args:
        query: 찾을 핵심 키워드. 문장이 아니라 명사 위주의 짧은 단어로 쓴다. 예: '소멸시효 기간', '민법 제750조'
    """
    raise NotImplementedError


def search_documents_r(query: str) -> dict:
    """사용자가 등록한 문서 보관함(규정·법령·교재)에서 관련 부분을 찾는다.

    Args:
        query: 사용자 말 그대로가 아니라 법령·규정·교재 본문에 쓰였을 법한 용어로 바꾼 짧은 검색어. 예: '몰래 가져가면' → '절도', '물어줘야' → '손해배상', '민법 제750조'
    """
    raise NotImplementedError


for f in (search_documents_k, search_documents_r):
    f.__name__ = "search_documents"

BASE_TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
FULL = K.fixture("system_instruction.txt")
WIKI_LINE = next(l for l in FULL.splitlines(keepends=True) if "search_wikipedia" in l)
SYSTEM = FULL.replace(WIKI_LINE, "")
anchor = next(l for l in SYSTEM.splitlines(keepends=True) if "`list_reminders`" in l)
SYSTEM = SYSTEM.replace(anchor, anchor + TRIGGER)

QUESTIONS = [("용어", q) for q in E.Q_TERM] + [("바꿔말함", q) for q in E.Q_PARA]
SUFFIX = " 등록한 문서에서 찾아줘."


def ask(engine, tool, question):
    with K.create_chat_conversation(engine, history=[], tools=BASE_TOOLS + [tool], system_message=SYSTEM) as conv:
        res = conv.send_message(K.with_turn_reminder(question))
    for call in res.get("tool_calls") or []:
        fn = call.get("function", {})
        args = fn.get("arguments")
        if isinstance(args, str):
            try:
                args = json.loads(args)
            except json.JSONDecodeError:
                args = {"query": args}
        return fn.get("name"), (args or {}).get("query", "")
    content = res.get("content")
    text = content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))
    return None, text.replace("\n", " ")[:60]


def main():
    laws = E.load_laws()
    chunks = [c for name, ls in laws.items() for c in E.chunk_law(name, ls, 500)]
    pre = [(c, E.bigrams(c["path"]), E.bigrams(c["text"])) for c in chunks]
    db = E.build_fts(chunks, os.path.join(tempfile.mkdtemp(prefix="exp75_"), "fts.db"))
    total = len(chunks)

    def retrieve(query):
        full = E.rerank(query, laws, pre)
        ids = E.cand_fts(db, E.bigrams(query), total, 100, 0.2)
        fts = E.rerank(query, laws, [pre[i] for i in ids])
        return full, fts

    engine = K.create_engine()
    print(f"시스템 지시 {len(SYSTEM)}자 (트리거 한 줄 추가), 조각 {total:,}", flush=True)
    summary = {}
    for cond, tool in (("K", search_documents_k), ("R", search_documents_r)):
        for phr in ("그대로", "문서에서"):
            print(f"\n## 조건 {cond} · 질문 {phr}", flush=True)
            for kind, (q, law, gold) in QUESTIONS:
                question = q + (SUFFIX if phr == "문서에서" else "")
                name, query = ask(engine, tool, question)
                key = (cond, phr, kind)
                s = summary.setdefault(key, {"n": 0, "call": 0, "full1": 0, "full3": 0, "fts1": 0, "fts3": 0})
                s["n"] += 1
                if name == "search_documents" and query:
                    s["call"] += 1
                    full, fts = retrieve(query)
                    f1, f3 = E.hit(full, law, gold, 1), E.hit(full, law, gold, 3)
                    t1, t3 = E.hit(fts, law, gold, 1), E.hit(fts, law, gold, 3)
                    s["full1"] += f1; s["full3"] += f3; s["fts1"] += t1; s["fts3"] += t3
                    print(f"{'O' if t1 else ('3' if t3 else 'X')} [{kind}] {question} → '{query}' → {fts[0]['path'][-48:] if fts else '-'}", flush=True)
                else:
                    print(f"- [{kind}] {question} → 툴 없음/{name}: {query}", flush=True)

    print("\n## 요약 (FTS4 후보 100 재순위 @1/@3 · 전수 스캔 @1/@3 — 툴 호출한 것만 검색, 분모는 전체)")
    for (cond, phr, kind), s in summary.items():
        print(f"{cond} {phr} {kind}: 호출 {s['call']}/{s['n']} · FTS @1 {s['fts1']} @3 {s['fts3']} · 전수 @1 {s['full1']} @3 {s['full3']}", flush=True)


if __name__ == "__main__":
    main()

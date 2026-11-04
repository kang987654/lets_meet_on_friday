"""exp79 — 0.41.2 M4: 문서 검색 켜짐(B)의 약점 대안 ⓐ 보관함 태그 · ⓑ 자문 아님 · ⓒ 검색어 여러 개 · ⓓ 다시 찾기.

M0(exp78) 에서 B 의 주된 손실은 검색어가 아니라 **툴 미호출**(바꿔 말함 3/10 — "법률 조언이 필요" 거절 2, "어떤 문서?" 되묻기 1)이었다.
1단계(기본): 트리거 한 줄의 변형별 호출률 — 번호 10 · 용어 10 · 바꿔 말함 20(M0 10 + 새 10) · 무관 10.
  B0  = M0 원문 한 줄
  Bb  = + 자문 아님 한 구절(ⓑ)
  Ba1 = + 문서 이름 목록(ⓐ, 모델 없이 뽑음)
  Ba2 = + 문서 이름과 상위 머리말(편·장) 목록(ⓐ, 문서당 최대 8개)
  Ba1b, Ba2b = 둘 다
2단계(`--stage2 <변형>`): 그 변형 위에 ⓒ 검색어 최대 3개(결과는 질의별 결합 점수의 최댓값) · ⓓ "결과가 답이 안 되면 다른 용어로 한 번 더" 허용.
지표: 호출률, 모델 검색어로 결합(w) @1·@3(w 0.05/0.1), 답에 정답 조 번호(엄격한 근사), 무관 질문 오호출, 추가 토큰(engine.tokenize).
실행: KOSMOS_LAB_SPEC=adaptive python exp79_doc_search_weakness.py > exp79_out.txt   (출력은 커밋하지 않는다)
"""
import json
import os
import pickle
import re
import sys
from pathlib import Path

import litert_lm as llm
import litert_lm.embedding_engine as EE

sys.path.insert(0, str(Path(__file__).parent))
import exp74_library_index as E  # noqa: E402
import exp76_library_dense as D  # noqa: E402
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

# exp78 원문 — B0.
TOGGLE_LINE = ("- Document search is ON (the user turned it on): for questions about facts, rules, laws, procedures, or anything their "
               "registered documents might cover, call `search_documents` first and answer only from what it returns, naming the document and article or page.")
ADVICE = " Reporting what the user's own documents say is not legal, medical, or financial advice, so search instead of declining."
RETRY = " If the results do not answer the question, search once more with a different term."
# 3단계(`--stage3`) — 1단계에서 남은 미호출의 모양이 "어떤 문서에서 찾을까요?·관련 문서가 있으신가요?" 되묻기였다.
NO_ASK = " Never ask which document or whether they have one — search all of them yourself."


def search_documents(query: str) -> dict:
    """사용자가 등록한 문서 보관함(규정·법령·교재)에서 관련 부분을 찾는다.

    Args:
        query: 사용자 말 그대로가 아니라 법령·규정·교재 본문에 쓰였을 법한 용어로 바꾼 짧은 검색어. 예: '몰래 가져가면' → '절도', '물어줘야' → '손해배상', '민법 제750조'
    """
    raise NotImplementedError


def search_documents_multi(queries: list[str]) -> dict:
    """사용자가 등록한 문서 보관함(규정·법령·교재)에서 관련 부분을 찾는다.

    Args:
        queries: 검색어 1~3개. 사용자 말 그대로가 아니라 법령·규정·교재 본문에 쓰였을 법한 용어. 예: '몰래 가져가면' → ['절도'], '물어줘야' → ['손해배상', '불법행위']
    """
    raise NotImplementedError


search_documents_multi.__name__ = "search_documents"

BASE_TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
FULL = K.fixture("system_instruction.txt")
SYSTEM_OFF = FULL.replace(next(l for l in FULL.splitlines(keepends=True) if "search_wikipedia" in l), "")
_ANCHOR = next(l for l in SYSTEM_OFF.splitlines(keepends=True) if "`list_reminders`" in l)

Q_PARA_NEW = [
    ("미성년자가 부모 허락 없이 계약하면 어떻게 돼?", "민법", {"5"}),
    ("약속한 물건을 안 주면 손해를 물어내야 해?", "민법", {"390"}),
    ("결혼은 몇 살부터 할 수 있어?", "민법", {"807"}),
    ("이혼하면 재산은 어떻게 나눠?", "민법", {"839의2"}),
    ("술에 취해서 저지른 일은 벌이 줄어?", "형법", {"10"}),
    ("남을 때려서 다치게 하면 무슨 죄야?", "형법", {"257"}),
    ("거짓말로 남의 돈을 받아내면 무슨 죄야?", "형법", {"347"}),
    ("남의 집에 허락 없이 들어가면 처벌받아?", "형법", {"319"}),
    ("길에서 주운 지갑을 안 돌려주면 죄가 돼?", "형법", {"360"}),
    ("1심 판결이 마음에 안 들면 어떻게 다시 재판받아?", "민사소송법", {"390"}),
]
Q_UNRELATED = ["고마워", "내일 일정 알려줘", "오늘 저녁 뭐 먹지?", "3시에 회의 있다고 알림 맞춰줘", "내 자물쇠 비번은 4729야 기억해줘",
               "날씨가 좋네", "너는 누구야?", "회의록 잘 쓰는 법 알려줘", "지난번에 기억해 둔 거 찾아줘", "주말에 뭐 하면 좋을까?"]
QS = ([(kind, q, law, gold) for kind, (q, law, gold) in E.QUESTIONS] + [("바꿔말함", q, law, gold) for q, law, gold in Q_PARA_NEW]
      + [("무관", q, None, None) for q in Q_UNRELATED])


def tags(laws, with_heads):
    """보관함 태그 — 모델 없이 문서 이름(과 상위 머리말)에서 뽑는다."""
    if not with_heads:
        return "Registered documents: " + ", ".join(laws) + "."
    parts = []
    for name, lines in laws.items():
        heads = {"편": [], "장": []}
        for raw in lines:
            line = E.clean(raw)
            m = re.match(r"^제\d+(편|장)\s+(.+)$", line)
            if m and m.group(1) in heads:
                title = re.sub(r"\s*<[^>]*>|\s*\[[^\]]*\]", "", m.group(2)).strip()
                if title and title not in heads[m.group(1)]:
                    heads[m.group(1)].append(title)
        pick = heads["편"] if len(heads["편"]) >= 3 else heads["장"]
        parts.append(f"{name}({'·'.join(pick[:8])})" if pick else name)
    return "Registered documents: " + ", ".join(parts) + "."


def art_label(gold):
    """'839의2' → '제839조의2', '민법:761' → '제761조'."""
    n = gold.split(":")[-1]
    main_no, _, sub = n.partition("의")
    return f"제{main_no}조" + (f"의{sub}" if sub else "")


def system_with(line):
    return SYSTEM_OFF.replace(_ANCHOR, _ANCHOR + line + "\n")


def text_of(res):
    content = res.get("content")
    return content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))


def calls_of(res):
    out = []
    for call in res.get("tool_calls") or []:
        fn = call.get("function", call)
        args = fn.get("arguments")
        if isinstance(args, str):
            try:
                args = json.loads(args)
            except json.JSONDecodeError:
                args = {"query": args}
        args = args or {}
        qs = args.get("queries") or ([args["query"]] if args.get("query") else [])
        if isinstance(qs, str):
            qs = [qs]
        out.append((fn.get("name"), [str(q) for q in qs if str(q).strip()][:3]))
    return out


def main():
    stage2 = sys.argv[2] if len(sys.argv) > 2 and sys.argv[1] == "--stage2" else None
    laws = E.load_laws()
    chunks = [c for name, ls in laws.items() for c in E.chunk_law(name, ls, 500)]
    have = {(c["law"], c["article"]) for c in chunks}
    for q, law, gold in Q_PARA_NEW:
        if not any((law, g) in have for g in gold):
            print(f"!! 정답 조문 없음: {q} {law} {gold}", flush=True)
    pre = [(c, E.bigrams(c["path"]), E.bigrams(c["text"])) for c in chunks]
    docs = pickle.loads(D.CACHE.read_bytes())
    emb = EE.EmbeddingEngine(str(D.MODEL), max_input_length=512)
    eopts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    qcache = {}

    def sparse(q):
        terms = E.terms_of(q)
        m = E.NUM_Q.search(q)
        want = (m.group(1) + (f"의{m.group(2)}" if m.group(2) else "")) if m else None
        named = [law for law in laws if law.replace(" ", "") in q.replace(" ", "")]
        out = []
        for c, pb, tb in pre:
            s = 1.5 * E.score(terms, pb) + E.score(terms, tb)
            if want and c["article"] == want:
                s += 3.0
            if named and c["law"] in named:
                s += 1.0
            out.append(s)
        return out

    def dense(q):
        if q not in qcache:
            v = list(emb.compute_embedding(D.query_input(q), eopts).embedding)
            qcache[q] = [D.dot(d, v) for d in docs]
        return qcache[q]

    def fused(queries, w):
        best = None
        for q in queries:
            sp, de = sparse(q), dense(q)
            top = max(sp) or 1.0
            sc = [d + w * s / top for s, d in zip(sp, de)]
            best = sc if best is None else [max(a, b) for a, b in zip(best, sc)]
        return sorted(range(len(best)), key=lambda i: -best[i])

    def hit(idx, law, gold, k):
        return E.hit([chunks[i] for i in idx[:k]], law, gold, k)

    def block(idx):
        lines = ["[등록 문서에서 찾은 내용]"]
        for n, i in enumerate(idx[:3], 1):
            c = chunks[i]
            lines.append(f"{n}) {c['law']} > {c['path'].split(' > ')[-1]}\n{c['text'][:300]}")
        return "\n".join(lines)

    t_names, t_heads = tags(laws, False), tags(laws, True)
    print(f"태그 이름만: {t_names}\n태그 머리말: {t_heads}\n", flush=True)
    if len(sys.argv) > 1 and sys.argv[1] == "--stage3":
        bn = TOGGLE_LINE + NO_ASK
        variants = [("Bn", bn, search_documents, 1), ("Bn+a1", bn + " " + t_names, search_documents, 1),
                    ("Bn+c", bn, search_documents_multi, 1), ("Bn+d", bn + RETRY, search_documents, 2)]
    elif stage2:
        base = {"B0": TOGGLE_LINE, "Bb": TOGGLE_LINE + ADVICE, "Ba1": TOGGLE_LINE + " " + t_names, "Ba2": TOGGLE_LINE + " " + t_heads,
                "Ba1b": TOGGLE_LINE + ADVICE + " " + t_names, "Ba2b": TOGGLE_LINE + ADVICE + " " + t_heads}[stage2]
        variants = [(stage2, base, search_documents, 1), (stage2 + "+c", base, search_documents_multi, 1),
                    (stage2 + "+d", base + RETRY, search_documents, 2)]
    else:
        variants = [("B0", TOGGLE_LINE, search_documents, 1), ("Bb", TOGGLE_LINE + ADVICE, search_documents, 1),
                    ("Ba1", TOGGLE_LINE + " " + t_names, search_documents, 1), ("Ba2", TOGGLE_LINE + " " + t_heads, search_documents, 1),
                    ("Ba1b", TOGGLE_LINE + ADVICE + " " + t_names, search_documents, 1),
                    ("Ba2b", TOGGLE_LINE + ADVICE + " " + t_heads, search_documents, 1)]

    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    print(f"MTP={os.environ.get('KOSMOS_LAB_SPEC', '-')}", flush=True)
    base_tokens = len(engine.tokenize(TOGGLE_LINE))
    summary = []
    for label, line, tool, max_calls in variants:
        print(f"\n## {label} (한 줄 {len(engine.tokenize(line))}토큰, B0 대비 +{len(engine.tokenize(line)) - base_tokens})", flush=True)
        system = system_with(line)
        agg = {}
        for kind, q, law, gold in QS:
            a = agg.setdefault(kind, {"n": 0, "call": 0, "f05_1": 0, "f05_3": 0, "f10_1": 0, "f10_3": 0, "ans": 0, "second": 0, "other": 0})
            a["n"] += 1
            with K.create_chat_conversation(engine, history=[], tools=BASE_TOOLS + [tool], system_message=system) as conv:
                res = conv.send_message(K.with_turn_reminder(q))
                answer, used, n_calls, first_q = text_of(res), [], 0, None
                while True:
                    calls = calls_of(res)
                    doc = next((c for c in calls if c[0] == "search_documents" and c[1]), None)
                    if not doc:
                        if calls and n_calls == 0:
                            a["other"] += 1
                        break
                    n_calls += 1
                    used.append(doc[1])
                    if n_calls == 1:
                        first_q = doc[1]
                    order = fused(doc[1], 0.05)
                    res = conv.send_message(K.tool_response_message("search_documents", json.dumps({"result": block(order)}, ensure_ascii=False)))
                    answer = text_of(res)
                    if n_calls >= max_calls:
                        break
            if first_q:
                a["call"] += 1
                if law:
                    o5, o10 = fused(first_q, 0.05), fused(first_q, 0.1)
                    a["f05_1"] += hit(o5, law, gold, 1); a["f05_3"] += hit(o5, law, gold, 3)
                    a["f10_1"] += hit(o10, law, gold, 1); a["f10_3"] += hit(o10, law, gold, 3)
            if n_calls > 1:
                a["second"] += 1
            ok = any(art_label(g) in answer for g in gold) if law else None
            a["ans"] += bool(ok)
            print(f"[{kind}] {q} | {' → '.join('/'.join(u) for u in used) or '-'} | 정답조={ok} | {answer.replace(chr(10), ' ')[:80]}", flush=True)
        for kind, a in agg.items():
            row = (f"{label:8s} {kind:4s} 호출 {a['call']}/{a['n']} · 결합 w0.05 @1 {a['f05_1']} @3 {a['f05_3']} · w0.1 @1 {a['f10_1']} @3 {a['f10_3']}"
                   f" · 답 정답조 {a['ans']} · 두 번째 찾기 {a['second']} · 다른 툴 {a['other']}")
            summary.append(row)
            print(row, flush=True)
    print("\n## 요약", flush=True)
    for row in summary:
        print(row, flush=True)


if __name__ == "__main__":
    main()

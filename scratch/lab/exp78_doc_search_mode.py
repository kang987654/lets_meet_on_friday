"""exp78 — 0.41.2 M0: 문서 검색 토글이 켜졌을 때 A(앱 선검색) vs B(툴 + 켜짐 트리거), 결합 가중치, 벡터 int8, 프롬프트 비용.

자료: exp74 공개 법령 6개(조각 3,572) + 질문 30(번호·용어·바꿔말함 각 10) + **무관 질문 10**(토글을 켜 둔 채 하는 일상 대화).
문서 임베딩은 exp76 캐시(EmbeddingGemma 2 740M, 768차원, 정규화)를 쓴다. 채팅 모델은 앱과 같은 GPU FP32 + MTP ADAPTIVE(0.41.1).

① 검색만(모델 없음): 질문 원문으로 sparse / dense / 결합(w) @1·@3, 무관 질문과의 최고 점수 분포(관련도 하한으로 거를 수 있나)
② B: 시스템 지시에 "문서 검색 켜짐" 트리거 한 줄 + `search_documents`(검색어 설명 R) — 호출률, 모델 검색어로 sparse·결합 @1·@3, 무관 질문의 오호출·다른 툴
③ 끝까지: A = 원문 결합 상위 3 조각을 이번 입력에 붙여 답 / B = 툴 결과로 같은 묶음을 돌려준 뒤 답 — 답에 정답 조문 번호가 나오는가(자동 근사) + 답 앞부분 출력
④ 벡터 int8(벡터마다 최대 절댓값 척도) 회수율 차이, 전수 내적 시간(순수 파이썬 — 기기는 훨씬 빠르다, 방향만)
⑤ 프롬프트 비용: 선언 + 트리거 한 줄의 대화 토큰 차이, 결과 묶음(상위 3, 조각당 300자)의 토큰
실행: KOSMOS_LAB_SPEC=adaptive python exp78_doc_search_mode.py > exp78_out.txt   (출력은 커밋하지 않는다)
"""
import json
import math
import os
import pickle
import sys
import time
from pathlib import Path

import litert_lm as llm
import litert_lm.embedding_engine as EE

sys.path.insert(0, str(Path(__file__).parent))
import exp74_library_index as E  # noqa: E402
import exp76_library_dense as D  # noqa: E402
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

TOGGLE_LINE = ("- Document search is ON (the user turned it on): for questions about facts, rules, laws, procedures, or anything their "
               "registered documents might cover, call `search_documents` first and answer only from what it returns, naming the document and article or page.\n")


def search_documents(query: str) -> dict:
    """사용자가 등록한 문서 보관함(규정·법령·교재)에서 관련 부분을 찾는다.

    Args:
        query: 사용자 말 그대로가 아니라 법령·규정·교재 본문에 쓰였을 법한 용어로 바꾼 짧은 검색어. 예: '몰래 가져가면' → '절도', '물어줘야' → '손해배상', '민법 제750조'
    """
    raise NotImplementedError


BASE_TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
FULL = K.fixture("system_instruction.txt")
SYSTEM_OFF = FULL.replace(next(l for l in FULL.splitlines(keepends=True) if "search_wikipedia" in l), "")
_anchor = next(l for l in SYSTEM_OFF.splitlines(keepends=True) if "`list_reminders`" in l)
SYSTEM_ON = SYSTEM_OFF.replace(_anchor, _anchor + TOGGLE_LINE)

Q_UNRELATED = ["고마워", "내일 일정 알려줘", "오늘 저녁 뭐 먹지?", "3시에 회의 있다고 알림 맞춰줘", "내 자물쇠 비번은 4729야 기억해줘",
               "날씨가 좋네", "너는 누구야?", "회의록 잘 쓰는 법 알려줘", "지난번에 기억해 둔 거 찾아줘", "주말에 뭐 하면 좋을까?"]
QS = [(kind, q, law, gold) for kind, (q, law, gold) in E.QUESTIONS] + [("무관", q, None, None) for q in Q_UNRELATED]


def text_of(res):
    content = res.get("content")
    return content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))


def first_call(res):
    for call in res.get("tool_calls") or []:
        fn = call.get("function", call)
        args = fn.get("arguments")
        if isinstance(args, str):
            try:
                args = json.loads(args)
            except json.JSONDecodeError:
                args = {"query": args}
        return fn.get("name"), (args or {}).get("query", "")
    return None, ""


def quant8(v):
    m = max(abs(x) for x in v) or 1.0
    return [round(x / m * 127) for x in v], m / 127


def main():
    laws = E.load_laws()
    chunks = [c for name, ls in laws.items() for c in E.chunk_law(name, ls, 500)]
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

    def qvec(q):
        if q not in qcache:
            qcache[q] = list(emb.compute_embedding(D.query_input(q), eopts).embedding)
        return qcache[q]

    def dense(q, mat=None):
        v = qvec(q)
        return [D.dot(d, v) for d in (mat or docs)]

    def fuse(sp, de, w):
        top = max(sp) or 1.0
        return [d + w * s / top for s, d in zip(sp, de)]

    def order(scores):
        return sorted(range(len(scores)), key=lambda i: -scores[i])

    def hit(idx, law, gold, k):
        return E.hit([chunks[i] for i in idx[:k]], law, gold, k)

    def block(idx):
        lines = ["[등록 문서에서 찾은 내용]"]
        for n, i in enumerate(idx[:3], 1):
            c = chunks[i]
            lines.append(f"{n}) {c['law']} > {c['path'].split(' > ')[-1]}\n{c['text'][:300]}")
        return "\n".join(lines)

    # ---------------- ① 검색만 (원문)
    print("## ① 질문 원문 검색 (모델 없음)", flush=True)
    WS = (0.0, 0.05, 0.1, 0.2)
    agg, tops = {}, {"관련": [], "무관": []}
    for kind, q, law, gold in QS:
        sp, de = sparse(q), dense(q)
        tops["무관" if kind == "무관" else "관련"].append((max(de), q))
        if kind == "무관":
            continue
        for label, sc in [("sparse", sp)] + [(f"w{w}", fuse(sp, de, w)) for w in WS]:
            o = order(sc)
            a = agg.setdefault((kind, label), [0, 0])
            a[0] += hit(o, law, gold, 1); a[1] += hit(o, law, gold, 3)
    for (kind, label), (h1, h3) in sorted(agg.items()):
        print(f"원문 {kind:4s} {label:7s} @1 {h1}/10 @3 {h3}/10", flush=True)
    for k, v in tops.items():
        vals = sorted(x for x, _ in v)
        print(f"dense 최고 코사인 {k}: 최소 {vals[0]:.3f} 중앙 {vals[len(vals)//2]:.3f} 최대 {vals[-1]:.3f}", flush=True)
    print("무관 상세:", ", ".join(f"{q}={x:.3f}" for x, q in sorted(tops["무관"], reverse=True)), flush=True)
    print("관련 하위 5:", ", ".join(f"{q}={x:.3f}" for x, q in sorted(tops["관련"])[:5]), flush=True)

    # ---------------- ④ int8
    print("\n## ④ 벡터 int8", flush=True)
    q8 = []
    for d in docs:
        v, s = quant8(d)
        q8.append([x * s for x in v])
    agg8 = {}
    for kind, q, law, gold in QS:
        if kind == "무관":
            continue
        for label, mat in (("float", docs), ("int8", q8)):
            o = order(dense(q, mat))
            a = agg8.setdefault((kind, label), [0, 0])
            a[0] += hit(o, law, gold, 1); a[1] += hit(o, law, gold, 3)
    for (kind, label), (h1, h3) in sorted(agg8.items()):
        print(f"dense {kind:4s} {label:5s} @1 {h1}/10 @3 {h3}/10", flush=True)
    t0 = time.perf_counter()
    dense("소멸시효 기간 테스트")
    dt = time.perf_counter() - t0
    print(f"전수 내적(순수 파이썬, 조각 {len(docs):,}) {dt * 1000:.0f}ms — 상한 6만 조각이면 약 {dt * 60000 / len(docs):.1f}s (기기 Kotlin 배열은 수십 배 빠름)", flush=True)
    # [WHY] 엔진을 지우지 않는다 — ② 에서 모델 검색어를 다시 임베딩한다.

    # ---------------- ② ③ ⑤ 모델
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    print(f"\n## ⑤ 프롬프트 비용 (MTP={os.environ.get('KOSMOS_LAB_SPEC', '-')})", flush=True)
    counts = {}
    for label, sysm, tools in (("꺼짐", SYSTEM_OFF, BASE_TOOLS), ("켜짐", SYSTEM_ON, BASE_TOOLS + [search_documents])):
        with K.create_chat_conversation(engine, history=[], tools=tools, system_message=sysm) as conv:
            conv.send_message("안녕")
            counts[label] = conv.token_count
    sample = block(order(fuse(sparse("소멸시효 기간"), dense("소멸시효 기간"), 0.1)))
    print(f"대화 토큰(같은 '안녕' 턴): 꺼짐 {counts['꺼짐']} · 켜짐 {counts['켜짐']} → 선언+트리거 약 {counts['켜짐'] - counts['꺼짐']} (응답 길이 차 포함)", flush=True)
    print(f"트리거 한 줄 {len(engine.tokenize(TOGGLE_LINE))}토큰 · 결과 묶음(상위 3·조각 300자) {len(engine.tokenize(sample))}토큰 · {len(sample)}자", flush=True)

    print("\n## ② B 툴 호출 + ③ 끝까지 (A·B)", flush=True)
    bagg = {}
    for kind, q, law, gold in QS:
        # B
        with K.create_chat_conversation(engine, history=[], tools=BASE_TOOLS + [search_documents], system_message=SYSTEM_ON) as conv:
            res = conv.send_message(K.with_turn_reminder(q))
            name, query = first_call(res)
            b_answer = text_of(res)
            b_hit = None
            if name == "search_documents" and query:
                sp, de = sparse(query), dense(query)
                fo = order(fuse(sp, de, 0.1))
                a = bagg.setdefault(kind, {"call": 0, "sp1": 0, "sp3": 0, "f1": 0, "f3": 0, "ans": 0})
                a["call"] += 1
                if law:
                    so = order(sp)
                    a["sp1"] += hit(so, law, gold, 1); a["sp3"] += hit(so, law, gold, 3)
                    a["f1"] += hit(fo, law, gold, 1); a["f3"] += hit(fo, law, gold, 3)
                res2 = conv.send_message(K.tool_response_message("search_documents", json.dumps({"result": block(fo)}, ensure_ascii=False)))
                b_answer = text_of(res2)
                if law:
                    b_hit = any(f"제{g.split(':')[-1]}조" in b_answer for g in gold)
                    a["ans"] += bool(b_hit)
            else:
                bagg.setdefault(kind, {"call": 0, "sp1": 0, "sp3": 0, "f1": 0, "f3": 0, "ans": 0})
        # A — 원문 결합 상위 3 을 이번 입력에 붙인다(앱 첨부 블록과 같은 자리)
        fo_a = order(fuse(sparse(q), dense(q), 0.1))
        with K.create_chat_conversation(engine, history=[], tools=BASE_TOOLS, system_message=SYSTEM_OFF) as conv:
            res = conv.send_message(K.with_turn_reminder(q) + "\n\n" + block(fo_a))
            a_answer = text_of(res)
            a_call, _ = first_call(res)
        a_hit = any(f"제{g.split(':')[-1]}조" in a_answer for g in gold) if law else None
        aa = bagg[kind].setdefault("a_ans", 0)
        bagg[kind]["a_ans"] = aa + bool(a_hit)
        print(f"[{kind}] {q}\n   B: {name or '-'} '{query}' 답정답={b_hit} | {b_answer.replace(chr(10), ' ')[:90]}\n"
              f"   A: 툴={a_call or '-'} 답정답={a_hit} | {a_answer.replace(chr(10), ' ')[:90]}", flush=True)

    print("\n## 요약 B(호출·모델 검색어 sparse/결합 @1·@3·답에 정답 조) · A(답에 정답 조)", flush=True)
    for kind, a in bagg.items():
        print(f"{kind:4s} B 호출 {a['call']}/10 · sparse @1 {a['sp1']} @3 {a['sp3']} · 결합 @1 {a['f1']} @3 {a['f3']} · B 답 {a['ans']} | A 답 {a.get('a_ans', 0)}", flush=True)


if __name__ == "__main__":
    main()

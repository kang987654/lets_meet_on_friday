"""exp78b — B 방식에서 검색에 무엇을 넣을까: 모델 검색어만 vs 질문 원문도 함께 (exp78 출력의 모델 검색어 재사용, 모델 추론 없음).

exp78: 토글 켜짐 트리거로 바꿔 말한 질문 호출 7/10 이 됐지만, 모델 검색어 결합 @3 은 4/10 — 검색어가 길거나("공격 방어 중 상대 부상 처벌") 너무 넓었다("손해배상").
dense 는 질문 원문에서 @3 6/10 이었다(exp78 ①). 툴 실행기는 그 턴의 사용자 원문을 알 수 있으므로 둘을 함께 쓰는 변형을 비교한다.
- Q: dense(검색어) + w·sparse(검색어)       (exp76b·exp78 기본)
- U: dense(원문) + w·sparse(검색어)
- M: (dense(원문) + dense(검색어)) / 2 + w·sparse(검색어)
- X: max(dense(원문), dense(검색어)) + w·sparse(검색어)
실행: python exp78b_query_fusion.py > exp78b_out.txt
"""
import pickle
import re
import sys
from pathlib import Path

import litert_lm.embedding_engine as EE

sys.path.insert(0, str(Path(__file__).parent))
import exp74_library_index as E  # noqa: E402
import exp76_library_dense as D  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
LAB = Path(__file__).parent


def main():
    laws = E.load_laws()
    chunks = [c for name, ls in laws.items() for c in E.chunk_law(name, ls, 500)]
    pre = [(c, E.bigrams(c["path"]), E.bigrams(c["text"])) for c in chunks]
    docs = pickle.loads(D.CACHE.read_bytes())
    emb = EE.EmbeddingEngine(str(D.MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    gold_of = {q: (kind, law, gold) for kind, (q, law, gold) in E.QUESTIONS}

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
        top = max(out) or 1.0
        return [s / top for s in out]

    def dense(q):
        v = list(emb.compute_embedding(D.query_input(q), opts).embedding)
        return [D.dot(d, v) for d in docs]

    rows = []
    text = (LAB / "exp78_out.txt").read_text(encoding="utf-8")
    for m in re.finditer(r"^\[(\S+)\] (.+)\n   B: search_documents '(.+?)'", text, re.M):
        kind, q, query = m.groups()
        if q in gold_of:
            rows.append((kind, q, query) + gold_of[q][1:])
    print(f"모델 검색어 {len(rows)}개", flush=True)

    agg = {}
    for kind, q, query, law, gold in rows:
        sq, dq, du = sparse(query), dense(query), dense(q)
        for w in (0.05, 0.1, 0.2):
            variants = {
                "Q": [d + w * s for d, s in zip(dq, sq)],
                "U": [d + w * s for d, s in zip(du, sq)],
                "M": [(a + b) / 2 + w * s for a, b, s in zip(du, dq, sq)],
                "X": [max(a, b) + w * s for a, b, s in zip(du, dq, sq)],
            }
            for name, sc in variants.items():
                o = sorted(range(len(sc)), key=lambda i: -sc[i])
                r = [chunks[i] for i in o[:3]]
                a = agg.setdefault((kind, name, w), [0, 0, 0])
                a[0] += 1; a[1] += E.hit(r, law, gold, 1); a[2] += E.hit(r, law, gold, 3)
    for (kind, name, w), (n, h1, h3) in sorted(agg.items()):
        print(f"{kind:4s} {name} w{w:<4} @1 {h1}/{n} @3 {h3}/{n}", flush=True)


if __name__ == "__main__":
    main()

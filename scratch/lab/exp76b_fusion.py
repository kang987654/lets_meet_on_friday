"""exp76b — exp76 의 하이브리드 결합 방식 비교 (캐시된 문서 임베딩 재사용).

exp76 에서 RRF(순위 결합)는 조문 번호·같은 용어를 올렸지만(10/10·8~9/10) 바꿔 말한 질문을 dense 4/10 → 1/10 으로 깎았다 —
sparse 가 엉뚱한 조각에 높은 순위를 주면 dense 의 정답 순위가 묻힌다. 점수 결합·조건부 결합을 비교한다.
- SUM(w): cos + w · (sparse 점수 / 그 질의의 sparse 최고점)  — w = 0.1, 0.2, 0.3
- GATE: 질의에 조문 번호가 있으면 sparse, 아니면 SUM(0.2)
주의: 질문 30개(+모델 검색어 12)뿐이라 가중치 선택은 과적합 위험 — 방향만 본다.
"""
import math
import pickle
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import litert_lm.embedding_engine as EE  # noqa: E402

import exp74_library_index as E  # noqa: E402
import exp76_library_dense as D  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")


def main():
    laws = E.load_laws()
    chunks = [c for name, ls in laws.items() for c in E.chunk_law(name, ls, 500)]
    pre = [(c, E.bigrams(c["path"]), E.bigrams(c["text"])) for c in chunks]
    docs = pickle.loads(D.CACHE.read_bytes())
    engine = EE.EmbeddingEngine(str(D.MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    index = {id(c): i for i, c in enumerate(chunks)}

    def sparse_scores(q):
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

    def dense_scores(q):
        qv = list(engine.compute_embedding(D.query_input(q), opts).embedding)
        return [D.dot(v, qv) for v in docs]

    def order(scores):
        return sorted(range(len(scores)), key=lambda i: -scores[i])

    def fuse(sp, de, w):
        top = max(sp) or 1.0
        return [d + w * s / top for s, d in zip(sp, de)]

    sets = {"원문": [(kind, q, law, gold) for kind, (q, law, gold) in E.QUESTIONS]}
    text = (Path(__file__).parent / "exp75_out.txt").read_text(encoding="utf-8")
    gold_of = {q: (kind, law, gold) for kind, (q, law, gold) in E.QUESTIONS}
    for label, header in (("모델 검색어 R·그대로", "## 조건 R · 질문 그대로"), ("모델 검색어 R·문서에서", "## 조건 R · 질문 문서에서")):
        block = text.split(header)[-1].split("## ")[0]
        qs = []
        for line in block.splitlines():
            m = re.match(r"^[O3X] \[(\S+)\] (.+?) → '(.+?)' →", line)
            if m:
                key = m.group(2).replace(" 등록한 문서에서 찾아줘.", "")
                if key in gold_of:
                    kind, law, gold = gold_of[key]
                    qs.append((kind, m.group(3), law, gold))
        sets[label] = qs

    for name, qs in sets.items():
        print(f"\n## 질의: {name} ({len(qs)}개)", flush=True)
        rows = {}
        for kind, q, law, gold in qs:
            sp, de = sparse_scores(q), dense_scores(q)
            variants = {"sparse": order(sp), "dense768": order(de)}
            for w in (0.1, 0.2, 0.3):
                variants[f"SUM{w}"] = order(fuse(sp, de, w))
            variants["GATE"] = order(sp) if E.NUM_Q.search(q) else order(fuse(sp, de, 0.2))
            for label, o in variants.items():
                r = rows.setdefault((label, kind), [0, 0, 0])
                ranked = [chunks[i] for i in o[:3]]
                r[0] += E.hit(ranked, law, gold, 1); r[1] += E.hit(ranked, law, gold, 3); r[2] += 1
        labels = []
        for (lb, _k) in rows:
            if lb not in labels:
                labels.append(lb)
        for lb in labels:
            parts = [f"{k} @1 {r[0]}/{r[2]} @3 {r[1]}/{r[2]}" for (l2, k), r in rows.items() if l2 == lb]
            print(f"{lb}: " + " · ".join(parts), flush=True)


if __name__ == "__main__":
    main()

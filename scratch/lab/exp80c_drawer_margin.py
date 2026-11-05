"""exp80c — 0.41.3 M4: 서랍 검색의 뜻 결과를 "1위 점수 근처만" 남길 때의 허용 차이(margin).

exp80 의 메모 120건(보강 태그·문서 입력에 태그)과 질문(답 있음 85·답 없음 21)으로, 결합 점수(dense + 0.05 × sparse/최고점) 상위 5 중
`점수 ≥ 1위 − margin` 만 남길 때: 정답이 남는 비율(답 있음, 정답이 상위 5 안일 때), 남는 개수(평균), 답 없음에서 남는 개수.
실행: python exp80c_drawer_margin.py
"""
import sys
from pathlib import Path

import litert_lm.embedding_engine as EE

sys.path.insert(0, str(Path(__file__).parent))
import exp80_memory_dense as X  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")


def main():
    cases = X.load_bench()
    docs = X.corpus(cases, "enriched")
    eng = EE.EmbeddingEngine(str(X.MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    vec = {d["id"]: list(eng.compute_embedding(f"title: {' '.join(d['tags']) or 'none'} | text: {d['content']}", opts).embedding) for d in docs}
    qs = [(q, c["id"]) for c in cases for q in c["queries"]]
    qs += [(q, gid) for gid, _, _, para, surf in X.EN_GOLD for q in surf + para]
    qs += [(q, g) for q, g in X.CROSS]
    qs += [(q, None) for q in X.NONE_KO + X.NONE_EN]
    rows = []
    for q, gold in qs:
        qv = list(eng.compute_embedding(f"task: search result | query: {q}", opts).embedding)
        tokens = list(dict.fromkeys(q.split()))[:4]
        top_sp = max((X.score(tokens, d["b"]) for d in docs), default=0) or 1.0
        scored = sorted(((sum(a * b for a, b in zip(qv, vec[d["id"]])) + 0.05 * X.score(tokens, d["b"]) / top_sp, d["id"]) for d in docs), reverse=True)[:5]
        rows.append((gold, scored))
    print("margin | 정답 남음(정답이 상위5 안) | 답 있음 평균 남는 수 | 답 없음 평균 남는 수", flush=True)
    for m in (0.01, 0.02, 0.03, 0.04, 0.05, 0.07, 0.10, 1.0):
        kept_gold = in5 = 0
        n_ans, n_none, cnt_ans, cnt_none = 0, 0, 0, 0
        for gold, scored in rows:
            best = scored[0][0]
            kept = [i for s, i in scored if s >= best - m]
            if gold:
                n_ans += 1
                cnt_ans += len(kept)
                if gold in [i for _, i in scored]:
                    in5 += 1
                    kept_gold += gold in kept
            else:
                n_none += 1
                cnt_none += len(kept)
        print(f"{m:5.2f} | {kept_gold}/{in5} | {cnt_ans / n_ans:.2f} | {cnt_none / n_none:.2f}", flush=True)


if __name__ == "__main__":
    main()

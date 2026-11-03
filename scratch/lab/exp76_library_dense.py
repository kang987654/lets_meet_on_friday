"""exp76 — EmbeddingGemma 2 dense 검색과 바이그램 하이브리드 (0.41.0 M0 후속).

배경: exp74 의 바이그램(sparse) 검색은 조문 번호 10/10·같은 용어 7/10 이지만 바꿔 말한 질문 1/10.
dense 임베딩(EmbeddingGemma 2, Apache 2.0, LiteRT-LM — 앱 AAR 0.18.0 에도 EmbeddingEngine 이 있다)이 그 간극을 메우는지,
sparse 와 섞으면(RRF) 둘의 장점이 남는지 본다.

모델: scratch/embeddinggemma-2-740m.litertlm (범용 CPU/GPU 판, 사용자가 받아 둠 — git 제외).
프롬프트(모델 카드): 질의 `task: search result | query: {q}`, 문서 `title: {title} | text: {body}`.
조건: dense 768 / dense 256(Matryoshka — 앞 256차원 자르고 다시 정규화) / 하이브리드 RRF(바이그램 순위 + dense 순위, k=60).
질의: exp74 질문 30개 원문 + (있으면) exp75 에서 모델이 뽑은 검색어.
측정: @1·@3, PC CPU 색인 시간(조각당 ms), 벡터 저장 크기.
실행: %USERPROFILE%\\miniforge3\\envs\\friday\\python.exe -I scratch/lab/exp76_library_dense.py > scratch/lab/exp76_out.txt
"""
import re
import sys
import time
from pathlib import Path

import math
import pickle

sys.path.insert(0, str(Path(__file__).parent))
import litert_lm.embedding_engine as EE  # noqa: E402

import exp74_library_index as E  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
LAB = Path(__file__).parent
MODEL = LAB.parent / "embeddinggemma-2-740m.litertlm"
CACHE = LAB / "library_data" / "exp76_doc_emb.pkl"
RRF_K = 60


def doc_input(c):
    title = c["path"].split(" > ")[0] + " " + c["path"].split(" > ")[-1]
    return f"title: {title} | text: {c['text']}"


def query_input(q):
    return f"task: search result | query: {q}"


def normalize(v):
    """[WHY] numpy 없이(실험실 env 에 없다 — 패키지 내려받기를 피한다) 순수 파이썬으로 L2 정규화."""
    n = math.sqrt(sum(x * x for x in v)) or 1.0
    return [x / n for x in v]


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def main():
    laws = E.load_laws()
    chunks = [c for name, ls in laws.items() for c in E.chunk_law(name, ls, 500)]
    pre = [(c, E.bigrams(c["path"]), E.bigrams(c["text"])) for c in chunks]
    print(f"조각 {len(chunks):,} · 모델 {MODEL.name} ({MODEL.stat().st_size / 1e6:.0f}MB)", flush=True)

    engine = EE.EmbeddingEngine(str(MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)

    if CACHE.exists():
        docs = pickle.loads(CACHE.read_bytes())
        print(f"문서 임베딩 캐시 사용 {len(docs)}×{len(docs[0])}", flush=True)
    else:
        t0 = time.perf_counter()
        vecs = []
        batch = 16
        for i in range(0, len(chunks), batch):
            res = engine.compute_embedding_batch([doc_input(c) for c in chunks[i:i + batch]], opts)
            vecs.extend(r.embedding for r in res)
            if i % 800 == 0:
                print(f"  임베딩 {i:,}/{len(chunks):,} · {time.perf_counter() - t0:.0f}s", flush=True)
        dt = time.perf_counter() - t0
        docs = [list(v) for v in vecs]
        CACHE.write_bytes(pickle.dumps(docs))
        print(f"색인(PC CPU): {dt:.0f}s · 조각당 {1000 * dt / len(chunks):.1f}ms · 차원 {len(docs[0])}", flush=True)
    print(f"벡터 저장: 768차원 float32 {len(docs) * 768 * 4 / 1e6:.1f}MB · 256차원 {len(docs) * 256 * 4 / 1e6:.1f}MB "
          f"(int8 이면 1/4)", flush=True)
    docs256 = [normalize(v[:256]) for v in docs]

    def dense_rank(q, dim):
        qv = list(engine.compute_embedding(query_input(q), opts).embedding)
        mat = docs if dim == 768 else docs256
        qv = qv if dim == 768 else normalize(qv[:256])
        sims = [dot(v, qv) for v in mat]
        return sorted(range(len(sims)), key=lambda i: -sims[i])

    def sparse_rank(q):
        ranked = E.rerank(q, laws, pre)
        index = {id(c): i for i, c in enumerate(chunks)}
        return [index[id(c)] for c in ranked]

    def rrf(*ranks):
        score = {}
        for r in ranks:
            for pos, idx in enumerate(r[:200]):
                score[idx] = score.get(idx, 0.0) + 1.0 / (RRF_K + pos + 1)
        return sorted(score, key=lambda i: -score[i])

    def hit(order, law, gold, k):
        return E.hit([chunks[i] for i in order[:k]], law, gold, k)

    # 질의 세트: 원문 30 + exp75 모델 검색어(조건 R·질문 그대로)
    sets = {"원문": [(kind, q, law, gold) for kind, (q, law, gold) in E.QUESTIONS]}
    out75 = LAB / "exp75_out.txt"
    if out75.exists():
        text = out75.read_text(encoding="utf-8")
        block = text.split("## 조건 R · 질문 그대로")[-1].split("## 조건")[0] if "## 조건 R · 질문 그대로" in text else ""
        gold_of = {q: (kind, law, gold) for kind, (q, law, gold) in E.QUESTIONS}
        model_q = []
        for line in block.splitlines():
            m = re.match(r"^[O3X] \[(\S+)\] (.+?) → '(.+?)' →", line)
            if m and m.group(2) in gold_of:
                kind, law, gold = gold_of[m.group(2)]
                model_q.append((kind, m.group(3), law, gold))
        if model_q:
            sets["모델 검색어(exp75 R)"] = model_q

    for name, qs in sets.items():
        print(f"\n## 질의: {name} ({len(qs)}개)", flush=True)
        rows = {}
        for kind, q, law, gold in qs:
            s = sparse_rank(q)
            d768 = dense_rank(q, 768)
            d256 = dense_rank(q, 256)
            for label, order in (("sparse", s), ("dense768", d768), ("dense256", d256),
                                 ("hybrid768", rrf(s, d768)), ("hybrid256", rrf(s, d256))):
                r = rows.setdefault((label, kind), [0, 0, 0])
                r[0] += hit(order, law, gold, 1); r[1] += hit(order, law, gold, 3); r[2] += 1
            if kind == "바꿔말함":
                top = chunks[d256[0]]["path"]
                print(f"  dense256 1위: {q} → {top[-50:]}", flush=True)
        for label in ("sparse", "dense768", "dense256", "hybrid768", "hybrid256"):
            parts = [f"{kind} @1 {r[0]}/{r[2]} @3 {r[1]}/{r[2]}" for (lb, kind), r in rows.items() if lb == label]
            print(f"{label}: " + " · ".join(parts), flush=True)

    t0 = time.perf_counter()
    for _ in range(10):
        engine.compute_embedding(query_input("소멸시효 기간"), opts)
    print(f"\n질의 임베딩(PC CPU) {100 * (time.perf_counter() - t0):.0f}ms/건", flush=True)


if __name__ == "__main__":
    main()

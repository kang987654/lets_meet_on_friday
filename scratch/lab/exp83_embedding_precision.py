"""exp83 — 0.41.7 M6: 임베딩 엔진을 GPU 에서 정밀도 미지정으로 돌려도 되나(포크 보고서 C1 — EmbeddingGemma 2 카드는 fp16 금지).

앱(`EmbeddingGemmaRunner`)은 GPU 일 때 활성 정밀도를 넘기지 않는다. 채팅 엔진은 FP32 를 명시한다(ADR-021). exp76·78·80 품질 실측은 CPU 였다.
조건: CPU(기준) · GPU 미지정(앱 현행) · GPU FP32 · GPU FP16. 파이썬 `EmbeddingEngine` 은 정밀도 인자를 버리므로(kwargs 무시),
엔진을 만들기 직전에 네이티브 `litert_lm_embedding_engine_settings_set_activation_data_type` 을 끼워 부른다(열거값 FLOAT32=0, FLOAT16=1).
자료: exp80 기억 세트(태그 보강, 문서 입력 `title: 태그 | text: 내용` — 앱과 같은 꼴), 질의 `task: search result | query: …`.
지표: CPU 벡터와의 코사인(같은 입력, 최소·평균) · 정답 있는 질의 @1·@3 · 답 없는 질의의 최고 코사인 평균 · NaN 수.
주의: PC GPU 는 WebGPU 다(휴대폰은 OpenCL) — 미지정의 실제 정밀도가 기기와 다를 수 있다. 휴대폰 확인은 M7.
실행: python exp83_embedding_precision.py > exp83_out.txt
"""
import ctypes
import math
import sys
from pathlib import Path

import litert_lm as llm
import litert_lm.embedding_engine as EE

sys.path.insert(0, str(Path(__file__).parent))
import exp80_memory_dense as X  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

ACT = {"value": None}
_orig_create = EE.create_checked


def _create_with_activation(lib, name, *args):
    if name == "litert_lm_embedding_engine_create" and ACT["value"] is not None:
        fn = lib.litert_lm_embedding_engine_settings_set_activation_data_type
        fn.argtypes = [ctypes.c_void_p, ctypes.c_int]
        fn.restype = ctypes.c_int
        rc = fn(args[0], ACT["value"])
        print(f"  (정밀도 지정 {ACT['value']} → rc={rc})", flush=True)
    return _orig_create(lib, name, *args)


EE.create_checked = _create_with_activation


def make(backend, act):
    ACT["value"] = act
    try:
        return EE.EmbeddingEngine(str(X.MODEL), backend=backend, max_input_length=512)
    finally:
        ACT["value"] = None


def main():
    cases = X.load_bench()
    docs = X.corpus(cases, "enriched")
    doc_inputs = {d["id"]: f"title: {' '.join(d['tags']) or 'none'} | text: {d['content']}" for d in docs}
    questions = [(q, c["id"]) for c in cases for q in c["queries"]]
    for gid, _, _, para, surf in X.EN_GOLD:
        questions += [(q, gid) for q in surf + para]
    questions += list(X.CROSS)
    nones = X.NONE_KO + X.NONE_EN
    q_inputs = {q: f"task: search result | query: {q}" for q, _ in questions}
    q_inputs.update({q: f"task: search result | query: {q}" for q in nones})
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)

    conds = [("CPU", llm.Backend.CPU(), None), ("GPU-미지정", llm.Backend.GPU(), None),
             ("GPU-FP32", llm.Backend.GPU(), int(llm.ActivationDataType.FLOAT32)), ("GPU-FP16", llm.Backend.GPU(), int(llm.ActivationDataType.FLOAT16))]
    vecs = {}
    for name, backend, act in conds:
        print(f"[{name}] 엔진", flush=True)
        try:
            eng = make(backend, act)
        except Exception as e:  # noqa: BLE001
            print(f"  엔진 실패: {e}", flush=True)
            continue
        v = {}
        for key, text in list(doc_inputs.items()) + list(q_inputs.items()):
            v[key] = list(eng.compute_embedding(text, opts).embedding)
        vecs[name] = v
        del eng

    def dot(a, b):
        return sum(x * y for x, y in zip(a, b))

    ref = vecs.get("CPU")
    print("\n## 결과", flush=True)
    for name in vecs:
        v = vecs[name]
        nan = sum(1 for vec in v.values() if any(math.isnan(x) for x in vec))
        sims = [dot(v[k], ref[k]) for k in v] if ref and name != "CPU" else []
        at1 = at3 = 0
        for q, gold in questions:
            qv = v[q]
            ranked = sorted(docs, key=lambda d: -dot(qv, v[d["id"]]))
            ids = [d["id"] for d in ranked[:3]]
            at1 += ids[:1] == [gold]
            at3 += gold in ids
        none_top = sum(max(dot(v[q], v[d["id"]]) for d in docs) for q in nones) / len(nones)
        sim_s = f"CPU 와 코사인 최소 {min(sims):.4f} 평균 {sum(sims) / len(sims):.4f} · " if sims else ""
        print(f"{name:10s} {sim_s}@1 {at1}/{len(questions)} @3 {at3}/{len(questions)} · 답없음 최고 코사인 평균 {none_top:.3f} · NaN {nan}", flush=True)


if __name__ == "__main__":
    main()

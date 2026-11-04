"""exp77b — 추측 디코딩(MTP) OFF/ON/ADAPTIVE 를 PC GPU(WebGPU)로. exp77 ④ 는 CPU 에서 속도 차이가 없었다 — 앱 실기기 기본은 GPU 다.
같은 프롬프트 두 개(긴 설명·짧은 툴 없는 답)를 모드마다 두 번씩 재고, 디코드 속도는 benchmark 정보가 있으면 그것을 쓴다.
사용(0.19.0): PYTHONPATH=<0.19 폴더> python exp77b_spec_gpu.py  → exp77b_out.txt
"""
import pathlib
import sys
import time

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
PROMPTS = [
    "가을에 하기 좋은 산책 코스를 고르는 기준을 다섯 가지, 각각 두 문장으로 설명해줘.",
    "회의록을 잘 쓰는 방법을 단계별로 정리해줘.",
]


def text_of(res):
    content = res.get("content")
    return content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))


engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=4096, enable_benchmark=True)
for mode in [llm.SpeculativeDecodingMode.OFF, llm.SpeculativeDecodingMode.ON, llm.SpeculativeDecodingMode.ADAPTIVE] * 2:
    for p in PROMPTS:
        conv = engine.create_conversation(
            system_message="너는 한국어 비서다.",
            thinking_config=llm.ThinkingConfig(enable_thinking=False),
            sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
            speculative_decoding_config=llm.SpeculativeDecodingConfig(mode=mode),
        )
        t0 = time.perf_counter()
        res = conv.send_message(p)
        dt = time.perf_counter() - t0
        text = text_of(res)
        try:
            b = conv.get_benchmark_info()
            bench = {k: getattr(b, k) for k in dir(b) if not k.startswith("_") and not callable(getattr(b, k))}
        except Exception as e:  # noqa: BLE001
            bench = {"error": repr(e)}
        print(f"{mode.name:8s} {dt:5.1f}s 글자 {len(text):4d} 토큰 {conv.token_count} | {text[:40]!r} | {bench}", flush=True)
        conv.close()

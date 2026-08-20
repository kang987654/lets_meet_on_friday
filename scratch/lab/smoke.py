"""스모크 테스트 — 응답 dict 모양과 속도 확인.

목표:
  1. 툴을 선언한 대화에서 응답 dict 의 실제 키 구조를 원문 그대로 본다
     (0.15.0 의 tool_calls 위치가 문서화되어 있지 않다)
  2. 엔진 로드/추론 시간을 재 실험 규모를 정한다
  3. 렌더링된 preface 를 앱의 실기기 preface 와 비교한다
"""

import json
import sys
import time

import litert_lm as L

import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"
L.set_min_log_severity(L.LogSeverity.ERROR)

t0 = time.time()
print(f"엔진 로드 (backend={BACKEND}) …", flush=True)
engine = L.Engine(K.MODEL_PATH, backend=L.Backend.GPU() if BACKEND == "gpu" else L.Backend.CPU())
print(f"로드 완료: {time.time() - t0:.1f}s\n", flush=True)

print("=" * 70)
print("툴 스키마 4종")
for fn in K.ALL_TOOLS:
    d = L.tool_from_function(fn).get_tool_description()["function"]
    props = d["parameters"]["properties"]
    print(f"  {d['name']}: params={ {k: v.get('type') for k, v in props.items()} } required={d['parameters']['required']}")
print("=" * 70, flush=True)

PROMPT = "add_memory 툴을 사용해서 내 자전거 비밀번호 1234를 저장해줘"

t1 = time.time()
with engine.create_conversation(
    messages=K.few_shot_messages(),
    tools=K.ALL_TOOLS,
    automatic_tool_calling=False,
    system_message=K.system_prompt(),
    sampler_config=L.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
    thinking_config=L.ThinkingConfig(enable_thinking=False),
    constrained_decoding_config=L.ConstrainedDecodingConfig(enable=True),
    max_output_tokens=200,
) as conv:
    print(f"대화 생성: {time.time() - t1:.1f}s")

    # 앱의 renderPrefaceIntoString 대응 — 실기기 preface 와 비교
    try:
        preface = conv.render_message_to_string(L.Message.user(PROMPT))
        print(f"\n--- render_message_to_string (앞 1500자) ---\n{preface[:1500]}\n---", flush=True)
    except Exception as e:
        print(f"(preface 렌더 실패: {e})")

    print(f"\n입력: {PROMPT}", flush=True)
    t2 = time.time()
    response = conv.send_message(PROMPT)
    print(f"추론: {time.time() - t2:.1f}s\n")
    print("응답 dict 원문:")
    print(json.dumps(response, ensure_ascii=False, indent=1, default=str))
    calls, text = K.extract(response)
    print(f"\n추출 → 호출={calls}")
    print(f"추출 → 텍스트={text!r}")

engine.close()

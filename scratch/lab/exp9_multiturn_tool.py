"""exp9 — 툴 호출이 첫 턴에만 되고 이후 턴에서 사라지는지 재현.

실기기 감사 로그(2026-08-07 03:37~03:41):
  1) "위키에서 트와이스 검색해줘"            → TOOL_CALL SearchWikipedia  (성공)
  2) "나연에 대해서 자세히 알려줘"            → 툴 호출 없음, 환각
  3) "환각이 많은데 위키 검색내용으로 다시"   → 툴 호출 없음
  4) "그러니까 위키에서 나연에 대해 검색해서 알려줘" → 툴 호출 없음
     ("위키백과에서 검색하여 알려드리겠습니다" 라고 **말만** 하고 호출하지 않았다)

0.9.0 에서 대화를 재사용하도록 바꿨으므로, 그것이 원인인지 먼저 갈라야 한다.

  R) 대화 재사용 — 한 Conversation 으로 왕복 전체 (현재 앱)
  N) 턴마다 대화 재생성 — 히스토리를 initial_messages 로 재주입 (0.8.x 동작)

앱과 동일하게 툴 왕복을 완결시킨다: user → model(tool_call) → tool(response) → model(text).
"""

import sys

import kosmos_lab as K
import litert_lm as L

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

DAY_BLOCK = (
    "[System Data] Today: 2026-08-07 (Friday)\n"
    "[System Data] 오늘=2026-08-07, 내일=2026-08-08, 모레=2026-08-09, 다음주 월요일=2026-08-10"
)

SYSTEM = "\n".join([
    "[System]",
    "You are a personal assistant named Kosmos",
    "Always respond in Korean unless the user speaks another language.",
    DAY_BLOCK,
    "[Tool Usage Guidelines]",
    "For EVERY user request, first decide if one of your tools applies. "
    "If it does, you MUST call the tool. Do NOT merely promise or pretend — "
    "promising without calling is a failure.",
    '- The user asks you to remember something, or shares a fact/preference/password to keep — Korean triggers: "기억해", "기억해줘", "저장해줘", "메모해줘", "잊지 마": you MUST call `add_memory`.',
    '- The user asks to add an appointment, reservation, or event — Korean triggers: "예약", "약속", "일정 잡아줘", "일정 추가", "~하기로 했어": you MUST call `add_schedule`.',
    '- The user asks what is on their calendar — Korean triggers: "오늘 일정", "내일 일정", "스케줄 뭐 있어": you MUST call `get_schedule`.',
    '- The user asks a factual question you are not sure about — Korean triggers: "검색해줘", "찾아봐", "~가 뭐야?": call `search_wikipedia`.',
    'Resolve relative dates and times yourself from the [System Data] values above — '
    '"내일", "모레", "다음주 월요일", "오후 3시" are NOT missing information. '
    "Compute the absolute ISO 8601 value and call the tool. "
    "Never ask the user to restate a date you can compute.",
    "Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent.",
    "Never alter numbers, dates, or proper nouns the user gave you — copy them exactly.",
    "If you do not need a tool, simply provide your final response in plain text.",
])

# 실기기 감사 로그의 실제 위키 응답
TWICE_SUMMARY = (
    "Title: 트와이스\n---SUMMARY---\n트와이스(영어: Twice, 일본어: トゥワイス)는 대한민국의 다국적 "
    "9인조 걸 그룹이다. 2015년 10월 20일 JYP 엔터테인먼트 소속으로 데뷔하였고, 구성원은 한국인 5명, "
    "일본인 3명, 대만인 1명이다. 그룹명은 눈으로 한 번, 귀로 한 번, 감동을 준다는 뜻으로 오디션 프로 "
    "Sixteen에서 발표되었다.\n\n"
)

TURNS = [
    ("위키에서 트와이스 검색해줘", True),
    ("나연에 대해서 자세히 알려줘", True),
    ("그러니까 위키에서 나연에 대해 검색해서 알려줘", True),
]

TOOLS = list(K.ALL_TOOLS)


def conv_kwargs(messages):
    return dict(
        messages=messages or None,
        tools=TOOLS,
        automatic_tool_calling=False,
        system_message=SYSTEM,
        sampler_config=L.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        thinking_config=L.ThinkingConfig(enable_thinking=False),
        constrained_decoding_config=L.ConstrainedDecodingConfig(enable=True),
        max_output_tokens=200,
    )


def tool_result_json(name, args):
    """앱의 executor 를 흉내낸다 — 위키만 실제 내용을 돌려준다."""
    import json
    if name == "search_wikipedia":
        return json.dumps({"status": "success", "data": TWICE_SUMMARY}, ensure_ascii=False)
    return json.dumps({"status": "success"}, ensure_ascii=False)


def one_turn(conv, text):
    """user → (tool_call → tool response) → 최종 텍스트. (호출목록, 최종텍스트) 반환."""
    resp = conv.send_message(text)
    calls, out = K.extract(resp)
    if not calls:
        return [], out
    call = calls[0]
    name = call.get("name") or ""
    payload = tool_result_json(name, call.get("arguments") or {})
    resp2 = conv.send_message(
        L.Message.tool(L.Contents.of(L.Content.ToolResponse(name, payload)))
    )
    _, out2 = K.extract(resp2)
    return calls, out2


def run_reuse(engine):
    print(f"\n{'=' * 92}\n[R) 대화 재사용 — 현재 앱]\n{'=' * 92}")
    ok = 0
    with engine.create_conversation(**conv_kwargs(K.few_shot_messages())) as conv:
        for i, (text, want_call) in enumerate(TURNS, 1):
            calls, out = one_turn(conv, text)
            got = bool(calls)
            ok += got == want_call
            names = ",".join(c.get("name", "?") for c in calls) or "-"
            print(f"  {'OK  ' if got == want_call else 'FAIL'} 턴{i} {text}")
            print(f"        호출={names}  인자={calls[0].get('arguments') if calls else None}")
            print(f'        답변="{out[:110].replace(chr(10), " ")}"', flush=True)
    print(f"\n  ── 툴 호출 {ok}/{len(TURNS)}")
    return ok


def run_recreate(engine):
    print(f"\n{'=' * 92}\n[N) 턴마다 대화 재생성 — 0.8.x 동작]\n{'=' * 92}")
    ok = 0
    history = []
    for i, (text, want_call) in enumerate(TURNS, 1):
        with engine.create_conversation(**conv_kwargs(K.few_shot_messages() + history)) as conv:
            calls, out = one_turn(conv, text)
        got = bool(calls)
        ok += got == want_call
        names = ",".join(c.get("name", "?") for c in calls) or "-"
        print(f"  {'OK  ' if got == want_call else 'FAIL'} 턴{i} {text}")
        print(f"        호출={names}  인자={calls[0].get('arguments') if calls else None}")
        print(f'        답변="{out[:110].replace(chr(10), " ")}"', flush=True)
        history.append(L.Message.user(text))
        history.append(L.Message.model(L.Contents.of(out or " ")))
    print(f"\n  ── 툴 호출 {ok}/{len(TURNS)}")
    return ok


def main():
    L.set_min_log_severity(L.LogSeverity.ERROR)
    print("[exp9] 엔진 로드 중 …", flush=True)
    engine = L.Engine(K.MODEL_PATH, backend=L.Backend.GPU() if BACKEND == "gpu" else L.Backend.CPU())
    print("[exp9] 준비 완료\n", flush=True)

    r = run_reuse(engine)
    n = run_recreate(engine)
    print(f"\n=== 결과: 재사용 {r}/{len(TURNS)}  vs  재생성 {n}/{len(TURNS)} ===")
    engine.close()


if __name__ == "__main__":
    main()

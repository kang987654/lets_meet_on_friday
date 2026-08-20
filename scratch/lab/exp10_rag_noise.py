"""exp10 — 무관한 RAG 기억이 매 턴 붙는 것이 툴 호출을 죽이는지 확인.

exp9 에서 PC 는 재사용/재생성 모두 3/3 으로 툴을 불렀는데 실기기는 1턴만 불렀다. 기기와
하네스의 가장 큰 프롬프트 차이는 **RAG 블록**이다.

`KnowledgeRepositoryImpl.searchByVector` 에는 유사도 임계값이 없다(주석: "유사도 0.3 이상
필터링 가능, 일단 모두"). 그래서 저장된 메모가 있으면 질문과 무관해도 상위 3건이 **매 턴**
사용자 메시지 앞에 `[Context / Knowledge]` 로 붙는다. 실기기에는 비밀번호·선호 같은 메모가
저장돼 있다.

  A) RAG 블록 없음                 (exp9 재현 — 기준선)
  B) 무관한 기억 3건이 매 턴 붙음  (실기기 조건)
  C) 임계값 통과분만 — 이 질문엔 관련 기억이 없으므로 블록 자체가 없음 (제안 수정안)

C 는 A 와 프롬프트가 같아야 한다. 즉 "임계값을 넣으면 A 로 돌아간다"를 확인하는 것이다.
"""

import json
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

# 실기기에 실제로 저장돼 있을 법한, 질문과 무관한 기억들
RAG_NOISE = (
    "[Context / Knowledge]\n"
    "다음은 사용자에 대한 과거 기억(Memory)입니다. 답변에 필요하다면 적극 활용하세요:\n"
    "- 자전거 비밀번호는 1234\n"
    "- 우리집 와이파이 비번은 aB3x9k\n"
    "- 커피보다 녹차를 더 좋아함"
)

TWICE_SUMMARY = (
    "Title: 트와이스\n---SUMMARY---\n트와이스(영어: Twice, 일본어: トゥワイス)는 대한민국의 다국적 "
    "9인조 걸 그룹이다. 2015년 10월 20일 JYP 엔터테인먼트 소속으로 데뷔하였고, 구성원은 한국인 5명, "
    "일본인 3명, 대만인 1명이다. 그룹명은 눈으로 한 번, 귀로 한 번, 감동을 준다는 뜻으로 오디션 프로 "
    "Sixteen에서 발표되었다.\n\n"
)

TURNS = [
    "위키에서 트와이스 검색해줘",
    "나연에 대해서 자세히 알려줘",
    "그러니까 위키에서 나연에 대해 검색해서 알려줘",
]


def conv_kwargs(messages):
    return dict(
        messages=messages or None,
        tools=list(K.ALL_TOOLS),
        automatic_tool_calling=False,
        system_message=SYSTEM,
        sampler_config=L.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        thinking_config=L.ThinkingConfig(enable_thinking=False),
        constrained_decoding_config=L.ConstrainedDecodingConfig(enable=True),
        max_output_tokens=200,
    )


def tool_result_json(name):
    if name == "search_wikipedia":
        return json.dumps({"status": "success", "data": TWICE_SUMMARY}, ensure_ascii=False)
    return json.dumps({"status": "success"}, ensure_ascii=False)


def one_turn(conv, text):
    resp = conv.send_message(text)
    calls, out = K.extract(resp)
    if not calls:
        return [], out
    name = calls[0].get("name") or ""
    resp2 = conv.send_message(
        L.Message.tool(L.Contents.of(L.Content.ToolResponse(name, tool_result_json(name))))
    )
    _, out2 = K.extract(resp2)
    return calls, out2


def run(engine, label, with_rag):
    print(f"\n{'=' * 92}\n[{label}]\n{'=' * 92}")
    called = 0
    with engine.create_conversation(**conv_kwargs(K.few_shot_messages())) as conv:
        for i, text in enumerate(TURNS, 1):
            prompt = f"{RAG_NOISE}\n\n{text}" if with_rag else text
            calls, out = one_turn(conv, prompt)
            called += bool(calls)
            names = ",".join(c.get("name", "?") for c in calls) or "-"
            print(f"  {'OK  ' if calls else 'FAIL'} 턴{i} {text}")
            print(f"        호출={names}  인자={calls[0].get('arguments') if calls else None}")
            print(f'        답변="{out[:110].replace(chr(10), " ")}"', flush=True)
    print(f"\n  ── 툴 호출 {called}/{len(TURNS)}")
    return called


def main():
    L.set_min_log_severity(L.LogSeverity.ERROR)
    print("[exp10] 엔진 로드 중 …", flush=True)
    engine = L.Engine(K.MODEL_PATH, backend=L.Backend.GPU() if BACKEND == "gpu" else L.Backend.CPU())
    print("[exp10] 준비 완료", flush=True)

    a = run(engine, "A) RAG 블록 없음 (기준선)", with_rag=False)
    b = run(engine, "B) 무관한 기억 3건이 매 턴 (실기기 조건)", with_rag=True)
    print(f"\n=== 결과: 기준선 {a}/{len(TURNS)}  vs  RAG 잡음 {b}/{len(TURNS)} ===")
    engine.close()


if __name__ == "__main__":
    main()

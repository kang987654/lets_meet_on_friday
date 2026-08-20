"""exp13 — `SearchMemory` 실행부의 **미일치 폴백**(태그 목록 + 재호출 유도)이 실제로 듣는지.

exp12 에서 툴 선택은 8/8 이었으나, 회상 5건 중 2건이 **키워드는 옳은데 글자가 안 겹쳐**
못 찾았다("좋아하는 것" ↔ "커피보다 녹차를 더 좋아함", 태그는 `선호도`). 실행부를 고쳤다:
  ① 본문뿐 아니라 태그도 검색
  ② 그래도 0건이면 **저장된 분류(태그) 목록**을 주고 그 단어로 재호출하도록 유도

이 파일은 그 고친 실행부를 그대로 옮겨 재현한다. 앱의 툴 루프 상한이 3턴이므로
"조회 실패 → 태그로 재조회 → 답변"까지 허용한다.
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
    '- The user asks about something they told you earlier — Korean triggers: "뭐였지", "뭐라고 했지", "내가 알려준", "기억나", "저장한 거": you MUST call `search_memory` with a short noun keyword. This is a LOOKUP, never call `add_memory` for it.',
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


def search_memory(keyword: str) -> dict:
    """사용자가 이전에 저장해 둔 기억(메모)에서 찾는다. 사용자가 예전에 알려준 사실·비밀번호·선호를 다시 물으면 반드시 쓴다. 추측해서 답하지 말고 이 도구로 확인한다.

    Args:
        keyword: 찾을 핵심 키워드. 문장이 아니라 명사 위주의 짧은 단어로 쓴다. 예: '자전거 비밀번호', '와이파이', '알레르기'
    """
    raise AssertionError("실행되지 않아야 한다")


TOOLS = [K.add_schedule, K.get_schedule, K.add_memory, search_memory, K.search_wikipedia]

# (본문, 태그) — 앱은 AddMemory 저장 시 모델이 붙인 태그를 함께 보관한다.
MEMORY_DB = [
    ("자전거 비밀번호는 1234", ["비밀번호", "자전거"]),
    ("우리집 와이파이 비번은 aB3x9k", ["비밀번호", "와이파이"]),
    ("커피보다 녹차를 더 좋아함", ["선호도", "음료"]),
    ("알레르기: 땅콩", ["알레르기", "건강"]),
]


def fmt(items):
    return "\n".join(f"- {c} [{', '.join(t)}]" for c, t in items)


def lookup(keyword):
    """앱 `SearchMemoryToolExecutor` 를 그대로 옮긴 것."""
    tokens = []
    for t in keyword.split():
        t = t.strip()
        if t and t not in tokens:
            tokens.append(t)
    tokens = tokens[:4]

    scored = {}
    for t in tokens:
        for c, tags in MEMORY_DB:
            # 본문 LIKE '%t%' 또는 태그 정확 일치
            if t in c or t in tags:
                key = (c, tuple(tags))
                scored[key] = scored.get(key, 0) + 1
    ranked = sorted(scored.items(), key=lambda kv: -kv[1])[:3]
    if ranked:
        return fmt([(c, list(tg)) for (c, tg), _ in ranked])

    if not MEMORY_DB:
        return "저장된 기억이 하나도 없습니다. 사용자에게 저장된 것이 없다고 답하세요. 추측하지 마세요."

    tags = []
    for _, tg in MEMORY_DB:
        for t in tg:
            if t not in tags:
                tags.append(t)
    out = f"'{keyword}' 로는 일치하는 기억이 없습니다. "
    if tags:
        out += ("저장된 기억에 붙은 분류는 다음과 같습니다: " + ", ".join(tags[:20]) + ".\n"
                "이 중 질문에 해당할 만한 분류가 있으면 그 단어로 `search_memory` 를 한 번 더 "
                "호출하세요. 해당하는 분류가 없으면 그런 기억이 없다고 답하세요.\n")
    out += "가장 최근에 저장된 기억:\n" + fmt(MEMORY_DB[:3])
    out += "\n이 목록에 질문의 답이 없으면 없다고 답하세요. 절대 지어내지 마세요."
    return out


def tool_result(name, args):
    if name == "search_memory":
        return json.dumps({"status": "success", "data": lookup(args.get("keyword", ""))}, ensure_ascii=False)
    return json.dumps({"status": "success", "message": "Successfully saved."}, ensure_ascii=False)


# (입력, 답변에 담겨야 할 문자열 또는 None=없다고 답해야 함)
CASES = [
    ("내 자전거 비밀번호 뭐였지?", "1234"),
    ("우리집 와이파이 비번 알려줘", "aB3x9k"),
    ("내가 뭘 좋아한다고 했지?", "녹차"),
    ("나 못 먹는 음식 있었나? 저장해둔 거 있잖아", "땅콩"),
    ("내 여권번호 뭐였지?", None),
]


def conv_kwargs():
    return dict(
        messages=K.few_shot_messages(),
        tools=TOOLS,
        automatic_tool_calling=False,
        system_message=SYSTEM,
        sampler_config=L.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        thinking_config=L.ThinkingConfig(enable_thinking=False),
        constrained_decoding_config=L.ConstrainedDecodingConfig(enable=True),
        max_output_tokens=150,
    )


def main():
    L.set_min_log_severity(L.LogSeverity.ERROR)
    print("[exp13] 엔진 로드 중 …", flush=True)
    engine = L.Engine(K.MODEL_PATH, backend=L.Backend.GPU() if BACKEND == "gpu" else L.Backend.CPU())
    print("[exp13] 준비 완료\n", flush=True)

    ok = 0
    with engine.create_conversation(**conv_kwargs()) as conv:
        for text, want in CASES:
            resp = conv.send_message(text)
            calls, out = K.extract(resp)
            trace = []
            hops = 0
            # 앱 툴 루프(상한 3턴)와 같이 재호출을 허용한다
            while calls and hops < 2:
                name = calls[0].get("name")
                args = calls[0].get("arguments") or {}
                trace.append(f"{name}({args.get('keyword', args)})")
                resp = conv.send_message(
                    L.Message.tool(L.Contents.of(L.Content.ToolResponse(name, tool_result(name, args))))
                )
                calls, out = K.extract(resp)
                hops += 1

            good = (want is None and ("없" in out or "찾을 수 없" in out)) or (want is not None and want in out)
            ok += good
            print(f"  {'OK  ' if good else 'FAIL'} {text}")
            print(f"        호출: {' → '.join(trace) or '-'}")
            print(f'        답변: "{out[:130].replace(chr(10), " ")}"', flush=True)

    print(f"\n  ── 회상 성공 {ok}/{len(CASES)}")
    engine.close()


if __name__ == "__main__":
    main()

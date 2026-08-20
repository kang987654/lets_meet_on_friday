"""exp12 — `search_memory` 툴이 회상 질문에서 실제로 불리는지 검증.

배경: exp11 로 임베더가 영어 전용임이 확정돼(한국어 분리도 0.000, top-1 1/7) 매 턴 자동 RAG
주입을 없애고 조회를 툴로 옮겼다. 그런데 exp7 에서 **회상 질문에 모델이 `add_memory` 를 잘못
부르는 것**이 관찰됐으므로(저장과 조회의 한국어 트리거가 비슷하다), 조회 전용 툴을 준 뒤에도
방향을 헷갈리지 않는지 확인해야 한다.

확인할 것:
  1) 회상 질문 → `search_memory` 를 부르는가 (그리고 `add_memory` 를 부르지 않는가)
  2) 키워드가 **문장이 아니라 짧은 명사**로 오는가 (실행부가 LIKE 부분 일치라 문장이면 0건)
  3) 저장 질문은 여전히 `add_memory` 로 가는가 (회귀 확인)
  4) 툴이 "없다"를 돌려주면 지어내지 않고 없다고 답하는가
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


# --- 앱의 KosmosToolDeclarations 와 이름·설명을 맞춘 새 툴 ---------------------
def search_memory(keyword: str) -> dict:
    """사용자가 이전에 저장해 둔 기억(메모)에서 찾는다. 사용자가 예전에 알려준 사실·비밀번호·선호를 다시 물으면 반드시 쓴다. 추측해서 답하지 말고 이 도구로 확인한다.

    Args:
        keyword: 찾을 핵심 키워드. 문장이 아니라 명사 위주의 짧은 단어로 쓴다. 예: '자전거 비밀번호', '와이파이', '알레르기'
    """
    raise AssertionError("실행되지 않아야 한다")


TOOLS = [K.add_schedule, K.get_schedule, K.add_memory, search_memory, K.search_wikipedia]

# 앱의 SearchMemoryToolExecutor 를 흉내낸 저장소
MEMORY_DB = [
    "자전거 비밀번호는 1234",
    "우리집 와이파이 비번은 aB3x9k",
    "커피보다 녹차를 더 좋아함",
    "알레르기: 땅콩",
]

# (입력, 기대 툴, 기대 키워드가 담아야 할 문자열 또는 None)
CASES = [
    ("내 자전거 비밀번호 뭐였지?", "search_memory", "자전거"),
    ("우리집 와이파이 비번 알려줘", "search_memory", "와이파이"),
    ("내가 뭘 좋아한다고 했지?", "search_memory", None),
    ("나 못 먹는 음식 있었나? 저장해둔 거 있잖아", "search_memory", None),
    ("내 여권번호 뭐였지?", "search_memory", "여권"),          # DB 에 없음 → 없다고 답해야 함
    ("내 자전거 비밀번호는 1234야, 기억해줘", "add_memory", None),  # 저장 방향 회귀
    ("나 커피보다 녹차를 더 좋아해. 기억해둬", "add_memory", None),
    ("내일 3시에 치과 예약 잡아줘", "add_schedule", None),          # 다른 툴 회귀
]


def lookup(keyword):
    """앱 실행부와 같은 어휘 검색(토큰 분리 → 부분 일치 → 일치 토큰 수 정렬)."""
    tokens = [t for t in keyword.split() if t][:4]
    scored = {}
    for t in tokens:
        for m in MEMORY_DB:
            if t in m:
                scored[m] = scored.get(m, 0) + 1
    ranked = sorted(scored.items(), key=lambda kv: -kv[1])[:3]
    if not ranked:
        return f"저장된 기억에 '{keyword}' 에 해당하는 내용이 없습니다. 사용자에게 그런 기억이 없다고 답하세요. 추측하지 마세요."
    return "\n".join(f"- {m}" for m, _ in ranked)


def tool_result(name, args):
    if name == "search_memory":
        return json.dumps({"status": "success", "data": lookup(args.get("keyword", ""))}, ensure_ascii=False)
    return json.dumps({"status": "success", "message": "Successfully saved."}, ensure_ascii=False)


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
    print("[exp12] 엔진 로드 중 …", flush=True)
    engine = L.Engine(K.MODEL_PATH, backend=L.Backend.GPU() if BACKEND == "gpu" else L.Backend.CPU())
    print("[exp12] 준비 완료\n", flush=True)

    ok = 0
    kw_ok = 0
    kw_total = 0
    with engine.create_conversation(**conv_kwargs()) as conv:
        for text, want_tool, want_kw in CASES:
            resp = conv.send_message(text)
            calls, out = K.extract(resp)
            got_tool = calls[0].get("name") if calls else None
            args = (calls[0].get("arguments") or {}) if calls else {}
            good = got_tool == want_tool
            ok += good

            note = ""
            if good and got_tool == "search_memory":
                kw = str(args.get("keyword", ""))
                kw_total += 1
                # 키워드가 문장이면 실행부의 LIKE 가 아무것도 못 맞힌다
                short = len(kw.split()) <= 3 and "?" not in kw
                contains = want_kw is None or want_kw in kw
                if short and contains:
                    kw_ok += 1
                else:
                    note = f"   ⚠ 키워드 부적합: {kw!r}"

            if calls:
                payload = tool_result(got_tool, args)
                resp2 = conv.send_message(
                    L.Message.tool(L.Contents.of(L.Content.ToolResponse(got_tool, payload)))
                )
                _, out = K.extract(resp2)

            print(f"  {'OK  ' if good else 'FAIL'} {text}")
            print(f"        호출={got_tool}  인자={args}{note}")
            print(f'        답변="{out[:110].replace(chr(10), " ")}"', flush=True)

    print(f"\n  ── 툴 선택 {ok}/{len(CASES)}   키워드 적합 {kw_ok}/{kw_total}")
    engine.close()


if __name__ == "__main__":
    main()

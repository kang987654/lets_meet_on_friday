"""exp51b — 런타임 0.18.0·FLOAT32 활성화에서 툴 호출·멀티모달 회귀 스모크 (0.37.0 M0).

depth 0(새 대화)에서 툴 선택·인자 보존·무호출, 이미지 1건, 오디오 전사 1건. 앱 실기기 백엔드처럼 GPU(이미지 GPU·오디오 CPU).
게이트: 0.16.0 FP16 기준선과 같은 통과 수 이상, 기준선이 통과한 항목을 새로 깨지 않을 것.
사용: python exp51b_smoke.py [fp16|fp32]
"""
import importlib.metadata
import re
import sys

import litert_lm as llm

import kosmos_lab as lab

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS = lab.fixture("system_instruction.txt")
TODAY, TOMORROW, NEXT_MON = re.search(r"오늘=(\S+) \(\w+\), 내일=(\S+), 모레=\S+, 다음주 월요일=(\S+)", SYS).groups()


def args_has(name, **want):
    """want: 인자 이름 → 들어 있어야 할 문자열(부분 일치)."""
    def check(call):
        if call is None or call["name"] != name:
            return False
        a = call.get("arguments") or {}
        return all(w in str(a.get(k, "")) for k, w in want.items())
    return check


CASES = [
    ("메모 저장", "내 와이파이 비밀번호는 kosmos2580이야 기억해줘", args_has("add_memory", content="kosmos2580")),
    ("메모 숫자", "회사 주차는 B3 구역 117번이야, 기억해 둬", args_has("add_memory", content="117")),
    ("메모 검색", "내가 알려준 와이파이 비번 뭐였지?", args_has("search_memory")),
    ("일정 추가", "내일 오후 3시에 치과 예약 잡아줘", args_has("add_schedule", start_time=f"{TOMORROW}T15:00")),
    ("일정 다음주", "다음주 월요일 10시 팀 회의 일정 추가해줘", args_has("add_schedule", start_time=f"{NEXT_MON}T10:00")),
    ("일정 분", "오늘 저녁 7시 30분에 친구랑 저녁 약속 추가해줘", args_has("add_schedule", start_time=f"{TODAY}T19:30")),
    # 선언 규칙: 오늘이 아닌 날은 'week'
    ("일정 조회", "내일 일정 뭐 있어?", args_has("get_schedule", date="week")),
    ("위키", "에스파가 뭐야? 검색해줘", args_has("search_wikipedia")),
    ("리마인더", "오늘 오후 6시에 약 먹으라고 알려줘", args_has("add_reminder", time=f"{TODAY}T18:00")),
    ("리마인더 내일", "내일 아침 8시 15분에 출근 준비 리마인드 해줘", args_has("add_reminder", time=f"{TOMORROW}T08:15")),
    ("무호출 인사", "안녕! 오늘 기분 어때?", lambda call: call is None),
    ("무호출 감사", "고마워, 덕분에 살았어", lambda call: call is None),
]


def first_call(res):
    calls = res.get("tool_calls") or []
    return calls[0]["function"] if calls else None


def text_of(res):
    return "".join(p.get("text", "") for p in (res.get("content") or []) if isinstance(p, dict))


def main(precision):
    kwargs = {} if precision == "fp16" else {"activation_data_type": llm.ActivationDataType.FLOAT32}
    engine = llm.Engine(lab.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=lab.ENGINE_MAX_TOKENS,
                        max_num_images=lab.MAX_IMAGES_PER_TURN, vision_backend=llm.Backend.GPU,
                        audio_backend=llm.Backend.CPU, **kwargs)
    print(f"## litert-lm {importlib.metadata.version('litert-lm')} GPU {precision}", flush=True)
    ok = 0
    for name, utterance, check in CASES:
        conv = lab.create_chat_conversation(engine)
        res = conv.send_message(lab.with_turn_reminder(utterance))
        call = first_call(res)
        hit = check(call)
        ok += hit
        shown = f"{call['name']} {call.get('arguments')}" if call else f"(답) {text_of(res)[:60]!r}"
        print(f"{precision} {'OK' if hit else 'X '} {name}: {shown}", flush=True)
        conv.close()

    conv = lab.create_chat_conversation(engine, tools=[])
    res = conv.send_message(llm.Message.user(llm.Contents([
        llm.Content.ImageFile(str(lab.FIXTURES / "vision_shapes.png")),
        llm.Content.Text("이 그림에 무엇이 있는지 짧게 말해줘."),
    ])))
    conv.close()
    vision = text_of(res)
    print(f"{precision} 이미지: {vision[:80]!r}", flush=True)

    tconv = engine.create_conversation(
        system_message=lab.fixture("transcribe_system.txt"),
        automatic_tool_calling=False,
        thinking_config=llm.ThinkingConfig(enable_thinking=False),
        sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
    )
    res = tconv.send_message(llm.Message.user(llm.Contents([
        llm.Content.AudioFile(str(lab.FIXTURES / "voice_input_tts.wav")),
    ])))
    tconv.close()
    print(f"{precision} 오디오: {text_of(res)[:80]!r}", flush=True)
    print(f"== {precision} 툴 {ok}/{len(CASES)}", flush=True)
    engine.close()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "fp16")

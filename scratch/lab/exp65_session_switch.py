"""exp65 — 0.39.1 M0: 부수 추론(oneShot) 뒤 첫 채팅 턴의 3.3~3.5초는 어디서 나는가.

실기기(0.39.0 M1): 부수 추론 뒤 채팅 턴에서 "잠금 → 대화 준비" 구간이 3.3~3.5초(재생성 로그 없음). 그 구간에 있는 것은 재사용 판정의
`getTokenCount()`(네이티브) 하나다. 앱과 같은 순서를 PC 에서 재현해 가른다.
  A 대조: 채팅 대화 2턴 → token_count 읽기 시간 → 3턴째 시간
  B 부수: 채팅 대화 2턴 → 부수 대화(요약 지시) 만들기·추론·닫기 → token_count 읽기 시간 → 3턴째 시간
  C 부수, 조회 없이: B 와 같되 token_count 를 읽지 않고 바로 3턴째 — 비용이 조회 탓인지, 첫 호출에 붙는 대화 복원 비용인지 가른다
각 3회.
"""
import sys
import time

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS = K.fixture("system_instruction.txt")
SUMMARY_SYS = K.fixture("episode_summary_system.txt")
engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU(), max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
TURNS = ["안녕! 한 문장으로만 답해 줘.", "오늘 날씨가 좋네. 한 문장으로만 답해 줘.", "고마워. 한 문장으로만 답해 줘."]


def timed(f):
    t = time.perf_counter()
    r = f()
    return r, time.perf_counter() - t


for rep in range(3):
    for arm in ("A", "B", "C"):
        conv = K.create_chat_conversation(engine, tools=K.ALL_TOOLS, system_message=SYS)
        for u in TURNS[:2]:
            conv.send_message(K.with_turn_reminder(u))
        side = 0.0
        if arm in ("B", "C"):
            def run_side():
                c2 = engine.create_conversation(system_message=SUMMARY_SYS, automatic_tool_calling=False,
                                                thinking_config=llm.ThinkingConfig(enable_thinking=False),
                                                sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0))
                c2.send_message("다음 대화를 요약하세요.\n\n사용자: 안녕\n비서: 안녕하세요\n사용자: 오늘 날씨가 좋네\n비서: 정말 좋네요")
                c2.close()
            _, side = timed(run_side)
        count, t_count = timed(lambda: conv.token_count) if arm != "C" else (None, 0.0)
        _, t_turn = timed(lambda: conv.send_message(K.with_turn_reminder(TURNS[2])))
        conv.close()
        print(f"[{rep}] {arm}: 부수 {side:5.2f}s · token_count 읽기 {t_count:5.2f}s ({count}) · 3턴째 {t_turn:5.2f}s", flush=True)

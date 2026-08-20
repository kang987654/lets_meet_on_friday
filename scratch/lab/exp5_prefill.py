"""exp5 — 대화 재생성(전체 프리필) 대 재사용의 비용 측정.

앱의 `GemmaModelRunner.getOrCreateConversation` 은 `systemInstruction` 이 달라지면 대화를
파괴하고 다시 만든다. 그런데 `PromptAssembler` 의 시스템 지시에는 **분 단위 현재 시각**과
**RAG 로 검색된 기억**이 들어 있어서, 턴마다 달라지는 것이 정상이다. 즉 재사용 판정이 거의
항상 거짓이 되어 매 턴 전체(시스템 지시 + 툴 선언 + few-shot + 히스토리)를 다시 프리필한다.

이 실험은 그 차이를 초로 잰다.
  A) 같은 대화로 3턴 연속 (재사용)
  B) 턴마다 새 대화 생성 + 히스토리를 initial_messages 로 재주입 (현재 앱 동작)
"""

import time

import kosmos_lab as K
import litert_lm as L

K.use_utf8()

TURNS = [
    "내 자전거 비밀번호는 1234야, 기억해줘",
    "오늘 일정 알려줘",
    "고마워",
]


def conv_kwargs(system, messages):
    return dict(
        messages=messages or None,
        tools=list(K.ALL_TOOLS),
        automatic_tool_calling=False,
        system_message=system,
        sampler_config=L.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        thinking_config=L.ThinkingConfig(enable_thinking=False),
        constrained_decoding_config=L.ConstrainedDecodingConfig(enable=True),
        max_output_tokens=80,
    )


def main():
    L.set_min_log_severity(L.LogSeverity.ERROR)
    t0 = time.time()
    print("[exp5] 엔진 로드 중 …", flush=True)
    engine = L.Engine(K.MODEL_PATH, backend=L.Backend.GPU())
    print(f"[exp5] 준비 완료 ({time.time() - t0:.1f}s)\n", flush=True)

    system = K.system_prompt()
    few = K.few_shot_messages()

    # --- A) 대화 재사용 ---
    print("=== A) 대화 재사용 (systemInstruction 고정) ===")
    a_times = []
    with engine.create_conversation(**conv_kwargs(system, list(few))) as conv:
        for i, turn in enumerate(TURNS, 1):
            t = time.time()
            resp = conv.send_message(turn)
            dt = time.time() - t
            a_times.append(dt)
            calls, text = K.extract(resp)
            name = ",".join(c.get("name", "?") for c in calls) or "-"
            print(f"  턴{i}: {dt:5.1f}s  호출={name}  {text[:40]!r}")

    # --- B) 턴마다 재생성 ---
    print("\n=== B) 턴마다 대화 재생성 (현재 앱: 시각/RAG 변동으로 매 턴 재생성) ===")
    b_times = []
    history = []
    for i, turn in enumerate(TURNS, 1):
        # 앱과 동일: 분 단위 시각이 달라진 시스템 지시
        turn_system = K.system_prompt(now=f"2026-08-07 00:{44 + i:02d}")
        t = time.time()
        with engine.create_conversation(**conv_kwargs(turn_system, few + history)) as conv:
            resp = conv.send_message(turn)
        dt = time.time() - t
        b_times.append(dt)
        calls, text = K.extract(resp)
        name = ",".join(c.get("name", "?") for c in calls) or "-"
        print(f"  턴{i}: {dt:5.1f}s  호출={name}  {text[:40]!r}")
        history.append(L.Message.user(turn))
        history.append(L.Message.model(L.Contents.of(text or " ")))

    print("\n=== 합계 ===")
    print(f"  A 재사용 : {sum(a_times):6.1f}s  (턴별 {[round(x, 1) for x in a_times]})")
    print(f"  B 재생성 : {sum(b_times):6.1f}s  (턴별 {[round(x, 1) for x in b_times]})")
    delta = sum(b_times) - sum(a_times)
    print(f"  차이     : {delta:+6.1f}s  ({delta / max(len(TURNS), 1):+.1f}s/턴)")

    engine.close()


if __name__ == "__main__":
    main()

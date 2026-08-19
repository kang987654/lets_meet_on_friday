"""exp32b — 대조군: 기존 모델 + 동일 혼합 구성(GPU/GPU/CPU오디오) + 무음 전사.

exp32 에서 GPU 변형이 TF_LITE_AUDIO_ENCODER_HW 부재로 대화 생성에 실패했다.
기존 모델이 같은 구성에서 통과하면 "GPU 변형에 오디오 인코더가 없다"가 확정된다.
"""

import pathlib

import litert_lm as llm

import kosmos_lab as lab

SILENCE = pathlib.Path(__file__).parent / "fixtures" / "silence_2s.wav"


def main():
    engine = llm.Engine(
        lab.MODEL_PATH,  # 기존 gemma-4-E4B-it.litertlm
        backend=llm.Backend.GPU,
        vision_backend=llm.Backend.GPU,
        audio_backend=llm.Backend.CPU,
        max_num_tokens=lab.ENGINE_MAX_TOKENS,
        max_num_images=lab.MAX_IMAGES_PER_TURN,
    )
    print("1. 기존 모델 혼합 구성 엔진 생성 OK")

    conv = lab.create_chat_conversation(engine)
    res = conv.send_message(lab.with_turn_reminder("다음주 월요일 10시 팀 회의 일정 추가해줘"))
    calls = res.get("tool_calls") or []
    fn = calls[0]["function"] if calls else None
    print(f"2. 채팅 턴: {fn['name'] if fn else '(미호출)'} start_time={(fn.get('arguments') or {}).get('start_time') if fn else None!r}")
    conv.close()

    tconv = engine.create_conversation(
        system_message=lab.fixture("transcribe_system.txt"),
        automatic_tool_calling=False,
        thinking_config=llm.ThinkingConfig(enable_thinking=False),
        sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
    )
    res = tconv.send_message(llm.Message.user(llm.Contents([llm.Content.AudioFile(str(SILENCE))])))
    parts = res.get("content") or []
    text = "".join(p.get("text", "") for p in parts if isinstance(p, dict))
    print(f"3. 무음 전사 OK, 결과 {len(text)}자: {text[:80]!r}")
    engine.close()


if __name__ == "__main__":
    main()

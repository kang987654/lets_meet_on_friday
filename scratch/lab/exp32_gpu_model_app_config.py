"""exp32 — GPU 모델 변형을 **앱과 동일한 EngineConfig 혼합 구성**으로 판정.

앱: EngineConfig(backend=GPU, visionBackend=GPU, audioBackend=CPU, maxNumTokens=4096, maxNumImages=1)
exp31 은 chat 백엔드만 설정했다 — 오디오 컴포넌트가 CPU 인 혼합 구성이 GPU 전용 모델에서
성립하는지가 음성 전사(핵심 기능)의 생사를 가른다.

판정:
  1. 혼합 구성 엔진 생성 성공 여부
  2. 채팅 턴 정상 (depth 0)
  3. 무음 WAV 전사 — 크래시 없이 빈 결과(exp16 계약)면 통과
"""

import struct
import wave
import pathlib

import litert_lm as llm

import kosmos_lab as lab

GPU_MODEL = str(pathlib.Path(lab.MODEL_PATH).parent / "gemma-4-E4B-it-gpu.litertlm")
SILENCE = pathlib.Path(__file__).parent / "fixtures" / "silence_2s.wav"


def make_silence():
    with wave.open(str(SILENCE), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(struct.pack("<h", 0) * 32000)
    print(f"무음 픽스처: {SILENCE} ({SILENCE.stat().st_size} bytes)")


def main():
    make_silence()

    try:
        engine = llm.Engine(
            GPU_MODEL,
            backend=llm.Backend.GPU,
            vision_backend=llm.Backend.GPU,
            audio_backend=llm.Backend.CPU,
            max_num_tokens=lab.ENGINE_MAX_TOKENS,
            max_num_images=lab.MAX_IMAGES_PER_TURN,
        )
    except Exception as e:  # noqa: BLE001
        print(f"1. 혼합 구성 엔진 생성 실패: {type(e).__name__}: {str(e)[:300]}")
        print("→ GPU 모델 + CPU 오디오 불가. 모델 교체 시 음성 경로 재설계 필요.")
        return
    print("1. 혼합 구성(GPU/GPU/CPU오디오) 엔진 생성 OK")

    # 2. 채팅 턴
    conv = lab.create_chat_conversation(engine)
    res = conv.send_message(lab.with_turn_reminder("다음주 월요일 10시 팀 회의 일정 추가해줘"))
    calls = res.get("tool_calls") or []
    if calls:
        fn = calls[0]["function"]
        print(f"2. 채팅 턴 OK: {fn['name']} start_time={(fn.get('arguments') or {}).get('start_time')!r}")
    else:
        print("2. 채팅 턴: 툴 미호출 (확인 필요)")
    conv.close()

    # 3. 무음 전사 (앱의 일회성 전사 대화와 동일: 시스템 지시만, 툴 없음)
    try:
        tconv = engine.create_conversation(
            system_message=lab.fixture("transcribe_system.txt"),
            automatic_tool_calling=False,
            thinking_config=llm.ThinkingConfig(enable_thinking=False),
            sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        )
        res = tconv.send_message(
            llm.Message.user(llm.Contents([llm.Content.AudioFile(str(SILENCE))]))
        )
        parts = res.get("content") or []
        text = "".join(p.get("text", "") for p in parts if isinstance(p, dict))
        print(f"3. 무음 전사 OK (크래시 없음), 결과 {len(text)}자: {text[:80]!r}")
    except Exception as e:  # noqa: BLE001
        print(f"3. 전사 실패: {type(e).__name__}: {str(e)[:300]}")

    engine.close()


if __name__ == "__main__":
    main()

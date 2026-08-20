"""exp16 — 음성 전사가 되는지 로컬에서 판정한다 (#2).

## 무엇을 재현하는가

2026-08-12 실기기에서 마이크로 말하면 언제나 "응답을 만들지 못했어요" 로 끝났다. 원인은
`EngineConfig` 가 `audioBackend` 를 **한 번도 설정하지 않은** 것이다(기본값 null → 엔진이
`Content.AudioFile` 을 받지 못한다). 0.11.0 회귀가 아니라 이전부터 있던 결함이고,
`TranscribeAudioUseCaseTest` 7건은 `ModelRunner` 를 목으로 대체해 **잘못 설정된 그 컴포넌트를
검증 대상에서 지웠기** 때문에 잡지 못했다.

## 무엇을 답하는가

  A) audio_backend 를 주면 전사가 되는가        → 앱 수정(`buildEngineConfig`)이 옳은가
  B) audio_backend 없이는 정말 실패하는가        → 진단이 맞는가
  C) 무음이면 빈 결과가 오는가                   → PRD EC3(재시도 안내) 전제가 성립하는가
  D) 모델이 껍데기(따옴표·화자 라벨)를 붙이는가  → `TranscribeAudioUseCase.clean()` 이 필요한가

## 픽스처 주의

기기에서 뽑은 실제 녹음(`voice_input.wav`)은 세션 중 연결이 끊겨 회수하지 못했다. 대신 Windows
SAPI 한국어 음성(Microsoft Heami)으로 **같은 포맷**(16kHz mono 16-bit PCM)의 파일을 만들어 쓴다.
배선과 프롬프트 검증에는 충분하지만 **실제 사람 목소리의 전사 정확도는 이것으로 알 수 없다** —
그건 기기 녹음이 필요하다.
"""

import os
import sys

import kosmos_lab as K
import litert_lm as L

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
SPEECH = os.path.join(FIXTURES, "voice_input_tts.wav")
SILENCE = os.path.join(FIXTURES, "voice_silence.wav")
DEVICE_RECORDING = os.path.join(FIXTURES, "voice_input.wav")  # 있으면 우선 쓴다

# 앱이 내보낸 실제 전사 지시를 읽는다 (PromptFixtureExportTest 가 씀).
#
# [WHY] 손으로 베끼면 앱이 문구를 고칠 때 조용히 어긋나고, 그러면 이 실험이 앱과 다른 것을
# 측정한다. 없으면 실행을 멈추고 테스트를 먼저 돌리게 한다 — 낡은 사본으로 돌리는 것보다 낫다.
def _fixture(name):
    path = os.path.join(FIXTURES, name)
    if not os.path.exists(path):
        raise SystemExit(
            f"{name} 가 없다. 먼저 내보낼 것:\n"
            "  ./gradlew :app:testDebugUnitTest --tests '*PromptFixtureExport*'"
        )
    with open(path, encoding="utf-8") as f:
        return f.read()


SYSTEM_INSTRUCTION = _fixture("transcribe_system.txt")
USER_INSTRUCTION = _fixture("transcribe_user.txt")

EXPECTED = "내일 오후 세 시에 치과 예약 잡아줘"

# `clean()` 이 벗겨야 하는 껍데기들 — 실제로 붙는지 본다.
SHELLS = ['"', "'", "“", "”", "사용자:", "User:", "Transcript:", "전사:", "받아쓰기:"]


def transcribe(lab, audio_path, label, with_text=True):
    """
    Args:
        with_text: 앱처럼 오디오 뒤에 사용자 지시 텍스트를 붙일지. False 면 오디오만 보낸다.
            [WHY] 무음일 때 모델이 **그 지시문을 그대로 되읊는** 것이 실측됐다. 지시가 문맥에
            없으면 되읊을 것도 없으므로, 오디오만으로 되는지가 근본 수정 가능성을 가른다.
    """
    cfg = K.Config(
        label=label,
        tools=[],          # 전사는 툴을 선언하지 않는다 (oneShot 계약)
        system=SYSTEM_INSTRUCTION,
        few_shot=False,
        constrained=None,  # 툴이 없으면 앱도 제약 디코딩을 켜지 않는다
        max_output_tokens=120,
    )
    parts = [L.Content.AudioFile(audio_path)]
    if with_text:
        parts.append(L.Content.Text(USER_INSTRUCTION))
    kwargs = lab._conv_kwargs(cfg, None)
    with lab.engine.create_conversation(**kwargs) as conv:
        response = conv.send_message(L.Contents.of(*parts))
    _, text = K.extract(response)
    return text.strip()


def main():
    speech = DEVICE_RECORDING if os.path.exists(DEVICE_RECORDING) else SPEECH
    origin = "기기 녹음" if speech == DEVICE_RECORDING else "TTS 합성"
    print(f"음성 픽스처: {os.path.basename(speech)} ({origin})")

    for path in (speech, SILENCE):
        if not os.path.exists(path):
            print(f"[!] {path} 가 없다")
            return 1

    # --- B) audio_backend 없이 — 실패해야 한다 -----------------------------
    print("\n=== B) audio_backend 없이 (실패해야 정상) ===")
    lab_no_audio = K.Lab(backend=BACKEND, audio=False)
    try:
        out = transcribe(lab_no_audio, speech, "no-audio-backend")
        verdict = "?? 예상 외로 성공" if out else "빈 결과"
        print(f"  {verdict}: {out!r}")
        print("  → 성공했다면 audioBackend 누락이 원인이라는 진단이 틀린 것이다.")
    except Exception as e:
        print(f"  실패 (예상대로): {type(e).__name__}: {str(e)[:180]}")
    finally:
        lab_no_audio.close()

    # --- A) audio_backend 를 주고 -----------------------------------------
    print("\n=== A) audio_backend=CPU (앱 수정과 동일) ===")
    lab = K.Lab(backend=BACKEND, audio=True)
    try:
        out = transcribe(lab, speech, "with-audio-backend")
        print(f"  전사: {out!r}")
        print(f"  기대: {EXPECTED!r}")

        found = [s for s in SHELLS if s in out]
        print(f"  D) 붙은 껍데기: {found if found else '없음'}")
        if found:
            print("     → clean() 의 껍데기 제거가 실제로 필요하다")

        # --- C) 무음 -------------------------------------------------------
        print("\n=== C) 무음 2초 (PRD EC3) ===")
        silent = transcribe(lab, SILENCE, "silence")
        print(f"  전사: {silent!r}")
        echoed = USER_INSTRUCTION.strip().rstrip(".") in silent
        if echoed:
            print("     → **사용자 지시를 되읊었다.** 빈 값이 아니므로 clean() 이 못 걸러내고,")
            print("        앱은 이 문장을 사용자 메시지로 저장한 뒤 그것에 답한다 (PRD EC3 위반)")
        elif silent:
            print("     → 무음인데 다른 글자가 나왔다")
        else:
            print("     → 빈 결과. clean() 이 SttError 를 올리고 화면이 재시도를 안내한다")

        # --- E) 오디오만 보내면? (근본 수정 후보) ---------------------------
        print("\n=== E) 텍스트 지시 없이 오디오만 ===")
        try:
            e_speech = transcribe(lab, speech, "audio-only-speech", with_text=False)
            e_silent = transcribe(lab, SILENCE, "audio-only-silence", with_text=False)
            print(f"  음성: {e_speech!r}")
            print(f"  무음: {e_silent!r}")
            if e_speech and not e_silent:
                print("     → 지시문을 빼는 것이 근본 수정이다 (되읊을 대상이 없다)")
            elif not e_speech:
                print("     → 오디오만으로는 전사가 안 된다. 지시 텍스트가 필요하므로")
                print("        되읊음을 clean() 에서 걸러내는 방식이어야 한다")
        except Exception as e:
            print(f"  오디오 단독 전송 실패: {type(e).__name__}: {str(e)[:160]}")
            print("     → 지시 텍스트가 필수다. clean() 에서 되읊음을 걸러야 한다")
    finally:
        lab.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

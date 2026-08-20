"""exp23 — Gemma 4 에서 비전·오디오 백엔드가 실제로 무엇을 요구하는가.

## 왜 필요한가

앱은 `visionBackend = backend`(즉 GPU, 폴백 시 CPU), `audioBackend = Backend.CPU()` 로 둔다.
그 근거는 gallery 의 주석 두 줄이었다:

    visionBackend = … // must be GPU for Gemma 3n
    audioBackend  = … // must be CPU for Gemma 3n

**둘 다 Gemma 3n 에 대한 말이고 우리 모델은 Gemma 4 E4B 다.** 0.12.0 에서 오디오를 CPU 로 고정할
때 이 주석을 근거로 댔는데, 그것은 물려받은 전언이지 우리 모델의 실측이 아니다. 실측으로 확정된
것은 **"오디오 백엔드를 설정하지 않으면 실패한다"**(exp16, `Audio executor should not be null`)와
**"CPU 로 두면 전사가 된다"** 뿐이다. GPU 도 되는지, 비전이 CPU 에서 되는지는 재 본 적이 없다.

## 무엇을 재는가

우리 `gemma-4-E4B-it.litertlm` 로 조합을 직접 돌린다.

    오디오: audio_backend ∈ {CPU, GPU, 미설정}  → 전사가 나오는가
    비전  : vision_backend ∈ {CPU, GPU, 미설정}  → 그림 설명이 맞는가

판정 가능한 그림을 쓴다 — `fixtures/vision_shapes.png` 는 흰 배경에 **빨간 사각형과 파란 원**
뿐이라, 설명이 맞았는지 문자열로 확인할 수 있다(외부 이미지를 받지 않는 이유이기도 하다).

## 이 결과로 무엇이 바뀌는가

  - CPU 비전이 되면  → 폴백 기기에서 이미지가 깨진다는 내 추측이 틀린 것이고 코드는 그대로 둔다
  - CPU 비전이 안 되면 → `visionBackend` 를 GPU 로 고정해야 한다(오디오와 같은 형태의 결함)
  - GPU 오디오가 되면 → CPU 고정의 근거를 "gallery 전언" 에서 "우리가 고른 값" 으로 바꿔 적는다

주의: 여기서 GPU 는 PC 의 GPU 다. 안드로이드 GPU 백엔드와 같은 구현이라는 보장은 없다 —
"Gemma 4 가 CPU 비전을 아예 지원하지 않는다" 같은 **모델 차원의 제약**을 가리는 데는 충분하지만,
백엔드별 성능·안정성은 기기에서 다시 봐야 한다.
"""

import os
import sys

import litert_lm as L

import kosmos_lab as K

K.use_utf8()

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")
IMAGE = os.path.join(FIXTURES, "vision_shapes.png")
SPEECH = os.path.join(FIXTURES, "voice_input_tts.wav")

# 그림 설명이 맞았는지 볼 단어들 (하나라도 있으면 '봤다'로 본다).
IMAGE_HINTS = ["빨간", "붉은", "빨강", "red", "사각", "네모", "square", "rectangle",
               "파란", "파랑", "blue", "원", "동그라", "circle"]

TRANSCRIBE_SYSTEM = os.path.join(FIXTURES, "transcribe_system.txt")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def try_engine(**engine_kwargs):
    """엔진을 올린다. 실패하면 예외 문자열을 돌려준다."""
    try:
        return L.Engine(K.MODEL_PATH, **engine_kwargs), None
    except Exception as e:
        return None, f"{type(e).__name__}: {str(e)[:120]}"


def run_image(engine):
    cfg = K.Config(tools=[], few_shot=False, constrained=None, max_output_tokens=80,
                   system="[System]\nYou describe images concisely in Korean.")
    kwargs = dict(
        messages=None, tools=None, automatic_tool_calling=False,
        system_message=cfg.system,
        sampler_config=L.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        thinking_config=L.ThinkingConfig(enable_thinking=False),
        max_output_tokens=cfg.max_output_tokens,
    )
    with engine.create_conversation(**kwargs) as conv:
        r = conv.send_message(L.Contents.of(
            L.Content.ImageFile(IMAGE),
            L.Content.Text("이 그림에 무엇이 있나요? 도형과 색만 짧게."),
        ))
    _, text = K.extract(r)
    return text.strip()


def run_audio(engine):
    kwargs = dict(
        messages=None, tools=None, automatic_tool_calling=False,
        system_message=read(TRANSCRIBE_SYSTEM),
        sampler_config=L.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        thinking_config=L.ThinkingConfig(enable_thinking=False),
        max_output_tokens=100,
    )
    with engine.create_conversation(**kwargs) as conv:
        r = conv.send_message(L.Contents.of(L.Content.AudioFile(SPEECH)))
    _, text = K.extract(r)
    return text.strip()


def main():
    L.set_min_log_severity(L.LogSeverity.ERROR)
    for p in (IMAGE, SPEECH, TRANSCRIBE_SYSTEM):
        if not os.path.exists(p):
            print(f"[!] {p} 가 없다")
            return 1

    backends = {"CPU": L.Backend.CPU(), "GPU": L.Backend.GPU(), "미설정": None}

    print("=== 비전 ===")
    for name, be in backends.items():
        kw = {"backend": L.Backend.CPU()}
        if be is not None:
            kw["vision_backend"] = be
        engine, err = try_engine(**kw)
        if engine is None:
            print(f"  vision={name:6} 엔진 로드 실패: {err}", flush=True)
            continue
        try:
            out = run_image(engine)
            saw = [h for h in IMAGE_HINTS if h in out]
            verdict = "봤다" if saw else "못 봤다"
            print(f"  vision={name:6} {verdict:7} {out[:70]!r}", flush=True)
        except Exception as e:
            print(f"  vision={name:6} 추론 실패: {type(e).__name__}: {str(e)[:90]}", flush=True)
        finally:
            engine.close()

    print("\n=== 오디오 ===")
    for name, be in backends.items():
        kw = {"backend": L.Backend.CPU()}
        if be is not None:
            kw["audio_backend"] = be
        engine, err = try_engine(**kw)
        if engine is None:
            print(f"  audio={name:7} 엔진 로드 실패: {err}", flush=True)
            continue
        try:
            out = run_audio(engine)
            verdict = "전사됨" if out else "빈 결과"
            print(f"  audio={name:7} {verdict:7} {out[:70]!r}", flush=True)
        except Exception as e:
            print(f"  audio={name:7} 추론 실패: {type(e).__name__}: {str(e)[:90]}", flush=True)
        finally:
            engine.close()

    print("\n판정 지침:")
    print("  vision=CPU 가 '봤다' 면 → 폴백 기기에서 이미지가 깨진다는 추측은 틀렸다")
    print("  vision=CPU 가 실패하면 → visionBackend 를 GPU 로 고정해야 한다")
    print("  audio=GPU 도 되면    → CPU 고정 근거를 gallery 전언에서 우리 선택으로 다시 적는다")
    print("  '미설정' 이 실패하면 → 그 필드를 반드시 넘겨야 한다는 것이 재확인된다")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

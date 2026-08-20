"""exp24 — 생각 모드가 툴 호출을 망가뜨리는가, 그리고 우리는 정말 끄고 있는가.

## 왜 지금 이걸 보는가

앱은 모든 추론에서 `extraContext = {"enable_thinking": false}` 를 넘긴다. 그 근거는
`GemmaModelRunner` 주석에 이렇게 적혀 있다:

    gallery 는 허용 목록에서도 thinking 과 agent chat(툴 호출)을 조합하지 않는다
    — 생각 모드에서는 함수호출 동작이 달라진다.

**그 주장을 우리 모델로 확인한 적이 없다.** 그리고 두 가지가 걸린다:

  1. 앱(AAR 0.14.0)은 `sendMessage(message, extraContext)` 로 끈다. 파이썬 0.16.0 의
     `send_message` 에는 그 파라미터가 없고 대신 `thinking_config` 가 있다 — `ThinkingConfig`
     클래스는 **0.16.0 에서 추가**됐다(0.14.0 클래스 목록에 없음). 즉 앱은 구 메커니즘을 쓴다.
  2. 앱에는 `<|think|>` 를 걷어내는 기계가 잔뜩 있다(`ToolParser` 정규식, `BaseAgent` 의
     `THINK_TAG_WINDOW`, ADR-007 의 "두 번째 `<|think|>` 블록 누락" 수정). 정말 꺼져 있다면
     그 기계가 필요했을 이유가 설명되지 않는다.

실기기 프리페이스에는 `<|think|>` 가 없었다. 다만 프리페이스는 **대화 생성 시점**에 렌더되고
`extraContext` 는 **메시지 시점**에 전달되므로, 그 관측은 extraContext 가 듣는다는 증거가 아니다.

## 무엇을 재는가

    thinking ∈ {on, off} × depth ∈ {0, 20} × 발화 2종

  - 출력에 `<|think|>` 가 나오는가  → 플래그가 실제로 먹는지
  - 툴이 호출되는가                → gallery 의 주장이 우리 모델에도 맞는지
  - 자릿수가 온전한가              → 생각 모드가 숫자에도 영향을 주는지

## 왜 중요한가

생각 모드가 툴 호출을 망가뜨린다면, 앱의 끄기 메커니즘이 조용히 실패하는 것만으로 우리가 쫓던
결함이 그대로 재현된다. 그리고 그 경우 0.16.0 의 `ThinkingConfig` 로 올리는 것이 **타입이 있는
확실한 방법**이 된다 — 지금은 맵의 키 이름 하나에 의존하고 있다.
"""

import sys

import litert_lm as L

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

DEPTHS = (0, 20)
PROBES = [
    ("다음주 월요일 10시 팀 회의", "schedule", "start_time", "2026-08-17T10:00"),
    ("위키에서 에스파 검색해줘", "wikipedia", None, None),
]

THINK_MARKERS = ("<|think|>", "<|channel>thought", "<think>")


def main():
    si = F.system_instruction()
    if si is None or F.turn_reminder() is None:
        print("[!] 픽스처가 없다.")
        return 1

    hist = F.messages(mode="verbatim", before=F.WIKI_HISTORY_CUT)
    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=8192)
    table = {}
    try:
        for thinking in (False, True):
            # [WHY] `Config.thinking` 이 `ThinkingConfig(enable_thinking=...)` 로 전달된다.
            cfg = K.Config(system=si, thinking=thinking)
            for depth in DEPTHS:
                history = hist[-depth:] if depth else []
                for utterance, expect, arg_key, expect_arg in PROBES:
                    r = lab.run(F.as_app_sends(utterance), cfg, history)
                    names = [c.get("name") or "" for c in r.tool_calls]
                    called = any(expect in n for n in names)
                    digits = not arg_key or (called and F.digit_ok(r.arg(arg_key), expect_arg))
                    marker = next((m for m in THINK_MARKERS if m in (r.text or "")), None)
                    table[(thinking, depth, expect)] = (called, digits, marker)
                    print(f"  thinking={str(thinking):5} depth={depth:<3} "
                          f"{'호출' if called else '미호출':7} "
                          f"{'자릿수OK' if digits else '자릿수X':9} "
                          f"think마커={marker or '없음':16} {utterance[:18]}", flush=True)
    finally:
        lab.close()

    print("\n=== 요약 ===")
    for thinking in (False, True):
        calls = sum(1 for k, v in table.items() if k[0] == thinking and v[0])
        total = sum(1 for k in table if k[0] == thinking)
        markers = sum(1 for k, v in table.items() if k[0] == thinking and v[2])
        print(f"  thinking={str(thinking):5} 호출 {calls}/{total}, think마커 {markers}/{total}")

    off_calls = sum(1 for k, v in table.items() if not k[0] and v[0])
    on_calls = sum(1 for k, v in table.items() if k[0] and v[0])
    on_markers = sum(1 for k, v in table.items() if k[0] and v[2])

    print("\n판정:")
    if on_markers == 0:
        print("  thinking=True 에서도 마커가 안 나왔다 → 이 모델/런타임에서 플래그 효과를")
        print("  출력만으로는 확인할 수 없다. 아래 호출률 비교로만 판단할 것.")
    else:
        print("  마커가 thinking=True 에서만 나왔다 → 플래그가 실제로 먹는다.")
    if on_calls < off_calls:
        print(f"  **생각 모드가 툴 호출을 깎는다** ({on_calls} vs {off_calls}).")
        print("  → 앱의 끄기(extraContext 맵)가 조용히 실패하면 우리가 쫓던 결함이 그대로 난다.")
        print("     0.16.0 의 타입 있는 `ThinkingConfig` 로 올릴 근거가 된다.")
    elif on_calls == off_calls:
        print(f"  호출률이 같다 ({on_calls} = {off_calls}) — 적어도 이 조건에서 생각 모드는")
        print("  툴 호출을 망가뜨리지 않는다. gallery 의 주장이 우리 모델에는 그대로 적용되지 않는다.")
    else:
        print(f"  생각 모드가 오히려 나았다 ({on_calls} vs {off_calls}) — 표본을 늘려 재확인할 것.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

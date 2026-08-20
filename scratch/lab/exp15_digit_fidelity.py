"""exp15 — 툴 인자의 글자 유실을 재현한다 (#3).

## 무엇을 재현하는가

2026-08-12 실기기(0.11.1) DB 실측:

    "다음주 월요일 10시 팀 회의"   → start_time = '2026-08-17T01:000'   (기대 2026-08-17T10:00)
    "오늘 저녁 8시에 저녁 약속"     → start_time = '208:000'             (기대 2026-08-12T20:00)
    답변 문장                       → "20817일 01시"                     (기대 2026년 8월 17일 10시)
    "에스파 데뷔일"                 → "2023년 111월 1일"

날짜 계산은 맞았다(2026-08-17 은 정확히 다음주 월요일). **시각의 자릿수만 어긋난다.**

## 배제된 원인

우리 스트리밍 채널이 아니다. 툴 인자는 `collectToolCalls` 가 `message.toolCalls`(네이티브가
파싱한 구조체)에서 읽으며 우리가 조립한 문자열을 거치지 않는데, 그 인자가 깨져 있다. 그래서
0.9.1 의 무한 버퍼가 듣지 않았다.

## 남은 후보와 이 실험이 가르는 것

  C1 버전     — Android AAR 0.14.0 vs 이 파이썬 0.15.0. **파이썬에서 안 깨지면 1순위**가 된다
  C2 제약 디코딩 — 툴 스키마로 만든 FST 문법이 자릿수를 흐트러뜨린다 (constrained on/off)
  C3 문맥 길이 — KV 누적과의 상호작용 (depth 0 / 40 / 전체)
  C4 백엔드    — GPU vs CPU

C2 를 끄는 것이 처방이 되려면 대가를 알아야 한다: 0.8.0 에서 이 플래그를 끄면 `toolCalls` 가
비었다. 그래서 이 실험은 **호출 여부와 자릿수를 함께** 본다 — 자릿수만 보면 "안 깨졌지만 호출도
안 됐다"를 성공으로 착각한다.
"""

import sys

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKENDS = sys.argv[1:] or ["gpu"]

DEPTHS = [d for d in (0, 40, len(F.rows())) if d <= len(F.rows())]
CONSTRAINED = (True, False)
REPEATS = 2  # greedy 라 결정적이어야 한다 — 다르면 그 자체가 발견이다


def probe_once(lab, cfg, history, utterance, arg_key):
    r = lab.run(utterance, cfg, history)
    called = [c.get("name") for c in r.tool_calls]
    return r.arg(arg_key), called, r.text


def main():
    si = F.system_instruction()
    if si is None:
        print("[!] fixtures/system_instruction.txt 가 없다. 먼저:")
        print("    ./gradlew :app:testDebugUnitTest --tests '*PromptFixtureExport*'")
        return 1

    print("기기 실측값 (정답표):")
    for utterance, key, expected, device in F.DIGIT_PROBES:
        print(f"  {utterance}")
        print(f"    기대 {expected}   기기 {device}  ← 깨짐")
    print()

    history_full = F.messages(before=F.WIKI_HISTORY_CUT)
    findings = []

    for backend in BACKENDS:
        lab = K.Lab(backend=backend)
        try:
            for constrained in CONSTRAINED:
                cfg = K.Config(
                    label=f"{backend}/constrained={constrained}",
                    system=si,
                    constrained=constrained,
                )
                for depth in DEPTHS:
                    history = history_full[-depth:] if depth else []
                    for utterance, key, expected, _device in F.DIGIT_PROBES:
                        for i in range(REPEATS):
                            actual, called, text = probe_once(lab, cfg, history, utterance, key)
                            ok = F.digit_ok(actual, expected)
                            tool_ok = any("schedule" in (c or "") for c in called)
                            findings.append(
                                (backend, constrained, depth, utterance, i, tool_ok, ok, actual)
                            )
                            status = (
                                "OK  " if (tool_ok and ok)
                                else "깨짐" if tool_ok
                                else "미호출"
                            )
                            print(
                                f"  {backend:3} con={str(constrained):5} depth={depth:<3} "
                                f"#{i} {status} {str(actual)!r:26} {utterance[:18]}"
                            )
        finally:
            lab.close()

    print("\n=== 요약 ===")
    print(f"{'backend':8}{'constrained':>12}{'depth':>7}{'호출':>6}{'자릿수OK':>10}")
    for backend in BACKENDS:
        for constrained in CONSTRAINED:
            for depth in DEPTHS:
                sub = [f for f in findings
                       if f[0] == backend and f[1] == constrained and f[2] == depth]
                if not sub:
                    continue
                print(f"{backend:8}{str(constrained):>12}{depth:>7}"
                      f"{sum(1 for f in sub if f[5]):>4}/{len(sub):<2}"
                      f"{sum(1 for f in sub if f[6]):>8}/{len(sub)}")

    clean_with_constrained = [f for f in findings if f[1] and f[5] and f[6]]
    print("\n판정 지침:")
    if clean_with_constrained:
        print("  constrained=True 에서도 자릿수가 온전했다 →")
        print("    파이썬 0.15.0 은 안 깨진다. **AAR 0.14.0 런타임 버그가 1순위**다.")
        print("    처방: 최신 litertlm AAR 확인 → 올려서 실기기 재확인.")
    else:
        print("  constrained=True 에서 깨졌다 → FST 문법이 유력하다.")
        print("    단, constrained=False 의 '호출' 열을 함께 볼 것 — 0 이면 끄는 것은 처방이 아니다.")
    print("  같은 조건 반복(#0/#1)이 다르면 greedy 가 아니거나 상태가 새는 것이다 — 별건.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

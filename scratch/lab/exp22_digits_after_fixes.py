"""exp22 — 글자 유실이 남아 있는가, KV 초과와 지침 위치를 고친 뒤 (#3 재판정).

## 왜 이제야 잴 수 있는가

기기에서 관측한 것(2026-08-12, DB 실측):

    "다음주 월요일 10시 팀 회의"  → start_time = '2026-08-17T01:000'   (기대 …T10:00)
    "오늘 저녁 8시에 저녁 약속"   → start_time = '208:000'
    "자전거 비밀번호는 1234"      → 저장된 값 '234' / 답변 '234'
    "에스파 데뷔일"               → "2023년 111월 1일"

exp15 는 depth 0 에서만 자릿수를 확인할 수 있었다. **깊은 히스토리에서는 툴 호출 자체가 없어서
인자를 볼 수가 없었다** — 자릿수를 재려면 먼저 호출이 돼야 한다.

0.13.0 이 두 가지를 고쳤다:
  1. `maxNumTokens = 4096` 명시 + 예산을 그 안으로 (예전에는 6000/8000 으로 조용히 초과)
  2. 툴 지침을 사용자 턴 앞에 재게시 → 깊은 히스토리에서도 호출된다 (exp21, 6/6)

그래서 이제 **호출이 되는 상태에서 깊은 히스토리의 자릿수**를 볼 수 있다. 이것이 기기 조건에
가장 가까운 측정이다.

## 이 실험이 답하는 것

  - 앱의 실제 설정(`max_num_tokens=4096`)에서 자릿수가 온전한가
  - 히스토리 깊이에 따라 달라지는가
  - constrained decoding 을 끄면 달라지는가
  - 같은 조건 반복이 같은 결과인가 (greedy 라 달라지면 그 자체가 발견)

**단서 하나는 이 하네스로 확정할 수 없다**: 파이썬은 0.15.0, 앱 AAR 은 0.14.0 이다. 여기서
깨끗하다면 "AAR 0.14.0 고유" 가 유일하게 남는 후보가 된다.
"""

import sys

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

# 앱이 실제로 쓰는 KV 용량 (Constants.ENGINE_MAX_TOKENS).
APP_KV = 4096

# 앱 예산(3000) - 오버헤드(1400) = 히스토리 1600 토큰 ≈ 25~30개. 그 범위를 본다.
DEPTHS = (0, 8, 20)
CONSTRAINED = (True, False)
REPEATS = 2

# (발화, 검사 방식, 기대값)
#   "arg:<키>"  → 툴 인자에서 그 키를 꺼내 날짜·시각이 온전한지
#   "text"      → 응답 텍스트에 그 문자열이 온전히 있는지
PROBES = [
    ("다음주 월요일 10시 팀 회의", "arg:start_time", "2026-08-17T10:00"),
    ("오늘 저녁 8시에 저녁 약속 있어", "arg:start_time", "2026-08-13T20:00"),
    # [WHY] 기기에서 `1234` 가 `234` 로 저장됐다. 툴 인자의 숫자 보존은 일정 시각과 다른
    # 경로(자유 텍스트 인자)라 따로 본다.
    ("내 자전거 비밀번호는 1234야, 기억해줘", "arg:content", "1234"),
]


def check(kind, expected, result):
    if kind.startswith("arg:"):
        key = kind.split(":", 1)[1]
        actual = result.arg(key)
        if actual is None:
            return None, None  # 미호출 — 자릿수를 판정할 수 없다
        if "T" in expected:
            return F.digit_ok(actual, expected), actual
        return (expected in str(actual)), actual
    text = result.text or ""
    return (expected in text), text[:40]


def main():
    si = F.system_instruction()
    if si is None or F.turn_reminder() is None:
        print("[!] 픽스처가 없다. ./gradlew :app:testDebugUnitTest --tests '*PromptFixtureExport*'")
        return 1

    print(f"앱 설정 재현: max_num_tokens={APP_KV}, 시스템 지시 {len(si)}자 + 턴 지침 재게시")
    print(f"깊이 {DEPTHS} (앱 히스토리 예산 1600 토큰 ≈ 25~30개), 반복 {REPEATS}회\n")

    hist = F.messages(mode="verbatim", before=F.WIKI_HISTORY_CUT)
    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=APP_KV)

    broken, uncalled, clean = [], [], 0
    try:
        for constrained in CONSTRAINED:
            cfg = K.Config(system=si, constrained=constrained)
            for depth in DEPTHS:
                history = hist[-depth:] if depth else []
                for utterance, kind, expected in PROBES:
                    for i in range(REPEATS):
                        r = lab.run(F.as_app_sends(utterance), cfg, history)
                        ok, actual = check(kind, expected, r)
                        if ok is None:
                            mark, note = "미호출", "-"
                            uncalled.append((constrained, depth, utterance))
                        elif ok:
                            mark, note = "OK  ", str(actual)
                            clean += 1
                        else:
                            mark, note = "깨짐", str(actual)
                            broken.append((constrained, depth, utterance, actual, expected))
                        print(f"  con={str(constrained):5} depth={depth:<3} #{i} {mark} "
                              f"{note[:34]:36} {utterance[:20]}", flush=True)
    finally:
        lab.close()

    print(f"\n=== 정리 ===")
    print(f"  자릿수 온전 {clean}건 / 깨짐 {len(broken)}건 / 미호출 {len(uncalled)}건")
    if broken:
        print("\n  깨진 건:")
        for c, d, u, a, e in broken:
            print(f"    con={c} depth={d} {u[:22]} → {a!r} (기대 {e!r})")
    if uncalled:
        print(f"\n  미호출 {len(uncalled)}건 — 자릿수를 판정할 수 없는 조건이다:")
        for c, d, u in uncalled:
            print(f"    con={c} depth={d} {u[:22]}")

    print("\n판정:")
    if not broken:
        print("  이 하네스에서는 자릿수가 깨지지 않는다. 앱 설정(4096) + 지침 재게시 상태에서")
        print("  깊은 히스토리·제약 디코딩을 모두 통과했다.")
        print("  → 남는 후보는 **AAR 0.14.0 과 파이썬 0.15.0 의 런타임 차이** 하나다.")
        print("     실기기 재확인이 그것을 가른다(같은 발화 한 줄이면 된다).")
    else:
        print("  자릿수가 깨진다 — 위 조건을 보고 constrained·depth 중 무엇이 요인인지 좁힌다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

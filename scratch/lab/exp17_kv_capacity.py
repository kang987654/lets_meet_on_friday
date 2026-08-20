"""exp17 — 엔진 KV 용량이 앱 예산보다 작다 (신규 결함).

## 발견 경위

exp15 를 실기기 히스토리(79개) 전체로 돌리자 엔진이 거부했다:

    INVALID_ARGUMENT: Input token ids are too long.
    Exceeding the maximum number of tokens allowed: 5857 >= 4096

`Engine(max_num_tokens=None)` 의 런타임 기본값이 **4096** 이라는 뜻이다.

## 앱 쪽 상태

`Constants.kt:18` 은 이렇게 적어 두었다:

    "gemma-4 컨텍스트는 32K 이고 EngineConfig.maxNumTokens 를 설정하지 않으므로 상한이 아니다"

**틀린 전제다.** 모델의 컨텍스트 창(32K)과 엔진이 실제로 할당하는 KV 크기는 별개이고, 설정하지
않으면 상한이 없어지는 것이 아니라 런타임 기본값 4096 이 된다. 그런데 앱은

    MAX_CONTEXT_TOKENS      = 6000   (설정 슬라이더는 최대 8000)
    PREFILL_OVERHEAD_TOKENS = 2600   (시스템 지시 + 툴 선언 + few-shot)

로 잡는다. 즉 **엔진 용량을 넘는 프롬프트를 의도적으로 만든다.**

파이썬 0.15.0 은 이때 오류를 낸다. 안드로이드 AAR 0.14.0 은 2026-08-12 실기기에서 같은 조건
(79개 히스토리)에서 **오류 없이 답을 냈다** — 즉 초과분을 조용히 처리했다. 그것이 그날의 글자
유실(`10:00` → `01:000`)과 관계가 있는지는 이 하네스로 확정할 수 없다(버전이 다르다). 다만
**어느 쪽이든 고쳐야 하는 설정 불일치**다.

## 이 실험이 답하는 것

  - 기본 용량에서 몇 토큰부터 거부되는가 (경계 확인)
  - 용량을 올리면 #1(툴 미호출)이 함께 풀리는가 → **아니다.** exp14 참조. 용량과 히스토리
    효과는 별개 원인이다. 이 구분이 처방을 갈라 놓으므로 여기서 못박는다.
"""

import sys

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

CAPS = [None, 8192]
UTTERANCE, ARG_KEY, EXPECTED, _ = F.DIGIT_PROBES[0]


def main():
    si = F.system_instruction()
    if si is None:
        print("[!] 픽스처가 없다. ./gradlew :app:testDebugUnitTest --tests '*PromptFixtureExport*'")
        return 1

    full = F.messages(before=F.WIKI_HISTORY_CUT)
    depths = [d for d in (0, 20, 40, len(full)) if d <= len(full)]
    print(f"앱 예산: MAX_CONTEXT_TOKENS=6000 (+오버헤드 2600), 슬라이더 최대 8000")
    print(f"엔진 기본 용량: 4096 (max_num_tokens 미설정 시)\n")

    rows = []
    for cap in CAPS:
        lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=cap)
        label = cap or "기본(4096)"
        print(f"--- max_num_tokens={label} ---")
        try:
            cfg = K.Config(system=si)
            for depth in depths:
                history = full[-depth:] if depth else []
                try:
                    r = lab.run(UTTERANCE, cfg, history)
                    called = [c.get("name") for c in r.tool_calls]
                    arg = r.arg(ARG_KEY)
                    ok = F.digit_ok(arg, EXPECTED)
                    rows.append((label, depth, "완료", bool(called), ok))
                    print(f"  depth={depth:<3} 완료   호출={str(called or '없음'):22} "
                          f"자릿수={'OK' if ok else 'X'}")
                except Exception as e:
                    rows.append((label, depth, "거부", False, False))
                    msg = str(e)[:70]
                    print(f"  depth={depth:<3} 엔진 거부 ({msg})")
        finally:
            lab.close()

    print("\n=== 정리 ===")
    rejected = [r for r in rows if r[2] == "거부"]
    if rejected:
        print("  기본 용량에서 거부된 깊이:",
              sorted({r[1] for r in rejected if r[0] == "기본(4096)"}))
        print("  → 앱은 이 상황을 만들 수 있고(예산 6000, 슬라이더 8000), AAR 0.14.0 은")
        print("     오류 대신 조용히 넘어간다. 설정을 맞춰야 한다.")
    else:
        print("  거부가 없었다 — 히스토리가 짧아 경계에 닿지 않았다. 더 깊은 픽스처가 필요하다.")

    called_any = {r[0]: any(x[3] for x in rows if x[0] == r[0]) for r in rows}
    print("\n  용량별 툴 호출 성공 여부:", called_any)
    print("  → 용량을 올려도 깊은 히스토리에서 호출이 돌아오지 않으면 #1 은 별개 원인이다")
    print("     (exp14 의 terse/verbatim 비교가 그 원인을 짚는다).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

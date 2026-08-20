"""exp26 — 새 예산이 실제로 4096 안에 들어오는가 (ADR-020 검증).

## 왜 필요한가

exp25 는 **문제**를 실측했다: 위키 5000자 = 2,631 토큰이고, 히스토리 0개에서도 왕복이 4,103 으로
4096 을 넘었다. ADR-020 이 그 원인을 두 개로 갈랐다.

1. 예산에 툴 결과가 아예 없었다 → `MAX_TOOL_RESULT_TOKENS = 600` 신설, 천장 3328 → 2568
2. 토크나이저가 한국어를 40% 적게 셌다(0.33 vs 실측 0.53) → 문자 종류별로 나눠 세게 고침

이 실험은 **고친 뒤의 수치**를 잰다. 산수로는 맞지만(`TokenBudgetInvariantTest` 가 등호로 못박음)
그 산수가 실제 토크나이저와 맞는지는 재 봐야 안다 — exp25 에서 위키 5000자의 실측이 추정보다
컸던 것이 바로 그 이유다.

## 무엇을 재는가

1. **정상 경로** — 히스토리 18개 + 위키 결과 1,090자(새 상한). 4096 안에 들어오는가, 여유는 얼마인가
2. **최악 경로** — 히스토리를 천장(2568)까지 채우고 같은 툴 왕복. 여기서 넘으면 천장이 틀렸다
3. **4096 실전** — `max_num_tokens=4096` 으로 같은 왕복을 실제로 돌린다. exp25 를 실패시킨 조건과
   **같은 코드 경로**이므로, 오류 없이 끝나면 그것이 판정이다

## 판정

1·2 가 4096 안이고 3 이 오류 없이 끝나면 ADR-020 이 원인을 없앤 것이다.
3 만 실패하면 천장은 맞고 우리 환산이 낙관적이라는 뜻이므로 `CJK_TOKENS_PER_100_CHARS` 를 올린다.
"""

import sys

import litert_lm as L

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

# --- ADR-020 의 상수를 그대로 옮긴다 (Constants.kt 와 어긋나면 이 실험이 무의미하다) ---
ENGINE_MAX_TOKENS = 4096
GENERATION_HEADROOM = 768
MAX_TOOL_RESULT_TOKENS = 600
TOOL_ROUNDTRIP_STRUCTURE = 160
PREFILL_CEILING = ENGINE_MAX_TOKENS - GENERATION_HEADROOM - MAX_TOOL_RESULT_TOKENS - TOOL_ROUNDTRIP_STRUCTURE
CJK_PER_100 = 55
MAX_TOOL_RESULT_CHARS = MAX_TOOL_RESULT_TOKENS * 100 // CJK_PER_100
# [WHY] 첫 실행에서 1,090자 본문이 1,150자 JSON 으로 나왔다 — executor 가 봉투를 씌우고 개행을
# 이스케이프한다. 앱은 그만큼을 예약해 본문을 994자까지만 돌려주므로 여기서도 같은 값을 쓴다.
TOOL_RESULT_ENVELOPE_CHARS = 96
MAX_TOOL_BODY_CHARS = MAX_TOOL_RESULT_CHARS - TOOL_RESULT_ENVELOPE_CHARS

KOREAN_FILLER = (
    "트와이스는 대한민국의 다국적 걸 그룹으로 여러 나라 출신 멤버가 함께 활동하며 "
    "다양한 음악 장르를 소화한다. 데뷔 이후 국내외 여러 시상식에서 상을 받았고 "
    "월드투어를 통해 폭넓은 팬층을 확보했다. "
)


def korean_chars(n):
    out = []
    while sum(len(x) for x in out) < n:
        out.append(KOREAN_FILLER)
    return "".join(out)[:n]


def wiki_result(chars):
    """앱이 실제로 만드는 모양의 위키 툴 결과."""
    return '{"status":"success","data":"Title: 트와이스\\n--- SUMMARY ---\\n' + korean_chars(chars) + '"}'


def roundtrip(lab, si, depth, tool_result, label, ceiling=ENGINE_MAX_TOKENS):
    """툴 왕복을 돌리고 단계별 토큰을 보고한다. 초과 여부를 돌려준다."""
    cfg = K.Config(system=si)
    history = F.messages(mode="verbatim", before=F.WIKI_HISTORY_CUT)
    history = history[-depth:] if depth else []
    kwargs = lab._conv_kwargs(cfg, history)

    print(f"\n--- {label} (히스토리 {depth}개, 툴 결과 {len(tool_result)}자) ---")
    with lab.engine.create_conversation(**kwargs) as conv:
        r1 = conv.send_message(F.as_app_sends("트와이스가 언제 데뷔했어?"))
        after_call = conv.token_count
        calls, _ = K.extract(r1)
        name = (calls[0].get("name") if calls else None) or "search_wikipedia"
        print(f"  1) 프리필 + 사용자 턴 + 툴 호출 : {after_call:>5} 토큰  (호출={name})", flush=True)
        if after_call > PREFILL_CEILING:
            print(f"     [!] 프리필이 천장 {PREFILL_CEILING} 을 넘었다 — 히스토리 예산을 다시 볼 것")

        r2 = conv.send_message(
            L.Message.tool(L.Contents.of(L.Content.ToolResponse(name, tool_result)))
        )
        after_final = conv.token_count
        _, text = K.extract(r2)
        grew = after_final - after_call
        print(f"  2) 툴 결과 + 최종 답변        : {after_final:>5} 토큰  "
              f"(증가 {grew}, 답변 {len(text)}자)", flush=True)

        over = after_final - ceiling
        if over > 0:
            print(f"     ** {ceiling} 대비 {over} 초과 **")
        else:
            print(f"     {ceiling} 대비 여유 {-over}")
    return after_final


def main():
    si = F.system_instruction()
    if si is None or F.turn_reminder() is None:
        print("[!] 픽스처가 없다. PromptFixtureExportTest 를 먼저 돌릴 것.")
        return 1

    print(f"새 예산: 천장 {PREFILL_CEILING} / 툴 결과 {MAX_TOOL_RESULT_TOKENS} 토큰"
          f"(JSON {MAX_TOOL_RESULT_CHARS}자, 본문 {MAX_TOOL_BODY_CHARS}자)"
          f" / 생성 {GENERATION_HEADROOM} / 구조 {TOOL_ROUNDTRIP_STRUCTURE}")
    print(f"등호 검사: {PREFILL_CEILING} + {TOOL_ROUNDTRIP_STRUCTURE} + {MAX_TOOL_RESULT_TOKENS} + "
          f"{GENERATION_HEADROOM} = "
          f"{PREFILL_CEILING + TOOL_ROUNDTRIP_STRUCTURE + MAX_TOOL_RESULT_TOKENS + GENERATION_HEADROOM}"
          f" (= {ENGINE_MAX_TOKENS} 이어야 한다)")

    result = wiki_result(MAX_TOOL_BODY_CHARS)

    # === 1·2) 용량을 크게 잡아 수치를 관측한다 ===
    print("\n=== 관측 (max_num_tokens=8192 — 넘어도 오류가 안 나므로 얼마나 남는지 보인다) ===")
    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=8192)
    try:
        # [WHY] 18개는 히스토리 예산 1,100 토큰 ÷ 실측 58 토큰/메시지다.
        normal = roundtrip(lab, si, 18, result, "정상 경로")
        # [WHY] 히스토리를 최대한 채워 천장 근처를 만든다. 픽스처가 79개뿐이므로 40 으로 둔다.
        worst = roundtrip(lab, si, 40, result, "최악 경로 (히스토리 과적)")
        # [WHY] 예전 상한이 얼마나 나빴는지 같은 조건에서 함께 보인다.
        old = roundtrip(lab, si, 18, wiki_result(5000), "예전 상한 5000자 (대조군)")
    finally:
        lab.close()

    # === 3) 실전 4096 ===
    print("\n=== 실전 (max_num_tokens=4096 — exp25 를 실패시킨 조건) ===")
    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=4096)
    ok = True
    try:
        roundtrip(lab, si, 18, result, "정상 경로 @ 4096")
    except Exception as e:
        ok = False
        print(f"  [!] 실패: {e}")
    finally:
        lab.close()

    print("\n=== 판정 ===")
    print(f"  정상 경로        {normal:>5} 토큰  {'OK' if normal <= ENGINE_MAX_TOKENS else '초과'}")
    print(f"  최악 경로        {worst:>5} 토큰  {'OK' if worst <= ENGINE_MAX_TOKENS else '초과'}")
    print(f"  예전 상한(대조군) {old:>5} 토큰  {'OK' if old <= ENGINE_MAX_TOKENS else '초과 — 예상대로'}")
    print(f"  실전 4096 왕복    {'오류 없이 완료' if ok else '실패'}")
    if normal <= ENGINE_MAX_TOKENS and ok:
        print("\n  → ADR-020 이 원인을 없앴다.")
    else:
        print("\n  → 아직 남았다. CJK_TOKENS_PER_100_CHARS 또는 천장을 다시 볼 것.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

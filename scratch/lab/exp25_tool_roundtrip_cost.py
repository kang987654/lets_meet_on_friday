"""exp25 — 툴 왕복이 KV 를 얼마나 먹는가 (예산 재계산용).

## 왜 필요한가

2026-08-13 실기기에서 `Input token ids are too long: 4097 >= 4096` 으로 추론이 실패했다.
직전 증상(`<|tool_call>call:add_schedule{` 가 본문으로 새고 그 자리에서 멈춤)도 같은 원인이다 —
0.14.0 은 초과를 조용히 삼켰고 0.16.0 은 오류로 낸다.

원인은 **내 예산 계산의 구멍**이다. ADR-017 에서 천장을 이렇게 잡았다:

    PREFILL_CEILING = ENGINE_MAX_TOKENS(4096) - GENERATION_HEADROOM(768) = 3328

`768` 을 **"한 번의 생성"** 분으로 추정했는데, 툴 턴은 한 번이 아니다:

    사용자 발화 → 모델의 툴 호출 출력 → 툴 결과 → 최종 답변

네 조각이 모두 같은 대화의 KV 에 쌓인다. 게다가 `getOrCreateConversation` 은 툴 응답 턴에서
**토큰 초과를 검사하지 않고** 기존 대화를 돌려준다(0.8.5 사고 때문에 넣은 가드) — 즉 툴 루프
안에서는 KV 가 검사 없이 자란다.

그리고 위키 결과에는 **문자 캡 5000자**만 있고 토큰 예산과의 연결이 없다
(`WikipediaSearchToolImpl`, 한국어는 `else` 분기). 한국어는 문자당 토큰이 크므로 이 한 번의
툴 결과가 KV 를 통째로 먹을 수 있다.

## 무엇을 재는가

1. **한국어 문자 → 토큰 비율** — 5000자 캡이 몇 토큰인지 환산할 근거
2. **툴 왕복 단계별 `token_count` 증가** — 기준선 → 사용자 턴 → 툴 호출 → 툴 결과 → 최종 답변
   - 툴 결과를 작은 것(일정 등록)과 큰 것(위키 5000자)으로 나눠 본다

이 숫자로 `GENERATION_HEADROOM_TOKENS` 와 툴 결과 상한을 **추정이 아니라 실측**에서 정한다.
"""

import sys

import litert_lm as L

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

# 위키 결과 최악 케이스 — 한국어 캡(5000자)을 채운 문자열.
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


def measure_ratio(lab, si):
    """한국어 문자 수 대비 토큰 수를 잰다."""
    print("=== 한국어 문자 → 토큰 비율 ===")
    for chars in (200, 1000, 5000):
        cfg = K.Config(tools=[], few_shot=False, system=si, constrained=None,
                       max_output_tokens=4)
        with lab.engine.create_conversation(**lab._conv_kwargs(cfg, None)) as conv:
            base = None
            conv.send_message("안녕")
            base = conv.token_count
        with lab.engine.create_conversation(**lab._conv_kwargs(cfg, None)) as conv:
            conv.send_message(korean_chars(chars))
            grown = conv.token_count
        delta = grown - base
        print(f"  {chars:>5}자 → 약 {delta:>5} 토큰  (문자당 {delta/chars:.2f})", flush=True)
    return None


def measure_roundtrip(lab, si, depth, tool_result, label):
    """툴 왕복 단계별 KV 증가를 잰다."""
    cfg = K.Config(system=si)
    history = F.messages(mode="verbatim", before=F.WIKI_HISTORY_CUT)
    history = history[-depth:] if depth else []
    kwargs = lab._conv_kwargs(cfg, history)

    print(f"\n=== 툴 왕복: {label} (히스토리 {depth}개) ===")
    with lab.engine.create_conversation(**kwargs) as conv:
        # 1) 사용자 발화 + 모델의 툴 호출
        r1 = conv.send_message(F.as_app_sends("다음주 월요일 10시 팀 회의"))
        after_call = conv.token_count
        calls, _ = K.extract(r1)
        name = (calls[0].get("name") if calls else None) or "add_schedule"
        print(f"  1) 사용자 턴 + 툴 호출 후 : {after_call:>5} 토큰  (호출={name})", flush=True)

        # 2) 툴 결과 되돌리기 + 최종 답변
        r2 = conv.send_message(
            L.Message.tool(L.Contents.of(L.Content.ToolResponse(name, tool_result)))
        )
        after_final = conv.token_count
        _, text = K.extract(r2)
        print(f"  2) 툴 결과 + 최종 답변 후 : {after_final:>5} 토큰  "
              f"(증가 {after_final - after_call:>4}, 답변 {len(text)}자)", flush=True)

        over = after_final - 4096
        verdict = f"**{over} 초과**" if over > 0 else f"여유 {-over}"
        print(f"     4096 대비: {verdict}", flush=True)
    return after_final


def main():
    si = F.system_instruction()
    if si is None or F.turn_reminder() is None:
        print("[!] 픽스처가 없다.")
        return 1

    small = '{"status":"success","message":"일정이 성공적으로 추가되었습니다."}'
    big = '{"status":"success","data":"Title: 트와이스\\n--- SUMMARY ---\\n' + korean_chars(5000) + '"}'
    print(f"작은 툴 결과 {len(small)}자 / 큰 툴 결과 {len(big)}자 (위키 한국어 캡 5000자 기준)\n")

    # [WHY] 용량을 크게 잡아 **초과 자체를 관측**한다. 4096 으로 두면 오류가 나서 얼마나 넘는지
    # 알 수 없다 — 우리가 알고 싶은 것은 "얼마나 부족한가" 다.
    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=8192)
    try:
        measure_ratio(lab, si)
        for depth in (0, 20):
            measure_roundtrip(lab, si, depth, small, "작은 결과(일정)")
            measure_roundtrip(lab, si, depth, big, "큰 결과(위키 5000자)")
    finally:
        lab.close()

    print("\n판정 지침:")
    print("  큰 결과에서 4096 을 넘으면 → 툴 결과 상한을 토큰 예산에서 파생시켜야 한다")
    print("  작은 결과에서도 넘으면    → 히스토리 예산 자체가 과하다")
    print("  단계 2 의 증가폭이 곧 GENERATION_HEADROOM 이 덮어야 하는 값이다")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

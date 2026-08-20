"""exp20 — 툴 지침을 사용자 턴에 다시 붙이면 호출이 돌아오는가 (#1 처방 후보).

## 여기까지의 결론

  exp18  툴 호출 구조를 히스토리에 복원해도 일정 툴은 0/3 — 구조 누락이 원인이 아니다
  exp19  같은 답변을 60자로 자르면 3/3 — 어시스턴트 산문이 있으면 툴을 안 부른다
  임계값  60자 통과 / 120자 실패 — 절단으로 해결하려면 대화 기억을 포기해야 한다

즉 "히스토리를 어떻게 담느냐"로는 풀리지 않는다. 남은 후보는 **지침의 위치**다.

## 가설

툴 지침은 시스템 턴에 한 번만 있다. 히스토리가 쌓이면 지침과 사용자 발화 사이에 수천 토큰의
산문이 끼어 지침이 멀어진다. ADR-010 에서 `[System Data]` 날짜 블록의 **위치**만 바꿔도 툴
선택이 달라진 선례가 있다(같은 문구, 다른 자리 → 조회 3/3 vs 1/3).

그래서 지침을 사용자 턴에 다시 붙여 **가까이** 둔다. 프롬프트 한 곳을 고치는 것으로 끝나므로
Room 마이그레이션도, 대화 기억 포기도 필요 없다.

세 조건:
    baseline   지금 앱 (지침은 시스템 턴에만)
    repeat     지침 전체를 사용자 턴 앞에 다시 붙임
    short      한 줄 요약만 사용자 턴 앞에 붙임  ← 토큰이 싸다

## 실측 (2026-08-12~13, cpu, 일정 등록 + 위키 검색)

    variant     depth=20  depth=40
    baseline    1/2       0/2
    repeat      2/2       -          (전체 지침 1,519자 재게시)
    short       2/2       2/2        (한 줄 166자)

**지침의 위치가 지렛대였다.** 전체 재게시와 한 줄 요약의 결과가 같으므로 **9배 싼 한 줄**을
쓴다. 앱은 `PromptAssembler.withTurnToolReminder` 로 이 한 줄을 사용자 턴 앞에 붙인다(ADR-017).

이것이 왜 듣는지: 지침은 시스템 턴에 한 번만 있고 히스토리가 쌓이면 사용자 발화와의 거리가
수천 토큰이 된다. ADR-010 에서 `[System Data]` 블록의 **문구가 아니라 위치**가 툴 선택을 갈랐던
것과 같은 현상이다.
"""

import sys

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

DEPTHS = (20, 40)
PROBES = [
    ("다음주 월요일 10시 팀 회의", "schedule", "start_time", "2026-08-17T10:00"),
    ("위키에서 에스파 검색해줘", "wikipedia", None, None),
]

SHORT_REMINDER = (
    "[Tool Usage Guidelines] For THIS request, first decide if one of your tools applies. "
    "If it does, you MUST call the tool — do not answer from memory or merely promise."
)


def guidance_block(system_instruction):
    """시스템 지시에서 `[Tool Usage Guidelines]` 절만 떼어낸다."""
    marker = "[Tool Usage Guidelines]"
    idx = system_instruction.find(marker)
    return system_instruction[idx:] if idx >= 0 else SHORT_REMINDER


def main():
    si = F.system_instruction()
    if si is None:
        print("[!] 픽스처가 없다.")
        return 1

    full_guidance = guidance_block(si)
    variants = {
        "baseline": lambda utterance: utterance,
        "repeat": lambda utterance: f"{full_guidance}\n\n{utterance}",
        "short": lambda utterance: f"{SHORT_REMINDER}\n\n{utterance}",
    }

    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=8192)
    hist = F.messages(mode="verbatim", before=F.WIKI_HISTORY_CUT)
    table = {}
    try:
        for name, wrap in variants.items():
            for depth in DEPTHS:
                history = hist[-depth:] if depth else []
                hits = []
                for utterance, expect, arg_key, expect_arg in PROBES:
                    r = lab.run(wrap(utterance), K.Config(system=si), history)
                    names = [c.get("name") or "" for c in r.tool_calls]
                    called = any(expect in n for n in names)
                    ok = called and (not arg_key or F.digit_ok(r.arg(arg_key), expect_arg))
                    hits.append(ok)
                    print(f"  {name:9} depth={depth:<3} "
                          f"{'O' if ok else ('~' if called else '.')} "
                          f"{str(names or '없음'):24} {utterance[:18]}", flush=True)
                table[(name, depth)] = hits
    finally:
        lab.close()

    print(f"\n{'variant':11}" + "".join(f"depth{d:<7}" for d in DEPTHS))
    for name in variants:
        cells = "".join(
            f"{sum(table[(name, d)])}/{len(table[(name, d)])}".ljust(12) for d in DEPTHS
        )
        print(f"{name:11}{cells}")

    base = sum(sum(table[("baseline", d)]) for d in DEPTHS)
    rep = sum(sum(table[("repeat", d)]) for d in DEPTHS)
    sho = sum(sum(table[("short", d)]) for d in DEPTHS)
    print(f"\n합계 — baseline {base}, repeat {rep}, short {sho}")
    if max(rep, sho) > base:
        cheaper = "short" if sho >= rep else "repeat"
        print(f"→ 지침 위치가 지렛대다. `{cheaper}` 로 프롬프트만 고치면 된다.")
    else:
        print("→ 위치도 아니다. 남은 후보는 두 단계 추론(짧은 문맥으로 툴 판단 → 전체 문맥으로 답변)이다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

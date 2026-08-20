"""exp18 — 히스토리에 툴 구조를 복원하면 툴 호출이 돌아오는가 (#1 수정 선검증).

## 왜 먼저 이걸 하는가

exp14 가 원인을 짚었다: 히스토리의 어시스턴트 답변을 짧게 대체하면(`terse`) 툴 호출이 돌아오고,
실제 답변을 두면(`verbatim`) 어느 깊이에서도 안 돌아온다. 그래서 처방은 "재생하는 히스토리에
툴 호출 구조를 복원한다" 인데, 그 처방은 **Room 마이그레이션 + 도메인 계약 변경**이 필요하다.
듣지 않는 수정에 그 비용을 쓰기 전에 여기서 판정한다.

## 세 모양을 같은 조건으로 비교한다

    verbatim    user(q) → model(산문 답변)                        ← 지금 앱이 재생하는 모양
    structured  user(q) → model("", tool_calls) → tool(resp)
                        → model(산문 답변)                        ← 제안하는 수정
    terse       user(q) → model("네, 알겠습니다.")                 ← 모방 대상 제거 (상한선)

사용자 발화와 최종 산문은 세 모양이 **모두 같다.** 다른 것은 툴 구조의 유무뿐이다.

`structured` 가 `verbatim` 보다 낫고 `terse` 에 가까우면 처방이 옳다. `verbatim` 과 같으면
구조 복원만으로는 부족하다는 뜻이고, 그때는 히스토리 축약(terse 쪽)을 함께 검토해야 한다.

## 주의

감사 로그에 `TOOL_CALL` 이 3건뿐이라(대부분 히스토리가 0.9.0 감사 복구 이전) 실제 기록으로
재구성할 수 없다. `structured` 는 답변 문구로 툴을 추론해 **구조만** 문서 형식으로 바꾼다 —
검증 대상이 내용이 아니라 구조이므로 이것으로 충분하다. 실제 수정에서는 DB 에 저장된 진짜
호출·응답이 들어간다.
"""

import sys

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

SHAPES = ("verbatim", "structured", "terse")
DEPTHS = (8, 20, 40)

# 툴이 필요한 발화 — 일정(구조화 인자까지 검사 가능)과 위키(기기에서 실패한 그 발화)를 섞는다.
PROBES = [
    ("다음주 월요일 10시 팀 회의", "add_schedule", "start_time", "2026-08-17T10:00"),
    ("위키에서 에스파 검색해줘", "search_wikipedia", None, None),
]


def main():
    si = F.system_instruction()
    if si is None:
        print("[!] 픽스처가 없다. ./gradlew :app:testDebugUnitTest --tests '*PromptFixtureExport*'")
        return 1

    print(f"구조 복원 대상 어시스턴트 턴: {F.structured_turn_count(before=F.WIKI_HISTORY_CUT)}개")
    print(f"발화 {len(PROBES)}개 × 깊이 {DEPTHS} × 모양 {SHAPES}\n")

    # [WHY] 용량을 넉넉히 준다. exp17 에서 깊은 히스토리가 기본 4096 을 넘어 거부되는 것을
    # 확인했다 — 여기서 재려는 것은 용량이 아니라 구조의 효과이므로 그 변수를 뺀다.
    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=8192)
    results = {}
    try:
        for shape in SHAPES:
            full = F.messages(mode=shape, before=F.WIKI_HISTORY_CUT)
            for depth in DEPTHS:
                history = full[-depth:] if depth else []
                hits = []
                for utterance, expect_tool, arg_key, expect_arg in PROBES:
                    r = lab.run(utterance, K.Config(system=si), history)
                    names = [c.get("name") or "" for c in r.tool_calls]
                    called = any(expect_tool in n for n in names)
                    digits = True
                    if called and arg_key:
                        digits = F.digit_ok(r.arg(arg_key), expect_arg)
                    hits.append((called, digits))
                    mark = "O" if called and digits else ("~" if called else ".")
                    print(f"  {shape:11} depth={depth:<3} {mark} {str(names or '없음'):26} {utterance[:20]}")
                results[(shape, depth)] = hits
    finally:
        lab.close()

    print("\n=== 요약 (O=호출+자릿수 OK, ~=호출했으나 자릿수 깨짐, .=미호출) ===")
    print(f"{'depth':>7}" + "".join(f"{s:>13}" for s in SHAPES))
    for depth in DEPTHS:
        cells = []
        for shape in SHAPES:
            hits = results.get((shape, depth), [])
            ok = sum(1 for c, d in hits if c and d)
            cells.append(f"{ok}/{len(hits)}")
        print(f"{depth:>7}" + "".join(f"{c:>13}" for c in cells))

    verb = sum(sum(1 for c, d in results.get(("verbatim", x), []) if c) for x in DEPTHS)
    stru = sum(sum(1 for c, d in results.get(("structured", x), []) if c) for x in DEPTHS)
    terse = sum(sum(1 for c, d in results.get(("terse", x), []) if c) for x in DEPTHS)
    print(f"\n호출 합계 — verbatim {verb}, structured {stru}, terse {terse}")
    if stru > verb:
        print("→ 구조 복원이 효과가 있다. Room 마이그레이션을 진행할 근거가 된다.")
    else:
        print("→ 구조 복원만으로는 부족하다. 히스토리 축약을 함께 검토해야 한다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

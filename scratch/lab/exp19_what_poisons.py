"""exp19 — terse 가 이긴 이유가 길이인가 내용인가 (#1 처방 확정용).

exp18 에서 `structured`(툴 구조 복원)는 일정 툴에 **전혀 듣지 않았고** `terse`(답변을
"네, 알겠습니다."로 대체)만 3/3 이었다. 그래서 구조가 아니라 어시스턴트 답변의 **내용**이
요인이라는 쪽으로 기운다.

기기 히스토리에는 툴이 동작하지 않던 시절의 답변이 많다 —
  "네, 알겠습니다! **내일 오후 3시에 치과 예약**이 있으시군요."
  "네, 사용자님! **자전거 비밀번호는 1234**라고 기억해 두겠습니다."
즉 **일정·기억 요청에 툴 없이 말로만 응답한 전례**가 잔뜩 있다. 모델이 그걸 따른다면 처방은
"툴 구조 복원"이 아니라 "그런 전례를 히스토리에 싣지 않는 것"이 된다.

네 모양을 비교한다:
    verbatim  실제 답변 그대로              (지금 앱)
    clipped   같은 답변을 60자로 자름       ← 길이만 줄임, 내용은 남음
    terse     "네, 알겠습니다." 로 대체     ← 내용 제거
    useronly  어시스턴트 턴을 아예 뺌       ← 하한선

  clipped 이 verbatim 과 같으면 → 길이가 아니라 **내용**이 요인 (terse/useronly 만 듣는다)
  clipped 이 terse 만큼 좋으면  → 길이가 요인 (히스토리 축약으로 해결 가능)

## 실측 (2026-08-12, cpu, 일정 등록 발화)

    형태        depth=20  depth=40
    verbatim    미호출     미호출
    clipped     호출 OK    호출 OK      ← 내용은 그대로, 길이만 60자로 자름
    terse       호출 OK    호출 OK
    useronly    호출 OK    -

**길이가 요인이다.** "툴 없이 답한 전례를 모방한다" 는 가설은 배제된다 — 그 전례가 남아 있어도
짧으면 툴을 부른다.

절단 길이 임계값을 따로 쟀다: **60자 통과 / 120자·240자 실패.** 임계가 너무 낮아 절단으로
해결하려면 대화 기억을 사실상 포기해야 하므로 제품 해법이 못 된다 → exp20 으로 넘어갔다.
"""

import sys

import device_fixture as F
import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "cpu"

SHAPES = ("verbatim", "clipped", "terse", "useronly")
DEPTHS = (20, 40)
UTTERANCE, ARG_KEY, EXPECTED = "다음주 월요일 10시 팀 회의", "start_time", "2026-08-17T10:00"


def main():
    si = F.system_instruction()
    if si is None:
        print("[!] 픽스처가 없다.")
        return 1

    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=8192)
    table = {}
    try:
        for shape in SHAPES:
            full = F.messages(mode=shape, before=F.WIKI_HISTORY_CUT)
            for depth in DEPTHS:
                hist = full[-depth:] if depth else []
                r = lab.run(UTTERANCE, K.Config(system=si), hist)
                names = [c.get("name") or "" for c in r.tool_calls]
                called = any("schedule" in n for n in names)
                arg = r.arg(ARG_KEY)
                ok = called and F.digit_ok(arg, EXPECTED)
                table[(shape, depth)] = (called, ok)
                print(f"  {shape:10} depth={depth:<3} "
                      f"{'O' if ok else ('~' if called else '.')} "
                      f"{str(names or '없음'):24} {str(arg)!r}", flush=True)
    finally:
        lab.close()

    print(f"\n{'shape':12}" + "".join(f"depth{d:<8}" for d in DEPTHS))
    for shape in SHAPES:
        cells = "".join(
            f"{'호출' if table[(shape, d)][0] else '미호출':<13}" for d in DEPTHS
        )
        print(f"{shape:12}{cells}")

    verb = any(table[("verbatim", d)][0] for d in DEPTHS)
    clip = any(table[("clipped", d)][0] for d in DEPTHS)
    terse = any(table[("terse", d)][0] for d in DEPTHS)
    print()
    if clip and not verb:
        print("→ 길이가 요인이다. 히스토리에 싣는 답변을 줄이는 것이 처방이다.")
    elif terse and not clip:
        print("→ **내용**이 요인이다. 툴 없이 답한 전례가 히스토리에 있으면 모델이 그것을 따른다.")
        print("   길이를 줄여도 그 전례가 남아 있으면 효과가 없다.")
    else:
        print("→ 판정 보류. 두 요인이 섞여 있거나 표본이 부족하다.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

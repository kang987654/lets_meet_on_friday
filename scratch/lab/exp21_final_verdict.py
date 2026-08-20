"""exp21 — 앱이 실제로 보내는 모양으로 최종 판정한다 (#1 수정 검증).

exp18~20 으로 원인이 **지침과 사용자 발화 사이의 거리**로 좁혀졌고, 앱은
`PromptAssembler.withTurnToolReminder` 로 지침 한 줄을 사용자 턴 앞에 붙이게 됐다(ADR-017).

이 실험은 그 수정이 실제로 듣는지 **앱이 내보낸 픽스처 그대로** 확인한다:
  - 시스템 지시   `fixtures/system_instruction.txt`   (PromptAssembler 출력)
  - 턴 지침       `fixtures/turn_reminder.txt`        (같은 출력에서 떼어낸 접두사)
  - 히스토리      실기기 DB **원본**(verbatim) — 자르거나 바꾸지 않는다
  - 툴 3종        일정 등록 / 위키 검색 / 기억 조회

손으로 베낀 문구를 쓰지 않는 것이 핵심이다. 앱 프롬프트가 바뀌면 픽스처도 함께 바뀌므로 이
실험은 언제 돌려도 **지금 배포되는 것**을 측정한다.

## 실측 (2026-08-13, cpu, max_num_tokens=8192)

    depth=20  O add_schedule  O search_wikipedia  O search_memory
    depth=40  O add_schedule  O search_wikipedia  O search_memory

**6/6.** 원본 히스토리에서 세 툴 모두, 두 깊이 모두 호출되고 일정 인자 자릿수도 온전하다
(`2026-08-17T10:00:00`). 같은 조건의 baseline 은 depth 40 에서 0/2 였다(exp20).
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
    ("내 자전거 비밀번호 뭐였지?", "search_memory", None, None),
]


def main():
    si = F.system_instruction()
    reminder = F.turn_reminder()
    if si is None or reminder is None:
        print("[!] 픽스처가 없다. 먼저:")
        print("    ./gradlew :app:testDebugUnitTest --tests '*PromptFixtureExport*'")
        return 1

    print(f"시스템 지시 {len(si)}자 / 턴 지침 {len(reminder)}자")
    print(f"턴 지침: {reminder}\n")

    hist = F.messages(mode="verbatim", before=F.WIKI_HISTORY_CUT)
    lab = K.Lab(backend=BACKEND, verbose=False, max_num_tokens=8192)
    total = ok_count = 0
    try:
        for depth in DEPTHS:
            for utterance, expect, arg_key, expect_arg in PROBES:
                r = lab.run(F.as_app_sends(utterance), K.Config(system=si), hist[-depth:])
                names = [c.get("name") or "" for c in r.tool_calls]
                called = any(expect in n for n in names)
                ok = called and (not arg_key or F.digit_ok(r.arg(arg_key), expect_arg))
                total += 1
                ok_count += 1 if ok else 0
                mark = "O" if ok else ("~" if called else ".")
                print(f"  depth={depth:<3} {mark} {str(names or '없음'):24} {utterance[:22]}",
                      flush=True)
    finally:
        lab.close()

    print(f"\n{ok_count}/{total}")
    if ok_count == total:
        print("→ 원본 히스토리에서 모든 툴이 호출된다. 수정이 듣는다.")
    else:
        print("→ 아직 빠지는 조건이 있다. 어떤 툴·깊이인지 위 표를 볼 것.")
    return 0 if ok_count == total else 1


if __name__ == "__main__":
    raise SystemExit(main())

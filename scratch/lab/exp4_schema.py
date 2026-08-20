"""실험 4 — 앱과 파이썬의 스키마 차이가 호출 품질에 영향을 주는지.

앱(Kotlin ReflectionTool)과 파이썬(tool_from_function)의 렌더링이 다르다:
  - 앱: `endTime: String?` → `nullable:true` 이지만 **required 에 남는다** (0.8.1 실기기 preface 확인)
  - 파이썬: `end_time: str = ""` → **required 에서 빠진다**

즉 실험 1~3 의 성공은 "required 가 2개인 스키마" 조건에서 얻은 것이고, 앱은 4개다.
required 4개가 호출을 방해하는지 직접 비교한다. 방해한다면 앱 선언을 손봐야 한다.
"""

import sys

import kosmos_lab as K
from exp3_final import final_prompt, get_schedule_narrowed

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"


def add_schedule_all_required(title: str, start_time: str, end_time: str, memo: str) -> dict:
    """사용자의 캘린더에 일정을 추가한다. 약속·예약·미팅·병원·시험 등 앞으로 일어날 일을 등록할 때 쓴다.

    Args:
        title: 일정 제목. 예: '치과 예약'
        start_time: 시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'
        end_time: 종료 시각. ISO 8601 형식. 모르면 시작 1시간 뒤.
        memo: 메모. 없으면 빈 문자열.
    """
    raise AssertionError("실행되지 않아야 한다")


CASES = [
    "내일 3시에 치과 예약 잡아줘",
    "모레 저녁 7시에 친구랑 약속 있어. 일정 추가해줘",
    "다음주 월요일 10시 팀 회의 등록해줘",
    "8월 20일 오후 2시 병원 예약",
]


def main():
    lab = K.Lab(backend=BACKEND)
    for label, sched in [
        ("required=2 (파이썬 기본, 실험1~3 조건)", K.add_schedule),
        ("required=4 (앱 현재 스키마와 동일)", add_schedule_all_required),
    ]:
        cfg = K.Config(
            label=label,
            tools=[sched, get_schedule_narrowed, K.add_memory, K.search_wikipedia],
            system=final_prompt(),
        )
        d = __import__("litert_lm").tool_from_function(sched).get_tool_description()["function"]
        print(f"\n{'=' * 100}\n{label}\n  required={d['parameters']['required']}\n{'=' * 100}")
        ok = 0
        for prompt in CASES:
            r = lab.run(prompt, cfg)
            called = r.called.startswith("add_schedule")
            ok += called
            line = f"  {'OK  ' if called else 'FAIL'} {r.seconds:5.1f}s  {prompt}"
            if r.tool_calls:
                line += f"\n            → {r.tool_calls[0].get('arguments')}"
            elif r.text:
                line += f'\n            → "{r.text[:110].replace(chr(10), " ")}"'
            print(line, flush=True)
        print(f"  ── {ok}/{len(CASES)}")
    lab.close()


if __name__ == "__main__":
    main()

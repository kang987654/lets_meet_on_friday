"""exp68 — 0.39.3 M1: 설명·문단 축약(②③④⑤)을 넣은 판으로 회귀 세트 전체를 돈다. 기준(baseline.json)에서 새로 깨진 것이 없으면 채택.

②′ get_schedule date 설명 53→34(제약 문장 유지 — ② 53→15 는 'tomorrow' 를 낳았다, exp68b) ③ 상대 날짜 문단 78→39 ④ add_schedule 예시 제거 ⑤ search_memory 설명 30→10 (exp67·M0 tokenize).
앱 반영(0.39.3 M2) 전 픽스처 기준 스크립트 — 반영 뒤의 픽스처에는 옛 문단이 없어 assert 에 걸린다(기록용).
사용: python exp68_trim.py [run.py 인자 — --sets=… 등]. 판정·출력은 run.py 그대로(기준 저장은 하지 않는다).
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402
import kosmos_lab as K  # noqa: E402

OLD_DATES = ("Resolve relative dates and times yourself from the [System Data] values above — \"내일\", \"모레\", \"다음주 월요일\", \"오후 3시\" are NOT missing information. "
             "Compute the absolute ISO 8601 value and call the tool. Never ask the user to restate a date you can compute. A time with no date word means today.")
NEW_DATES = "Compute dates and times yourself from [System Data] above (\"내일\", \"모레\", \"오후 3시\" are not missing). A time with no date word means today."

assert R.SYS_OFF.count(OLD_DATES) == 1 and R.SYS_ON.count(OLD_DATES) == 1
R.SYS_ON = R.SYS_ON.replace(OLD_DATES, NEW_DATES)
R.SYS_OFF = R.SYS_OFF.replace(OLD_DATES, NEW_DATES)
R.turn.__defaults__ = (R.SYS_OFF,) + R.turn.__defaults__[1:]

K.get_schedule.__doc__ = """사용자의 캘린더 일정을 조회한다.

    Args:
        date: 반드시 'today' 또는 'week'. 오늘이 아닌 날(내일·모레·다음주)은 'week'. 다른 값은 쓰지 않는다.
    """
K.add_schedule.__doc__ = """사용자의 캘린더에 일정을 추가한다.

    Args:
        title: 일정 제목.
        start_time: 시작 시각. ISO 8601 형식.
        end_time: 종료 시각. ISO 8601 형식. 모르면 시작 1시간 뒤.
        memo: 메모. 없으면 빈 문자열.
    """
K.search_memory.__doc__ = """저장해 둔 메모에서 찾는다.

    Args:
        keyword: 찾을 핵심 키워드. 문장이 아니라 명사 위주의 짧은 단어로 쓴다. 예: '자전거 비밀번호', '와이파이', '알레르기'
    """

if __name__ == "__main__":
    if "--save-baseline" in sys.argv:
        sys.exit("기준 저장은 하지 않는다 — 결과 확인 뒤 앱 반영(M2)에서")
    first = R.first_kv if hasattr(R, "first_kv") else None
    c = K.create_chat_conversation(R.engine(), tools=R.TOOLS_OFF, system_message=R.SYS_OFF)
    c.send_message(K.with_turn_reminder("안녕"))
    print(f"[exp68] 축약판 첫 턴 KV {c.token_count}", flush=True)
    c.close()
    R.main()

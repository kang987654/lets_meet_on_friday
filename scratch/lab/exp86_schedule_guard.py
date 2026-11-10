"""exp86 — 일정 추가 가드(0.41.11 후보 (가)) 미리 보기.

사용자 말에 날짜·시각 표현이 없으면 add_schedule 을 실행하지 않고 안내를 돌려줄 때 모델이 어떻게 답하는지,
그 뒤 "내일 3시"로 답하면 일정 추가로 이어지는지 본다. 앱과 같은 프롬프트(회귀 세트 모듈)를 쓴다.
"""
import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
TIME_WORDS = re.compile(
    r"\d+\s*시|몇\s*시|\d+\s*분|오전|오후|아침|점심|저녁|밤|새벽|정오|자정|오늘|내일|모레|글피|어제|요일|주말|"
    r"(다음|이번|담)\s*(주|달)|\d+\s*월|\d+\s*일|\d{1,2}:\d{2}|"
    r"\b(today|tomorrow|tonight|morning|noon|evening|am|pm|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next\s+week)\b",
    re.I,
)
GUARD = "아직 일정을 추가하지 않았습니다. 사용자의 말에 날짜나 시각이 없습니다. 일정을 잡아 달라는 말이면 언제인지 물어보고, 사실을 알려 준 것이면 확인한 뒤 \"메모해 둘까요?\"라고 물어보세요."


def turn(history, user, guard):
    hist = R.model_history((history or []) + [R.M("user", user)])[:-1]
    log = []
    with R.open_conversation(hist, R.TOOLS_OFF, R.SYS_OFF) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        for _ in range(3):
            calls = res.get("tool_calls") or []
            if not calls:
                break
            fn = calls[0]["function"]
            name, args = fn["name"], fn.get("arguments") or {}
            if guard and name == "add_schedule" and not TIME_WORDS.search(user):
                body = {"status": "error", "message": GUARD}
                log.append(f"막힘 {name} {json.dumps(args, ensure_ascii=False)}")
            elif name == "add_schedule":
                body = {"status": "success", "message": "일정이 성공적으로 추가되었습니다."}
                log.append(f"실행 {name} {json.dumps(args, ensure_ascii=False)}")
            elif name == "search_memory":
                body = {"status": "success", "data": R.SEARCH_EMPTY}
                log.append(f"실행 {name} {json.dumps(args, ensure_ascii=False)}")
            else:
                body = {"status": "success", "data": "없음"}
                log.append(f"실행 {name} {json.dumps(args, ensure_ascii=False)}")
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
    return R.text_of(res), log


def show(tag, user, ans, log):
    print(f"[{tag}] 사용자: {user}", flush=True)
    for line in log:
        print(f"    툴: {line}", flush=True)
    print(f"    답: {ans}", flush=True)


if __name__ == "__main__" and "--h3" not in sys.argv:
    for first, follow in [("치과 예약 잡아줘", "내일 3시"), ("회의 일정 추가해줘", "모레 오전 10시"), ("주차는 지하 2층 C-14에 했어", None)]:
        for guard in (False, True):
            tag = "가드" if guard else "지금"
            ans, log = turn(None, first, guard)
            show(tag, first, ans, log)
            if follow:
                ans2, log2 = turn([R.M("user", first), R.M("model", ans)], follow, guard)
                show(tag + " 이어서", follow, ans2, log2)
            print(flush=True)

if "--h3" in sys.argv:
    # 회귀 세트 fact h3 와 같은 앞 기록 3쌍 — 이 조건에서만 일정으로 오인했다(0.41.9·0.41.10 회귀 세트).
    for u in ["주차는 지하 2층 C-14에 했어", "오늘 차는 B2 17번에 세웠어", "나 다음 주부터 판교로 출근해"]:
        for guard in (False, True):
            ans, log = turn(R.fact_history(3), u, guard)
            show("가드" if guard else "지금", u, ans, log)
        print(flush=True)

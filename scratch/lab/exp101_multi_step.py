"""exp101 — 0.42.6: 여러 단계 요청을 지금 구조로 어디까지 하나 (재기만).

앱 BaseAgent 와 같게: 한 턴에 생성 최대 3번, 툴 실행은 2번까지 — 세 번째 생성이 또 툴을 부르면 실행하지 않고 "상한 초과"(앱은 오류 말풍선).
가드(일정 시각·요일·지난 시각·메모 저장 등)는 회귀 세트 turn() 과 같은 판정. 가짜 툴 결과는 고정(아래 SCHEDULE·MEMO), 쓰기 툴은 성공으로 돌려준다(승인 시트는 통과로 가정).
지금 = 2026-11-09(월) 10:53(픽스처). 내일 = 11-10(화).
사용: python exp101_multi_step.py > exp101_out.txt
"""
import json
import pathlib
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
SCHEDULE_KO = ("[일정]\n- 팀 회의 (11월 10일 오전 10:00 ~ 오전 11:00)\n- 점심 약속 (11월 10일 오후 1:00 ~ 오후 2:00)\n- 고객 미팅 (11월 10일 오후 4:00 ~ 오후 5:00)"
               "\n이 목록에서 사용자가 물은 날짜와 일치하는 일정만 골라 답하고, 그 날짜에 일정이 없으면 없다고만 답하고 다른 날짜 일정은 말하지 마세요.")
SCHEDULE_EN = ("[Schedule]\n- Team meeting (Nov 10, 10:00 AM ~ 11:00 AM)\n- Lunch (Nov 10, 1:00 PM ~ 2:00 PM)\n- Client meeting (Nov 10, 4:00 PM ~ 5:00 PM)"
               "\nOnly report events whose date matches the date the user asked about; if there is no event on that date, state only that nothing is scheduled for that date and do not mention events on other dates.")
MEMO_KO = "- 병원 예약은 11월 12일 오후 2시 [병원, 예약]"
MEMO_EN = "- Hospital appointment is Nov 12 at 2 PM [hospital, appointment]"
BUSY = [("10:00", "11:00"), ("13:00", "14:00"), ("16:00", "17:00")]


def free_on_tomorrow(st):
    if not st or not st.startswith("2026-11-10T"):
        return False
    hm = st[11:16]
    return not any(a <= hm < b for a, b in BUSY)


# (이름, 언어, 사용자 말, 판정 함수(calls) -> bool, 쓰기 없어야 하는가)
CASES = [
    ("A1 일정 확인 → 빈 시간 치과", "ko", "내일 일정 확인하고 비는 시간에 치과 예약 잡아줘",
     lambda c: [n for n, _ in c][:1] == ["get_schedule"] and any(n == "add_schedule" and free_on_tomorrow(str(a.get("start_time"))) for n, a in c)),
    ("A2 오후 빈 시간 헬스", "ko", "내일 오후에 비는 시간 찾아서 헬스 일정 넣어줘",
     lambda c: [n for n, _ in c][:1] == ["get_schedule"] and any(n == "add_schedule" and free_on_tomorrow(str(a.get("start_time"))) and str(a.get("start_time"))[11:13] >= "12" for n, a in c)),
    ("B1 두 일정 한 번에", "ko", "내일 3시 치과, 5시 팀 미팅 둘 다 잡아줘",
     lambda c: sorted(str(a.get("start_time"))[:16] for n, a in c if n == "add_schedule") == ["2026-11-10T15:00", "2026-11-10T17:00"]),
    ("B2 모레 두 일정", "ko", "모레 오전 10시 회의랑 오후 2시 병원 일정 넣어줘",
     lambda c: sorted(str(a.get("start_time"))[:16] for n, a in c if n == "add_schedule") == ["2026-11-11T10:00", "2026-11-11T14:00"]),
    ("C1 기억 → 알림", "ko", "내가 알려준 병원 예약 시간에 알림 맞춰줘",
     lambda c: [n for n, _ in c][:1] == ["search_memory"] and any(n == "add_reminder" and str(a.get("time", "")).startswith("2026-11-12T14:00") for n, a in c)),
    ("C2 기억 → 하루 전 알림", "ko", "병원 예약 하루 전에 알림 맞춰줘",
     lambda c: [n for n, _ in c][:1] == ["search_memory"] and any(n == "add_reminder" and str(a.get("time", "")).startswith("2026-11-11T14:00") for n, a in c)),
    ("D1 첫 일정 30분 전 알림", "ko", "내일 첫 일정 30분 전에 알려줘",
     lambda c: [n for n, _ in c][:1] == ["get_schedule"] and any(n == "add_reminder" and str(a.get("time", "")).startswith("2026-11-10T09:30") for n, a in c)),
    ("E1 못 하는 요청", "ko", "이번 주 일정 다 지우고 다시 잡아줘", None),
    ("A-en free slot dentist", "en", "Check my schedule tomorrow and book a dentist appointment in a free slot",
     lambda c: [n for n, _ in c][:1] == ["get_schedule"] and any(n == "add_schedule" and free_on_tomorrow(str(a.get("start_time"))) for n, a in c)),
    ("B-en two events", "en", "Schedule a dentist at 3pm and a team meeting at 5pm tomorrow",
     lambda c: sorted(str(a.get("start_time"))[:16] for n, a in c if n == "add_schedule") == ["2026-11-10T15:00", "2026-11-10T17:00"]),
    ("C-en memory to reminder", "en", "Set a reminder at the hospital appointment time I told you about",
     lambda c: [n for n, _ in c][:1] == ["search_memory"] and any(n == "add_reminder" and str(a.get("time", "")).startswith("2026-11-12T14:00") for n, a in c)),
]
WRITE = {"add_schedule", "add_reminder", "add_memory"}
CLAIM = ("잡았", "추가했", "등록했", "넣었", "맞춰 드렸", "설정했", "added", "scheduled", "set a reminder", "booked")


def turn(user, en):
    sysm = R.SYS_EN_OFF if en else R.SYS_OFF
    calls, blocked, overflow = [], [], False
    with K.create_chat_conversation(R.engine(), tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        for _ in range(2):  # 앱: 툴 실행은 2번까지(생성 1·2번째의 호출), 생성 3번째의 호출은 상한 초과
            tc = res.get("tool_calls") or []
            if not tc:
                break
            fn = tc[0]["function"]
            name, args = fn["name"], fn.get("arguments") or {}
            # 가드 — 회귀 세트와 같은 판정(일정만 필요한 것)
            err = None
            if name == "add_schedule":
                if not R.SCHEDULE_TIME.search(user):
                    err = R.SCHEDULE_NO_TIME
                elif (wd := R.weekday_mismatch_guidance(user, args.get("start_time"), sysm, en=en)):
                    err = wd
                elif (pe := R.past_guidance(user, args.get("start_time"), sysm, en=en)):
                    err = pe
            if err:
                body = {"status": "error", "message": err}
                blocked.append(name)
            elif name == "get_schedule":
                body = {"status": "success", "data": SCHEDULE_EN if en else SCHEDULE_KO}
            elif name == "search_memory":
                body = {"status": "success", "data": MEMO_EN if en else MEMO_KO}
            elif name in WRITE:
                body = {"status": "success", "message": "Done." if en else "완료했어요."}
            else:
                body = {"status": "success", "data": "없음"}
            if not err:
                calls.append((name, args))
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
        # 세 번째 생성 뒤에도 툴을 부르면 앱은 실행하지 않고 오류 — 실행 2번을 넘긴 것
        if res.get("tool_calls"):
            overflow = True
        return R.text_of(res), calls, blocked, overflow


def main():
    totals = {}
    for rep in range(2):
        for name, lang, user, judge in CASES:
            t0 = time.time()
            ans, calls, blocked, overflow = turn(user, lang == "en")
            sec = time.time() - t0
            names = [n for n, _ in calls]
            if judge is None:
                ok = not any(n in WRITE for n in names) and not any(w in ans for w in ("지웠", "삭제했", "deleted"))
                verdict = "정답(못 한다고 함)" if ok else "틀림"
            elif overflow:
                verdict, ok = "상한 초과", False
            elif judge(calls):
                verdict, ok = "정답", True
            elif not calls or all(n not in WRITE for n in names):
                claimed = any(w in ans for w in CLAIM)
                verdict, ok = ("지어낸 성공" if claimed else "중간에 멈춤/물음"), False
            else:
                verdict, ok = "틀린 툴·인자", False
            totals.setdefault(name, []).append(verdict)
            args_s = "; ".join(f"{n}({json.dumps(a, ensure_ascii=False)[:90]})" for n, a in calls)
            print(f"[{rep}] {name}: {verdict} | {sec:.1f}s | {args_s}{' 막힘' + str(blocked) if blocked else ''}\n     답: {ans[:150]}", flush=True)
    print("\n######## 합계", flush=True)
    for name, vs in totals.items():
        print(f"{name}: {vs}", flush=True)
    flat = [v for vs in totals.values() for v in vs]
    for v in ("정답", "정답(못 한다고 함)", "중간에 멈춤/물음", "틀린 툴·인자", "지어낸 성공", "상한 초과", "틀림"):
        print(f"  {v}: {flat.count(v)}", flush=True)


if __name__ == "__main__":
    main()

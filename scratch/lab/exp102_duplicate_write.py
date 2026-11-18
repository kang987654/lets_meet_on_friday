"""exp102 — 0.42.7: 같은 턴 같은 인자 쓰기 재호출 막기(결과 문구) + 툴 실행 상한 2 → 3.

조건: NOW(지금 — 실행 2번, 중복도 실행) · G1/G2(중복 막기 문구 두 개, 실행 2번) · G1+3 / G2+3(실행 3번).
막힌 호출도 생성 1번을 쓴다(앱과 같음). 가드는 exp101 과 같은 일정 판정. 쓰기는 성공으로 돌려준다.
판정: 바라는 시작 시각 집합이 정확히 들어감(정답) · 일부만 + 다 했다고 말함(거짓 완료) · 일부만 + 솔직 · 상한 초과.
사용: python exp102_duplicate_write.py > exp102_out.txt
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
MSG = {
    "G1": ("실행하지 않았습니다. 이 일정은 방금 이미 추가했습니다. 사용자가 부탁한 다른 항목이 남았으면 그 항목을 추가하세요.",
           "Not executed. You already added this one. If the user asked for other items, add those now."),
    "G2": ("실행하지 않았습니다. 같은 내용을 이미 추가했습니다({what}). 아직 추가하지 않은 다른 항목을 추가하고, 없으면 끝났다고 답하세요.",
           "Not executed. This was already added ({what}). Add any item you have not added yet, or say you are done."),
}
CONDS = [("NOW", None, 2), ("G1", "G1", 2), ("G2", "G2", 2), ("G1+3", "G1", 3), ("G2+3", "G2", 3)]
CASES = [
    ("B1 두 일정", "ko", "내일 3시 치과, 5시 팀 미팅 둘 다 잡아줘", ["2026-11-10T15:00", "2026-11-10T17:00"]),
    ("B2 모레 두 일정", "ko", "모레 오전 10시 회의랑 오후 2시 병원 일정 넣어줘", ["2026-11-11T10:00", "2026-11-11T14:00"]),
    ("B3 세 일정", "ko", "내일 오전 9시 운동, 12시 점심, 오후 3시 치과 다 잡아줘", ["2026-11-10T09:00", "2026-11-10T12:00", "2026-11-10T15:00"]),
    ("B-en 두 일정", "en", "Schedule a dentist at 3pm and a team meeting at 5pm tomorrow", ["2026-11-10T15:00", "2026-11-10T17:00"]),
    ("대조 한 일정", "ko", "내일 오후 3시에 치과 예약 잡아줘", ["2026-11-10T15:00"]),
    ("대조 알림 하나", "ko", "내일 아침 8시에 약 먹으라고 알려줘", ["2026-11-10T08:00"]),
]
WRITE = {"add_schedule", "add_reminder", "add_memory"}
CLAIM = ("모두", "둘 다", "다 잡", "세 가지", "all", "both")


def start_of(name, args):
    return str(args.get("start_time") or args.get("time") or "")[:16]


def turn(user, en, guard, max_exec):
    sysm = R.SYS_EN_OFF if en else R.SYS_OFF
    done, writes_seen, blocked = [], set(), 0
    with K.create_chat_conversation(R.engine(), tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        for _ in range(max_exec):
            tc = res.get("tool_calls") or []
            if not tc:
                break
            fn = tc[0]["function"]
            name, args = fn["name"], fn.get("arguments") or {}
            key = name + json.dumps(args, ensure_ascii=False, sort_keys=True)
            err = None
            if name == "add_schedule":
                if not R.SCHEDULE_TIME.search(user):
                    err = R.SCHEDULE_NO_TIME
                elif (wd := R.weekday_mismatch_guidance(user, args.get("start_time"), sysm, en=en)):
                    err = wd
                elif (pe := R.past_guidance(user, args.get("start_time"), sysm, en=en)):
                    err = pe
            if not err and guard and name in WRITE and key in writes_seen:
                what = f"{args.get('title') or args.get('content', '')} {start_of(name, args)}"
                err = MSG[guard][1 if en else 0].replace("{what}", what)
                blocked += 1
            if err:
                body = {"status": "error", "message": err}
            elif name in WRITE:
                body = {"status": "success", "message": "Done." if en else "완료했어요."}
                writes_seen.add(key)
                done.append(start_of(name, args))
            else:
                body = {"status": "success", "data": "없음"}
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
        overflow = bool(res.get("tool_calls"))
        return R.text_of(res), done, blocked, overflow


def main():
    summary = {}
    for cname, guard, max_exec in CONDS:
        print(f"\n######## {cname}", flush=True)
        tally = {"정답": 0, "거짓 완료": 0, "일부만(솔직)": 0, "상한 초과": 0, "틀림": 0}
        for rep in range(2):
            for name, lang, user, want in CASES:
                ans, done, blocked, overflow = turn(user, lang == "en", guard, max_exec)
                got = sorted(set(done))
                dup = len(done) - len(set(done))
                if got == sorted(want) and not dup and not overflow:
                    v = "정답"
                elif overflow:
                    v = "상한 초과"
                elif set(got) < set(want) or dup:
                    v = "거짓 완료" if any(w in ans for w in CLAIM) else "일부만(솔직)"
                else:
                    v = "틀림"
                tally[v] += 1
                print(f"  [{rep}] {name}: {v} | 실행 {done} 중복막힘 {blocked}{' 상한' if overflow else ''}\n     답: {ans[:130]}", flush=True)
        summary[cname] = tally
        print(f"== {cname}: {tally}", flush=True)
    print("\n######## 합계 (문항 6 × 2회)", flush=True)
    for c, t in summary.items():
        print(f"{c}: {t}", flush=True)


if __name__ == "__main__":
    main()

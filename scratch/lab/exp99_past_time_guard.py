"""exp99 — 0.42.5 M1·M2: 지난 시각으로 잡는 일정을 앱 가드로 막고 묻게 하기 + 종료 시각 되묻기 빈도 + "병원 이름 물음" 재현 빈도.

지금 시각을 15:20(월 2026-11-09)으로 흉내 낸다(턴 리마인더 [Now]). 회귀 세트의 turn() 을 그대로 쓰고, 요일 가드 뒤에 "지난 시각" 가드를 끼운다(앱과 같은 순서 예정).
조건: G0 가드 없음(지금) · P1 · P2 · P3 (결과 문구 후보). 가드에 막히면 모델 답 → 사용자 "응" → 다음 턴.
판정: 지난 시각 문항에서 최종 시작 시각이 내일(11-10) 같은 시각이면 성공 · 오늘 과거로 넣으면 실패 · 종료 시각을 물은 턴 수.
사용: python exp99_past_time_guard.py > exp99_out.txt
"""
import datetime as dt
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
NOW = dt.datetime(2026, 11, 9, 15, 20)
TODAY, TOMORROW = "2026-11-09", "2026-11-10"
_orig_reminder = K.with_turn_reminder
K.with_turn_reminder = lambda u: _orig_reminder(u).replace("[Now] 10:53", "[Now] 15:20")

DATE_WORD = re.compile(r"오늘|어제|아까|방금|\d+\s*월|\d+\s*일|today|yesterday|earlier", re.I)

VARIANTS = {
    "G0": None,
    "P1": lambda hm, d: f"실행하지 않았습니다. 오늘 {hm}은 이미 지났습니다. 내일 {hm}인지 사용자에게 물어보세요.",
    "P2": lambda hm, d: f"실행하지 않았습니다. 지금은 15:20 이라 오늘 {hm}은 지났습니다. 내일({d}) {hm}로 잡을지 사용자에게 물어보세요.",
    "P3": lambda hm, d: f"실행하지 않았습니다. 오늘 {hm}은 이미 지났습니다. 내일({d}) {hm}로 잡을지만 물어보세요. 끝나는 시각은 묻지 마세요.",
}
VARIANT = None
_orig_wd = R.weekday_mismatch_guidance


def guard(user, start_time, sysm, en=False):
    wd = _orig_wd(user, start_time, sysm, en=en)
    if wd or VARIANT is None or not start_time:
        return wd
    try:
        st = dt.datetime.fromisoformat(str(start_time)[:16])
    except ValueError:
        return None
    if st < NOW and not DATE_WORD.search(user):
        return VARIANT(st.strftime("%H:%M"), TOMORROW)
    return None


R.weekday_mismatch_guidance = guard

# (이름, [사용자 말...], 기대 시작 접두 또는 None(통과 대조))
PAST = [
    ("아침 운동 → 7시", ["아침 운동 일정 추가해줘", "7시"], f"{TOMORROW}T07:00"),
    ("헬스 → 아침 8시", ["헬스 일정 잡아줘", "아침 8시"], f"{TOMORROW}T08:00"),
    ("점심 약속 → 12시", ["점심 약속 일정 잡아줘", "12시"], f"{TOMORROW}T12:00"),
    ("한 턴 아침 9시 회의", ["아침 9시에 팀 회의 일정 넣어줘"], f"{TOMORROW}T09:00"),
    ("한 턴 오전 10시 치과", ["오전 10시에 치과 예약 잡아줘"], f"{TOMORROW}T10:00"),
    ("한 턴 오전 11시 미용실", ["오전 11시 미용실 예약해줘"], f"{TOMORROW}T11:00"),
]
CONTROL = [
    ("대조 오후 5시 회의", ["오후 5시에 회의 일정 잡아줘"], f"{TODAY}T17:00"),
    ("대조 오늘이라고 말함", ["오늘 아침 9시에 회의 있었는데 일정에 기록해줘"], f"{TODAY}T09:00"),
]
HOSPITAL = [
    ("모레 치과(새 대화)", None, "모레 오전 10시에 치과 예약 잡아줘"),
    ("모레 치과(앞 기록)", [R.M("user", "요즘 이가 좀 시려"), R.M("model", "찬 음식에 시리시면 치과에 한번 가 보시는 게 좋아요.")], "모레 오전 10시에 치과 예약 잡아줘"),
    ("내일 안과(새 대화)", None, "내일 오후 2시에 안과 예약 잡아줘"),
    ("모레 피부과(앞 기록)", [R.M("user", "피부가 많이 건조하네"), R.M("model", "보습제를 자주 발라 주세요.")], "모레 오후 4시에 피부과 예약 잡아줘"),
]
END_ASK = re.compile(r"종료|끝나는|몇 시까지|언제까지")
YES = "응"


def run_case(utts):
    hist, steps, final, end_asks = [], [], None, 0
    queue = list(utts)
    for _ in range(4):
        if not queue:
            break
        u = queue.pop(0)
        ans, ex, bl, _ = R.turn(hist, u)
        st = next((str(a.get("start_time", "")) for n, a in ex if n == "add_schedule"), "")
        steps.append(f"{u} → {('넣음 ' + st) if st else ''}{(' 막힘' + str(bl)) if bl else ''} | {ans[:70]}")
        if not st and END_ASK.search(ans):
            end_asks += 1
        hist += [R.M("user", u), R.M("model", ans)]
        if st:
            final = st
            break
        if bl and not queue:
            queue.append(YES)
    return final, end_asks, steps


def main():
    global VARIANT
    for vname, vfun in VARIANTS.items():
        VARIANT = vfun
        good = bad_past = other = end_total = ctrl_ok = 0
        print(f"\n######## {vname}", flush=True)
        for rep in range(2):
            for name, utts, want in PAST:
                final, ends, steps = run_case(utts)
                end_total += ends
                if final and final.startswith(want):
                    good += 1
                elif final and final.startswith(TODAY):
                    bad_past += 1
                else:
                    other += 1
                print(f"  [{rep}] {name}: {final or '안 넣음'} | 종료질문 {ends}\n     " + "\n     ".join(steps), flush=True)
            for name, utts, want in CONTROL:
                final, ends, steps = run_case(utts)
                ctrl_ok += bool(final and final.startswith(want))
                print(f"  [{rep}] {name}: {final or '안 넣음'}\n     " + "\n     ".join(steps), flush=True)
        print(f"== {vname}: 내일로 바르게 {good}/12 · 오늘 과거로 {bad_past} · 그 밖 {other} · 대조 통과 {ctrl_ok}/4 · 종료 시각 질문 {end_total}", flush=True)

    VARIANT = None
    print("\n######## 병원 이름 물음 (가드 없음, 2회)", flush=True)
    called = 0
    for rep in range(2):
        for name, hist, u in HOSPITAL:
            ans, ex, bl, first = R.turn(hist, u)
            ok = any(n == "add_schedule" for n, _ in ex) or (first and first["name"] == "add_schedule")
            called += ok
            print(f"  [{rep}] {name}: {'호출' if ok else '안 부름'} | {ans[:80]}", flush=True)
    print(f"== 병원: 바로 호출 {called}/8", flush=True)


if __name__ == "__main__":
    main()

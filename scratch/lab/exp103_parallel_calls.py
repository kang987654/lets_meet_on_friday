"""exp103 — 0.42.7: 한 생성의 툴 호출 여러 개를 어떻게 처리할까.

exp102 에서 원인 확인: 모델은 "둘 다 잡아줘"에 첫 생성에서 호출 2개(세 항목이면 3개)를 바르게 내는데, 앱이 첫 번째만 실행하고 나머지를 버린다(`toolCalls.first()`).
조건
  NOW : 지금 — 첫 호출만 실행, 결과는 첫 것만(실행 2번)
  S   : 차례로 모두 실행 — 한 생성의 호출을 하나씩(가드 각각) 실행하고 결과를 묶어 한 응답으로("results" 목록)
  T   : 첫 호출만 실행 + 결과에 "다른 호출 n건(…)은 실행하지 않았습니다. 하나씩 다시 부르세요." + 실행 3번
판정은 exp102 와 같음(바라는 시작 시각 집합 · 중복 · 거짓 완료).
사용: python exp103_parallel_calls.py > exp103_out.txt
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import exp102_duplicate_write as E  # noqa: E402

R, K = E.R, E.K
TELL = ("같은 차례에 부른 다른 호출 {n}건({items})은 실행하지 않았습니다. 남은 것은 하나씩 다시 부르세요.",
        "{n} other call(s) in the same step ({items}) were not executed. Call the remaining ones one at a time.")


def guard_err(name, args, user, sysm, en):
    if name != "add_schedule":
        return None
    if not R.SCHEDULE_TIME.search(user):
        return R.SCHEDULE_NO_TIME
    return R.weekday_mismatch_guidance(user, args.get("start_time"), sysm, en=en) or R.past_guidance(user, args.get("start_time"), sysm, en=en)


def run_one(name, args, user, sysm, en, done):
    err = guard_err(name, args, user, sysm, en)
    if err:
        return {"status": "error", "message": err}
    if name in E.WRITE:
        done.append(E.start_of(name, args))
        return {"status": "success", "message": "Done." if en else "완료했어요."}
    return {"status": "success", "data": "없음"}


def turn(user, en, mode):
    sysm = R.SYS_EN_OFF if en else R.SYS_OFF
    done = []
    max_exec = 3 if mode == "T" else 2
    with K.create_chat_conversation(R.engine(), tools=R.TOOLS_OFF, system_message=sysm) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        for _ in range(max_exec):
            tc = res.get("tool_calls") or []
            if not tc:
                break
            fns = [c["function"] for c in tc]
            first = fns[0]
            if mode == "S" and len(fns) > 1:
                results = [{"call": f["name"], **run_one(f["name"], f.get("arguments") or {}, user, sysm, en, done)} for f in fns]
                body = {"status": "success" if all(r["status"] == "success" for r in results) else "partial", "results": results}
            else:
                body = run_one(first["name"], first.get("arguments") or {}, user, sysm, en, done)
                if mode == "T" and len(fns) > 1:
                    items = ", ".join(f"{(f.get('arguments') or {}).get('title', f['name'])} {E.start_of(f['name'], f.get('arguments') or {})[11:]}" for f in fns[1:])
                    body["note"] = TELL[1 if en else 0].format(n=len(fns) - 1, items=items)
            res = conv.send_message(K.tool_response_message(first["name"], json.dumps(body, ensure_ascii=False)))
        overflow = bool(res.get("tool_calls"))
        return R.text_of(res), done, overflow


def main():
    summary = {}
    for mode in ("NOW", "S", "T"):
        print(f"\n######## {mode}", flush=True)
        tally = {"정답": 0, "거짓 완료": 0, "일부만(솔직)": 0, "상한 초과": 0, "틀림": 0}
        for rep in range(2):
            for name, lang, user, want in E.CASES:
                ans, done, overflow = turn(user, lang == "en", mode)
                got, dup = sorted(set(done)), len(done) - len(set(done))
                if got == sorted(want) and not dup and not overflow:
                    v = "정답"
                elif overflow:
                    v = "상한 초과"
                elif set(got) < set(want) or dup:
                    v = "거짓 완료" if any(w in ans for w in E.CLAIM + ("and your", "and a team")) else "일부만(솔직)"
                else:
                    v = "틀림"
                tally[v] += 1
                print(f"  [{rep}] {name}: {v} | 실행 {done}{' 상한' if overflow else ''}\n     답: {ans[:130]}", flush=True)
        summary[mode] = tally
        print(f"== {mode}: {tally}", flush=True)
    print("\n######## 합계 (문항 6 × 2회)", flush=True)
    for m, t in summary.items():
        print(f"{m}: {t}", flush=True)


if __name__ == "__main__":
    main()

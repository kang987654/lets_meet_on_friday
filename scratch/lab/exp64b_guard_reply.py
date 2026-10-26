"""exp64b — 0.38.9 M1: 사실 저장 가드(앱이 add_memory 를 막고 결과 문구를 돌려줌)의 결과 문구 후보.

앱 가드 흉내: 사용자 말에 저장 낱말(넓힌 판)이 없고 바로 앞 비서 말이 저장 물음이 아니면 add_memory 를 실행하지 않고 ToolResultJson.error(문구)를 돌려준다.
후보:
  G1 "저장하지 않았습니다. 사용자는 저장을 부탁하지 않았습니다. 들은 내용을 짧게 확인하고 \"메모해 둘까요?\"라고 물어보세요."
  G2 G1 + "기억하겠다거나 저장했다고 말하지 마세요."
  G3 "아직 저장하지 않았습니다. 저장하기 전에 \"메모해 둘까요?\"라고 물어보세요."
지표: 사실 7 × 기록 h0/h3 — 막힌 뒤 답이 물어봄·거짓 약속·엉뚱(못했어요/기능 없음)·같은 턴 재호출, 이어 "응" → 저장(값 포함).
낱말: 넓힌 저장 낱말이 저장 부탁 12(안 6·밖 6)를 잡는지, 사실 7 에 잘못 걸리지 않는지(코드 판정만).
"""
import json
import re
import sys

import exp62_trust_order as X
import exp64_fact_guard_base as B
import kosmos_lab as K

VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "G1,G2,G3").split(",")
G1 = "저장하지 않았습니다. 사용자는 저장을 부탁하지 않았습니다. 들은 내용을 짧게 확인하고 \"메모해 둘까요?\"라고 물어보세요."
CANDS = {"G1": G1, "G2": G1 + " 기억하겠다거나 저장했다고 말하지 마세요.", "G3": "아직 저장하지 않았습니다. 저장하기 전에 \"메모해 둘까요?\"라고 물어보세요."}

SAVE_WIDE = re.compile(r"기억|저장|메모|잊지\s*마|잊으면\s*안|까먹|적어|기록|외워|알아\s*(둬|두|놔)|챙겨")
ASK_PREV = re.compile(r"(메모|저장|기억)[^.?!]{0,8}(둘까|드릴까|할까)")
ODD = re.compile(r"(못했|실패|기능은 없|기능이 없|드릴 수 없|할 수 없)")
KEYS = {"택배": "6043", "사물함": "214", "주차": "C-14", "와이파이": "sky7788", "판교": "판교", "강아지": "콩", "회의": "4시"}


def key_of(fact):
    return next(v for k, v in KEYS.items() if k in fact)


def guarded_turn(conv, text, result, prev_model=""):
    """한 사용자 턴 — 막힌 호출 수와 최종 답, 실행된 add_memory 인자를 돌려준다."""
    res = conv.send_message(text)
    user = text.split("\n\n")[-1]
    blocked = 0
    saved = []
    for _ in range(3):
        cs = res.get("tool_calls") or []
        if not cs:
            break
        name = cs[0]["function"]["name"]
        if name == "add_memory" and not SAVE_WIDE.search(user) and not ASK_PREV.search(prev_model):
            blocked += 1
            body = json.dumps({"status": "error", "message": result}, ensure_ascii=False)
        elif name == "add_memory":
            saved.append(str(cs[0]["function"].get("arguments", {})))
            body = json.dumps({"status": "success", "message": "기억에 저장했어요."}, ensure_ascii=False)
        else:
            body = json.dumps({"status": "success", "message": "완료"}, ensure_ascii=False)
        res = conv.send_message(K.tool_response_message(name, body))
    return X.text_of(res).strip(), blocked, saved


print("## 낱말: 저장 부탁", sum(bool(SAVE_WIDE.search(u)) for u, _ in B.SAVE_IN + B.SAVE_OUT), "/", len(B.SAVE_IN + B.SAVE_OUT),
      "· 사실에 잘못 걸림", sum(bool(SAVE_WIDE.search(f)) for f in B.FACTS), "/", len(B.FACTS), flush=True)

for v in VSEL:
    for n in (0, 3):
        asked = promised = odd = again = yes_ok = yes_n = 0
        for f in B.FACTS:
            with K.create_chat_conversation(X.engine(), history=B.hist(n), tools=X.TOOLS, system_message=X.SYS) as conv:
                out, blocked, _ = guarded_turn(conv, f"{X.REM}\n\n{f}", CANDS[v])
                a = bool(X.ASK_SAVE.search(out))
                asked += a
                promised += bool(X.PROMISE.search(out))
                odd += bool(ODD.search(out))
                again += blocked > 1
                if a:
                    yout, _, saved = guarded_turn(conv, f"{X.REM}\n\n응", CANDS[v], prev_model=out)
                    yes_n += 1
                    hit = any(key_of(f) in s for s in saved)
                    yes_ok += hit
                    if not hit:
                        print(f"  [응 놓침] 저장 {saved[:1]} → {yout.replace(chr(10), ' / ')[:80]}", flush=True)
            print(f"[{v} h{n}] 막힘{blocked} {'물어봄 ' if a else ''}{'약속 ' if X.PROMISE.search(out) else ''}{'엉뚱 ' if ODD.search(out) else ''}| {f} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
        m = len(B.FACTS)
        print(f"== {v} h{n}: 물어봄 {asked}/{m} · 약속 {promised}/{m} · 엉뚱 {odd}/{m} · 재호출 {again}/{m} · 응→저장 {yes_ok}/{yes_n}", flush=True)

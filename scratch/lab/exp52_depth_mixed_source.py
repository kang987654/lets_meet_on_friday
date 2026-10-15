"""exp52 — 깊은 대화에서 메모·과거 대화 충돌 답 (0.37.0 M3 실기기 잔여).

실기기(2026-10-14, FP32·예산 2,800): KV 2,727 의 툴 회신 턴에서 "자전거 비밀번호 기억나?" → 4321(과거 대화 요약 값). 저장 메모는 134 이고
결과에는 메모가 맨 앞 + MIXED_SOURCE_GUIDE(0.36.1, exp50 B — 얕은 대화에서 충돌 2/2)가 붙는다. 숫자 깨짐이 아니라 깊이에 따른 안내 약화 가설.
조건(결과 문구, 모델 입력):
  B 현행 — 안내를 맨 앞에
  T 안내를 맨 뒤에(결과의 마지막 줄 — 답하기 직전에 읽힌다)
  BT 안내를 앞뒤 둘 다
깊이: exp29 깨끗한 히스토리 k 쌍(0 / 8 / 14 — 대략 1,400 / 2,000 / 2,500토큰). 앱처럼 GPU·FP32, 0.18.0(conda `friday`).
대조(부작용): 과거만·일치·메모만 — 깊이 14 에서 값을 그대로 답해야 한다.
"""
import json
import sys

import litert_lm as llm

import kosmos_lab as lab
from exp29_gpu_characterize import cleaned_history

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

GUIDE = "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."
EP_A = ("(과거 대화) 일정 알림, 자전거 비밀번호, 출시 준비: 사용자는 오후 1시 점심 알림을 요청했으나 지난 시각이라 안내받았습니다. 이후 자전거 비밀번호를 "
        "문의하여 이전에 '4321'로 변경된 기록이 있음을 확인했습니다. [알림, 자전거 비밀번호, 출시 준비]")
EP_B = ("(과거 대화) 자전거 비밀번호 변경 및 치과 예약 확인: 사용자는 처음에 자전거 비밀번호를 1234로 설정했으나, 이후 4321로 변경했습니다. "
        "비서는 이 변경 사항을 기억하겠다고 응답했습니다. [자전거, 비밀번호, 1234, 4321]")
EP_C = "(과거 대화) 자전거 비밀번호 확인 및 알림 설정 요청: 사용자는 자전거 비밀번호를 확인했고, 비서는 **4321**이라고 답변했습니다. [자전거, 비밀번호]"

CONFLICT = [
    ("충돌", "자전거 비밀번호 기억나?", ["자전거 비밀번호는 134 [비밀번호, 자전거]"], [EP_A, EP_B, EP_C], "134"),
    ("충돌", "내 자전거 비번 뭐였지?", ["자전거 비밀번호는 134 [비밀번호, 자전거]"], [EP_A, EP_B, EP_C], "134"),
    ("충돌2", "사물함 번호 뭐였지?", ["회사 사물함 번호는 58번 [사물함]"],
     ["(과거 대화) 사물함 정리: 사용자는 사물함을 옮기며 번호가 112번이라고 말했고 비서는 기억하겠다고 했습니다. [사물함, 112]"], "58"),
    ("충돌2", "내가 알려준 사물함 번호 기억나?", ["회사 사물함 번호는 58번 [사물함]"],
     ["(과거 대화) 사물함 정리: 사용자는 사물함을 옮기며 번호가 112번이라고 말했고 비서는 기억하겠다고 했습니다. [사물함, 112]"], "58"),
]
CONTROLS = [
    ("과거만", "집 와이파이 비밀번호 뭐였지?", [],
     ["(과거 대화) 공유기 교체: 사용자는 새 공유기를 달고 와이파이 비밀번호를 kosmos77로 바꿨다고 말했습니다. [와이파이, 공유기]"], "kosmos77"),
    ("일치", "자물쇠 비밀번호 뭐였지?", ["자물쇠 비밀번호는 4936 [비밀번호, 자물쇠]"],
     ["(과거 대화) 자물쇠 교체: 사용자는 새 자물쇠를 사서 비밀번호를 4936으로 맞췄습니다. [자물쇠, 4936]"], "4936"),
    ("메모만", "회사 주차 어디라고 했지?", ["회사 주차는 B2 구역 [주차]"], [], "B2"),
]


def data_for(variant, notes, episodes):
    body = "\n".join([f"- {n}" for n in notes] + [f"- {e}" for e in episodes])
    if not (notes and episodes):
        return body
    if variant == "B":
        return f"{GUIDE}\n{body}"
    if variant == "T":
        return f"{body}\n{GUIDE}"
    return f"{GUIDE}\n{body}\n{GUIDE}"


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def history_of(k):
    full = cleaned_history()
    pairs = [(full[i], full[i + 1]) for i in range(0, len(full) - 1, 2)]
    out = []
    for i in range(k):
        u, m = pairs[i % len(pairs)]
        out += [{"role": u[0], "content": u[1]}, {"role": m[0], "content": m[1]}]
    return out


def ask(engine, variant, k, case):
    name, q, notes, episodes, want = case
    conv = lab.create_chat_conversation(engine, history=history_of(k))
    res = conv.send_message(lab.with_turn_reminder(q))
    calls = res.get("tool_calls") or []
    if not calls or calls[0]["function"]["name"] != "search_memory":
        conv.close()
        return False, f"search_memory 아님 {calls}", 0
    payload = json.dumps({"status": "success", "data": data_for(variant, notes, episodes)}, ensure_ascii=False)
    final = conv.send_message(lab.tool_response_message("search_memory", payload))
    depth = conv.token_count
    conv.close()
    out = text_of(final)
    first = out.split("\n")[0].split(".")[0]
    return want in first, out[:90], depth


engine = llm.Engine(lab.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=lab.ENGINE_MAX_TOKENS,
                    max_num_images=lab.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
for variant in ("B", "T", "BT"):
    for k in (0, 8, 14):
        ok = 0
        for case in CONFLICT:
            hit, out, depth = ask(engine, variant, k, case)
            ok += hit
            print(f"[{variant}] k={k:2} {case[0]:4} {'OK' if hit else 'X '} tokens={depth} {out!r}", flush=True)
        print(f"== [{variant}] k={k} 충돌 {ok}/{len(CONFLICT)}", flush=True)
    ok = 0
    for case in CONTROLS:
        hit, out, depth = ask(engine, variant, 14, case)
        ok += hit
        print(f"[{variant}] k=14 {case[0]:4} {'OK' if hit else 'X '} tokens={depth} {out!r}", flush=True)
    print(f"== [{variant}] 대조 {ok}/{len(CONTROLS)}  안내 토큰 {len(engine.tokenize(GUIDE))}", flush=True)
engine.close()

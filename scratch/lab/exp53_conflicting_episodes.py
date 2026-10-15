"""exp53 — 메모와 다른 값을 적은 과거 대화 요약이 여럿일 때 결과 문구 (0.37.2).

exp52: 4321 을 적은 요약이 3건이면 깊이와 무관하게 0.36.1 안내(MIXED_SOURCE_GUIDE)가 진다(메모 134). 실기기에는 그런 요약이 6건.
조건(코드가 결과를 만든다 — 모델 입력):
  B 현행 — 안내 + 메모 + 요약 전부
  A 제거 — 메모와 같은 대상인데 메모의 숫자가 없는 요약 줄을 뺀다(남는 요약이 있으면 안내 유지)
  C 압축 — 그런 요약을 "과거 대화 N건에는 다른 값(…)이 적혀 있습니다" 한 줄로 줄인다
  C2 C 와 같고 줄 머리표만 다른 요약 줄과 같은 "(과거 대화) N건에는 …" (앱 적용 문구)
  자전거2 — 앱은 결과 상한(MAX_KNOWLEDGE_CONTEXT_ITEMS 3)이라 요약이 최대 2건 실린다(실기기 실패가 이 경우)
판정: 첫 문장에 정답 값. 대조: 과거만·일치·메모만·메모 값과 날짜가 함께 적힌 요약(빼면 안 되는 쪽) — 값 그대로.
깊이 0, GPU·FP32(앱과 같음), 0.18.0(friday18).
"""
import json
import sys

import litert_lm as llm

import kosmos_lab as lab

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

GUIDE = "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."

BIKE_NOTE = "자전거 비밀번호는 134 [비밀번호, 자전거]"
BIKE_EPS = [  # 실기기 요약 6건을 줄인 것 — 앞 5건은 134 가 없고(충돌), 마지막은 134 를 적었다(유지)
    ("(과거 대화) 자전거 비밀번호 변경 및 치과 예약 확인: 사용자는 처음에 자전거 비밀번호를 1234로 설정했으나, 이후 4321로 변경했습니다. [자전거, 비밀번호]", True),
    ("(과거 대화) 다양한 비밀번호 변경 요청: 사용자는 자전거 비밀번호를 4521로 설정했다가 1234로 변경하고 다시 4321로 변경했습니다. [비밀번호]", True),
    ("(과거 대화) 일정 알림, 자전거 비밀번호, 출시 준비: 자전거 비밀번호를 문의하여 이전에 '4321'로 변경된 기록이 있음을 확인했습니다. [알림, 자전거 비밀번호]", True),
    ("(과거 대화) 자전거 비밀번호 확인 및 알림 설정 요청: 사용자는 자전거 비밀번호를 확인했고, 비서는 **4321**이라고 답변했습니다. [자전거, 비밀번호]", True),
    ("(과거 대화) 자전거 비밀번호 변경 및 저장 요청: 사용자가 자전거 비밀번호를 1234로 저장해달라고 요청했습니다. [자전거, 비밀번호]", True),
    ("(과거 대화) 자전거 비밀번호 질문: 사용자가 자전거 비밀번호를 질문했고 비서는 134라고 답변했습니다. [자전거, 비밀번호]", False),
]

# (이름, 질문들, 메모들, [(요약, 메모와 충돌?)], 정답)
CASES = [
    ("자전거6", ["자전거 비밀번호 기억나?", "내 자전거 비번 뭐였지?"], [BIKE_NOTE], BIKE_EPS, "134"),
    ("자전거2", ["자전거 비밀번호 기억나?", "내 자전거 비번 뭐였지?"], [BIKE_NOTE], BIKE_EPS[:2], "134"),
    ("자전거3", ["자전거 비밀번호 기억나?", "내 자전거 비번 뭐였지?"], [BIKE_NOTE], BIKE_EPS[:3], "134"),
    ("사물함", ["사물함 번호 뭐였지?", "내가 알려준 사물함 번호 기억나?"], ["회사 사물함 번호는 58번 [사물함]"],
     [("(과거 대화) 사물함 정리: 사용자는 사물함을 옮기며 번호가 112번이라고 말했고 비서는 기억하겠다고 했습니다. [사물함, 112]", True)], "58"),
]
CONTROLS = [
    ("과거만", ["집 와이파이 비밀번호 뭐였지?"], [],
     [("(과거 대화) 공유기 교체: 사용자는 새 공유기를 달고 와이파이 비밀번호를 kosmos77로 바꿨다고 말했습니다. [와이파이, 공유기]", False)], "kosmos77"),
    ("일치", ["자물쇠 비밀번호 뭐였지?"], ["자물쇠 비밀번호는 4936 [비밀번호, 자물쇠]"],
     [("(과거 대화) 자물쇠 교체: 사용자는 새 자물쇠를 사서 비밀번호를 4936으로 맞췄습니다. [자물쇠, 4936]", False)], "4936"),
    ("일치+날짜", ["자물쇠 비밀번호 기억나?"], ["자물쇠 비밀번호는 4936 [비밀번호, 자물쇠]"],
     [("(과거 대화) 10월 3일 자물쇠 교체: 사용자는 10월 3일에 새 자물쇠를 사서 비밀번호를 4936으로 맞췄습니다. [자물쇠]", False)], "4936"),
    ("메모만", ["회사 주차 어디라고 했지?"], ["회사 주차는 B2 구역 [주차]"], [], "B2"),
]


def other_values(episodes):
    import re
    vals = []
    for text, _ in episodes:
        for v in re.findall(r"\d{3,}", text):
            if v not in vals:
                vals.append(v)
    return vals


def data_for(variant, notes, episodes):
    kept = episodes if variant == "B" else [(t, c) for t, c in episodes if not (notes and c)]
    dropped = [] if variant == "B" else [(t, c) for t, c in episodes if notes and c]
    lines = [f"- {n}" for n in notes] + [f"- {t}" for t, _ in kept]
    if variant == "C" and dropped:
        lines.append(f"- (과거 대화 {len(dropped)}건에는 다른 값이 적혀 있습니다: {', '.join(other_values(dropped))})")
    if variant == "C2" and dropped:
        lines.append(f"- (과거 대화) {len(dropped)}건에는 다른 값이 적혀 있습니다: {', '.join(other_values(dropped))}")
    body = "\n".join(lines)
    mixed = notes and (kept or (variant in ("C", "C2") and dropped))
    return f"{GUIDE}\n{body}" if mixed else body


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def ask(engine, variant, q, notes, episodes, want):
    conv = lab.create_chat_conversation(engine)
    res = conv.send_message(lab.with_turn_reminder(q))
    calls = res.get("tool_calls") or []
    if not calls or calls[0]["function"]["name"] != "search_memory":
        conv.close()
        return False, f"search_memory 아님 {calls}"
    payload = json.dumps({"status": "success", "data": data_for(variant, notes, episodes)}, ensure_ascii=False)
    final = conv.send_message(lab.tool_response_message("search_memory", payload))
    conv.close()
    out = text_of(final)
    first = out.split("\n")[0].split(".")[0]
    return want in first, out[:110]


engine = llm.Engine(lab.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=lab.ENGINE_MAX_TOKENS,
                    max_num_images=lab.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
for variant in (sys.argv[1:] or ["B", "A", "C", "C2"]):
    for label, group in (("충돌", CASES), ("대조", CONTROLS)):
        ok = total = 0
        for name, questions, notes, episodes, want in group:
            for q in questions:
                hit, out = ask(engine, variant, q, notes, episodes, want)
                ok += hit
                total += 1
                print(f"[{variant}] {name:6} {'OK' if hit else 'X '} {q!r} -> {out!r}", flush=True)
        print(f"== [{variant}] {label} {ok}/{total}", flush=True)
engine.close()

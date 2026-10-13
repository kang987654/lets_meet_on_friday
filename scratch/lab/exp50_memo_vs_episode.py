"""exp50 — 기억 검색 결과에서 직접 저장한 메모와 과거 대화 요약이 다를 때 무엇을 답하는가 (0.36.1 실기기 잔여 결함).

실기기(2026-10-13): 결과 맨 앞에 "자전거 비밀번호는 134"(수동 메모)가 와도, 뒤의 과거 대화 요약 두 건이 "4321로 변경"이라 적고 있어
모델이 4321 로 답했다. 결과 문구(모델 입력) 후보:
  A 현행 — SearchMemoryToolExecutor.formatHits 그대로
  B 메모와 과거 대화가 섞일 때만 맨 앞에 안내 한 줄
  C B + 메모 줄 앞에 "(저장된 메모)" 표지
대조(부작용): 과거 대화만 / 메모·과거 대화 일치 / 메모만 — 값을 그대로 답해야 한다.
"""
import json
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

GUIDE = "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."

EP_4321_A = ("(과거 대화) 일정 알림, 자전거 비밀번호, 출시 준비: 사용자는 오후 1시 점심 알림을 요청했으나 지난 시각이라 안내받았습니다. 이후 자전거 비밀번호를 "
             "문의하여 이전에 '4321'로 변경된 기록이 있음을 확인했습니다. [알림, 자전거 비밀번호, 출시 준비]")
EP_4321_B = ("(과거 대화) 자전거 비밀번호 변경 및 치과 예약 확인: 사용자는 처음에 자전거 비밀번호를 1234로 설정했으나, 이후 4321로 변경했습니다. "
             "비서는 이 변경 사항을 기억하겠다고 응답했습니다. [자전거, 비밀번호, 1234, 4321]")

# (이름, 질문들, 메모 줄들, 과거 대화 줄들, 답에 있어야 할 값, 답의 주장으로 나오면 안 되는 값)
CASES = [
    ("충돌", ["자전거 비밀번호 기억나?", "내 자전거 비번 뭐였지?"],
     ["자전거 비밀번호는 134 [비밀번호, 자전거]"], [EP_4321_A, EP_4321_B], "134", None),
    ("충돌2", ["사물함 번호 뭐였지?", "내가 알려준 사물함 번호 기억나?"],
     ["회사 사물함 번호는 58번 [사물함]"],
     ["(과거 대화) 사물함 정리: 사용자는 사물함을 옮기며 번호가 112번이라고 말했고 비서는 기억하겠다고 했습니다. [사물함, 112]"], "58", None),
    ("과거만", ["와이파이 비번 기억나?", "집 와이파이 비밀번호 뭐였지?"],
     [], ["(과거 대화) 공유기 교체: 사용자는 새 공유기를 달고 와이파이 비밀번호를 kosmos77로 바꿨다고 말했습니다. [와이파이, 공유기]"], "kosmos77", None),
    ("일치", ["자물쇠 비밀번호 뭐였지?", "자물쇠 비번 기억나?"],
     ["자물쇠 비밀번호는 4936 [비밀번호, 자물쇠]"],
     ["(과거 대화) 자물쇠 교체: 사용자는 새 자물쇠를 사서 비밀번호를 4936으로 맞췄습니다. [자물쇠, 4936]"], "4936", None),
    ("메모만", ["주차 구역 기억나?", "회사 주차 어디라고 했지?"],
     ["회사 주차는 B2 구역 [주차]"], [], "B2", None),
]


def data_for(variant, notes, episodes):
    note_lines = [f"- (저장된 메모) {n}" if variant == "C" else f"- {n}" for n in notes]
    lines = note_lines + [f"- {e}" for e in episodes]
    body = "\n".join(lines)
    if variant in ("B", "C") and notes and episodes:
        body = f"{GUIDE}\n{body}"
    return body


def text_of(res):
    c = res.get("content")
    if isinstance(c, str):
        return c
    return "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


engine = K.create_engine()
for v in ("A", "B", "C"):
    ok = total = 0
    for name, questions, notes, episodes, want, _ in CASES:
        for q in questions:
            with K.create_chat_conversation(engine) as conv:
                res = conv.send_message(K.with_turn_reminder(q))
                calls = res.get("tool_calls") or []
                if not calls or calls[0]["function"]["name"] != "search_memory":
                    print(f"[{v}] {name} -- search_memory 아님 {q!r} -> {calls}", flush=True)
                    total += 1
                    continue
                payload = json.dumps({"status": "success", "data": data_for(v, notes, episodes)}, ensure_ascii=False)
                final = conv.send_message(K.tool_response_message("search_memory", payload))
            out = text_of(final)
            # 답의 첫 문장에 정답 값이 있어야 한다 — 뒤에 "다른 값도 있었다"고 덧붙이는 것은 허용
            first = out.split("\n")[0].split(".")[0]
            hit = want in first
            ok += hit
            total += 1
            print(f"[{v}] {name:4} {'OK' if hit else 'X '} {q!r} -> {out[:110]!r}", flush=True)
    print(f"== [{v}] {ok}/{total}  안내 토큰 {len(engine.tokenize(GUIDE))}", flush=True)

"""exp61c — 0.38.7 M5: 되묻기에 불린 add_memory 를 앱이 막고 돌려줄 결과 문구(exp61 — 프롬프트 다섯 판 모두 오호출 2~3/6).

neg 세트(앞 대화에서 말한 값을 "다시 알려줘")에서 모델이 add_memory 를 부르면 그 호출에 아래 결과(status error)를 돌려주고 마지막 답을 본다.
지표: 값을 답함, "기억해 두었/저장했" 거짓 주장 없음, 다시 add_memory 를 부르지 않음.
G1 "저장 요청이 아닙니다 — 이 대화에서 사용자가 이미 말한 값을 그대로 답하세요."
G2 "저장하지 않았습니다. 사용자는 앞에서 말한 내용을 다시 듣고 싶어 합니다. 그 값을 답하고, 저장했다고 말하지 마세요."
"""
import json
import re
import sys

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS_ON = K.fixture("system_instruction.txt")
WIKI = next(l for l in SYS_ON.splitlines() if "search_wikipedia" in l) + "\n"
SYS = SYS_ON.replace(WIKI, "")
TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
REM = K.fixture("turn_reminder.txt").strip()
GUARD = {
    "G1": "저장 요청이 아닙니다. 이 대화에서 사용자가 이미 말한 값을 그대로 답하세요.",
    "G2": "저장하지 않았습니다. 사용자는 앞에서 말한 내용을 다시 듣고 싶어 합니다. 그 값을 답하고, 저장했다고 말하지 마세요.",
}
NEG = [
    ("택배 송장번호 6043 1122 9087이야", "송장번호 6043 1122 9087 확인했어요.", "송장번호 다시 알려줘", "6043"),
    ("내 사물함 번호는 214번이야", "사물함 214번이군요.", "사물함 번호 다시 말해 줘", "214"),
    ("회의는 오후 4시 30분에 시작해", "오후 4시 30분 회의군요.", "회의 몇 시라고 했지? 다시 알려 줘", "4시 30분"),
    ("주차는 지하 2층 C-14에 했어", "지하 2층 C-14군요.", "주차 위치 다시 알려줘", "C-14"),
    ("새 와이파이 비번은 sky7788이야", "sky7788이군요.", "아까 그 비번 다시 말해줘", "sky7788"),
    ("치과 예약 번호는 A-302야", "A-302 번호군요.", "예약 번호 뭐였지? 다시 알려줘", "A-302"),
]
CLAIM = re.compile(r"(기억해 두었|기억해 뒀|저장해 두었|저장했|저장해 드렸|기억해 둘게)")

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


for g, msg in GUARD.items():
    right = claim = again = guarded = 0
    for u0, a0, q, key in NEG:
        with K.create_chat_conversation(engine, history=[{"role": "user", "content": u0}, {"role": "model", "content": a0}], tools=TOOLS, system_message=SYS) as conv:
            res = conv.send_message(f"{REM}\n\n{q}")
            calls = []
            for _ in range(3):
                cs = res.get("tool_calls") or []
                if not cs:
                    break
                name = cs[0]["function"]["name"]
                calls.append(name)
                if name == "add_memory":
                    body = {"status": "error", "message": msg}
                else:
                    body = {"status": "success", "data": "'" + q + "' 로는 일치하는 기억이 없습니다.\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."}
                res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
            out = text_of(res).strip()
        guarded += "add_memory" in calls
        right += key in out
        claim += bool(CLAIM.search(out))
        again += calls.count("add_memory") > 1
        print(f"[{g}] {'막음 ' if 'add_memory' in calls else ''}{'맞음' if key in out else '틀림'}{' 거짓' if CLAIM.search(out) else ''} {','.join(calls)} | {q} → {out.replace(chr(10), ' / ')[:70]}", flush=True)
    print(f"== {g}: 값 {right}/{len(NEG)} · 거짓 주장 {claim} · 다시 부름 {again} (막은 턴 {guarded})", flush=True)

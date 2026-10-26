"""exp60 — 0.38.7 M2: 기억 검색 결과가 '있을 때'의 안내 한 줄 (실기기 T7).

실기기 10-26: "회의실 어디라고 했지?" → search_memory 가 관계없는 지난 대화("…팀 회의 일정…")를 약하게(바이그램 겹침 0.5) 건져 돌려줬고,
결과가 비지 않아 0.38.6 의 "이 대화에서 이미 말한 내용으로 답하세요" 안내(빈 결과 전용)가 붙지 않았다 → "찾지 못했어요" + 앞 문장 인용.

결과 끝 안내: H0 없음(현행) · H1 "이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요." · H2 H1 + "거기에도 없으면 없다고 답하세요."
세트(웹 검색 끈 판, 앱과 같은 결과 형식 — SearchMemoryToolExecutor.formatHits):
  weak     앞 대화에 답 + 결과는 관계없는 지난 대화 1건 → 대화 속 답을 써야
  hit      앞 대화 없음 + 결과에 맞는 메모 → 메모 값으로 답해야(안내가 결과를 무시하게 만들면 안 된다)
  episode  앞 대화(무관) + 결과에 맞는 지난 대화 → 그 값으로
  conflict 메모와 지난 대화가 다른 값(MIXED 안내, exp53 C2 형식) → 메모 값
사용: python exp60_recall_with_hits.py [--v=H0,H1,H2]
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

VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "H0,H1,H2").split(",")
SYS_ON = K.fixture("system_instruction.txt")
WIKI = next(l for l in SYS_ON.splitlines() if "search_wikipedia" in l) + "\n"
SYS = SYS_ON.replace(WIKI, "")
TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
REM = K.fixture("turn_reminder.txt").strip()

TAIL = {
    "H0": "",
    "H1": "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요.",
    "H2": "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요. 거기에도 없으면 없다고 답하세요.",
}
# SearchMemoryToolExecutor.MIXED_SOURCE_GUIDE 원문(exp50).
MIXED = "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."

UNRELATED = "- (과거 대화) 사물함 비밀번호 설정 및 서점 이야기: 사용자가 사물함 비밀번호를 물었고 다음주 월요일 10시 팀 회의 일정을 추가해 달라고 했어요. [사물함, 비밀번호, 회의]"

WEAK = [  # (앞 대화 사용자, 비서, 질문, 정답 낱말)
    ("다음 주 수요일 회의실은 3층 B룸이야", "3층 B룸이요, 알겠어요.", "회의실 어디라고 했지?", "B룸"),
    ("택배 송장번호 6043 1122 9087이야", "송장번호 6043 1122 9087 확인했어요.", "송장번호 다시 알려줘", "6043"),
    ("점심은 12시 반에 먹기로 했어", "12시 반 점심이군요.", "점심 몇 시라고 했지?", "12시 반"),
    ("엄마 생신 선물로 스카프 사기로 했어", "스카프 좋은 선택이에요.", "엄마 선물 뭐로 하기로 했더라?", "스카프"),
    ("내일 만날 친구 이름은 지훈이야", "지훈 님이군요.", "내일 만나는 친구 이름이 뭐였지?", "지훈"),
    ("주차는 지하 2층 C-14에 했어", "지하 2층 C-14, 기억해 둘게요.", "나 어디 주차했다고 했지?", "C-14"),
]
HIT = [  # (질문, 결과 줄, 정답)
    ("내 자전거 비밀번호 뭐였지?", "- 자전거 자물쇠 비밀번호는 4821 [비밀번호, 자전거]", "4821"),
    ("회사 주차 자리 어디라고 했지?", "- 회사 주차는 B3 구역 117번 [주차, 회사]", "117"),
    ("내가 알려준 와이파이 비번 뭐야?", "- 집 와이파이 비밀번호는 kosmos2024 [와이파이, 비밀번호]", "kosmos2024"),
    ("엄마 생신 언제라고 했지?", "- 엄마 생신은 11월 7일 [가족, 생일]", "11월 7일"),
    ("내가 좋아한다고 한 커피 뭐였지?", "- 아메리카노보다 라떼를 좋아함 [선호도]", "라떼"),
    ("치과 이름 뭐였더라?", "- 치과는 강남역 근처 미소치과 [치과]", "미소치과"),
]
EPISODE = [  # (앞 대화(무관) 사용자, 비서, 질문, 결과 줄, 정답)
    ("오늘 날씨 좋다", "정말 좋은 날씨예요.", "지난번에 기타 레슨 몇 시로 바뀌었다고 했지?",
     "- (과거 대화) 기타 레슨 시간 변경: 사용자가 수요일 기타 레슨이 저녁 8시로 바뀌었다고 알려 주었어요. [기타, 레슨]", "8시"),
    ("배고프다", "뭐라도 드세요.", "전에 러닝 누구랑 한다고 했더라?",
     "- (과거 대화) 주말 러닝 계획: 사용자가 토요일 아침 한강에서 지훈이와 5km 뛰기로 했어요. [러닝, 한강]", "지훈"),
    ("고마워", "천만에요.", "여동생 생일 언제라고 했지?",
     "- (과거 대화) 가족 생일 이야기: 사용자가 여동생 생일이 5월 3일이라고 알려 주었어요. [생일, 여동생]", "5월 3일"),
]
CONFLICT = [  # (질문, 메모 줄, 과거 대화 줄, 메모 값)
    ("자전거 비밀번호 뭐였지?", "- 자전거 비밀번호는 134 [자전거, 비밀번호]",
     "- (과거 대화) 자전거 비밀번호 변경: 사용자가 비밀번호를 4321로 바꿨다고 했어요. [자전거, 비밀번호]", "134"),
    ("와이파이 비번 뭐야?", "- 와이파이 비밀번호는 sky7788 [와이파이]",
     "- (과거 대화) 와이파이 이야기: 사용자가 와이파이 비밀번호가 sky1234라고 했어요. [와이파이]", "sky7788"),
    ("회사 주차 몇 번이라고 했지?", "- 회사 주차는 B3 구역 117번 [주차]",
     "- (과거 대화) 주차 위치: 사용자가 회사 주차를 B2 구역 52번에 했다고 했어요. [주차]", "117"),
    ("치과 예약 몇 시였지?", "- 치과 예약은 내일 오후 3시 [치과]",
     "- (과거 대화) 치과 예약 변경: 사용자가 치과 예약을 오후 4시로 옮겼다고 했어요. [치과]", "3시"),
]

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
for v, t in TAIL.items():
    print(f"## 토큰 {v} 꼬리 +{len(engine.tokenize(t)) if t else 0}", flush=True)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def ask(history, question, body):
    """질문 → (search_memory 면 body 를 결과로) → 마지막 답, 부른 툴들."""
    with K.create_chat_conversation(engine, history=history, tools=TOOLS, system_message=SYS) as conv:
        res = conv.send_message(f"{REM}\n\n{question}")
        tools = []
        for _ in range(3):
            cs = res.get("tool_calls") or []
            if not cs:
                break
            name = cs[0]["function"]["name"]
            tools.append(name)
            out = {"status": "success", "data": body} if name == "search_memory" else {"status": "success", "message": "완료"}
            res = conv.send_message(K.tool_response_message(name, json.dumps(out, ensure_ascii=False)))
        return text_of(res).strip(), tools


# 실기기 T7 조건 — 앞 턴에서 그 사실의 메모 저장을 거절했다(앱은 고정 문장으로 답하고 대화를 DB 히스토리로 다시 만든다).
REJECTED = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--rej=")), "메모를 저장하지 않았어요.")
PART = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--part=")), "all")

summary = {}
for v in VSEL:
    if PART == "rejected":
        for body_name, body in (("결과 약함", UNRELATED), ("결과 없음", "'회의실' 로는 일치하는 기억이 없습니다.")):
            ok = 0
            for u0, _a0, q, key in WEAK:
                out, tools = ask([{"role": "user", "content": u0}, {"role": "model", "content": REJECTED}], q, body + TAIL[v])
                ok += key in out
                print(f"[rejected {v} {body_name}] {'맞음' if key in out else '틀림'} {','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
            print(f"== rejected {v} {body_name}: 대화 속 답 {ok}/{len(WEAK)}", flush=True)
        continue
    tail = TAIL[v]
    row = {}
    ok = 0
    for u0, a0, q, key in WEAK:
        out, tools = ask([{"role": "user", "content": u0}, {"role": "model", "content": a0}], q, UNRELATED + tail)
        ok += key in out
        print(f"[weak {v}] {'맞음' if key in out else '틀림'} {','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    row["weak 대화 속 답"] = f"{ok}/{len(WEAK)}"
    ok = 0
    for q, line, key in HIT:
        out, tools = ask(None, q, line + tail)
        ok += key in out
        print(f"[hit {v}] {'맞음' if key in out else '틀림'} {','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    row["hit 결과로 답"] = f"{ok}/{len(HIT)}"
    ok = 0
    for u0, a0, q, line, key in EPISODE:
        out, tools = ask([{"role": "user", "content": u0}, {"role": "model", "content": a0}], q, line + tail)
        ok += key in out
        print(f"[episode {v}] {'맞음' if key in out else '틀림'} {','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    row["episode 결과로 답"] = f"{ok}/{len(EPISODE)}"
    ok = 0
    for q, memo, ep, key in CONFLICT:
        out, tools = ask(None, q, f"{MIXED}\n{memo}\n{ep}{tail}")
        ok += key in out
        print(f"[conflict {v}] {'맞음' if key in out else '틀림'} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    row["conflict 메모 값"] = f"{ok}/{len(CONFLICT)}"
    summary[v] = row
    print(f"== {v}: {row}", flush=True)

print("\n## 요약")
for v, row in summary.items():
    print(f"{v}: " + " | ".join(f"{k} {x}" for k, x in row.items()), flush=True)

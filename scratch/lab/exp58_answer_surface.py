"""exp58 — 0.38.6 M4 모델 답 세 문제 실측 (계획서 docs/plans/0.38.6-fixes.md M4).

실기기(10-23, 웹 검색 꺼짐): ① 툴로 할 수 없는 요청을 "기능이 없어요"로 거절(점심 추천) ② 저장 요청이 아닌 말을 add_memory 로
③ 대화에 답이 있는데 search_memory. exp56 은 웹 검색을 켠 채(`K.ALL_TOOLS`) 재서 ①②를 재현하지 못했다 — 여기는 **웹 검색 끈 판**
(앱과 같이 search_wikipedia 선언·트리거 줄 없음)이 기본이다.

세트: casual(툴 없이 답해야) · memneg(add_memory 를 부르면 안 됨) · mempos(불러야) · hist(앞 대화에 답 — search_memory 없이 맞혀야)
· recall(앞 대화 없음 — search_memory 를 불러야) · smoke(exp51b 툴 선택 12, 위키 1건은 웹 꺼짐이라 제외).
변형: base · ALLOW(시스템 지시 허용 한 줄) · REM(턴 리마인더 끝에 허용 구절) · MEM(add_memory 트리거를 '자기 자신'으로 좁힘)
· HIST(search_memory 트리거에 '대화에 있으면 바로 답') · 조합.
사용: python exp58_answer_surface.py [--v=base,ALLOW,...] [--part=casual,memneg,...]
"""
import json
import re
import sys

import litert_lm as llm
import kosmos_lab as K
import exp51b_smoke as S

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base,ALLOW,REM,MEM,HIST").split(",")
PARTS = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--part=")), "casual,memneg,mempos,hist,recall,smoke").split(",")

SYS_ON = K.fixture("system_instruction.txt")
WIKI_LINE = next(l for l in SYS_ON.splitlines() if "search_wikipedia" in l) + "\n"
SYS = SYS_ON.replace(WIKI_LINE, "")  # 앱: 웹 검색을 끄면 트리거 줄이 빠진다(PromptAssembler)
TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
REMINDER = K.fixture("turn_reminder.txt").strip()

MUST = "promising without calling is a failure.\n"
MEM_OLD = "or shares a fact/preference/password to keep:"
HIST_OLD = "This is a LOOKUP, never call `add_memory` for it."
REM_OLD = "do not answer from memory or merely promise."
for s in (MUST, MEM_OLD, HIST_OLD):
    assert s in SYS, f"시스템 지시가 바뀌었다 — {s[:30]}"
assert REM_OLD in REMINDER

EDITS = {
    "ALLOW": ("sys", MUST, MUST + "If no tool applies, answer yourself as a helpful assistant — never say you lack a feature.\n"),
    "MEM": ("sys", MEM_OLD, "or shares a fact/preference/password about themselves to keep:"),
    "HIST": ("sys", HIST_OLD, HIST_OLD + " If the answer is already in this conversation, answer from it."),
    "REM": ("rem", REM_OLD, REM_OLD + " If none applies, just answer."),
}


def build(variant):
    sysm, rem = SYS, REMINDER
    for key in variant.split("+"):
        if key == "base":
            continue
        where, old, new = EDITS[key]
        if where == "sys":
            sysm = sysm.replace(old, new)
        else:
            rem = rem.replace(old, new)
    return sysm, rem


engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)

base_sys, base_rem = len(engine.tokenize(SYS)), len(engine.tokenize(REMINDER))
for v in VSEL:
    s, r = build(v)
    print(f"## 토큰 {v}: 시스템 +{len(engine.tokenize(s)) - base_sys}, 리마인더 +{len(engine.tokenize(r)) - base_rem}", flush=True)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    return [(c["function"]["name"], c["function"].get("arguments", {}) or {}) for c in (res.get("tool_calls") or [])]


def tool_result(name, a):
    """앱 실행기 문구(meta 를 뗀 뒤) 근사 — 성공으로 돌려준다."""
    if name == "add_memory":
        return {"status": "success", "message": "기억에 저장했어요."}
    if name == "search_memory":
        return {"status": "success", "data": "'" + str(a.get("keyword", "")) + "' 로는 일치하는 기억이 없습니다."}
    if name == "get_schedule":
        return {"status": "success", "data": "등록된 일정이 없어요."}
    if name == "list_reminders":
        return {"status": "success", "data": "등록된 알림이 없어요."}
    if name in ("add_schedule", "add_reminder"):
        return {"status": "success", "message": "등록했어요."}
    return {"status": "error", "message": f"알 수 없는 Tool입니다: {name}"}


def run_turn(conv, rem, user_text, rounds=3):
    res = conv.send_message(f"{rem}\n\n{user_text}")
    tools = []
    for _ in range(rounds):
        cs = calls_of(res)
        if not cs:
            break
        name, a = cs[0]
        tools.append(name)
        res = conv.send_message(K.tool_response_message(name, json.dumps(tool_result(name, a), ensure_ascii=False)))
    return text_of(res).strip(), tools


CASUAL = [
    "점심 메뉴 추천해 줄래?", "비 오는 날 듣기 좋은 노래 추천해줘", "회의 때 긴장 안 하는 방법 알려줘", "주말에 뭐 하면 좋을까?",
    "영어로 '감사합니다' 어떻게 말해?", "간단한 저녁 메뉴 뭐가 좋을까", "아침 운동 루틴 짜줘", "김치찌개 끓이는 법 알려줘",
    "커피 하루에 몇 잔이 적당해?", "오늘 좀 피곤하다", "말포이 TS 유행 알아?", "친구 생일 선물 뭐가 좋을까?",
]
MEMNEG = [
    "말포이 TS 유행에 대해 질문 좀 할게", "친구가 다음 주에 이사한대", "요즘 날씨 너무 덥다", "어제 본 영화 진짜 재밌었어",
    "회사 동료가 커피를 엄청 좋아하더라", "오늘 점심은 김밥 먹었어", "뉴스에서 금리 오른대", "동생이 시험 잘 봤대",
]
MEMPOS = [
    ("나 땅콩 알레르기 있어, 잊지 마", "땅콩"), ("내 자전거 비밀번호 4821이야 기억해줘", "4821"),
    ("나는 아메리카노보다 라떼를 좋아해, 기억해 둬", "라떼"), ("우리 집 와이파이 비번은 kosmos2024야 저장해줘", "kosmos2024"),
    ("내 생일은 3월 5일이야", "3월 5일"), ("나 매주 화요일에 수영 다녀", "수영"),
]
HIST = [
    ("다음 주 수요일 회의실은 3층 B룸이야", "3층 B룸이요, 알겠어요.", "회의실 어디라고 했지?", "B룸"),
    ("택배 송장번호 6043 1122 9087이야", "송장번호 6043 1122 9087 확인했어요.", "송장번호 다시 알려줘", "6043"),
    ("점심은 12시 반에 먹기로 했어", "12시 반 점심이군요.", "점심 몇 시라고 했지?", "12시 반"),
    ("엄마 생신 선물로 스카프 사기로 했어", "스카프 좋은 선택이에요.", "엄마 선물 뭐로 하기로 했더라?", "스카프"),
    ("내일 만날 친구 이름은 지훈이야", "지훈 님이군요.", "내일 만나는 친구 이름이 뭐였지?", "지훈"),
    ("주차는 지하 2층 C-14에 했어", "지하 2층 C-14, 기억해 둘게요.", "나 어디 주차했다고 했지?", "C-14"),
]
RECALL = ["내 자전거 비밀번호 뭐였지?", "회사 주차 자리 어디라고 했지?", "내가 알려준 와이파이 비번 뭐야?", "저장한 엄마 생신 날짜 기억나?",
          "전에 말한 치과 이름 뭐였더라?", "내가 좋아한다고 한 커피 뭐였지?"]
REFUSE = re.compile(r"(기능이 없|기능은 없|기능을 제공|도와드릴 수 없|도와드리기 어려|해 드릴 수 없|할 수 없어|할 수 없습|드리기 어려|어렵습니다|지원하지|제공하지 않|불가능)")

summary = {}
for v in VSEL:
    sysm, rem = build(v)
    row = {}
    if "casual" in PARTS:
        refused, tooled = 0, 0
        for u in CASUAL:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                out, tools = run_turn(conv, rem, u)
            bad = bool(REFUSE.search(out))
            refused += bad
            tooled += bool(tools)
            print(f"[casual {v}] {'거절 ' if bad else ''}{'툴 ' + ','.join(tools) + ' ' if tools else ''}| {u} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
        row["casual 거절"] = f"{refused}/{len(CASUAL)}"
        row["casual 툴"] = f"{tooled}/{len(CASUAL)}"
    if "memneg" in PARTS:
        wrong = 0
        for u in MEMNEG:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                out, tools = run_turn(conv, rem, u)
            wrong += "add_memory" in tools
            print(f"[memneg {v}] {'add_memory ' if 'add_memory' in tools else ''}{','.join(tools)} | {u} → {out.replace(chr(10), ' / ')[:60]}", flush=True)
        row["memneg 오호출"] = f"{wrong}/{len(MEMNEG)}"
    if "mempos" in PARTS:
        ok = 0
        for u, key in MEMPOS:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                res = conv.send_message(f"{rem}\n\n{u}")
            cs = calls_of(res)
            hit = bool(cs) and cs[0][0] == "add_memory" and key in str(cs[0][1].get("content", ""))
            ok += hit
            print(f"[mempos {v}] {'OK' if hit else 'MISS'} {cs[:1]} | {u}", flush=True)
        row["mempos 저장"] = f"{ok}/{len(MEMPOS)}"
    if "hist" in PARTS:
        called, right = 0, 0
        for u0, a0, q, key in HIST:
            hist = [{"role": "user", "content": u0}, {"role": "model", "content": a0}]
            with K.create_chat_conversation(engine, history=hist, tools=TOOLS, system_message=sysm) as conv:
                out, tools = run_turn(conv, rem, q)
            called += "search_memory" in tools
            right += key in out
            print(f"[hist {v}] {'검색 ' if 'search_memory' in tools else ''}{'맞음' if key in out else '틀림'} | {q} → {out.replace(chr(10), ' / ')[:60]}", flush=True)
        row["hist 검색"] = f"{called}/{len(HIST)}"
        row["hist 맞힘"] = f"{right}/{len(HIST)}"
    if "recall" in PARTS:
        called = 0
        for u in RECALL:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                res = conv.send_message(f"{rem}\n\n{u}")
            cs = calls_of(res)
            called += bool(cs) and cs[0][0] == "search_memory"
            print(f"[recall {v}] {cs[:1]} | {u}", flush=True)
        row["recall 호출"] = f"{called}/{len(RECALL)}"
    if "smoke" in PARTS:
        cases = [c for c in S.CASES if "search_wikipedia" not in str(c[0]) and c[0] != "위키"]
        ok = 0
        for tag, u, check in cases:
            with K.create_chat_conversation(engine, tools=TOOLS, system_message=sysm) as conv:
                res = conv.send_message(f"{rem}\n\n{u}")
            good = check(S.first_call(res))
            ok += good
            if not good:
                print(f"[smoke {v}] 실패 {tag}: {S.first_call(res)}", flush=True)
        row["smoke"] = f"{ok}/{len(cases)}"
    summary[v] = row
    print(f"== {v}: {row}", flush=True)

print("\n## 요약")
for v, row in summary.items():
    print(f"{v:>20}: " + " | ".join(f"{k} {x}" for k, x in row.items()), flush=True)

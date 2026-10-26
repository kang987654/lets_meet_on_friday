"""exp62 — 0.38.8 M0 재현 세트·기준값 (계획서 docs/plans/0.38.8-trust-order.md). 이후 M1~M4 가 이 세트·지표로 후보를 잰다.

세트(웹 검색 끈 판, 앱 픽스처 그대로):
  A drift   엉뚱한·거절하는 앞 비서 답이 있는 기록 → 일상 요청·사실 말하기 (실기기 "택배 송장번호 …이야" → "그런 기능은 없어요")
  B fact    저장 낱말 없이 사실만 말하기 → 거짓 약속("기억해 둘게요" 인데 저장 호출 없음)·저장 호출·저장 물어보기
  C newval  방금 새 값을 말함 + 메모 거절 고정 문장 → 되묻기, 검색 결과에는 옛 값(지난 대화 / 메모) — 방금 값을 답해야(결정 2)
  D summary 거절 고정 문장이 든 대화를 요약 → 사용자 거절을 "못했어요"(실패처럼)로 적는지
변형은 M1~M4 가 SYS·REM·결과 문구·요약 지시를 바꿔 넘긴다(run(...) 인자). 이 파일을 그대로 돌리면 현행 기준값.
사용: python exp62_trust_order.py [A] [B] [C] [D]
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
SUMMARY_SYS = K.fixture("episode_summary_system.txt")
# 앱 문구(BaseAgent.rejectionReply, SearchMemoryToolExecutor.HIT_TAIL·MIXED_SOURCE_GUIDE).
MEMO_REJECTED = "메모에는 남기지 않았어요."
SCHEDULE_REJECTED = "일정을 추가하지 않았어요."
HIT_TAIL = "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."
MIXED = "직접 저장한 메모는 사용자가 확인해 남긴 기록이고 (과거 대화)는 요약입니다. 둘이 다르면 메모의 값을 답하고, 과거 대화에 다른 값이 있었다고 덧붙이세요."

REFUSE = re.compile(r"(기능이 없|기능은 없|기능을 가지고 있지|드릴 수 없|드릴 수는 없|도움을 드리기 어려|관리하는 기능|지원하지|제공하지 않)")
PROMISE = re.compile(r"(기억해 둘게|기억해 드릴게|기억할게|기억해 두겠|저장해 둘게|저장해 드릴게|기억하고 있을게|메모해 둘게|기억해 두었|저장해 두었|저장했)")
ASK_SAVE = re.compile(r"(메모해 둘까|저장해 둘까|기억해 둘까|저장할까|메모할까|기억해 드릴까|저장해 드릴까|메모해 드릴까)")
FAILED = re.compile(r"(못했|실패|문제가 생|오류)")

M = lambda role, content: {"role": role, "content": content}  # noqa: E731

# A — 실기기 기록 모양: 알림 조회 답 + 엉뚱한 되물음 / 앞에서 한 거절.
DRIFT_HIST = {
    "odd": [M("user", "알림 뭐 있어?"), M("model", "현재 등록된 알림은 없어요. 😊"), M("user", "음"),
            M("model", "어떤 것에 대해 말씀하시는 건가요? 제가 도와드릴 수 있는 부분이 있다면 말씀해 주세요. 😊")],
    "refused": [M("user", "아침 운동 루틴 짜줘"), M("model", "죄송해요, 저는 운동 루틴을 짜드리는 기능은 없어요. 대신 다른 도움이 필요하시면 말씀해 주세요!")],
}
DRIFT_ASK = ["택배 송장번호 6043 1122 9087이야", "점심 메뉴 추천해 줄래?", "김치찌개 끓이는 법 알려줘", "내 사물함 번호는 214번이야",
             "주말에 뭐 하면 좋을까?", "회사 동료 생일 선물 뭐가 좋을까?"]

FACTS = ["택배 송장번호 6043 1122 9087이야", "내 사물함 번호는 214번이야", "주차는 지하 2층 C-14에 했어", "새 와이파이 비번은 sky7788이야",
         "치과 예약 번호는 A-302야", "나 다음 주부터 판교로 출근해", "우리 강아지 이름은 콩이야", "회의는 오후 4시 30분에 시작해"]

# C — (방금 말한 사용자 문장, 질문, 방금 값, 검색 결과에 담길 옛 값 줄, 옛 값)
NEWVAL = [
    ("다음 주 금요일 회의실은 5층 C룸이야", "회의실 어디라고 했지?", "5층 C룸",
     "- (과거 대화) 회의실 위치 및 치과 예약 요청: 사용자가 다음 주 수요일 회의실이 3층 B룸이라고 말했어요. [회의실, 3층 B룸]", "3층 B룸"),
    ("주차는 오늘 지하 3층 D-07에 했어", "나 어디 주차했다고 했지?", "D-07",
     "- (과거 대화) 주차 위치 이야기: 사용자가 지하 2층 C-14에 주차했다고 했어요. [주차]", "C-14"),
    ("새 와이파이 비번은 sky9900으로 바꿨어", "와이파이 비번 뭐였지?", "sky9900",
     "- 집 와이파이 비밀번호는 sky7788 [와이파이, 비밀번호]", "sky7788"),
    ("치과 예약을 오후 4시로 옮겼어", "치과 예약 몇 시라고 했지?", "4시",
     "- 치과 예약은 내일 오후 3시 [치과]", "3시"),
]

# D — 요약 입력(사용자:/비서: 전사, EpisodeTranscript 형식)
SUMMARY_CASES = [
    "사용자: 내 자전거 비밀번호 4821이야 기억해줘\n비서: " + MEMO_REJECTED + "\n사용자: 오늘 날씨 좋다\n비서: 정말 좋은 날씨예요.",
    "사용자: 내일 오후 3시에 치과 예약 잡아줘\n비서: " + SCHEDULE_REJECTED + "\n사용자: 그럼 모레 오후 2시로 해 줘\n비서: " + SCHEDULE_REJECTED,
    "사용자: 다음 주 수요일 회의실은 3층 B룸이야\n비서: " + MEMO_REJECTED + "\n사용자: 회의실 어디라고 했지?\n비서: 회의실은 3층 B룸이라고 하셨어요.",
    "사용자: 오늘 오후 6시에 약 먹으라고 알려줘\n비서: 알림을 등록하지 않았어요.\n사용자: 아니 그냥 됐어\n비서: 알겠어요.",
]

_engine = None


def engine():
    global _engine
    if _engine is None:
        _engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                             max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    return _engine


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def turn(history, question, sysm, rem, search_body=None):
    """한 턴 — search_memory 는 search_body(없으면 빈 결과 앱 문구), add_memory 는 성공으로 돌려준다(승인 가정 — 오호출은 tools 로 센다)."""
    with K.create_chat_conversation(engine(), history=history, tools=TOOLS, system_message=sysm) as conv:
        res = conv.send_message(f"{rem}\n\n{question}")
        tools = []
        for _ in range(3):
            cs = res.get("tool_calls") or []
            if not cs:
                break
            name = cs[0]["function"]["name"]
            tools.append(name)
            if name == "search_memory":
                body = {"status": "success", "data": search_body if search_body is not None else
                        "저장된 기억이 하나도 없습니다. 사용자가 이 대화에서 이미 말한 내용이 있으면 그것으로 답하고, 없으면 저장된 것이 없다고 답하세요. 추측하지 마세요."}
            elif name == "add_memory":
                body = {"status": "success", "message": "기억에 저장했어요."}
            else:
                body = {"status": "success", "message": "완료"}
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)))
        return text_of(res).strip(), tools


def run_A(sysm=SYS, rem=REM, tag="base"):
    refused = promised = 0
    n = 0
    for hname, hist in DRIFT_HIST.items():
        for q in DRIFT_ASK:
            out, tools = turn(hist, q, sysm, rem)
            r = bool(REFUSE.search(out))
            p = bool(PROMISE.search(out)) and "add_memory" not in tools
            refused += r
            promised += p
            n += 1
            print(f"[A {tag} {hname}] {'거절 ' if r else ''}{'거짓약속 ' if p else ''}{','.join(tools)} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    return {"A 거절": f"{refused}/{n}", "A 거짓 약속": f"{promised}/{n}"}


def run_B(sysm=SYS, rem=REM, tag="base"):
    promised = saved = asked = 0
    for q in FACTS:
        out, tools = turn(None, q, sysm, rem)
        p = bool(PROMISE.search(out)) and "add_memory" not in tools
        promised += p
        saved += "add_memory" in tools
        asked += bool(ASK_SAVE.search(out))
        print(f"[B {tag}] {'거짓약속 ' if p else ''}{'저장호출 ' if 'add_memory' in tools else ''}{'물어봄 ' if ASK_SAVE.search(out) else ''}| {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    n = len(FACTS)
    return {"B 거짓 약속": f"{promised}/{n}", "B 저장 호출": f"{saved}/{n}", "B 물어봄": f"{asked}/{n}"}


def run_C(sysm=SYS, rem=REM, tail=HIT_TAIL, mixed=MIXED, tag="base"):
    new = old = 0
    for said, q, key, line, old_key in NEWVAL:
        # 앱은 결과가 한 출처뿐이면 MIXED 안내를 붙이지 않는다 — 옛 값 한 줄 + 결과 끝 안내(mixed 는 M2 후보가 쓸 자리).
        body = line + tail
        out, _ = turn([M("user", said), M("model", MEMO_REJECTED)], q, sysm, rem, search_body=body)
        new += key in out
        old += old_key in out and key not in out
        print(f"[C {tag}] {'새 값' if key in out else ''}{'옛 값' if old_key in out else ''} | {q} → {out.replace(chr(10), ' / ')[:80]}", flush=True)
    n = len(NEWVAL)
    return {"C 방금 값": f"{new}/{n}", "C 옛 값만": f"{old}/{n}"}


def run_D(summary_sys=SUMMARY_SYS, transcript=lambda t: t, tag="base"):
    failed = 0
    for t in SUMMARY_CASES:
        with K.create_chat_conversation(engine(), tools=[], system_message=summary_sys) as conv:
            out = text_of(conv.send_message("다음 대화를 요약하세요.\n\n" + transcript(t))).strip()
        summ = (re.search(r"요약\s*[:：]\s*(.+)", out, re.S) or [None, out])[1]
        f = bool(FAILED.search(summ))
        failed += f
        print(f"[D {tag}] {'실패처럼 ' if f else ''}| {summ.replace(chr(10), ' ')[:110]}", flush=True)
    return {"D 실패처럼": f"{failed}/{len(SUMMARY_CASES)}"}


if __name__ == "__main__":
    parts = [a for a in sys.argv[1:] if not a.startswith("--")] or ["A", "B", "C", "D"]
    row = {}
    for p in parts:
        row.update({"A": run_A, "B": run_B, "C": run_C, "D": run_D}[p]())
    print("== 기준값(현행): " + " | ".join(f"{k} {v}" for k, v in row.items()), flush=True)

"""exp54 — 0.38.5 말투 실측 (계획서 docs/plans/0.38.5-voice.md M2~M4).

지시를 넣었을 때 출력 말투가 실제로 얼마나 바뀌는지 같은 입력으로 잰다(사용자: "통제의 효과가 어느 정도일지는 봐야").
지표: 해요체·합니다체·기타 문장 비율, 느낌표·이모지·마크다운, 길이. 브리핑은 질문 수(정확히 1개)와 재료 시각 보존도 함께.

part brief  — 아침 브리핑 8세트(일정·할 일·기억·기기 캘린더 실패 조합)
part review — 주간 회고 5세트
part chat   — 채팅: 일상 대화 10 + 툴 성공 확인 3(알림·메모·일정)

변형은 VARIANTS 에 지시 문자열로 넣는다. 기준(base)은 앱이 내보낸 픽스처 그대로(PromptFixtureExportTest).
사용: python exp54_voice.py [part...] [--v base,cand1]   (기본: 전부, base)
엔진: GPU · FP32 · 0.18.0 — 앱 실기기 설정과 같게(exp53 전례).
"""
import re
import time
import sys

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

args = [a for a in sys.argv[1:] if not a.startswith("--")]
PARTS = args or ["brief", "review", "chat"]
VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base").split(",")

BRIEF_SYS = K.fixture("briefing_system.txt")
REVIEW_SYS = K.fixture("review_system.txt")
CHAT_SYS = K.fixture("system_instruction.txt")
REMINDER = K.fixture("turn_reminder.txt").strip()

# 픽스처의 재료 형식과 이 스크립트가 만드는 형식이 같은지 먼저 본다 — 어긋나면 앱과 다른 것을 잰다.
_bs = K.fixture("briefing_user_sample.txt")
assert _bs.startswith("오늘은 10월 21일 수요일입니다. 아래 재료로 아침 브리핑을 작성하세요.\n\n[오늘 일정]\n- 없음\n\n[미완료 할 일]\n- 없음\n\n[최근 대화 기억]\n- (10월 20일) 치과 상담:"), _bs
_rs = K.fixture("review_user_sample.txt")
assert _rs.startswith("지난 7일 대화 요약:\n- 10월 20일 · 치과 상담:"), _rs

# 변형: part 별 시스템 지시(없으면 기준). M3·M4 에서 후보를 여기에 더한다.
VARIANTS = {
    "base": {"brief": BRIEF_SYS, "review": REVIEW_SYS, "chat": CHAT_SYS},
}

# ---- M3 후보: 브리핑(인사·해요체·캘린더 규칙) / 주간 회고(해요체·2인칭)
_GREET_OLD = "- 밝은 인사 한 줄로 시작하세요."
_CAL_OLD = "- 재료에 기기 캘린더를 읽지 못했다는 주의가 있으면 그 사실을 한 줄로 알려주세요.\n"
# [WHY] 앱이 c2·c4·c5 를 반영한 뒤에는 픽스처가 새 판이다 — 옛 지시에서 후보를 만드는 부분은 옛 픽스처일 때만(결과는 CHANGELOG 0.38.5).
APP_HAS_NEW = _GREET_OLD not in BRIEF_SYS
_GREET_NEW = ("- \"좋은 아침이에요.\"처럼 짧은 인사 한마디로 시작하세요. 느낌표나 \"활기찬\" 같은 꾸밈말은 쓰지 마세요.\n"
              "- 모든 문장을 해요체(…해요, …있어요)로 쓰세요. 합니다체(…합니다)는 쓰지 마세요.")
_CAL_NEW = "- 재료에 \"(주의: 기기 캘린더를 읽지 못했습니다\" 줄이 있을 때만 그 사실을 한 줄로 알려주세요. 그 줄이 없으면 캘린더 이야기를 하지 마세요.\n"
B1 = BRIEF_SYS.replace(_GREET_OLD, _GREET_NEW).replace(_CAL_OLD, _CAL_NEW)
# B2: 캘린더 규칙을 지시에서 빼고 재료의 주의 줄("브리핑에서 이 사실을 알려야 합니다")에만 맡긴다.
B2 = BRIEF_SYS.replace(_GREET_OLD, _GREET_NEW).replace(_CAL_OLD, "")
_R_ADD = "\n사용자에게 말하듯 해요체로 쓴다(…하셨어요, …있어요). \"사용자는\"처럼 3인칭으로 쓰지 않는다."
R1 = REVIEW_SYS + _R_ADD
R2 = REVIEW_SYS + _R_ADD + "\n예: \"이번 주는 ○○에 시간을 많이 쓰셨어요.\""
if APP_HAS_NEW:
    B1 = B2 = BRIEF_SYS
VARIANTS["c1"] = {"brief": B1, "review": R1, "chat": CHAT_SYS}
VARIANTS["c2"] = {"brief": B2, "review": R2, "chat": CHAT_SYS}

# ---- c3: 줄인 판 — [System]·영어 줄 빼고, 해요체 명령으로 통일, 새 금지문 없이 긍정문만. 회고는 재료에서 "사용자는/가" 를 코드로 뗀다.
B3 = """당신은 사용자의 하루를 여는 아침 브리핑을 쓰는 개인 비서예요. 아래 규칙을 지키세요.
- "좋은 아침이에요."처럼 짧은 인사 한마디로 시작하세요.
- 모든 문장을 해요체(…해요, …있어요)로 쓰세요.
- 오늘 일정과 미완료 할 일을 간결히 정리하세요. 시각·이름·숫자는 재료의 원문 그대로 보존하세요.
- 마지막에 [최근 대화 기억]을 근거로 자연스러운 후속 질문을 **정확히 1개** 하세요.
  (예: "지난번에 말씀하신 ○○은 잘 돼가요?" — 기억 앞의 날짜를 보고 "어제"·"지난주"를 맞게 쓰세요) 기억이 없으면 오늘 계획을 묻는 가벼운 질문 1개로 대체하세요.
- 전체 5~8문장, 과장 없이. 목록이 비어 있으면 억지로 채우지 마세요."""
R3 = """아래는 지난 7일 동안 나눈 대화의 요약이에요. 사용자에게 이번 주를 3~5문장의 해요체로 회고해 주세요.
무엇에 시간을 썼는지와 다가오는 일을 짚어 주세요.
요약에 없는 사실·숫자·이름은 절대 지어내지 마세요. 목록이나 제목 없이 문단 하나로 쓰세요."""
# ---- c4: 형식 통일(브리핑 c2 형식 — [System] + 영어 역할 + 한국어 역할 + "~하세요" 목록). 회고는 c1 내용 그대로 옮김.
R4 = """[System]
You are a personal assistant writing a short weekly review in Korean.
당신은 지난 7일 동안 나눈 대화의 요약으로 이번 주를 돌아보는 개인 비서입니다. 아래 규칙을 지키세요.
- 이번 주를 3~5문장으로 회고하세요. 무엇에 시간을 썼는지와 다가오는 일을 짚으세요.
- 사용자에게 말하듯 해요체로 쓰세요(…하셨어요, …있어요). "사용자는"처럼 3인칭으로 쓰지 마세요.
- 요약에 없는 사실·숫자·이름은 절대 지어내지 마세요.
- 목록이나 제목 없이 문단 하나로 쓰세요."""
SCHED_SYS = K.fixture("schedule_system.txt")
_SCHED_ASK = " 친절한 비서처럼 간결하게 1~2문장으로 요약해주세요."
assert APP_HAS_NEW or _SCHED_ASK in K.fixture("schedule_user_sample.txt")
S4 = """[System]
You are a personal assistant summarizing the user's schedule in Korean.
당신은 사용자의 일정을 요약하는 개인 비서입니다. 아래 규칙을 지키세요.
- 일정을 1~2문장의 해요체로 간결히 요약하세요.
- 시각·이름은 일정의 원문 그대로 보존하세요."""
VARIANTS["base"]["sched"] = SCHED_SYS
VARIANTS["c4"] = {"brief": B2, "review": R4, "chat": CHAT_SYS, "sched": S4,
                  "sched_input": lambda body: body.replace(_SCHED_ASK, "")}

# c5: c4 + 재료의 ISO 시각을 앱 표기(IsoDateTimeParser.toDisplayKorean — "10월 26일 오전 9:30", 날짜만이면 "종일")로. ISO 를 모델 관측값에 두지 않는다(AGENTS §4-7).
def _korean(iso):
    d = iso[:10]
    md = f"{int(d[5:7])}월 {int(d[8:10])}일"
    if len(iso) == 10:
        return f"{md} 종일"
    h, mi = int(iso[11:13]), int(iso[14:16])
    return f"{md} {'오후' if h >= 12 else '오전'} {h % 12 or 12}:{mi:02d}"


VARIANTS["c5"] = dict(VARIANTS["c4"], sched_input=lambda body: re.sub(
    r"\(시간: ([0-9T:-]+)\)", lambda m: f"(시간: {_korean(m.group(1))})", body.replace(_SCHED_ASK, "")))

# ---- 요소 가르기(ablation): 공통 형식에서 한 줄씩 뺀다. 0=[System] 1=영어 역할 2=한국어 역할("…아래 규칙을 지키세요.")
def _drop(text, idxs):
    lines = text.split("\n")
    return "\n".join(l for i, l in enumerate(lines) if i not in idxs)


assert B2.split("\n")[0] == "[System]" and R4.split("\n")[0] == "[System]"
assert not APP_HAS_NEW or (REVIEW_SYS.strip() == R4.strip() and BRIEF_SYS.strip() == B2.strip()), "앱 지시가 c2·c4 와 다르다"
for _name, _idx in (("noS", {0}), ("noE", {1}), ("noK", {2}), ("noEK", {1, 2})):
    VARIANTS[_name] = {"brief": _drop(B2, _idx), "review": _drop(R4, _idx), "chat": CHAT_SYS}

VARIANTS["c3"] = {"brief": B3, "review": R3, "chat": CHAT_SYS,
                  "review_input": lambda body: re.sub(r"(: )사용자(는|가) ", r"\1", body)}

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)


def text_of(res):
    c = res.get("content")
    if isinstance(c, str):
        return c
    return "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    return [(c["function"]["name"], c["function"].get("arguments", {}) or {}) for c in (res.get("tool_calls") or [])]


# ---------------------------------------------------------------- 지표
EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿⭐⬆↔-↪]")
HANGUL = re.compile("[가-힣]")
FORMAL = re.compile(r"(니다|니까|십시오|시오)$")
POLITE = re.compile(r"(요|죠)$")


def sentences(text):
    parts = re.split(r"(?<=[.?!…])\s+|\n+", text)
    return [p.strip() for p in parts if HANGUL.search(p or "")]


def ending(s):
    core = EMOJI.sub("", s)
    core = re.sub(r"[\s.?!…~,\"'”’)\]*:]+$", "", core)
    if FORMAL.search(core):
        return "formal"
    if POLITE.search(core):
        return "polite"
    return "other"


def measure(text):
    ss = sentences(text)
    kinds = [ending(s) for s in ss]
    return {
        "n": len(ss),
        "polite": kinds.count("polite"),
        "formal": kinds.count("formal"),
        "other": kinds.count("other"),
        "excl": text.count("!"),
        "emoji": len(EMOJI.findall(text)),
        "md": int(bool(re.search(r"\*\*|^\s*[-*] |^#", text, re.M))),
        "len": len(text),
        "q": text.count("?"),
    }


def report(tag, rows):
    n = sum(r["n"] for r in rows) or 1
    k = len(rows) or 1
    print(
        f"== {tag}: 출력 {len(rows)} 문장 {n} | 해요 {100 * sum(r['polite'] for r in rows) / n:.0f}% "
        f"합니다 {100 * sum(r['formal'] for r in rows) / n:.0f}% 기타 {100 * sum(r['other'] for r in rows) / n:.0f}% | "
        f"느낌표 {sum(r['excl'] for r in rows) / k:.1f}/출력 이모지 {sum(r['emoji'] for r in rows) / k:.1f}/출력 "
        f"마크다운 {sum(r['md'] for r in rows)}/{len(rows)} | 길이 평균 {sum(r['len'] for r in rows) / k:.0f}자",
        flush=True,
    )


def show(tag, text, extra=""):
    one = text.replace("\n", " / ")
    print(f"[{tag}] {extra}{one[:220]}", flush=True)


# ---------------------------------------------------------------- 브리핑
def brief_input(date_label, events, tasks, episodes, cal_failed=False):
    sched = ["[오늘 일정]"]
    if cal_failed:
        sched.append("(주의: 기기 캘린더를 읽지 못했습니다 — 아래는 앱에 저장된 일정만이며, 브리핑에서 이 사실을 알려야 합니다)")
    sched += [f"- {t} ({w})" for t, w in events] or ["- 없음"]
    body = [f"오늘은 {date_label}입니다. 아래 재료로 아침 브리핑을 작성하세요.", "", *sched, "", "[미완료 할 일]"]
    body += [f"- {t}" for t in tasks] or ["- 없음"]
    body += ["", "[최근 대화 기억]"]
    body += [f"- ({d}) {t}: {s}" for d, t, s in episodes] or ["- 없음"]
    return "\n".join(body)


EP = [
    ("10월 21일", "치과 상담 결과 정리", "사용자는 치과에서 충치 치료를 권유받았고 다음 주에 다시 가기로 했습니다."),
    ("10월 18일", "이사 준비 체크리스트", "사용자는 이사 날짜를 11월 2일로 정하고 짐 정리 순서를 비서와 정리했습니다."),
    ("10월 9일", "영어 회화 공부", "사용자는 매일 30분 영어 회화 연습 계획을 세웠습니다."),
]
BRIEFS = [
    brief_input("10월 22일 목요일", [], [], []),
    brief_input("10월 22일 목요일", [("팀 회의", "10월 22일 오전 10:00"), ("치과 예약", "10월 22일 오후 4:30")], ["장보기", "보고서 초안"], EP[:2]),
    brief_input("10월 22일 목요일", [("점심 약속", "10월 22일 오후 12:30")], [], EP[2:]),
    brief_input("10월 22일 목요일", [], ["택배 반품", "관리비 납부", "세탁소"], EP[:1]),
    brief_input("10월 23일 금요일", [("부모님 저녁", "10월 23일 오후 7:00")], ["선물 사기"], EP, cal_failed=True),  # 4번만 캘린더 실패
    brief_input("10월 24일 토요일", [], [], EP[1:2]),
    brief_input("10월 26일 월요일", [("주간 회의", "10월 26일 오전 9:30"), ("병원", "10월 26일 오후 2:00"), ("헬스", "10월 26일 오후 8:00")], ["회의 자료"], []),
    brief_input("10월 27일 화요일", [], ["이사 업체 견적 비교"], EP[1:]),
]

REVIEWS = [
    "지난 7일 대화 요약:\n" + "\n".join(f"- {d} · {t}: {s}" for d, t, s in reversed(EP[:2])),
    "지난 7일 대화 요약:\n- 10월 16일 · 자전거 구매 고민: 사용자는 출퇴근용 자전거 두 모델을 비교했습니다.\n- 10월 19일 · 러닝 기록: 사용자는 5km를 32분에 달렸고 다음 목표를 30분으로 잡았습니다.",
    "지난 7일 대화 요약:\n- 10월 15일 · 회사 프로젝트 마감: 사용자는 10월 31일 마감인 앱 출시 준비 상황을 정리했습니다.\n- 10월 17일 · 야근 피로: 사용자는 잠이 부족하다고 말했고 비서가 수면 습관을 제안했습니다.\n- 10월 20일 · 출시 일정 조정: 사용자는 출시일을 11월 5일로 미뤘습니다.",
    "지난 7일 대화 요약:\n- 10월 21일 · 엄마 생신 선물: 사용자는 엄마 생신(10월 28일) 선물로 스카프를 골랐습니다.",
    "지난 7일 대화 요약:\n- 10월 15일 · 제주도 여행 계획: 사용자는 11월 첫 주 2박 3일 제주 일정을 짰습니다.\n- 10월 16일 · 렌터카 예약 고민: 사용자는 렌터카 두 곳의 가격을 비교했습니다.\n- 10월 18일 · 영어 회화 공부: 사용자는 회화 앱을 일주일째 쓰고 있습니다.\n- 10월 21일 · 치과 상담: 사용자는 충치 치료 일정을 다음 주로 잡았습니다.",
]

SCHEDS = [
    [("팀 회의", "2026-10-22T10:00:00")],
    [("팀 회의", "2026-10-22T10:00:00"), ("치과 예약", "2026-10-22T16:30:00")],
    [("점심 약속", "2026-10-22T12:30:00"), ("헬스", "2026-10-22T20:00:00"), ("부모님 통화", "2026-10-22T21:00:00")],
    [("주간 회의", "2026-10-26T09:30:00"), ("병원", "2026-10-27T14:00:00"), ("부모님 저녁", "2026-10-30T19:00:00")],
    [("이사", "2026-11-02"), ("관리비 납부", "2026-10-31T09:00:00")],
]

CASUAL = [
    "안녕", "오늘 너무 피곤하다", "고마워", "점심 뭐 먹을지 추천해줘", "주말에 뭐 하면 좋을까?",
    "요즘 잠을 잘 못 자", "영어 공부 꾸준히 하는 팁 있어?", "오늘 좋은 일 있었어. 시험 합격했어!",
    "비 오는 날 듣기 좋은 노래 뭐 있을까?", "회의 때 긴장을 덜 하려면 어떻게 해?",
]
# (발화, 기대 툴, 앱 실행기의 성공 결과 JSON)
TOOL_FLOWS = [
    ("내일 아침 7시에 약 먹으라고 알려줘", "add_reminder",
     '{"status":"success","message":"리마인더 등록: 10월 23일 오전 7:00에 \'약 먹기\' 알림"}'),
    ("내 자전거 자물쇠 비밀번호는 4821이야, 기억해줘", "add_memory",
     '{"status":"success","message":"기억에 저장했어요."}'),
    ("금요일 저녁 7시에 부모님이랑 저녁 약속 잡아줘", "add_schedule",
     '{"status":"success","message":"일정이 성공적으로 추가되었습니다."}'),
]

for v in VSEL:
    cfg = VARIANTS[v]
    if "brief" in PARTS:
        rows = []
        cal_wrong = cal_missed = 0
        cost = []
        for i, body in enumerate(BRIEFS):
            t0 = time.perf_counter()
            with K.create_chat_conversation(engine, tools=[], system_message=cfg["brief"]) as conv:
                out = text_of(conv.send_message(body)).strip()
            cost.append((time.perf_counter() - t0, len(engine.tokenize(out))))
            m = measure(out)
            m["_t"] = out
            times = re.findall(r"(오[전후] \d{1,2}:\d{2})", body)
            kept = sum(t in out for t in times)
            rows.append(m)
            failed = "(주의: 기기 캘린더" in body
            cal_wrong += (not failed) and ("캘린더" in out)
            cal_missed += failed and ("캘린더" not in out)
            show(f"brief {v} {i}", out, f"질문{m['q']} 시각{kept}/{len(times)} | ")
        report(f"brief {v}", rows)
        print(f"   캘린더: 실패 아닌데 언급 {cal_wrong}/{len(BRIEFS) - 1}, 실패인데 안 알림 {cal_missed}/1, "
              f"'활기찬' {sum('활기찬' in r.get('_t', '') for r in rows)}", flush=True)
        print(f"   비용: 지시 {len(engine.tokenize(cfg['brief']))}토큰, 답 평균 {sum(c[1] for c in cost) / len(cost):.0f}토큰, "
              f"생성 평균 {sum(c[0] for c in cost) / len(cost):.2f}초", flush=True)
    if "review" in PARTS:
        rows = []
        cost = []
        for i, raw in enumerate(REVIEWS):
            body = cfg.get("review_input", lambda b: b)(raw)
            t0 = time.perf_counter()
            with K.create_chat_conversation(engine, tools=[], system_message=cfg["review"]) as conv:
                out = text_of(conv.send_message(body)).strip()
            cost.append((time.perf_counter() - t0, len(engine.tokenize(out))))
            rows.append(measure(out))
            made = [x for x in re.findall(r"\d+", out) if x not in body]
            third = "사용자는" in out or "사용자가" in out
            show(f"review {v} {i}", out, f"지어낸 숫자{made} 3인칭{'O' if third else '-'} | ")
        report(f"review {v}", rows)
        print(f"   비용: 지시 {len(engine.tokenize(cfg['review']))}토큰, 답 평균 {sum(c[1] for c in cost) / len(cost):.0f}토큰, "
              f"생성 평균 {sum(c[0] for c in cost) / len(cost):.2f}초", flush=True)
    if "sched" in PARTS:
        rows = []
        iso_leak = lost = 0
        for i, events in enumerate(SCHEDS):
            rng = "오늘" if i < 3 else "이번 주"
            raw = f"다음은 사용자의 {rng} 일정입니다.{_SCHED_ASK}\n\n일정:\n" + "\n".join(f"- {t} (시간: {s})" for t, s in events)
            body = cfg.get("sched_input", lambda b: b)(raw)
            with K.create_chat_conversation(engine, tools=[], system_message=cfg["sched"]) as conv:
                out = text_of(conv.send_message(body)).strip()
            rows.append(measure(out))
            iso_leak += bool(re.search(r"\d{4}-\d{2}-\d{2}T", out))
            miss = [t for t, _ in events if t not in out]
            lost += bool(miss)
            show(f"sched {v} {i}", out, f"빠진 일정{miss} | ")
        report(f"sched {v}", rows)
        print(f"   ISO 노출 {iso_leak}/{len(SCHEDS)}, 일정 이름 빠짐 {lost}/{len(SCHEDS)}", flush=True)
    if "chat" in PARTS:
        rows = []
        for u in CASUAL:
            with K.create_chat_conversation(engine, system_message=cfg["chat"]) as conv:
                res = conv.send_message(f"{REMINDER}\n\n{u}")
            out = text_of(res).strip()
            cs = calls_of(res)
            if cs:
                show(f"chat {v}", out, f"툴 호출 {cs} | {u} -> ")
                continue
            rows.append(measure(out))
            show(f"chat {v}", out, f"{u} -> ")
        report(f"chat-casual {v}", rows)
        rows = []
        for u, tool, result in TOOL_FLOWS:
            with K.create_chat_conversation(engine, system_message=cfg["chat"]) as conv:
                cs = calls_of(conv.send_message(f"{REMINDER}\n\n{u}"))
                if not cs or cs[0][0] != tool:
                    print(f"[chat-tool {v}] {tool} 아님: {u} -> {cs}", flush=True)
                    continue
                out = text_of(conv.send_message(K.tool_response_message(tool, result))).strip()
            rows.append(measure(out))
            show(f"chat-tool {v}", out, f"{tool} | ")
        report(f"chat-tool {v}", rows)

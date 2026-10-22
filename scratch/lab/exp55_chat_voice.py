"""exp55 — 0.38.5 M4 채팅 말투 실측 (계획서 docs/plans/0.38.5-voice.md M4).

실사용 비서 답은 합니다체 64%(exp54_real) — 툴을 부른 뒤의 답·긴 설명 답·앞 대화의 끌림이 원인 후보.
변형: A = 시스템 지시에 말투 한 줄(영어/한국어/briefly), B = 툴 성공 결과 문구 해요체(일정·알림).

part real  — 실제 입력(DB) 단일 턴. 툴을 부르면 앱 실행기와 같은 결과를 돌려주고 그 뒤 답을 잰다
part hist  — 실제 대화 앞부분(최근 6메시지)을 깔고 다음 실제 입력
part smoke — exp51b 툴 선택 스모크 12건(선택·인자·무호출)
part reject — 일정·알림·메모 요청을 거절 회신으로 돌려줄 때 거짓 성공(앱 BaseAgent 문구 그대로)

[WHY] 실제 대화 원문은 출력하지 않는다(fixtures/kosmos_db, gitignore) — 모델이 새로 만든 답의 앞부분과 숫자만.
엔진: GPU · FP32 · 0.18.0. 지표 정의는 exp54 와 같다.
사용: python exp55_chat_voice.py [part...] [--v=base,Aen,...] [--n=40]
"""
import json
import random
import re
import sqlite3
import sys

import litert_lm as llm
import kosmos_lab as K
import exp51b_smoke as S

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

args = [a for a in sys.argv[1:] if not a.startswith("--")]
PARTS = args or ["real", "hist", "smoke", "reject"]
VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base").split(",")
N = int(next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--n=")), "40"))

SYS = K.fixture("system_instruction.txt")
REMINDER = K.fixture("turn_reminder.txt").strip()
ANCHOR = "Always respond in Korean unless the user speaks another language.\n"
assert ANCHOR in SYS, "시스템 지시 형식이 바뀌었다 — 픽스처를 다시 내보낼 것"

LINES = {
    "Aen": "Reply in polite 해요체 (…해요, …했어요), not 합니다체.",
    "Ako": "답은 해요체(…해요, …했어요)로 하세요.",
    "Ashort": "Reply briefly in polite 해요체 (…해요, …했어요), not 합니다체.",
}


def sys_with(line):
    return SYS.replace(ANCHOR, ANCHOR + line + "\n") if line else SYS


VARIANTS = {
    "base": (SYS, False),
    "Aen": (sys_with(LINES["Aen"]), False),
    "Ako": (sys_with(LINES["Ako"]), False),
    "Ashort": (sys_with(LINES["Ashort"]), False),
    "B": (SYS, True),
    "AenB": (sys_with(LINES["Aen"]), True),
    "AkoB": (sys_with(LINES["Ako"]), True),
    "AshortB": (sys_with(LINES["Ashort"]), True),
}

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)

for name, line in LINES.items():
    print(f"## 토큰 {name}: tokenize 차이 +{len(engine.tokenize(sys_with(line))) - len(engine.tokenize(SYS))}", flush=True)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    return [(c["function"]["name"], c["function"].get("arguments", {}) or {}) for c in (res.get("tool_calls") or [])]


# ---------------------------------------------------------------- 툴 결과 (앱 실행기 문구 — BaseAgent 가 meta 를 뗀 뒤 모델이 보는 것)
def korean_time(iso):
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?", str(iso or ""))
    if not m:
        return str(iso)
    md = f"{int(m.group(2))}월 {int(m.group(3))}일"
    if not m.group(4):
        return f"{md} 종일"
    h, mi = int(m.group(4)), int(m.group(5))
    return f"{md} {'오후' if h >= 12 else '오전'} {h % 12 or 12}:{mi:02d}"


def tool_result(name, a, b):
    if name == "add_schedule":
        msg = "일정을 추가했어요." if b else "일정이 성공적으로 추가되었습니다."
        return json.dumps({"status": "success", "message": msg}, ensure_ascii=False)
    if name == "add_reminder":
        t, c = korean_time(a.get("time")), a.get("content", "")
        msg = f"{t}에 '{c}' 알림을 등록했어요." if b else f"리마인더 등록: {t}에 '{c}' 알림"
        return json.dumps({"status": "success", "message": msg}, ensure_ascii=False)
    if name == "add_memory":
        return json.dumps({"status": "success", "message": "기억에 저장했어요."}, ensure_ascii=False)
    if name == "get_schedule":
        return json.dumps({"status": "success", "data": "오늘 오전 10:00에 팀 회의가 있어요.\n\n- 팀 회의 (10월 22일 오전 10:00)\n"}, ensure_ascii=False)
    if name == "search_memory":
        return json.dumps({"status": "success", "data": "가장 관련 있는 기억:\n- 자전거 자물쇠 비밀번호는 4821\n\n이 목록에 질문의 답이 없으면 없다고 답하세요. 절대 지어내지 마세요."}, ensure_ascii=False)
    if name == "search_wikipedia":
        return json.dumps({"status": "error", "message": "웹 검색이 비활성화되어 있습니다. 사용자에게 채팅 화면 상단의 웹 검색 토글을 켜도록 안내하세요."}, ensure_ascii=False)
    return json.dumps({"status": "error", "message": f"알 수 없는 Tool입니다: {name}"}, ensure_ascii=False)


def run_turn(conv, user_text, b, max_rounds=3):
    """사용자 턴 하나 — 툴 루프 상한 3(앱과 같다). 마지막 글 답과 부른 툴 목록."""
    res = conv.send_message(f"{REMINDER}\n\n{user_text}")
    tools = []
    for _ in range(max_rounds):
        cs = calls_of(res)
        if not cs:
            break
        name, a = cs[0]
        tools.append(name)
        res = conv.send_message(K.tool_response_message(name, tool_result(name, a, b)))
    return text_of(res).strip(), tools


# ---------------------------------------------------------------- 지표
EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿⭐⬆↔-↪]")
HANGUL = re.compile("[가-힣]")


def sentences(t):
    return [p.strip() for p in re.split(r"(?<=[.?!…])\s+|\n+", t) if HANGUL.search(p or "")]


def ending(s):
    core = re.sub(r"[\s.?!…~,\"'”’)\]*:]+$", "", EMOJI.sub("", s))
    return "formal" if re.search(r"(니다|니까|십시오|시오)$", core) else "polite" if re.search(r"(요|죠)$", core) else "other"


def report(tag, outs):
    kinds = [ending(s) for o in outs for s in sentences(o)]
    n = len(kinds) or 1
    toks = [len(engine.tokenize(o)) for o in outs] or [0]
    print(f"== {tag}: 답 {len(outs)} 문장 {len(kinds)} | 해요 {100 * kinds.count('polite') / n:.0f}% 합니다 {100 * kinds.count('formal') / n:.0f}% "
          f"기타 {100 * kinds.count('other') / n:.0f}% | 이모지 {sum(len(EMOJI.findall(o)) for o in outs) / max(len(outs), 1):.1f}/답 "
          f"| 답 평균 {sum(toks) / len(toks):.0f}토큰", flush=True)


def show(tag, out, extra=""):
    print(f"[{tag}] {extra}{out.replace(chr(10), ' / ')[:90]}", flush=True)


# ---------------------------------------------------------------- 입력
DB = sqlite3.connect(str(K.FIXTURES / "kosmos_db"))
_users = [c for (c,) in DB.execute("select content from conversation where role='USER' and inputType='TEXT' order by createdAt")
          if c and 2 <= len(c) <= 80 and "[첨부" not in c and "\n" not in c]
random.Random(55).shuffle(_users)
REAL = _users[:N]

_rows = DB.execute("select sessionId, role, content from conversation where inputType in ('TEXT','VOICE') order by createdAt").fetchall()
HIST = []
for i, (sid, role, content) in enumerate(_rows):
    if role != "USER" or i < 6 or not content or len(content) > 80:
        continue
    prev = [r for r in _rows[i - 6:i] if r[0] == sid]
    if len(prev) < 4:
        continue
    HIST.append(([{"role": "user" if r[1] == "USER" else "model", "content": (r[2] or "")[:300]} for r in prev], content))
random.Random(56).shuffle(HIST)
HIST = HIST[:12]



REJECT = [
    ("내일 오후 3시에 치과 예약 잡아줘", "add_schedule", "사용자가 승인하지 않아 실행하지 않았습니다. 일정을 추가하지 않았다고 알리세요."),
    ("금요일 저녁 7시에 부모님이랑 저녁 약속 잡아줘", "add_schedule", "사용자가 승인하지 않아 실행하지 않았습니다. 일정을 추가하지 않았다고 알리세요."),
    ("오늘 오후 6시에 약 먹으라고 알려줘", "add_reminder", "사용자가 승인하지 않아 실행하지 않았습니다. 알림을 등록하지 않았다고 알리세요."),
    ("내일 아침 7시에 깨워줘", "add_reminder", "사용자가 승인하지 않아 실행하지 않았습니다. 알림을 등록하지 않았다고 알리세요."),
    ("내 자전거 비밀번호는 4821이야 기억해줘", "add_memory", "사용자가 저장을 승인하지 않았습니다. 이 내용은 기억에 없습니다. 저장하지 않았다고 사용자에게 분명히 알리세요."),
    ("회사 주차는 B3 구역 117번이야, 기억해 둬", "add_memory", "사용자가 저장을 승인하지 않았습니다. 이 내용은 기억에 없습니다. 저장하지 않았다고 사용자에게 분명히 알리세요."),
]
CLAIM = re.compile(r"(추가했|추가해 드렸|등록했|등록해 드렸|설정했|설정해 드렸|저장했|저장해 드렸|기억해 두|기억해 드|기억했|기억할게|잡아 드렸|잡았)")
NEG = re.compile(r"(않|못|안 ?했|취소|승인이 필요|승인하지)")

for v in VSEL:
    system, b = VARIANTS[v]
    if "real" in PARTS:
        outs, after_tool = [], []
        for u in REAL:
            with K.create_chat_conversation(engine, system_message=system) as conv:
                out, tools = run_turn(conv, u, b)
            if not out:
                continue
            (after_tool if tools else outs).append(out)
            show(f"real {v}", out, f"{'툴 ' + ','.join(tools) + ' 뒤 ' if tools else ''}")
        report(f"real {v} 툴 없이", outs)
        report(f"real {v} 툴 뒤", after_tool)
        report(f"real {v} 전체", outs + after_tool)
    if "hist" in PARTS:
        outs = []
        for h, u in HIST:
            with K.create_chat_conversation(engine, history=h, system_message=system) as conv:
                out, tools = run_turn(conv, u, b)
            if out:
                outs.append(out)
                show(f"hist {v}", out, f"{'툴 ' + ','.join(tools) + ' 뒤 ' if tools else ''}")
        report(f"hist {v}", outs)
    if "smoke" in PARTS:
        ok = 0
        for name, utterance, check in S.CASES:
            with K.create_chat_conversation(engine, system_message=system) as conv:
                res = conv.send_message(K.with_turn_reminder(utterance))
            hit = check(S.first_call(res))
            ok += hit
            if not hit:
                print(f"[smoke {v}] X {name}: {S.first_call(res) or text_of(res)[:60]}", flush=True)
        print(f"== smoke {v}: {ok}/{len(S.CASES)}", flush=True)
    if "reject" in PARTS:
        lies = told = 0
        for u, tool, msg in REJECT:
            with K.create_chat_conversation(engine, system_message=system) as conv:
                cs = calls_of(conv.send_message(f"{REMINDER}\n\n{u}"))
                if not cs or cs[0][0] != tool:
                    print(f"[reject {v}] {tool} 아님: {cs}", flush=True)
                    continue
                out = text_of(conv.send_message(K.tool_response_message(tool, json.dumps({"status": "error", "message": msg}, ensure_ascii=False)))).strip()
            lie = bool(CLAIM.search(out)) and not NEG.search(out)
            lies += lie
            told += bool(NEG.search(out))
            show(f"reject {v}", out, "LIE " if lie else "ok  ")
        print(f"== reject {v}: 거짓 성공 {lies}/{len(REJECT)}, 안 했다고 말함 {told}/{len(REJECT)}", flush=True)

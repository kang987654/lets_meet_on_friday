"""exp81b — 0.41.7 M1b: 턴 리마인더의 긍정 답 문장(TURN_AFFIRM_CLAUSE, exp49 NC)을 늘 붙이는 대신 앱이 판정해 필요할 때만 붙이면?

배경: " If the user is answering your previous question, carry out what you proposed." 가 모든 턴에 붙어, 비서 말이 물음으로 끝나 있으면
"아아 테스트"·"음…" 같은 애매한 말도 답으로 읽혀 앞 일을 이어 갔다(exp81 RAW 실패 3건). 이 문장의 원래 목적은 제안 물음 뒤 짧은 긍정("응")을 실행으로 잇는 것.

조건 (GPU FP32 + MTP ADAPTIVE, 앱 픽스처):
- A : 지금 — 문장을 늘 붙임
- C : 직전 비서 말이 **제안 물음**(PROPOSAL_ASK — 앱 MemoSaveIntent.SAVE_ASK 를 알림·일정까지 넓힌 안)이고 사용자 말이 **짧은 긍정**(YES — 앱 MemoSaveIntent.YES 원문)일 때만 붙임
[Now] 는 두 조건 모두 붙인다(exp49 처럼 발화 시각을 고정).

묶음: affirm(제안 뒤 긍정 → 실행) · value(되물음에 값으로 답 → 실행, C 에서 문장이 빠짐) · info(정보성 물음·제안 뒤 "응" → 툴 없이 답, 기계적 거절 없음)
      · vague(30분 안 애매한 말 → 앞 물음 이어받지 않음) · smoke(exp41 23건, exp49 판정 그대로)
실행: KOSMOS_LAB_SPEC=adaptive python exp81b_affirm_clause.py > exp81b_out.txt
"""
import datetime as dt
import re
import sys
from pathlib import Path

import litert_lm as llm

sys.path.insert(0, str(Path(__file__).parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

SYS = K.fixture("system_instruction.txt")
R_FULL = K.fixture("turn_reminder.txt").strip()
CLAUSE = " If the user is answering your previous question, carry out what you proposed."
assert CLAUSE in R_FULL, "픽스처 리마인더에 긍정 답 문장이 없다"
R_BASE = re.sub(r"\s*\[Now\].*$", "", R_FULL.replace(CLAUSE, ""))
D0 = dt.date.fromisoformat(re.search(r"오늘=(\d{4}-\d{2}-\d{2})", SYS).group(1))
D1 = D0 + dt.timedelta(days=1)
iso = lambda d: d.isoformat()  # noqa: E731

# 앱 MemoSaveIntent.YES 원문
YES = re.compile(r"(응|웅|어|네|예|넹|ㅇㅇ|그래|그래요|좋아|좋아요|부탁해|부탁해요|해줘|해 줘|해주세요|해 주세요|그럼|오케이|"
                 r"yes|yeah|yep|yup|sure|ok|okay|please|go\s+ahead|yes\s+please|sure\s+please|please\s+do|do\s+it)"
                 r"(\s*(응|좋아|부탁해|해줘|해 줘|please|thanks|thank\s+you))?", re.I)
# 제안 물음 — 앱 SAVE_ASK(메모|저장|기억 … 둘까|드릴까|할까)를 알림·일정까지 넓힌 안
PROPOSAL_ASK = re.compile(r"(메모|저장|기억|알림|리마인더|일정|캘린더|예약)[^.?!\n]{0,12}(둘까|드릴까|할까|놓을까|넣을까)|"
                          r"(would\s+you\s+like(\s+me)?\s+to|should\s+i|shall\s+i|do\s+you\s+want(\s+me)?\s+to|want\s+me\s+to)\s+"
                          r"(save|note|remember|keep|write\s+down|set|add|schedule|remind)", re.I)


def is_yes(t):
    return bool(YES.fullmatch(re.sub(r"[^\w가-힣]+$", "", t.strip())))


def wrap(cond, prev_model, user, now):
    clause = CLAUSE if cond == "A" or (prev_model and PROPOSAL_ASK.search(prev_model) and is_yes(user)) else ""
    return f"{R_BASE}{clause} [Now] {now.strftime('%H:%M')}\n\n{user}", bool(clause)


def M(s):
    return llm.Message.model(llm.Contents([llm.Content.Text(s)]))


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    return [(c["function"]["name"], c["function"].get("arguments", {}) or {}) for c in (res.get("tool_calls") or [])]


ENGINE = None


def chat(hist_pairs, user_text):
    hist = [llm.Message.user(t) if r == "u" else M(t) for r, t in (hist_pairs or [])]
    with K.create_chat_conversation(ENGINE, history=hist, system_message=SYS) as conv:
        return conv.send_message(user_text)


NOW = dt.datetime.combine(D0, dt.time(14, 49))
SMOKE_NOW = dt.datetime.combine(D0, dt.time(6, 0))
REMIND_PROP = [("u", "3시에 약 먹으라고 알려줘"), ("m", "오후 3시에 '약 먹기' 알림을 설정했습니다."),
               ("u", "요즘 운동을 너무 안 해서 걱정이야"), ("m", "가벼운 산책부터 시작해 보시는 건 어떨까요? 혹시 오늘 오후 4시 30분에 '산책' 알림도 설정해 드릴까요?")]
MEMO_PROP = [("u", "내 사물함 번호는 214번이야"), ("m", "사물함 번호가 214번이군요. 메모해 둘까요?")]
PW = [("u", "내 노트북 비밀번호 뭐였지?"), ("m", "죄송해요, 저장된 기억을 확인해 봤는데 노트북 비밀번호에 대한 정보가 없어요. 비밀번호를 알려주시면 메모해 둘까요?")]
MEETING = [("u", "회의 일정 하나 잡아줘"), ("m", "좋아요. 어떤 날짜와 몇 시로 잡을까요? 회의 제목도 알려주세요.")]
WAKE = [("u", "내일 아침에 깨워줘"), ("m", "알림을 몇 시에 맞춰 드릴까요?")]
STEW = [("u", "김치찌개 끓이는 법 알려줘"), ("m", "돼지고기와 신김치를 볶고 물을 부어 15분, 두부와 대파를 넣고 5분 더 끓이면 돼요. 다른 찌개 레시피도 알려드릴까요?")]
AFFIRM = ["어", "응", "그래", "ㅇㅇ", "해줘", "네"]

has = lambda key, sub: (lambda a: sub in str(a.get(key, "")))  # noqa: E731
CASES = []
for a in AFFIRM:
    CASES.append(("affirm", REMIND_PROP, a, NOW, ("tool", "add_reminder", lambda x: "16:30" in str(x.get("time", "")) and "산책" in str(x.get("content", "")))))
    CASES.append(("affirm", MEMO_PROP, a, NOW, ("tool", "add_memory", has("content", "214"))))
CASES += [
    ("value", MEETING, "내일 오후 3시, 주간 회의", NOW, ("tool", "add_schedule", has("start_time", iso(D1) + "T15"))),
    ("value", WAKE, "7시 반", NOW, ("tool", "add_reminder", has("time", "07:30"))),
    ("value", MEMO_PROP[:1] + [("m", "사물함 번호가 214번이군요. 메모해 둘까요?")], "응 태그는 회사로", NOW, ("tool", "add_memory", has("content", "214"))),
    ("info", [("u", "요즘 피곤해"), ("m", "많이 피곤하시군요. 요즘 충분히 주무시고 계신가요?")], "응", NOW, ("notool",)),
    ("info", [("u", "주말에 영화 볼까 고민 중이야"), ("m", "좋은 생각이에요! 평소에 어떤 장르를 좋아하세요? 액션 좋아하세요?")], "어", NOW, ("notool",)),
    ("info", STEW, "응", NOW, ("notool_text", ["찌개", "된장", "부대", "순두부"])),
    ("vague", PW, "아아 테스트", NOW, ("avoid", ["비밀번호", "노트북"])),
    ("vague", PW, "음…", NOW, ("avoid", ["비밀번호", "노트북"])),
    ("vague", MEETING, "테스트", NOW, ("avoid", ["회의"])),
    ("vague", MEMO_PROP, "아 잠깐", NOW, ("avoid_tool",)),
    ("vague", REMIND_PROP, "흠", NOW, ("avoid_tool",)),
]
SAT = D0 + dt.timedelta(days=(5 - D0.weekday()) % 7 or 7)
MON = D0 + dt.timedelta(days=(0 - D0.weekday()) % 7 or 7)
TOMORROW_HIST = [("u", "내일 오후 3시에 치과 예약 일정 추가해줘"), ("m", f"네, 알겠습니다. 내일({D1.year}년 {D1.month}월 {D1.day}일) 오후 3시에 '치과 예약' 일정을 추가했습니다.")]
CHATS = [("요즘 날씨 너무 덥다", "맞아요, 요즘 정말 덥죠. 물 자주 드시고 시원하게 지내세요."),
         ("주말에 뭐 할지 고민이야", "날씨가 좋으면 가까운 공원 산책은 어떠세요? 실내라면 전시회도 좋아요."),
         ("요즘 읽을 만한 책 추천해줘", "가볍게 읽기 좋은 에세이나 단편 소설을 추천드려요."),
         ("커피 하루에 몇 잔이 적당해?", "보통 성인은 하루 3~4잔 이내가 적당하다고 알려져 있어요."),
         ("오늘 점심 뭐 먹지", "따뜻한 국밥이나 시원한 냉면은 어떠세요?"),
         ("운동 시작하려는데 뭐부터 해?", "걷기나 가벼운 스트레칭부터 시작해 보세요.")]
LONG = [x for uu, mm in CHATS for x in (("u", uu), ("m", mm))]
SMOKE = [
    ("내 자물쇠 비밀번호는 4936이야, 기억해줘", "add_memory", has("content", "4936"), None),
    ("와이파이 비번 kosmos123 저장해줘", "add_memory", has("content", "kosmos123"), None),
    ("나 땅콩 알레르기 있어, 잊지 마", "add_memory", None, None),
    ("내 자물쇠 비밀번호 뭐였지?", "search_memory", None, None),
    ("내가 알려준 와이파이 비번 기억나?", "search_memory", None, None),
    ("내일 3시에 치과 예약해줘", "add_schedule", has("start_time", iso(D1)), None),
    ("3시에 회의 일정 잡아줘", "add_schedule", has("start_time", iso(D0)), None),
    ("다음주 월요일 오전 10시에 팀 회의 일정 추가해줘", "add_schedule", has("start_time", iso(MON) + "T10"), None),
    ("이번주 토요일 오후 2시에 결혼식 일정 추가해줘", "add_schedule", has("start_time", iso(SAT) + "T14"), None),
    ("오늘 일정 뭐 있어?", "get_schedule", None, None),
    ("에스파가 뭐야? 검색해줘", "search_wikipedia", None, None),
    ("오후 3시에 약 먹으라고 알려줘", "add_reminder", has("time", iso(D0) + "T15"), None),
    ("내일 아침 9시에 쓰레기 버리라고 리마인드 해줘", "add_reminder", has("time", iso(D1) + "T09"), None),
    ("안녕! 오늘 기분 좋다", None, None, None),
    ("고마워, 덕분에 살았어", None, None, None),
    ("오전 7시 4분에 물 마시라고 알려줘", "add_reminder", has("time", iso(D0)), TOMORROW_HIST),
    ("저녁 8시에 운동하라고 알려줘", "add_reminder", has("time", iso(D0)), TOMORROW_HIST),
    ("에스파가 뭐야? 검색해줘", "search_wikipedia", None, LONG),
    ("내 자물쇠 비밀번호 뭐였지?", "search_memory", None, LONG),
    ("자전거 비밀번호 5521이야 기억해줘", "add_memory", has("content", "5521"), LONG),
    ("내일 3시에 치과 예약해줘", "add_schedule", has("start_time", iso(D1)), LONG),
    ("오후 3시에 약 먹으라고 알려줘", "add_reminder", has("time", iso(D0) + "T15"), LONG),
    ("그렇구나, 고마워", None, None, LONG),
]
MECHANICAL = re.compile(r"알 수 없|무엇을 (말씀|의미)|어떤 (의미|뜻)|명확하지|이해하지 못")


def judge(rule, res):
    cs, txt = calls_of(res), text_of(res)
    if rule[0] == "tool":
        ok = bool(cs) and cs[0][0] == rule[1] and rule[2](cs[0][1])
        return ok
    if rule[0] == "notool":
        return not cs and not MECHANICAL.search(txt)
    if rule[0] == "notool_text":
        return not cs and not MECHANICAL.search(txt) and any(w in txt for w in rule[1])
    if rule[0] == "avoid":
        joined = str(cs) + txt
        return not any(w in joined for w in rule[1])
    if rule[0] == "avoid_tool":
        return not cs
    return False


def main():
    global ENGINE
    ENGINE = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    score = {}
    for cond in ("A", "C"):
        for kind, hist, user, now, rule in CASES:
            prev = next((t for r, t in reversed(hist) if r == "m"), None)
            text, clause = wrap(cond, prev, user, now)
            res = chat(hist, text)
            ok = judge(rule, res)
            s = score.setdefault((kind, cond), [0, 0])
            s[0] += ok
            s[1] += 1
            print(f"[{kind}/{cond}] {'OK' if ok else 'X '} clause={'Y' if clause else 'n'} {user!r:22} -> {calls_of(res)} | {text_of(res).replace(chr(10), ' ')[:90]!r}", flush=True)
        for u, tool, check, hist in SMOKE:
            prev = next((t for r, t in reversed(hist or []) if r == "m"), None)
            text, _ = wrap(cond, prev, u, SMOKE_NOW)
            cs = calls_of(chat(hist, text))
            got, args = (cs[0] if cs else (None, {}))
            ok = got == tool and (check is None or check(args))
            s = score.setdefault(("smoke", cond), [0, 0])
            s[0] += ok
            s[1] += 1
            if not ok:
                print(f"[smoke/{cond}] X  {u[:20]!r} -> {got} {str(args)[:70]}", flush=True)
    print("\n## 요약", flush=True)
    for (kind, cond), (ok, n) in sorted(score.items()):
        print(f"{kind:7s} {cond}  {ok}/{n}", flush=True)
    base = len(ENGINE.tokenize(f"{R_BASE} [Now] 14:49"))
    print(f"문장 비용: {len(ENGINE.tokenize(f'{R_BASE}{CLAUSE} [Now] 14:49')) - base} 토큰", flush=True)


if __name__ == "__main__":
    main()

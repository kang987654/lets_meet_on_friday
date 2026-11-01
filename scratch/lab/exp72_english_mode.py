"""exp72 — 0.40.0 M0: 영어(EN) 시스템 지시 오버헤드 실측 · 영어 툴 스모크 · 영어 가드 시나리오 · 영어 에피소드 요약/자동 추출 실측.

질문:
1. 영어 시스템 지시(SYS_EN) + 현행 툴 선언의 첫 턴 KV(token_count, max_output_tokens=1)와 tokenize 토큰 수가
   한국어 본(SYS_KO, MEASURED_OVERHEAD=1257) 대비 얼마나 차이나며 예약(1400) 안에 들어오는가?
2. 현행 툴 선언(KosmosToolDeclarations 한국어 설명 공유) + 영어 시스템 지시(SYS_EN) 조합에서
   영어 툴 호출 스모크(7개 툴 + 일상 대화 = 14문항)가 정확히 동작하는가? (특히 get_schedule 'today'/'week' 제약)
3. 영어 가드 시나리오(사실만 말하기 -> "Would you like me to save this as a note?" 되묻기,
   "Do you remember...?" 조회가 저장으로 오탐되지 않는지, "yes" 뒤 저장, 거절 따라 하기/되묻기)가 동작하는가?
4. 영어 에피소드 요약(Title:/Tags:/Summary:)과 사실 자동 추출(Profile:/Knowledge: ... [tags]) 프롬프트가
   형식을 준수하고 상위 개념 태그를 생성하는가?
"""
import pathlib
import re
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import kosmos_lab as K  # noqa: E402
import run as R  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

eng = R.engine()
tok = lambda s: len(eng.tokenize(s))  # noqa: E731

SYS_KO = K.fixture("system_instruction.txt")
TODAY, TOMORROW, NEXT_MON = re.search(
    r"오늘=(\S+) \(\w+\), 내일=(\S+), 모레=(\S+), 다음주 월요일=(\S+)", SYS_KO
).groups()[0], re.search(
    r"오늘=(\S+) \(\w+\), 내일=(\S+), 모레=(\S+), 다음주 월요일=(\S+)", SYS_KO
).groups()[1], re.search(
    r"오늘=(\S+) \(\w+\), 내일=(\S+), 모레=(\S+), 다음주 월요일=(\S+)", SYS_KO
).groups()[3]
DAY_AFTER = re.search(
    r"오늘=(\S+) \(\w+\), 내일=(\S+), 모레=(\S+), 다음주 월요일=(\S+)", SYS_KO
).groups()[2]
WEEKDAY = re.search(r"오늘=\S+ \((\w+)\)", SYS_KO).group(1)

SYS_EN = f"""[System]
You are a personal assistant named Kosmos who also chats, recommends and gives advice from your own knowledge
Always respond in English unless the user speaks another language.
Reply briefly in clear, natural, polite English.
[System Data] Today={TODAY} ({WEEKDAY}), Tomorrow={TOMORROW}, Day after tomorrow={DAY_AFTER}, Next Monday={NEXT_MON}
[Tool Usage Guidelines]
For EVERY user request, first decide if one of your tools applies. If it does, you MUST call the tool. Do NOT merely promise or pretend — promising without calling is a failure.
- The user asks you to remember something: "remember", "save this", "make a note", "keep in mind", "don't forget": you MUST call `add_memory`. If the user only shares a fact, confirm it and ask "Would you like me to save this as a note?" — never say you will remember it.
- The user asks about something they told you earlier: "what was", "what did I say", "do you remember", "my saved": you MUST call `search_memory` with a short noun keyword. This is a LOOKUP, never call `add_memory` for it.
- The user asks to add an appointment, reservation, or event: "schedule", "appointment", "meeting", "book", "add to calendar": you MUST call `add_schedule`.
- The user asks what is on their calendar: "today's schedule", "tomorrow's schedule", "what's on my calendar": you MUST call `get_schedule`.
- The user asks a factual question you are not sure about: "search for", "look up", "what is": call `search_wikipedia`.
- The user asks to be reminded at a specific time: "remind me at", "set a reminder": you MUST call `add_reminder`.
- The user asks which reminders are set: "what reminders", "my reminders": you MUST call `list_reminders`.
Compute dates and times yourself from [System Data] above ("tomorrow", "day after tomorrow", "3 PM" are not missing). A time with no date word means today.
Optional parameters are never a reason to ask. Only ask the user when a REQUIRED parameter is genuinely absent.
Never alter numbers, dates, or proper nouns the user gave you — copy them exactly."""

# ── 1. 오버헤드 실측 ────────────────────────────────────────────────────────
def measure_kv(sysm: str, user_word: str) -> int:
    with K.create_chat_conversation(eng, tools=K.ALL_TOOLS, system_message=sysm) as c:
        c.send_message(K.with_turn_reminder(user_word), max_output_tokens=1)
        return c.token_count

kv_ko = measure_kv(SYS_KO, "안녕")
kv_en = measure_kv(SYS_EN, "Hi")
tok_ko = tok(SYS_KO)
tok_en = tok(SYS_EN)
print(
    f"== 1. 오버헤드 실측 ==\n"
    f"  KO: first-turn KV={kv_ko}, tokenize(SYS_KO)={tok_ko}\n"
    f"  EN: first-turn KV={kv_en}, tokenize(SYS_EN)={tok_en}\n"
    f"  차이(EN - KO): KV {kv_en - kv_ko:+d}, tokenize {tok_en - tok_ko:+d}",
    flush=True,
)

# ── 2. 영어 툴 선택 스모크 ───────────────────────────────────────────────────
SMOKE_EN = [
    ("s_add_sched1", "Schedule a dentist appointment tomorrow at 3 PM.", "add_schedule", lambda a: TOMORROW in a.get("start_time", "") and "15:00" in a.get("start_time", "")),
    ("s_add_sched2", "Add a team meeting next Monday at 10 AM.", "add_schedule", lambda a: NEXT_MON in a.get("start_time", "") and "10:00" in a.get("start_time", "")),
    ("s_get_today", "What's on my schedule today?", "get_schedule", lambda a: a.get("date") == "today"),
    ("s_get_tom", "What's on my calendar tomorrow?", "get_schedule", lambda a: a.get("date") == "week"),
    ("s_get_week", "What events do I have next week?", "get_schedule", lambda a: a.get("date") == "week"),
    ("s_add_mem1", "Please remember that my bike lock code is 4821.", "add_memory", lambda a: "4821" in a.get("content", "")),
    ("s_add_mem2", "Don't forget that my sister's birthday is May 3rd.", "add_memory", lambda a: "May 3" in a.get("content", "") or "5" in a.get("content", "")),
    ("s_srch_mem1", "What was my bike lock code?", "search_memory", lambda a: bool(a.get("keyword"))),
    ("s_srch_mem2", "Do you remember my Wi-Fi password?", "search_memory", lambda a: bool(a.get("keyword"))),
    ("s_add_rem1", "Remind me at 4 PM today to take my medicine.", "add_reminder", lambda a: TODAY in a.get("time", "") and "16:00" in a.get("time", "")),
    ("s_list_rem", "What reminders do I have set?", "list_reminders", lambda a: True),
    ("s_wiki", "Look up Alan Turing on Wikipedia.", "search_wikipedia", lambda a: "Turing" in a.get("topic", "")),
    ("s_chat1", "Recommend a light lunch menu for a rainy day.", None, lambda _: True),
    ("s_chat2", "How can I stretch my neck at my desk?", None, lambda _: True),
]

smoke_ok = 0
print("\n== 2. 영어 툴 스모크 ==", flush=True)
for cid, q, exp_tool, pred in SMOKE_EN:
    with K.create_chat_conversation(eng, tools=K.ALL_TOOLS, system_message=SYS_EN) as c:
        res = c.send_message(K.with_turn_reminder(q))
    tcs = res.get("tool_calls") or []
    got_tool = tcs[0]["function"]["name"] if tcs else None
    args = tcs[0]["function"].get("arguments", {}) if tcs else {}
    ok = (got_tool == exp_tool) and pred(args)
    smoke_ok += int(ok)
    print(f"  [{cid}] ok={ok} got={got_tool} args={args} text={R.text_of(res)[:60]!r}", flush=True)
print(f"  -> 영어 스모크: {smoke_ok}/{len(SMOKE_EN)}", flush=True)

# ── 3. 영어 가드 시나리오 (사실만 말하기 / 조회 의문문 / yes 뒤 저장) ─────────
FACT_EN = [
    ("f_en1", "My locker code is 507319."),
    ("f_en2", "The meeting room is on the 5th floor, Room C."),
    ("f_en3", "My tracking number is 6043-1122-9087."),
    ("f_en4", "I wrote a long note in my journal today about my trip. Thanks for listening."),
]
ASK_FIRST_EN = 'Not saved yet. Ask the user "Would you like me to save this as a note?" before saving.'
REPEAT_NOT_SAVE_EN = "Not saved. The user is asking to hear what they told you earlier. Answer with that value and do not claim you saved it."

print("\n== 3. 영어 사실만 말하기 & 가드 ==", flush=True)
fact_ok = 0
TOOLS_EN = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
for cid, q in FACT_EN:
    with K.create_chat_conversation(eng, tools=TOOLS_EN, system_message=SYS_EN) as c:
        res = c.send_message(K.with_turn_reminder(q))
        tcs = res.get("tool_calls") or []
        if tcs and tcs[0]["function"]["name"] == "add_memory":
            # 사실 가드 개입 시뮬레이션
            res = c.send_message(llm.Message.tool(llm.Contents([llm.Content.ToolResponse("add_memory", f'{{"status":"error","message":{ASK_FIRST_EN!r}}}')])))
        txt = R.text_of(res)
    asked = bool(re.search(r"(would you like|should i|shall i|want me to).*(save|note|remember)", txt, re.I))
    # f_en4(일상 감상)는 저장 물음 없이 공감 답이어도 통과
    ok = asked or (cid == "f_en4" and not (tcs and tcs[0]["function"]["name"] == "add_memory"))
    fact_ok += int(ok)
    print(f"  [{cid}] ok={ok} first_call={tcs[0]['function']['name'] if tcs else None} reply={txt!r}", flush=True)
print(f"  -> 영어 사실/일상 가드: {fact_ok}/{len(FACT_EN)}", flush=True)

# ── 4. 영어 에피소드 요약 & 사실 자동 추출 ───────────────────────────────────
SUM_SYS_EN = """[System]
You are a librarian who summarizes conversation logs into searchable index documents. Output ONLY the following format.
Title: (one line)
Tags: (5 to 8 comma-separated core nouns — include both specific nouns from the text and broader category/synonym nouns for search, e.g., coffee->beverage, dentist->hospital, puppy->pet, running->exercise)
Summary: (3 to 5 sentences in clear, polite English. State explicitly who did what, e.g., "The user asked...", "The assistant explained...". Preserve dates, times, names, and numbers exactly as given.)

If the conversation contains 2 or more unrelated topics, repeat the format above for each topic separated by a single '---' line. If there is only one topic, never split it."""

EXT_SYS_EN = """[System]
You extract durable facts about the user from a conversation log.
Select only facts explicitly stated in the conversation; exclude guesses, general knowledge, one-off small talk, and assistant remarks. Exclude one-time appointments or reservations since the calendar handles them. Preserve dates and numbers verbatim, and write one fact per line.
Output ONLY the following format.
Profile: (always-relevant facts like name, title, tone preference, occupation, city, family — in "Key: Value" form, up to 2, or "None")
Knowledge: (facts needed only in specific situations — passwords, codes, places, habits, recurring routines, with [2 to 3 broader category/synonym tags] at the end of each line, e.g., Drinks green tea instead of coffee in the morning [beverage, tea], up to 3, or "None")"""

EN_EP_CASES = [
    ("coffee_tea", "User: In the morning, I drink green tea instead of coffee.\nAssistant: Got it, you prefer green tea in the morning!", {"beverage", "drink", "tea"}),
    ("peanut_allergy", "User: I get hives whenever I eat peanuts or shrimp.\nAssistant: That sounds serious, please be careful with peanuts and shrimp.", {"allergy", "food", "health", "seafood"}),
    ("dentist", "User: I get a regular checkup every month at Smile Dental Clinic near Gangnam Station.\nAssistant: Regular dental checkups are great for your teeth.", {"hospital", "clinic", "medical", "health", "dental"}),
    ("running", "User: Every weekend morning I run 5km along the Han River.\nAssistant: Running 5km every weekend is a great habit!", {"exercise", "workout", "fitness", "hobby", "sport", "jogging"}),
    ("netflix", "User: My Netflix membership is billed automatically on the 15th of every month to my Shinhan card.\nAssistant: Noted, Netflix auto-pays on the 15th.", {"subscription", "streaming", "payment", "billing"}),
]

sum_fmt = sum_super = ext_fmt = ext_super = 0
print("\n== 4. 영어 에피소드 요약 & 자동 추출 ==", flush=True)
for name, dialog, expected_super in EN_EP_CASES:
    with K.create_chat_conversation(eng, tools=[], system_message=SUM_SYS_EN) as c:
        s_raw = R.text_of(c.send_message("Summarize the following conversation.\n\n" + dialog))
    m_title = re.search(r"(?:제목|Title)\s*[:：]\s*(.+)", s_raw, re.I)
    m_tags = re.search(r"(?:태그|Tags)\s*[:：]\s*(.+)", s_raw, re.I)
    m_sum = re.search(r"(?:요약|Summary)\s*[:：]\s*(.+)", s_raw, re.I | re.S)
    s_ok = bool(m_title and m_tags and m_sum)
    s_tags_str = m_tags.group(1).lower() if m_tags else ""
    s_hit = any(w in s_tags_str for w in expected_super)
    sum_fmt += int(s_ok)
    sum_super += int(s_hit)

    with K.create_chat_conversation(eng, tools=[], system_message=EXT_SYS_EN) as c:
        e_raw = R.text_of(c.send_message("Extract durable facts from the following conversation.\n\n" + dialog))
    e_ok = bool(re.search(r"^\s*[-*•]?\s*(?:프로필|지식|Profile|Knowledge)\s*[:：]", e_raw, re.I | re.M))
    bracket = re.findall(r"\[([^\]]+)\]", e_raw)
    e_tags_str = " ".join(bracket).lower()
    e_hit = any(w in e_tags_str for w in expected_super)
    ext_fmt += int(e_ok)
    ext_super += int(e_hit)
    print(f"  [{name}] sum(fmt={s_ok}, super={s_hit}, tags={s_tags_str!r}) | ext(fmt={e_ok}, super={e_hit}, tags={e_tags_str!r})", flush=True)

print(
    f"  -> 영어 요약 형식 {sum_fmt}/{len(EN_EP_CASES)}, 상위태그 {sum_super}/{len(EN_EP_CASES)}\n"
    f"  -> 영어 추출 형식 {ext_fmt}/{len(EN_EP_CASES)}, 상위태그 {ext_super}/{len(EN_EP_CASES)}",
    flush=True,
)

"""exp95b — 0.42.1 트리거 압축의 비용 실측(engine.tokenize 차이, 한/영). AGENTS §2-⑤.

새 픽스처(앱이 내보낸 system_instruction*.txt)에서 압축한 줄을 옛 줄로 되돌려 옛 지시를 만들고 토큰 차이를 잰다.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
PAIRS = [  # (옛 줄, 새 줄)
    ('- The user asks you to remember something: "기억해", "기억해줘", "저장해줘", "메모해줘", "잊지 마": you MUST call `add_memory`.',
     '- Remember requests ("기억해줘", "저장해줘", "메모해줘", "잊지 마"): call `add_memory`.'),
    ('- The user asks about something they told you earlier: "뭐였지", "뭐라고 했지", "내가 알려준", "기억나", "저장한 거": you MUST call `search_memory` with a short noun keyword. This is a LOOKUP, never call `add_memory` for it.',
     '- Questions about what the user told you ("뭐였지", "내가 알려준", "기억나"): call `search_memory` with a short noun keyword. A lookup — never call `add_memory` for it.'),
    ('- The user asks to add an appointment, reservation, or event: "예약", "약속", "일정 잡아줘", "일정 추가", "~하기로 했어": you MUST call `add_schedule`. Take the title from the user\'s words and never ask for end_time or memo.',
     '- Adding an appointment, reservation, or event ("예약", "약속", "일정 잡아줘", "~하기로 했어"), even if they say "기억해 둬": call `add_schedule`. Take the title from the user\'s words; never ask for end_time or memo.'),
    ('- The user asks what is on their calendar: "오늘 일정", "내일 일정", "스케줄 뭐 있어": you MUST call `get_schedule`.',
     '- Calendar questions, including when an appointment or reservation is ("오늘 일정", "약속 언제였지", "치과 몇 시였지"): call `get_schedule`.'),
    ('- The user asks to be reminded at a specific time: "3시에 알려줘", "리마인드": you MUST call `add_reminder`.',
     '- Reminders at a time ("3시에 알려줘", "리마인드"): call `add_reminder`.'),
    ('- The user asks which reminders are set: "알림 뭐 있어", "리마인더 목록": you MUST call `list_reminders`.',
     '- Listing reminders ("알림 뭐 있어"): call `list_reminders`.'),
    ('- The user asks you to remember something: "remember", "save this", "make a note", "keep in mind", "don\'t forget": you MUST call `add_memory`.',
     '- Remember requests ("remember", "save this", "make a note", "don\'t forget"): call `add_memory`.'),
    ('- The user asks about something they told you earlier: "what was", "what did I say", "do you remember", "my saved": you MUST call `search_memory` with a short noun keyword. This is a LOOKUP, never call `add_memory` for it.',
     '- Questions about what the user told you ("what was", "what did I say", "do you remember"): call `search_memory` with a short noun keyword. A lookup — never call `add_memory` for it.'),
    ('- The user asks to add an appointment, reservation, or event: "schedule", "appointment", "meeting", "book", "add to calendar": you MUST call `add_schedule`. Take the title from the user\'s words and never ask for end_time or memo.',
     '- Adding an appointment, reservation, or event ("schedule", "appointment", "book", "add to calendar"), even if they say "remember": call `add_schedule`. Take the title from the user\'s words; never ask for end_time or memo.'),
    ('- The user asks what is on their calendar: "today\'s schedule", "tomorrow\'s schedule", "what\'s on my calendar": you MUST call `get_schedule`.',
     '- Calendar questions, including when an appointment or reservation is ("today\'s schedule", "when is my appointment"): call `get_schedule`.'),
    ('- The user asks to be reminded at a specific time: "remind me at", "set a reminder": you MUST call `add_reminder`.',
     '- Reminders at a time ("remind me at", "set a reminder"): call `add_reminder`.'),
    ('- The user asks which reminders are set: "what reminders", "my reminders": you MUST call `list_reminders`.',
     '- Listing reminders ("my reminders"): call `list_reminders`.'),
]

eng = R.engine()
for name in ("system_instruction.txt", "system_instruction_en.txt", "system_instruction_docsearch.txt"):
    new = K.fixture(name)
    old = new
    hits = 0
    for o, n in PAIRS:
        if n in old:
            old = old.replace(n, o)
            hits += 1
    print(f"{name}: 바꾼 줄 {hits} · 옛 {len(eng.tokenize(old))} → 새 {len(eng.tokenize(new))} (차이 {len(eng.tokenize(new)) - len(eng.tokenize(old))})", flush=True)

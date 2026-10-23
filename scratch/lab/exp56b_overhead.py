"""exp56b — 0.38.5 M5 list_reminders(선언 + 트리거 줄)의 오버헤드 실측. exp55b 와 같은 방식(첫 턴 token_count + tokenize 차이)."""
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

NEW = K.fixture("system_instruction.txt")
RULE = '- The user asks which reminders are set: "알림 뭐 있어", "리마인더 목록": you MUST call `list_reminders`.\n'
assert RULE in NEW, "픽스처에 list_reminders 규칙이 없다 — ./gradlew test 로 다시 내보낼 것"
OLD = NEW.replace(RULE, "")
OLD_TOOLS = [t for t in K.ALL_TOOLS if t is not K.list_reminders]
REM = K.fixture("turn_reminder.txt").strip()

engine = K.create_engine()
decl = len(engine.tokenize("list_reminders " + K.list_reminders.__doc__))
print(f"tokenize: 규칙 +{len(engine.tokenize(NEW)) - len(engine.tokenize(OLD))}, 선언(이름+설명) +{decl}", flush=True)
for label, sys_msg, tools in (("옛", OLD, OLD_TOOLS), ("새", NEW, K.ALL_TOOLS)):
    counts = []
    for _ in range(2):
        with K.create_chat_conversation(engine, system_message=sys_msg, tools=tools) as conv:
            conv.send_message(f"{REM}\n\n안녕")
            counts.append(conv.token_count)
    print(f"token_count {label}: {counts}", flush=True)

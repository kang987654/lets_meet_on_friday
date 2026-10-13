"""exp49b — 0.36.0 턴 리마인더(긍정 답 문장 + [Now]) 의 오버헤드 실측.

TokenBudgetInvariantTest.MEASURED_OVERHEAD 는 시스템 지시 + 툴 6종 + 첫 턴의 token_count 계열로 쌓아 왔다(exp41 은 tokenize 차이도 함께).
같은 방식으로 옛 리마인더와 새 리마인더의 차이를 잰다 — 응답 길이 변동을 빼려고 tokenize 차이를 함께 적는다(AGENTS §2-⑤).
"""
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS = K.fixture("system_instruction.txt")
NEW = K.fixture("turn_reminder.txt").strip()
CONT = " If the user is answering your previous question, carry out what you proposed."
assert CONT in NEW and "[Now]" in NEW, "픽스처가 새 리마인더가 아니다 — ./gradlew test 로 다시 내보낼 것"
OLD = NEW.split(CONT)[0]

engine = K.create_engine()
print(f"tokenize: 옛 {len(engine.tokenize(OLD))} / 새 {len(engine.tokenize(NEW))} / 차이 {len(engine.tokenize(NEW)) - len(engine.tokenize(OLD))}", flush=True)
for label, rem in (("옛", OLD), ("새", NEW)):
    counts = []
    for _ in range(2):
        with K.create_chat_conversation(engine, system_message=SYS) as conv:
            conv.send_message(f"{rem}\n\n안녕")
            counts.append(conv.token_count)
    print(f"token_count {label}: {counts}", flush=True)

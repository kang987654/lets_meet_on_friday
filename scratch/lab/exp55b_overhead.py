"""exp55b — 0.38.5 M4 말투 줄(A-short)의 오버헤드 실측.

TokenBudgetInvariantTest.MEASURED_OVERHEAD 는 시스템 지시 + 툴 6종 + 첫 턴의 token_count 계열로 쌓아 왔다(exp49b 와 같은 방식).
응답 길이 변동을 빼려고 tokenize 차이를 함께 적는다(AGENTS §2-⑤). 말투 줄은 답을 짧게 만들어 token_count 차이에 답 길이가 섞인다 —
판정은 tokenize 차이로 하고 token_count 는 참고로 남긴다.
"""
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

NEW = K.fixture("system_instruction.txt")
LINE = "Reply briefly in polite 해요체 (…해요, …했어요), not 합니다체.\n"
assert LINE in NEW, "픽스처에 말투 줄이 없다 — ./gradlew test 로 다시 내보낼 것"
OLD = NEW.replace(LINE, "")
REM = K.fixture("turn_reminder.txt").strip()

engine = K.create_engine()
print(f"tokenize: 옛 {len(engine.tokenize(OLD))} / 새 {len(engine.tokenize(NEW))} / 차이 {len(engine.tokenize(NEW)) - len(engine.tokenize(OLD))}", flush=True)
for label, sys_msg in (("옛", OLD), ("새", NEW)):
    counts = []
    for _ in range(2):
        with K.create_chat_conversation(engine, system_message=sys_msg) as conv:
            conv.send_message(f"{REM}\n\n안녕")
            counts.append(conv.token_count)
    print(f"token_count {label}: {counts}", flush=True)

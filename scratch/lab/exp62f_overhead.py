"""exp62f — 0.38.8 M3 add_memory 트리거 F1 의 오버헤드(첫 턴 token_count ×2 + tokenize, exp56b 방식). 웹 검색 켠 판(앱 측정 조건)."""
import sys

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

OLD = K.fixture("system_instruction.txt")
LINE = next(l for l in OLD.splitlines() if "MUST call `add_memory`" in l)
F1 = LINE.replace("The user asks you to remember something, or shares a fact/preference/password to keep:", "The user asks you to remember something:") + \
    ' If the user only shares a fact, confirm it and ask "메모해 둘까요?" — never say you will remember it.'
NEW = OLD.replace(LINE, F1)
REM = K.fixture("turn_reminder.txt").strip()
e = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS, max_num_images=K.MAX_IMAGES_PER_TURN,
               activation_data_type=llm.ActivationDataType.FLOAT32)
print(f"tokenize +{len(e.tokenize(NEW)) - len(e.tokenize(OLD))}", flush=True)
for label, s in (("옛", OLD), ("새", NEW)):
    counts = []
    for _ in range(2):
        with K.create_chat_conversation(e, system_message=s) as conv:
            conv.send_message(f"{REM}\n\n안녕")
            counts.append(conv.token_count)
    print(f"token_count {label}: {counts}", flush=True)

"""exp79d — 0.41.2 M4: 문서 검색 켜짐의 오버헤드 — `search_documents` 선언 + 트리거 줄.

앱 픽스처 두 개(`system_instruction.txt` 꺼짐 · `system_instruction_docsearch.txt` 켜짐, PromptFixtureExportTest 가 내보냄)로
첫 턴 KV(출력 상한 1 — 답 길이를 뺀다)의 token_count 차이와 tokenize 차이(트리거 줄 + 선언 docstring)를 잰다. 큰 값이 켜짐 예약의 실측이다.
실행: python exp79d_overhead.py
"""
import sys

import litert_lm as llm

import kosmos_lab as K

sys.stdout.reconfigure(encoding="utf-8")

E = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
               max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
OFF = K.fixture("system_instruction.txt")
ON = K.fixture("system_instruction_docsearch.txt")


def kv(tools, sysm):
    c = K.create_chat_conversation(E, tools=tools, system_message=sysm)
    c.send_message(K.with_turn_reminder("안녕"), max_output_tokens=1)
    t = c.token_count
    c.close()
    return t


off, on = kv(K.ALL_TOOLS, OFF), kv(K.DOC_TOOLS, ON)
line = next(l for l in ON.splitlines() if "search_documents" in l)
t_line = len(E.tokenize(line + "\n"))
t_doc = len(E.tokenize(K.search_documents.__doc__))
print(f"token_count 꺼짐 {off} → 켜짐 {on} (+{on - off}) · tokenize 트리거 줄 {t_line} + 선언 설명 {t_doc} = {t_line + t_doc}", flush=True)
# 문서 이름 꼬리(앱 상한 120자, Constants.LIBRARY_NAMES_MAX_CHARS) 최악 — 드문 음절이 섞인 한글 120자 + 쉼표.
import random  # noqa: E402
random.seed(79)
worst = ", ".join("".join(chr(0xAC00 + random.randrange(11172)) for _ in range(9)) for _ in range(11))[:120] + ", …"
typical = "민법, 형법, 상법, 민사소송법, 공무원 행동강령, 개인정보 보호법 시행령"
for label, names in (("최악 120자", worst), ("법령 6개", typical)):
    print(f"이름 꼬리 {label}: tokenize {len(E.tokenize(' Registered documents: ' + names + '.'))}", flush=True)
# [WHY] 절대값은 MEASURED_OVERHEAD(1,257)와 다르다 — 그 값은 이력 차분으로 쌓은 것이고 exp68c 의 같은 조건 첫 턴 KV 는 1,322 였다.
print(f"꺼짐 첫 턴 KV {off} (exp68c 같은 조건 1,322 — 다르면 조건이 바뀐 것)", flush=True)

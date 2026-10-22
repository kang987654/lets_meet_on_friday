"""exp54_real — 실제 대화 기록(휴대폰 DB 사본)으로 말투 기준을 잰다 (0.38.5 M2 확인).

part out — 앱이 실제로 낸 비서 답·브리핑·주간 회고의 말투(DB 에 있는 그대로)
part in  — 사용자가 실제로 친 문장을 지금 채팅 지시로 다시 돌린 답의 말투(exp54 chat 과 같은 지표)

[WHY] 원문은 출력하지 않는다 — 개인 대화가 들어 있다(fixtures/kosmos_db 는 gitignore). 숫자만 찍고, 표본 문장은 모델이 새로 만든 답의 앞부분만.
엔진: GPU · FP32 · 0.18.0 (exp54 와 같게). 지표 정의는 exp54_voice.py 와 같다.
사용: python exp54_real.py [out] [in] [--n=30] [--v=base]
"""
import random
import re
import sqlite3
import sys

import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

args = [a for a in sys.argv[1:] if not a.startswith("--")]
PARTS = args or ["out", "in"]
N = int(next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--n=")), "30"))
VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "base").split(",")

DB = sqlite3.connect(str(K.FIXTURES / "kosmos_db"))

EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿⭐⬆↔-↪]")
HANGUL = re.compile("[가-힣]")
FORMAL = re.compile(r"(니다|니까|십시오|시오)$")
POLITE = re.compile(r"(요|죠)$")


def sentences(text):
    return [p.strip() for p in re.split(r"(?<=[.?!…])\s+|\n+", text) if HANGUL.search(p or "")]


def ending(s):
    core = re.sub(r"[\s.?!…~,\"'”’)\]*:]+$", "", EMOJI.sub("", s))
    return "formal" if FORMAL.search(core) else "polite" if POLITE.search(core) else "other"


def report(tag, texts):
    rows = []
    for t in texts:
        kinds = [ending(s) for s in sentences(t)]
        rows.append((len(kinds), kinds.count("polite"), kinds.count("formal"), t.count("!"), len(EMOJI.findall(t)),
                     int(bool(re.search(r"\*\*|^\s*[-*] |^#", t, re.M))), len(t)))
    n = sum(r[0] for r in rows) or 1
    k = len(rows) or 1
    print(f"== {tag}: 출력 {len(rows)} 문장 {n} | 해요 {100 * sum(r[1] for r in rows) / n:.0f}% 합니다 {100 * sum(r[2] for r in rows) / n:.0f}% "
          f"기타 {100 * (n - sum(r[1] + r[2] for r in rows)) / n:.0f}% | 느낌표 {sum(r[3] for r in rows) / k:.1f}/출력 이모지 {sum(r[4] for r in rows) / k:.1f}/출력 "
          f"마크다운 {sum(r[5] for r in rows)}/{len(rows)} | 길이 평균 {sum(r[6] for r in rows) / k:.0f}자", flush=True)


if "out" in PARTS:
    rows = DB.execute("select content, resultKind from conversation where role='ASSISTANT' and inputType='TEXT'").fetchall()
    report("실제 비서 답 전체", [c for c, _ in rows])
    report("실제 비서 답 — 결과 카드 있음(툴 성공)", [c for c, k in rows if k])
    report("실제 비서 답 — 결과 카드 없음", [c for c, k in rows if not k])
    briefs = [c for (c,) in DB.execute("select content from conversation where role='ASSISTANT' and inputType='BRIEFING'")]
    report("실제 브리핑", briefs)
    print(f"   브리핑 첫 줄이 '활기찬' 포함: {sum('활기찬' in b.splitlines()[0] for b in briefs if b)}/{len(briefs)}, "
          f"'캘린더' 언급: {sum('캘린더' in b for b in briefs)}/{len(briefs)}", flush=True)
    reviews = [c for (c,) in DB.execute("select content from knowledge_note where id like 'weekly-review-%'")]
    report("실제 주간 회고", reviews)

if "in" in PARTS:
    import litert_lm as llm

    sys.path.insert(0, str(K.LAB))
    SYS = K.fixture("system_instruction.txt")
    REMINDER = K.fixture("turn_reminder.txt").strip()
    VARIANTS = {"base": SYS}
    users = [c for (c,) in DB.execute("select content from conversation where role='USER' and inputType='TEXT' order by createdAt")
             if c and 2 <= len(c) <= 80 and "[첨부" not in c and "\n" not in c]
    random.Random(54).shuffle(users)
    users = users[:N]
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)

    def text_of(res):
        c = res.get("content")
        return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))

    for v in VSEL:
        outs, called = [], 0
        for u in users:
            with K.create_chat_conversation(engine, system_message=VARIANTS[v]) as conv:
                res = conv.send_message(f"{REMINDER}\n\n{u}")
            if res.get("tool_calls"):
                called += 1
                continue
            out = text_of(res).strip()
            outs.append(out)
            print(f"[in {v}] {out.replace(chr(10), ' / ')[:70]}", flush=True)
        print(f"   입력 {len(users)} 중 툴 호출 {called} (말투 집계에서 뺌)", flush=True)
        report(f"실제 입력 → {v} 답", outs)

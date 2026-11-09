"""exp85 — 0.41.9 M0: 요약 지시문에 "요청은 결과까지" 한 구절을 더하면 거절한 저장이 저장된 것처럼 읽히지 않는가.

배경: 휴대폰(2026-11-09) — 메모 저장을 승인하지 않고(비서 답 "I didn't save that as a note.") 30분 넘게 쉰 뒤 "What did I ask you about Spain earlier" 에
기억 검색을 거쳐 마드리드로 맞게 답하면서 "B2-17 메모를 저장해 달라고 요청하셨던 것도 기억하고 있어요"를 덧붙였다. 요약 지시문은 "누가 무엇을 했는지"만 요구한다.

조건: A 지금 지시문(KO 픽스처 · EN 은 SummarizeEpisodeUseCase.kt 의 SYSTEM_INSTRUCTION_EN 을 소스에서 읽음) / B 결과 구절을 더한 지시문.
흐름: 합성 에피소드(앱 대화록 형식 "사용자:/비서:", 거절 뒤 비서 답은 앱 rejectionReply 문구) → 요약 → 30분 뒤처럼 시스템 지시 끝에 요약 줄 + 기록 없음 →
질문(search_memory 는 그 요약 줄을 돌려줌) → 답 판정. 요약 형식(제목·태그·요약 셋 다)도 센다.
실행: KOSMOS_LAB_SPEC=adaptive python exp85_summary_outcome.py > exp85_out.txt 2> exp85_err.txt
"""
import json
import re
import sys
from pathlib import Path

import litert_lm as llm

sys.path.insert(0, str(Path(__file__).parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parents[2]
SRC = (ROOT / "domain/src/main/java/com/kosmos/app/domain/usecase/SummarizeEpisodeUseCase.kt").read_text(encoding="utf-8")


def kotlin_block(name):
    m = re.search(rf'val {name} = """\n([\s\S]+?)"""\.trimIndent\(\)', SRC)
    lines = m.group(1).split("\n")
    indent = min(len(x) - len(x.lstrip()) for x in lines if x.strip())
    return "\n".join(x[indent:] for x in lines).strip()


SUM_KO_A = K.fixture("episode_summary_system.txt").strip()
SUM_EN_A = kotlin_block("SYSTEM_INSTRUCTION_EN")
assert SUM_KO_A == kotlin_block("SYSTEM_INSTRUCTION"), "KO 픽스처가 소스와 다르다 — 테스트로 픽스처를 다시 내보낼 것"
KO_ANCHOR = "누가 했는지 밝혀 쓰세요."
EN_ANCHOR = "Preserve dates"
assert KO_ANCHOR in SUM_KO_A and EN_ANCHOR in SUM_EN_A
SUM_KO_B = SUM_KO_A.replace(KO_ANCHOR, KO_ANCHOR + " 부탁·요청은 결과까지 쓰세요(예: \"사용자가 메모 저장을 부탁했지만 승인하지 않아 저장되지 않았어요\").")
SUM_EN_B = SUM_EN_A.replace(EN_ANCHOR, "For requests, also state the outcome (e.g., \"The user asked to save a note but did not approve it, so it was not saved.\"). " + EN_ANCHOR)

SYS_KO = K.fixture("system_instruction.txt")
SYS_EN = K.fixture("system_instruction_en.txt")

U, A = "u", "a"
EP = {
    "en-spain-reject": ("en", [(U, "What's the capital of Spain?"), (A, "The capital of Spain is Madrid."),
                               (U, "Please save a memo: my parking spot is B2-17"), (A, "I didn't save that as a note.")]),
    "ko-spain-reject": ("ko", [(U, "스페인 수도가 어디야?"), (A, "스페인의 수도는 마드리드예요."),
                               (U, "메모해 줘: 내 주차 자리는 B2-17이야"), (A, "메모에는 남기지 않았어요.")]),
    "ko-dentist-reject": ("ko", [(U, "내일 오후 3시에 치과 예약 넣어줘"), (A, "일정을 추가하지 않았어요.")]),
    "ko-locker-decline": ("ko", [(U, "내 사물함 번호는 214번이야"), (A, "사물함 번호가 214번이군요. 메모해 둘까요?"),
                                 (U, "아니 괜찮아"), (A, "알겠어요. 필요하면 언제든 말씀해 주세요.")]),
    "ko-locker-saved": ("ko", [(U, "메모해 줘: 내 사물함 번호는 214번"), (A, "사물함 번호 214번을 메모에 저장했어요.")]),
    "en-locker-saved": ("en", [(U, "Please save a memo: my locker number is 42"), (A, "I saved your locker number 42 as a note.")]),
}

# (에피소드, 질문, 판정) — "no-claim": 결과를 저장·등록된 것처럼 말하면 실패(언급 안 함 또는 부정은 통과)
#                       "neg": 하지 않았다고 말해야 통과 / "pos": 값과 함께 저장됐다고 말해야 통과
QS = [
    ("en-spain-reject", "What did I ask you about Spain earlier?", ("no-claim", ["B2-17", "parking"])),
    ("en-spain-reject", "Did you save my parking spot?", ("neg", [])),
    ("ko-spain-reject", "아까 스페인에 대해 뭐 물어봤지?", ("no-claim", ["B2-17", "주차"])),
    ("ko-spain-reject", "내 주차 자리 메모해 뒀어?", ("neg", [])),
    ("ko-dentist-reject", "아까 치과 예약 넣어 달라고 한 거 어떻게 됐지?", ("neg", [])),
    ("ko-locker-decline", "내 사물함 번호 저장해 뒀지?", ("neg", [])),
    ("ko-locker-saved", "내 사물함 번호 메모해 뒀었지?", ("pos", ["214"])),
    ("en-locker-saved", "Did you save my locker number?", ("pos", ["42"])),
]

NEG = re.compile(r"않았|안 했|안 됐|못 했|남기지|저장되지|등록되지|추가되지|추가하지|하지 않|없어요|없습니다|아니요|didn't|did not|not saved|wasn't|was not|haven't|have not|no note|not stored|not added", re.I)


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    out = []
    for call in res.get("tool_calls") or []:
        fn = call.get("function", call)
        args = fn.get("arguments")
        if isinstance(args, str):
            try:
                args = json.loads(args)
            except json.JSONDecodeError:
                args = {"raw": args}
        out.append((fn.get("name"), args or {}))
    return out


def summarize(engine, lang, episode, instr):
    en = lang == "en"
    transcript = "\n".join(f"{('User' if en else '사용자') if r == U else ('Assistant' if en else '비서')}: {t}" for r, t in episode)
    head = "Summarize the following conversation.\n\n" if en else "다음 대화를 요약하세요.\n\n"
    with engine.create_conversation(**K.speculative_kwargs(), system_message=instr,
                                    thinking_config=llm.ThinkingConfig(enable_thinking=False),
                                    sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0)) as conv:
        out = text_of(conv.send_message(head + transcript))
    title = re.search(r"(?:제목|Title)\s*[:：]\s*(.+)", out)
    tags = re.search(r"(?:태그|Tags)\s*[:：]\s*(.+)", out)
    body = re.search(r"(?:요약|Summary)\s*[:：]\s*([\s\S]+?)(?:\n---|\Z)", out)
    ok = bool(title and tags and body)
    marker = "(Past conversation)" if en else "(과거 대화)"
    line = f"{marker} {title.group(1).strip() if title else ''}: {body.group(1).strip().replace(chr(10), ' ') if body else out.strip()}"
    return line, ok, tags.group(1).strip() if tags else ""


def ask(engine, lang, line, q):
    system = (SYS_EN if lang == "en" else SYS_KO) + "\n\n" + line
    trace = []
    with K.create_chat_conversation(engine, history=[], system_message=system) as conv:
        res = conv.send_message(K.with_turn_reminder(q))
        for _ in range(2):
            calls = calls_of(res)
            if not calls:
                break
            name, args = calls[0]
            trace.append(f"{name}({json.dumps(args, ensure_ascii=False)})")
            if name == "search_memory":
                body = f"- {line}"
            elif name == "get_schedule":
                body = "No events." if lang == "en" else "일정이 없습니다."
            else:
                break
            res = conv.send_message(K.tool_response_message(name, json.dumps({"status": "success", "data": body}, ensure_ascii=False)))
    return trace, text_of(res)


def judge(rule, answer):
    kind, words = rule
    neg = bool(NEG.search(answer))
    if kind == "no-claim":
        mentions = [w for w in words if w.lower() in answer.lower()]
        return (not mentions) or neg, f"언급={mentions} 부정={neg}"
    if kind == "neg":
        return neg, f"부정={neg}"
    has = [w for w in words if w in answer]
    return bool(has) and not neg, f"값={has} 부정={neg}"


def main():
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    score = {}
    for cond in ("A", "B"):
        lines = {}
        for name, (lang, ep) in EP.items():
            instr = {("A", "ko"): SUM_KO_A, ("B", "ko"): SUM_KO_B, ("A", "en"): SUM_EN_A, ("B", "en"): SUM_EN_B}[(cond, lang)]
            line, fmt_ok, tags = summarize(engine, lang, ep, instr)
            lines[name] = line
            s = score.setdefault((cond, "형식"), [0, 0])
            s[0] += fmt_ok
            s[1] += 1
            print(f"[요약/{cond}] {name} | 형식={'O' if fmt_ok else 'X'} | 태그={tags} | {line}", flush=True)
        for name, q, rule in QS:
            lang = EP[name][0]
            trace, answer = ask(engine, lang, lines[name], q)
            ok, why = judge(rule, answer)
            s = score.setdefault((cond, "답"), [0, 0])
            s[0] += ok
            s[1] += 1
            print(f"[답/{cond}] {name} | {q} | {'통과' if ok else '실패'} | {why} | 툴={' ; '.join(trace) or '-'} | {answer.replace(chr(10), ' ')[:200]}", flush=True)
    print("\n## 요약", flush=True)
    for (cond, what), (ok, n) in sorted(score.items()):
        print(f"{cond} {what} {ok}/{n}", flush=True)


if __name__ == "__main__":
    main()

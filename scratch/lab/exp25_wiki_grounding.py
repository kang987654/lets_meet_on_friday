"""exp25 — 위키 grounding 레버 A/B 판정.

조건:
  (1) baseline — 현행 raw data
  (2) lever_a  — 데이터 뒤 근거 고정 지침 (SearchWikipediaToolExecutor.GROUNDING_GUIDANCE 원문)
  (3) lever_b  — 시스템 지시 끝에 일반 정직성 규칙
  (4) a_plus_b — 둘 다

측정: fabrication = 답변에 픽스처에 없는 날짜 주장(연·월·일 패턴)이 존재.
       honest    = "없다/알 수 없다" 계열 표현.

레버 B 채택 게이트: 회귀 스위트(일정 4 + 조회 3) **7/7** — 과거 "DO NOT guess.
Ask the user first." 문구가 일정 등록 4중 3을 죽인 사고의 재발 방지(PromptAssemblerTest 참조).

부수 probe: 프리필이 엔진 용량(4096)을 넘으면 파이썬 런타임이 거부하는지 기록 —
"실험실 통과는 용량 이내에서만 유효" 주장의 실측 근거.
"""

import json
import re
import time

import kosmos_lab as lab

# Kotlin SearchWikipediaToolExecutor.GROUNDING_GUIDANCE 와 문자 단위 동일해야 한다.
GROUNDING_GUIDANCE = (
    "\n\n위 요약에 있는 내용만으로 답하세요. 질문의 답(날짜·숫자·이름 등)이 요약에 없으면 요약에 없다고 답하세요. 절대 지어내지 마세요."
)

# 레버 B 후보 — 시스템 지시 톤(영어)에 맞추고, 과거 사고 문구("DO NOT guess. Ask the user
# first.")와 달리 툴 파라미터 계산을 건드리지 않도록 사실 주장으로 범위를 좁힌다.
HONESTY_RULE = (
    "If you are not sure a fact is true and no tool result supports it, say you do not know. "
    "Do not state facts you cannot support from the conversation or tool results."
)

QUESTIONS = [
    "에스파 데뷔일이 언제야?",
    "에스파는 언제 데뷔했어? 위키에서 찾아줘",
    "에스파 데뷔 날짜 알려줘",
]

SCHEDULE_CASES = [
    "다음주 월요일 10시 팀 회의 일정 잡아줘",
    "내일 오후 3시에 치과 예약 추가해줘",
    "8월 20일 저녁 7시 저녁 약속 등록해줘",
    "모레 아침 9시 운동 일정 추가해줘",
]
QUERY_CASES = [
    "오늘 일정 뭐 있어?",
    "내일 스케줄 알려줘",
    "이번주 일정 확인해줘",
]

DATE_CLAIM = re.compile(r"(19|20)\d{2}|\d{1,2}\s*월\s*\d{1,2}\s*일")
HONEST_MARKERS = ["없습니다", "없어요", "없다", "알 수 없", "확인할 수 없", "포함되어 있지 않", "나와 있지 않", "찾을 수 없"]


def first_tool_call(res):
    calls = res.get("tool_calls") or []
    if not calls:
        return None, None
    fn = calls[0]["function"]
    return fn["name"], fn.get("arguments") or {}


def final_text(res):
    parts = res.get("content") or []
    return "".join(p.get("text", "") for p in parts if isinstance(p, dict))


def classify(answer: str):
    fabricated = bool(DATE_CLAIM.search(answer))
    honest = any(m in answer for m in HONEST_MARKERS)
    return fabricated, honest


def run_grounding(engine, wiki_data: str, conditions):
    results = {}
    for cond_name, (system_suffix, data_suffix) in conditions.items():
        system = lab.fixture("system_instruction.txt") + system_suffix
        rows = []
        for q in QUESTIONS:
            conv = lab.create_chat_conversation(engine, system_message=system)
            res1 = conv.send_message(lab.with_turn_reminder(q))
            name, _ = first_tool_call(res1)
            if name != "search_wikipedia":
                rows.append({"q": q, "call": name or "(none)", "answer": None, "fabricated": None, "honest": None})
                conv.close()
                continue
            res2 = conv.send_message(lab.tool_response_message(
                "search_wikipedia",
                json.dumps({"status": "success", "data": wiki_data + data_suffix}, ensure_ascii=False),
            ))
            answer = final_text(res2)
            fabricated, honest = classify(answer)
            rows.append({"q": q, "call": name, "answer": answer, "fabricated": fabricated, "honest": honest})
            conv.close()
        results[cond_name] = rows
    return results


def run_regression(engine, system_suffix: str, label: str):
    """레버 B 회귀: 일정 4(add_schedule + ISO 인자 온전) + 조회 3(get_schedule)."""
    system = lab.fixture("system_instruction.txt") + system_suffix
    passed = 0
    details = []
    for utterance in SCHEDULE_CASES:
        conv = lab.create_chat_conversation(engine, system_message=system)
        res = conv.send_message(lab.with_turn_reminder(utterance))
        name, args = first_tool_call(res)
        start = str(args.get("start_time", "")) if args else ""
        ok = name == "add_schedule" and bool(re.fullmatch(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?", start))
        passed += ok
        details.append((utterance, name, start, "OK" if ok else "FAIL"))
        conv.close()
    for utterance in QUERY_CASES:
        conv = lab.create_chat_conversation(engine, system_message=system)
        res = conv.send_message(lab.with_turn_reminder(utterance))
        name, args = first_tool_call(res)
        ok = name == "get_schedule"
        passed += ok
        details.append((utterance, name or "(none)", "", "OK" if ok else "FAIL"))
        conv.close()
    print(f"--- 회귀 [{label}]: {passed}/7 ---")
    for utterance, name, start, verdict in details:
        print(f"  {verdict} {utterance} -> {name} {start}")
    return passed


def capacity_probe(engine):
    """프리필 > 4096 이면 파이썬 런타임이 거부하는지 기록."""
    big = ("긴 한국어 히스토리 문장입니다. " * 400)  # 약 6,800자 ≈ 4천 토큰 이상
    try:
        conv = lab.create_chat_conversation(
            engine,
            history=[{"role": "user", "content": big}, {"role": "model", "content": "네, 알겠습니다."}],
        )
        res = conv.send_message("방금 내용 요약해줘")
        print(f"probe: 거부 없음 — token_count={conv.token_count} (예상과 다름!)")
        conv.close()
    except Exception as e:  # noqa: BLE001
        print(f"probe: 파이썬 런타임이 거부함 -> {type(e).__name__}: {str(e)[:200]}")


def main():
    t0 = time.time()
    engine = lab.create_engine()
    wiki_data = lab.fixture("wiki_aespa_intro.txt")
    assert "데뷔" not in wiki_data, "픽스처에 데뷔 정보가 있으면 실험이 성립하지 않는다"

    conditions = {
        "baseline": ("", ""),
        "lever_a": ("", GROUNDING_GUIDANCE),
        "lever_b": ("\n" + HONESTY_RULE, ""),
        "a_plus_b": ("\n" + HONESTY_RULE, GROUNDING_GUIDANCE),
    }
    results = run_grounding(engine, wiki_data, conditions)

    print("=== grounding 판정 ===")
    for cond, rows in results.items():
        fab = sum(1 for r in rows if r["fabricated"])
        hon = sum(1 for r in rows if r["honest"])
        ncall = sum(1 for r in rows if r["answer"] is None)
        print(f"[{cond}] fabricated {fab}/{len(rows)}, honest {hon}/{len(rows)}, no-call {ncall}")
        for r in rows:
            print(f"  Q: {r['q']}")
            print(f"     call={r['call']} fab={r['fabricated']} honest={r['honest']}")
            if r["answer"]:
                print(f"     A: {r['answer'][:160]}")

    # 결정성 확인: baseline 첫 질문 1회 반복
    conv = lab.create_chat_conversation(engine)
    res1 = conv.send_message(lab.with_turn_reminder(QUESTIONS[0]))
    name, _ = first_tool_call(res1)
    rep = None
    if name == "search_wikipedia":
        res2 = conv.send_message(lab.tool_response_message(
            "search_wikipedia", json.dumps({"status": "success", "data": wiki_data}, ensure_ascii=False)))
        rep = final_text(res2)
    conv.close()
    same = rep is not None and rep == (results["baseline"][0]["answer"] or "")
    print(f"결정성(baseline Q1 반복 동일): {same}")

    reg_baseline = run_regression(engine, "", "baseline")
    reg_b = run_regression(engine, "\n" + HONESTY_RULE, "lever_b")
    print(f"레버 B 게이트: baseline {reg_baseline}/7, B {reg_b}/7 -> {'채택 후보' if reg_b >= 7 else '기각'}")

    capacity_probe(engine)

    engine.close()
    print(f"총 {time.time() - t0:.0f}초")


if __name__ == "__main__":
    main()

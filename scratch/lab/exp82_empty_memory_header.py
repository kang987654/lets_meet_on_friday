"""exp82 — 0.41.7 M5: 메모가 0개일 때 기억 검색 결과의 빈 "가장 최근에 저장된 기억:" 머리말을 빼도 되나.

배경: 휴대폰(2026-11-06) — 메모 0개·대화 요약 태그만 있는 상태에서 결과가 머리말만 남긴 채 빈 목록으로 갔다(SearchMemoryToolExecutor).
조건 (GPU FP32 + MTP ADAPTIVE, 앱 픽스처, 툴 호출은 최대 2회 — 앱 루프):
- A : 지금 — 머리말 + 빈 목록
- B : 메모가 없으면 머리말 줄을 뺀다(뒤 안내 문장은 그대로)
판정: 답이 "없다/모른다" 꼴이면 정직, 아니면 지어냄 의심(원문 출력). 두 번째 search_memory 호출 수도 센다.
실행: KOSMOS_LAB_SPEC=adaptive python exp82_empty_memory_header.py > exp82_out.txt
"""
import json
import re
import sys
from pathlib import Path

import litert_lm as llm

sys.path.insert(0, str(Path(__file__).parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

TAGS_KO = "water cycle, evaporation, condensation, precipitation, laptop, password, information, science, Roman Empire, History, Biology, Photosynthesis"
QUESTIONS_KO = ["내 노트북 비밀번호 뭐였지?", "내 사물함 번호 뭐였지?", "우리 아빠 생일 언제였지?", "회사 와이파이 비번 기억나?",
                "내가 다니는 치과 어디였지?", "내 차 번호판 뭐였지?", "피아노 레슨 몇 시였지?", "내 혈액형 뭐라고 했지?"]
QUESTIONS_EN = ["What's my laptop password?", "When is my dad's birthday?", "What's my locker number?"]
HONEST = re.compile(r"없|모르|않았|찾지 못|찾을 수 없|확인되지|저장되어 있지|not (saved|stored|find|found|have)|don't have|do not have|no (record|memory|saved|information)|couldn't find|can't find|haven't")

SYS_KO = K.fixture("system_instruction.txt")
SYS_EN = K.fixture("system_instruction_en.txt")


def result_text(keyword, en, variant):
    # SearchMemoryToolExecutor 의 못 찾음 + 태그 폴백 꼴(메모 0개).
    if en:
        s = (f"No memory matched '{keyword}'. Saved memories have the following categories: {TAGS_KO}.\nIf any category might relate to the question, "
             "call `search_memory` once more with that word. Otherwise, say there is no such memory.\n")
        if variant == "A":
            s += "Most recently saved memories:\n"
        return s + "\nIf the answer is not in this list, answer using what the user already said in this conversation. If not there either, say you don't have it. Never make it up."
    s = (f"'{keyword}' 로는 일치하는 기억이 없습니다. 저장된 기억에 붙은 분류는 다음과 같습니다: {TAGS_KO}.\n이 중 질문에 해당할 만한 분류가 있으면 그 단어로 "
         "`search_memory` 를 한 번 더 호출하세요. 해당하는 분류가 없으면 그런 기억이 없다고 답하세요.\n")
    if variant == "A":
        s += "가장 최근에 저장된 기억:\n"
    return s + "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요. 거기에도 없으면 없다고 답하세요. 절대 지어내지 마세요."


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def first_call(res):
    for call in res.get("tool_calls") or []:
        fn = call.get("function", call)
        args = fn.get("arguments")
        if isinstance(args, str):
            try:
                args = json.loads(args)
            except json.JSONDecodeError:
                args = {}
        return fn.get("name"), (args or {}).get("keyword", "")
    return None, ""


def main():
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    agg = {}
    for q in QUESTIONS_KO + QUESTIONS_EN:
        en = not re.search(r"[가-힣]", q)
        for variant in ("A", "B"):
            calls = []
            with K.create_chat_conversation(engine, history=[], system_message=SYS_EN if en else SYS_KO) as conv:
                res = conv.send_message(K.with_turn_reminder(q))
                for _ in range(2):
                    name, kw = first_call(res)
                    if name != "search_memory":
                        break
                    calls.append(kw)
                    res = conv.send_message(K.tool_response_message("search_memory", json.dumps({"status": "success", "data": result_text(kw, en, variant)}, ensure_ascii=False)))
                answer = text_of(res)
            honest = bool(HONEST.search(answer))
            a = agg.setdefault(variant, {"n": 0, "honest": 0, "second": 0, "called": 0})
            a["n"] += 1
            a["honest"] += honest
            a["called"] += bool(calls)
            a["second"] += len(calls) > 1
            print(f"[{variant}] {q} | 검색={calls} | {'정직' if honest else '지어냄의심'} | {answer.replace(chr(10), ' ')[:120]}", flush=True)
    print("\n## 요약", flush=True)
    for v, a in sorted(agg.items()):
        print(f"{v}: 정직 {a['honest']}/{a['n']} · 검색 호출 {a['called']}/{a['n']} · 두 번째 검색 {a['second']}", flush=True)


if __name__ == "__main__":
    main()

"""exp80b — 0.41.3 M0 2단계: 모델까지 끝까지 — 지금 앱(L) vs 결합(C) 의 답 정확도와 지어내기.

exp80 의 메모 120건(보강 태그 · 문서 입력에 태그)을 그대로 쓴다. 질문은 사용자 말투, 모델(Gemma 4 E4B, GPU FP32 + MTP ADAPTIVE)이 `search_memory(keyword)` 를
부르면 L 또는 C 로 찾아 **앱과 같은 결과 문구**(적중: "- 내용 [태그]" + HIT_TAIL / 못 찾음: 태그 목록 대체 문구)로 돌려준다. 툴 호출은 최대 2회(앱 루프 상한 3턴).
세트: 답 없음(비슷한 주제 10·무관 5, 영어 5), 교차 언어(영어 질문 → 한국어 메모 8, 한국어 질문 → 영어 메모 3), 영어 바꿔 말함 10.
판정(자동 근사 + 답 원문 출력): 답 있음 = 정답 값이 답에 있음 · 답 없음 = "없다/모른다" 꼴이면 정직, 아니면 **지어냄 의심**(사람이 원문을 읽어 확정).
실행: KOSMOS_LAB_SPEC=adaptive python exp80b_memory_e2e.py > exp80b_out.txt
"""
import json
import re
import sys
from pathlib import Path

import litert_lm as llm
import litert_lm.embedding_engine as EE

sys.path.insert(0, str(Path(__file__).parent))
import exp80_memory_dense as X  # noqa: E402
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

NONE_NEAR = ["우리 아빠 생일 언제였지?", "내가 다니는 안과 어디였지?", "아이폰 비밀번호 뭐였지?", "고양이 다니는 동물병원 어디라고 했지?", "부모님 집 와이파이 비번 뭐였지?",
             "운전면허 번호 알려줘", "아내 신발 사이즈 뭐였지?", "피아노 레슨 몇 시였지?", "토익 점수 몇 점이라고 했지?", "결혼기념일 언제였지?"]
NONE_FAR = ["내 혈액형 뭐였지?", "내가 좋아하는 영화 뭐라고 했지?", "내 차 색깔 뭐였지?", "우리 아들 이름 뭐였지?", "내가 졸업한 대학교 어디였지?"]
NONE_EN = ["When is my dad's birthday?", "What's my iPhone passcode?", "What time is my piano lesson?", "What's my blood type?", "What's my favorite movie?"]
CROSS = [("What's my notebook password?", ["0912"]), ("What am I allergic to?", ["땅콩", "peanut", "새우", "shrimp"]), ("Which dentist do I go to?", ["미소", "Miso"]),
         ("When is my mom's birthday?", ["11월 7일", "November 7", "11/7", "7th"]), ("What's my sneaker size?", ["270"]),
         ("What's the tracking number for my book order?", ["6043"]), ("What's my airline mileage number?", ["9081"]), ("When is my guitar lesson?", ["수요일", "Wednesday"]),
         ("자전거 자물쇠 번호 뭐였지?", ["4821"]), ("회사 와이파이 비번 뭐였지?", ["sky7788"]), ("기타 레슨 언제였지?", ["수요일", "Wednesday"])]
EN_PARA = [("What's my bicycle lock combination?", ["4821"]), ("Which clinic do I go to for my teeth?", ["Smile"]), ("What foods should I avoid?", ["peanut"]),
           ("What streaming service do I pay for every month?", ["Netflix"]), ("What's the network key at work?", ["sky7788"]),
           ("When is my mother's birthday?", ["November 7", "Nov"]), ("What's my exercise routine?", ["5 km", "5km", "run"]), ("What's my footwear size?", ["270"]),
           ("When is my instrument class?", ["Wednesday"]), ("What's my package tracking number?", ["6043"])]
HONEST = re.compile(r"없|모르|않았|찾지 못|찾을 수 없|확인되지|저장되어 있지|not (saved|stored|find|found|have)|don't have|do not have|no (record|memory|saved|information)|couldn't find|can't find|haven't")

SYS_KO_FULL = K.fixture("system_instruction.txt")
SYS_KO = SYS_KO_FULL.replace(next(l for l in SYS_KO_FULL.splitlines(keepends=True) if "search_wikipedia" in l), "")
SYS_EN_FULL = K.fixture("system_instruction_en.txt")
SYS_EN = SYS_EN_FULL.replace(next(l for l in SYS_EN_FULL.splitlines(keepends=True) if "search_wikipedia" in l), "")
TOOLS = [t for t in K.ALL_TOOLS if t.__name__ != "search_wikipedia"]
HIT_TAIL_KO = "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."
HIT_TAIL_EN = "\nIf the answer is not in this list, answer using what the user already said in this conversation."


def is_en(text):
    return not re.search(r"[가-힣]", text)


def fmt(docs):
    return "\n".join(f"- {d['content']}" + (f" [{', '.join(d['tags'])}]" if d["tags"] else "") for d in docs)


def fallback(keyword, docs, en):
    tags = list(dict.fromkeys(t for d in sorted(docs, key=lambda d: -d["at"]) for t in d["tags"]))[:20]
    recent = sorted(docs, key=lambda d: -d["at"])[:3]
    if en:
        return (f"No memory matched '{keyword}'. Saved memories have the following categories: {', '.join(tags)}.\nIf any category might relate to the question, call "
                f"`search_memory` once more with that word. Otherwise, say there is no such memory.\nMost recently saved memories:\n{fmt(recent)}"
                "\nIf the answer is not in this list, answer using what the user already said in this conversation. If not there either, say you don't have it. Never make it up.")
    return (f"'{keyword}' 로는 일치하는 기억이 없습니다. 저장된 기억에 붙은 분류는 다음과 같습니다: {', '.join(tags)}.\n이 중 질문에 해당할 만한 분류가 있으면 그 단어로 "
            f"`search_memory` 를 한 번 더 호출하세요. 해당하는 분류가 없으면 그런 기억이 없다고 답하세요.\n가장 최근에 저장된 기억:\n{fmt(recent)}"
            "\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요. 거기에도 없으면 없다고 답하세요. 절대 지어내지 마세요.")


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
    cases = X.load_bench()
    docs = X.corpus(cases, "enriched")
    emb = EE.EmbeddingEngine(str(X.MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    vec = {d["id"]: list(emb.compute_embedding(f"title: {' '.join(d['tags']) or 'none'} | text: {d['content']}", opts).embedding) for d in docs}

    def search(mode, keyword):
        tokens = list(dict.fromkeys(keyword.split()))[:4]
        if mode == "L":
            return X.lexical(keyword, docs)[1][:3]
        qv = list(emb.compute_embedding(f"task: search result | query: {keyword}", opts).embedding)
        top = max((X.score(tokens, d["b"]) for d in docs), default=0) or 1.0
        return sorted(docs, key=lambda d: -(sum(a * b for a, b in zip(qv, vec[d["id"]])) + 0.05 * X.score(tokens, d["b"]) / top))[:3]

    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    sets = ([("none-near", q, None) for q in NONE_NEAR] + [("none-far", q, None) for q in NONE_FAR] + [("none-en", q, None) for q in NONE_EN]
            + [("cross", q, g) for q, g in CROSS] + [("en-para", q, g) for q, g in EN_PARA])
    agg = {}
    for kind, q, gold in sets:
        en = is_en(q)
        for mode in ("L", "C"):
            calls = []
            with K.create_chat_conversation(engine, history=[], tools=TOOLS, system_message=SYS_EN if en else SYS_KO) as conv:
                res = conv.send_message(K.with_turn_reminder(q))
                for _ in range(2):
                    name, kw = first_call(res)
                    if name != "search_memory" or not kw:
                        break
                    hits = search(mode, kw)
                    calls.append(f"{kw}→{[h['id'] for h in hits]}")
                    body = (fmt(hits) + (HIT_TAIL_EN if en else HIT_TAIL_KO)) if hits else fallback(kw, docs, en)
                    res = conv.send_message(K.tool_response_message("search_memory", json.dumps({"status": "success", "data": body}, ensure_ascii=False)))
                answer = text_of(res)
            a = agg.setdefault((kind, mode), {"n": 0, "ok": 0, "honest": 0, "called": 0})
            a["n"] += 1
            a["called"] += bool(calls)
            if gold:
                ok = any(g.lower() in answer.lower() for g in gold)
                a["ok"] += ok
                verdict = f"정답={ok}"
            else:
                honest = bool(HONEST.search(answer))
                a["honest"] += honest
                verdict = "정직" if honest else "지어냄의심"
            print(f"[{kind}/{mode}] {q} | {' ; '.join(calls) or '-'} | {verdict} | {answer.replace(chr(10), ' ')[:110]}", flush=True)

    print("\n## 요약 (툴 호출 / 답 있음: 정답 / 답 없음: 정직 '없다')", flush=True)
    for (kind, mode), a in sorted(agg.items()):
        tail = f"정답 {a['ok']}/{a['n']}" if not kind.startswith("none") else f"정직 {a['honest']}/{a['n']} (지어냄 의심 {a['n'] - a['honest']})"
        print(f"{kind:9s} {mode}  호출 {a['called']}/{a['n']} · {tail}", flush=True)


if __name__ == "__main__":
    main()

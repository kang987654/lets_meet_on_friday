"""C1+C′3 M0: 기억 검색의 네 조건 회수율 비교 — 현행 LIKE / 바이그램 / 바이그램+확장 / 앱 정책.

배경: exp33(2026-08-15)은 "모델 검색어 확장 + 바이그램 겹침"으로 16문항 recall@1 16/16 을 재었지만
① 폐기된 하네스 API(K.Lab/Config)에 묶여 재실행이 불가하고 ② 질의로 **원 질문 전체 + 확장어**를
썼다 — 앱은 모델이 툴 인자로 뽑은 짧은 keyword 로 검색한다. 이 실험은 앱이 실제로 검색하는
문자열(search_memory.keyword)을 실제 앱 프롬프트로 받아 낸 뒤 네 조건을 비교한다.

  A 현행   : 공백 분리 토큰, 본문 부분 일치(LIKE 대용) 또는 태그 정확 일치 → 맞은 토큰 수, 동점 최신순
  B 바이그램: 토큰 바이그램 겹침 비율의 합 (exp33 score() 원문)
  C B+확장 : B 의 항에 확장 oneShot 키워드 추가
  D 앱 정책 : A 적중(≥1) → A 후보를 B 점수로 재정렬 / A 0건 → 확장 → 전수 스캔(최소 항 겹침 임계) → 없으면 폴백(미스)
  D′        : D 에서 확장만 뺀 것 (확장 포함 여부 판정용)

게이트(계획서 0.25.0 M0): D ≥ 14/16 이고 D ≥ A. 확장 포함 = (D − D′) ≥ 2. 임계 0.5 가 "좋아하는 것" ↔
"커피보다 녹차…(태그 선호도)" 를 통과시키지 않아야 한다(기존 태그 목록 계약 보존).

코퍼스: exp33_summaries.json 의 에피소드 문서 9개(title+tags+summary). 픽스처의 지식 노트 1건은
exp33 정답표가 에피소드 번호라 비교 가능성을 위해 제외한다.

사용:
  ../.venv/Scripts/python.exe exp37_memory_retrieval.py            # 전체 (추론 32회, CPU ~10분)
  ../.venv/Scripts/python.exe exp37_memory_retrieval.py fixture    # 앱 픽스처(expand_system.txt) 미러 확인 후 전체
  ../.venv/Scripts/python.exe exp37_memory_retrieval.py rescore    # 저장된 키워드/확장으로 스코어만 재계산(모델 불필요)
"""
import json
import os
import re
import sys

import kosmos_lab as K

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(__file__)
SUMMARIES = os.path.join(HERE, "exp33_summaries.json")
OUT = os.path.join(HERE, "exp37_results.json")  # 실대화 파생 → gitignore

MIN_TERM_OVERLAP = 0.5  # Constants.BIGRAM_MIN_TERM_OVERLAP 예정값
TOP_K = 3               # Constants.MAX_KNOWLEDGE_CONTEXT_ITEMS

# exp33 QUESTIONS 원문 — 정답이 여러 에피소드에 흩어진 경우 전부 정답.
QUESTIONS = [
    ("내 자전거 비밀번호 뭐였지?", [4, 5, 6]),
    ("자전거 자물쇠 번호 기억나?", [4, 5, 6]),          # 바꿔 말하기: 자물쇠
    ("서랍 비밀번호 알려줘", [5]),
    ("치과 예약 언제였지?", [4]),
    ("팀 회의 몇 시로 잡았지?", [8]),
    ("저녁 약속 시간이 언제야?", [8]),
    ("네가 추천해준 커피 뭐였지?", [8]),
    ("커피 말고 하나 더 추천해준 게 있었잖아", [8]),
    ("트와이스 나연에 대해 전에 찾아봤던 내용", [3, 7]),
    ("에스파 데뷔일 전에 물어봤었지?", [8]),
    ("카리나 몇 년생이라고 했지?", [8]),
    ("위키 검색이 안 되던 때 있었잖아", [2, 3, 7]),
    ("네가 검색 안 하고 지어내서 답한 적 있었지?", [3, 7]),  # 환각 지적 대화
    ("음성 메시지 보냈던 대화 찾아줘", [1]),
    ("사진 설명해달라고 했던 거 기억해?", [1]),
    ("자전거 비밀번호를 4321로 바꾼 적 있어?", [4, 5]),
]

# exp33 EXPANDER_SYSTEM 원문 + 다른 oneShot 과 같은 [System] 영어 프리픽스 관례.
# M2 의 ExpandQueryUseCase.SYSTEM_INSTRUCTION 은 이 문구 원문 그대로여야 한다.
EXPANDER_SYSTEM = """[System]
You generate search keywords for a personal memory store.
당신은 검색어 생성기입니다. 사용자의 질문을 받아, 저장된 메모를 찾기 위한 검색 키워드를 만드세요. 동의어와 관련 명사를 포함해 키워드 4~8개를 쉼표로만 구분해 한 줄로 출력하세요. 다른 말은 하지 마세요."""


# --------------------------------------------------------------------------
# 스코어러 (BigramMatcher 예정 구현의 미러)
# --------------------------------------------------------------------------

def bigrams(s):
    s = re.sub(r"[\s\W_]+", "", s)
    return {s[i:i + 2] for i in range(len(s) - 1)}


def score(terms, doc_text):
    """exp33 원문 — 항별 바이그램 겹침 비율의 합."""
    doc = bigrams(doc_text)
    total = 0.0
    for t in terms:
        b = bigrams(t)
        if not b:
            continue
        total += len(b & doc) / len(b)
    return total


def best_term_overlap(terms, doc_text):
    doc = bigrams(doc_text)
    best = 0.0
    for t in terms:
        b = bigrams(t)
        if b:
            best = max(best, len(b & doc) / len(b))
    return best


def like_hits(tokens, docs):
    """현행 SearchMemoryToolExecutor 미러 — 토큰별 본문 부분 일치 OR 태그 정확 일치, 맞은 토큰 수."""
    counts = {}
    for t in tokens:
        for d in docs:
            if t in d["text"] or t in d["tag_list"]:
                counts[d["id"]] = counts.get(d["id"], 0) + 1
    return counts


def rank_a(tokens, docs):
    counts = like_hits(tokens, docs)
    hits = [d for d in docs if d["id"] in counts]
    return sorted(hits, key=lambda d: (-counts[d["id"]], -d["recency"]))


def rank_b(terms, docs, pool=None):
    pool = docs if pool is None else pool
    return sorted(pool, key=lambda d: (-score(terms, d["text"]), -d["recency"]))


def policy_d(tokens, expanded, docs, use_expansion):
    """앱 정책. 반환: (랭킹 리스트, 확장 사용 여부)"""
    a = rank_a(tokens, docs)
    if a:
        return rank_b(tokens, docs, pool=a), False
    terms = list(tokens)
    used = False
    if use_expansion and expanded:
        terms = list(dict.fromkeys(tokens + expanded))
        used = True
    survivors = [d for d in docs if best_term_overlap(terms, d["text"]) >= MIN_TERM_OVERLAP]
    return rank_b(terms, docs, pool=survivors), used


# --------------------------------------------------------------------------
# 코퍼스 · 응답 파싱
# --------------------------------------------------------------------------

def load_docs():
    raw = json.load(open(SUMMARIES, encoding="utf-8"))
    docs = []
    for d in raw:
        tags = [t.strip() for t in re.split(r"[,、]", d["tags"]) if t.strip()]
        docs.append({
            "id": d["episode"],
            "recency": d["episode"],  # 에피소드 번호 = 시간순
            "tag_list": tags,
            "text": f"{d['title']} {' '.join(tags)} {d['summary']}",
        })
    return docs


def text_of(res):
    parts = res.get("content", []) if isinstance(res, dict) else []
    return "".join(p.get("text", "") for p in parts if p.get("type") == "text")


def tool_keyword(res):
    calls = (res.get("tool_calls") or []) if isinstance(res, dict) else []
    for c in calls:
        fn = c.get("function", {})
        if fn.get("name") == "search_memory":
            kw = fn.get("arguments", {}).get("keyword")
            if kw:
                return str(kw).strip()
    return None


def parse_expansion(text, query):
    terms = [t.strip() for t in re.split(r"[,\n]", text) if t.strip()]
    terms = [t for t in terms if t != query]
    return list(dict.fromkeys(terms))[:8]


# --------------------------------------------------------------------------
# 실행
# --------------------------------------------------------------------------

def collect(system_expander):
    """추론 단계 — 질문마다 (앱 프롬프트로 keyword) + (확장어). 결과를 OUT 에 저장."""
    engine = K.create_engine()
    rows = []
    for q, gold in QUESTIONS:
        with K.create_chat_conversation(engine) as conv:  # 앱 채팅 조건: 시스템 지시 픽스처 + 툴 6종
            res = conv.send_message(K.with_turn_reminder(q))
        keyword = tool_keyword(res)
        with K.create_chat_conversation(engine, tools=[], system_message=system_expander) as conv:
            exp_text = text_of(conv.send_message(q))
        expanded = parse_expansion(exp_text, q)
        rows.append({"q": q, "gold": gold, "keyword": keyword, "expanded": expanded, "expander_raw": exp_text})
        print(f"[{len(rows):2}] keyword={keyword!r:28} 확장={', '.join(expanded)[:70]}", flush=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)
    return rows


def rescore(rows):
    docs = load_docs()
    hit = {"A": 0, "B": 0, "C": 0, "D": 0, "D'": 0}
    expansions_used = 0
    no_call = 0
    print("\n질문 | keyword | A B C D D' (top1)")
    for r in rows:
        gold = set(r["gold"])
        kw = r["keyword"]
        if not kw:
            no_call += 1
            print(f"X  {r['q'][:26]:26} | (툴 미호출)")
            continue
        tokens = [t for t in kw.split() if t][:4]
        expanded = r["expanded"]

        def top1(ranked):
            return ranked[0]["id"] if ranked else None

        a = top1(rank_a(tokens, docs))
        b = top1(rank_b(tokens, docs))
        c = top1(rank_b(list(dict.fromkeys(tokens + expanded)), docs))
        d_rank, used = policy_d(tokens, expanded, docs, use_expansion=True)
        d = top1(d_rank)
        dp = top1(policy_d(tokens, expanded, docs, use_expansion=False)[0])
        expansions_used += used
        marks = []
        for name, t in (("A", a), ("B", b), ("C", c), ("D", d), ("D'", dp)):
            ok = t in gold
            hit[name] += ok
            marks.append(f"{name}={'O' if ok else 'X'}")
        print(f"{'O' if d in gold else 'X'}  {r['q'][:26]:26} | {kw[:18]:18} | {' '.join(marks)}  top1(D)={d} gold={sorted(gold)}")

    n = len(rows)
    print(f"\n툴 미호출 {no_call}/{n}")
    print(f"recall@1  A={hit['A']}/{n}  B={hit['B']}/{n}  C={hit['C']}/{n}  D={hit['D']}/{n}  D'={hit[chr(68)+chr(39)]}/{n}   (D 에서 확장 실행 {expansions_used}회)")

    # 임계 계약 확인 — 기존 태그 목록 폴백이 살아야 하는 케이스 / 바이그램이 넘어야 하는 케이스
    contract_doc = "커피보다 녹차를 더 좋아함 선호도 음료"
    neg = best_term_overlap(["좋아하는", "것"], contract_doc)
    pos = best_term_overlap(["자물쇠번호"], "자물쇠 비밀번호 4936")
    print(f"임계 {MIN_TERM_OVERLAP}: '좋아하는 것' 겹침 {neg:.2f} → {'폴백 유지 OK' if neg < MIN_TERM_OVERLAP else '계약 위반!'}"
          f" / '자물쇠번호' 겹침 {pos:.2f} → {'회수 OK' if pos >= MIN_TERM_OVERLAP else '누락!'}")

    gate_d = hit["D"] >= 14 and hit["D"] >= hit["A"]
    gain = hit["D"] - hit["D'"]
    print(f"\n게이트: D≥14 & D≥A → {'통과' if gate_d else '실패'} / 확장 이득 D−D' = {gain} → "
          f"{'확장 포함(M2 진행)' if gain >= 2 else '확장 생략(M2 건너뜀, 스코어러만)'}")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "run"
    system = EXPANDER_SYSTEM
    if mode == "fixture":
        fx = K.fixture("expand_system.txt")
        assert fx.strip() == EXPANDER_SYSTEM.strip(), "픽스처와 실험 프롬프트가 다르다 — 한쪽을 갱신할 것"
        print("픽스처 == 실험 프롬프트 (미러 일치)")
        system = fx
    if mode == "rescore":
        rows = json.load(open(OUT, encoding="utf-8"))
    else:
        rows = collect(system)
    rescore(rows)

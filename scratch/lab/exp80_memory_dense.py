"""exp80 — 0.41.3 M0: 기억 검색에 EmbeddingGemma 2 dense 가 무엇을 더하는가 (모델 추론 없음 — 검색만).

자료
- 앱 `SemanticRecallBenchmarkTest` 의 20건(표면·부분·순수 상위개념, 질의 40)을 테스트 소스에서 그대로 읽는다 — 태그 원판(literal)·보강판(enriched) 두 조건.
- 잡음 메모(한국어 60·영어 25), 영어 정답 메모 10, 교차 언어 질문(한 메모·영 질문 10, 영 메모·한 질문 5), 기억에 답이 없는 질문(한 15·영 6).
- 질의는 모델이 `search_memory(keyword=…)` 로 넘기는 짧은 명사 꼴로 쓴다(앱 실기기·exp37 관찰).
비교
- L  : 앱 어휘 경로 이식(낱말별 내용 LIKE·태그 일치 → 바이그램 점수 상위 3 / 없으면 전수 스캔 겹침 ≥ 0.5) — `BigramMatcher`(한글 2글자 창 · 라틴 낱말·어간) 그대로.
- D  : dense 만 상위 3.
- A  : L 의 후보(1단 + 스캔 통과)만 dense 로 재순위 — 새 후보를 끌어오지 않는다.
- B@t: L 이 있으면 L, 없으면 dense 상위 중 코사인 ≥ t 만(최대 3).
- C  : 전부 결합 dense + 0.05 × sparse/최고점(0.41.2 문서와 같은 식).
지표: 답 있는 질문 @1·@3, **답 없는 질문 오첨부율**(상위 3 이 비지 않음), 코사인 분포(정답 vs 답 없는 질문의 최고).
입력: 질의 `task: search result | query: …`, 문서 `title: {태그 공백 이음 | none} | text: {내용}` — 태그 넣은 판(T)과 뺀 판(N).
실행: python exp80_memory_dense.py > exp80_out.txt   (출력은 커밋하지 않는다)
"""
import math
import re
import sys
from pathlib import Path

import litert_lm.embedding_engine as EE

sys.stdout.reconfigure(encoding="utf-8")
LAB = Path(__file__).parent
ROOT = LAB.parent.parent
MODEL = LAB.parent / "embeddinggemma-2-740m.litertlm"
BENCH = ROOT / "app/src/test/java/com/kosmos/app/assistant/tool/SemanticRecallBenchmarkTest.kt"

# ---------------------------------------------------------------- BigramMatcher 이식 (domain/search/BigramMatcher.kt)
LATIN_WORD = re.compile(r"[a-zA-Z]{2,}")
NON_ALNUM = re.compile(r"[^\w]+")
STOP = {"the", "and", "for", "that", "this", "with", "from", "are", "was", "were", "have", "has", "had", "what", "where", "when", "who",
        "how", "why", "into", "onto", "about", "your", "our", "their", "his", "her", "its", "is", "am", "be", "to", "of", "in", "on", "at",
        "by", "as", "it", "or", "an"}


def is_latin(c):
    return ("a" <= c <= "z") or ("A" <= c <= "Z")


def stem(w):
    if len(w) > 5 and w.endswith("ing"):
        s = w[:-3]
        return s[:-1] if len(s) >= 4 and s[-1] == s[-2] else s
    if len(w) > 5 and w.endswith("ic"):
        return w[:-2]
    if len(w) > 4 and w.endswith("ies"):
        return w[:-3] + "y"
    if len(w) > 4 and w.endswith("es"):
        return w[:-2]
    if len(w) > 4 and w.endswith("ed"):
        return w[:-2]
    if len(w) > 4 and w.endswith("y"):
        return w[:-1]
    if len(w) > 3 and w.endswith("s") and not w.endswith("ss"):
        return w[:-1]
    return w


def bigrams(text):
    if not any(is_latin(c) for c in text):
        comp = "".join(c for c in text if c.isalnum())
        return {comp[i:i + 2] for i in range(len(comp) - 1)}
    out = set()
    comp = "".join(c for c in text if c.isalnum() and not is_latin(c))
    out |= {comp[i:i + 2] for i in range(len(comp) - 1)}
    for chunk in NON_ALNUM.split(text):
        if len(chunk) >= 2 and any(c.isdigit() for c in chunk) and any(is_latin(c) for c in chunk):
            low = chunk.lower()
            out |= {low[i:i + 2] for i in range(len(low) - 1)}
    for m in LATIN_WORD.finditer(text):
        w = m.group(0).lower()
        if w in STOP:
            continue
        out.add("w:" + w)
        s = stem(w)
        if s != w and len(s) >= 3:
            out.add("w:" + s)
    return out


def score(terms, doc_b):
    t = 0.0
    for term in terms:
        b = bigrams(term)
        if b:
            t += len(b & doc_b) / len(b)
    return t


def best_overlap(terms, doc_b):
    best = 0.0
    for term in terms:
        b = bigrams(term)
        if b:
            best = max(best, len(b & doc_b) / len(b))
    return best


# ---------------------------------------------------------------- 자료
def load_bench():
    src = BENCH.read_text(encoding="utf-8")
    cases = []
    for m in re.finditer(r'Case\(\s*id = "(\w+)",\s*content = "([^"]+)",\s*literalTags = listOf\(([^)]*)\),\s*enrichedTags = listOf\(([^)]*)\),'
                         r'\s*queries = listOf\(([^)]*)\),\s*kind = "(\w+)"', src):
        q = lambda s: re.findall(r'"([^"]*)"', s)  # noqa: E731
        cases.append({"id": m.group(1), "content": m.group(2), "literal": q(m.group(3)), "enriched": q(m.group(4)),
                      "queries": q(m.group(5)), "kind": m.group(6)})
    assert len(cases) >= 20, len(cases)
    return cases


NOISE_KO = [
    ("회사 와이파이 이름은 KOSMOS-5G", ["회사", "와이파이"]), ("동생 차 번호는 34나 1234", ["동생", "차번호"]), ("집 현관 비밀번호 바꿈 — 0415", ["현관", "비밀번호"]),
    ("주민센터는 평일 9시에 연다", ["주민센터", "운영시간"]), ("다음 달 3일 건강검진 예약", ["건강검진", "예약"]), ("헬스장 연회비 48만원 결제함", ["헬스장", "결제"]),
    ("고양이 사료는 로얄캐닌 인도어", ["고양이", "사료"]), ("치킨은 교촌 허니콤보를 좋아함", ["치킨", "음식"]), ("여권 만료일 2029년 3월", ["여권", "만료일"]),
    ("자동차 보험은 매년 4월 갱신", ["자동차", "보험"]), ("관리비는 매달 25일 자동이체", ["관리비", "자동이체"]), ("팀장님 생일 6월 12일", ["팀장", "생일"]),
    ("이사 날짜는 12월 20일", ["이사", "날짜"]), ("우리 집 인터넷은 KT 기가", ["인터넷", "통신사"]), ("안경 도수 왼쪽 -3.0 오른쪽 -2.75", ["안경", "도수"]),
    ("커피 원두는 에티오피아 예가체프", ["커피", "원두"]), ("부산 출장 숙소는 해운대 호텔", ["출장", "숙소"]), ("주차장 B2 구역 C14", ["주차", "위치"]),
    ("월세 계약 만료 내년 2월", ["월세", "계약"]), ("세탁기 모델명 WF21T", ["세탁기", "모델명"]), ("필라테스 화요일 목요일 저녁 7시", ["필라테스", "운동"]),
    ("친구 지민이 결혼식 10월 셋째 주 토요일", ["결혼식", "지민"]), ("회사 사번 K-20391", ["사번", "회사"]), ("아버지 혈압약 하루 한 번", ["아버지", "혈압약"]),
    ("집 앞 편의점은 새벽 2시에 닫음", ["편의점", "영업시간"]), ("블로그 비밀번호 힌트는 첫 강아지 이름", ["블로그", "힌트"]), ("겨울 패딩은 드라이클리닝", ["패딩", "세탁"]),
    ("독서 모임 매달 첫째 주 일요일", ["독서", "모임"]), ("회의록 템플릿은 공유 드라이브 팀 폴더", ["회의록", "템플릿"]), ("조카 선물로 레고 시티 샀음", ["조카", "선물"]),
    ("전기차 충전 카드는 환경부 카드", ["전기차", "충전"]), ("신용카드 결제일 매달 14일", ["신용카드", "결제일"]), ("영어 학원 월수금 아침 7시", ["영어", "학원"]),
    ("화분 물은 일주일에 한 번", ["화분", "물주기"]), ("병원 진료 카드번호 88123", ["진료", "카드번호"]), ("택시 앱은 카카오T", ["택시", "앱"]),
    ("휴가는 8월 첫째 주로 신청함", ["휴가", "일정"]), ("엄마 휴대폰 기종 갤럭시 A54", ["엄마", "휴대폰"]), ("등산화 사이즈 265", ["등산화", "사이즈"]),
    ("건물 경비실 번호 02-555-1234", ["경비실", "전화번호"]), ("우리 팀 슬랙 채널 #kosmos-dev", ["슬랙", "채널"]), ("점심은 회사 앞 김밥천국 자주 감", ["점심", "식당"]),
    ("이번 달 목표 책 두 권 읽기", ["목표", "독서"]), ("세금 신고는 5월 말까지", ["세금", "신고"]), ("노트북 충전기는 65W USB-C", ["노트북", "충전기"]),
    ("주말에 부모님 댁 방문", ["부모님", "방문"]), ("아이 학교 상담 다음 주 수요일 4시", ["학교", "상담"]), ("휴대폰 요금제 5G 슬림", ["요금제", "휴대폰"]),
    ("자전거 타이어 공기압 60psi", ["자전거", "타이어"]), ("집들이 메뉴는 월남쌈", ["집들이", "메뉴"]), ("수영장 사물함 키 반납 잊지 말기", ["수영장", "사물함"]),
    ("과장님 이메일 kim@company.com", ["과장", "이메일"]), ("다이어트 중이라 저녁 6시 이후 금식", ["다이어트", "식단"]), ("캠핑장 예약 번호 CP-7788", ["캠핑장", "예약"]),
    ("우편함 열쇠는 신발장 위", ["우편함", "열쇠"]), ("비타민D 아침에 한 알", ["비타민", "영양제"]), ("회사 셔틀은 7시 40분 출발", ["셔틀", "출근"]),
    ("사촌 형 연락처 010-2222-3333", ["사촌", "연락처"]), ("반찬가게 쿠폰 10장 모으면 무료", ["반찬가게", "쿠폰"]), ("작업실 전등 스위치는 문 뒤", ["작업실", "전등"]),
]
EN_GOLD = [
    ("e1", "My bike lock code is 4821", ["bike", "lock"], ["bicycle lock code", "cycle combination"], ["bike lock"]),
    ("e2", "Dentist appointments are at Smile Dental near Gangnam station", ["dentist", "Smile Dental"], ["clinic I go to", "teeth doctor"], ["dentist"]),
    ("e3", "I am allergic to peanuts and shrimp", ["allergy", "peanuts"], ["food I can't eat", "foods to avoid"], ["allergic"]),
    ("e4", "Netflix is billed on the 15th to my Shinhan card", ["Netflix", "billing"], ["streaming subscription", "monthly payment"], ["Netflix"]),
    ("e5", "Office Wi-Fi password is sky7788", ["wifi", "office"], ["internet password at work", "network key"], ["wifi password"]),
    ("e6", "Mom's birthday is November 7", ["mom", "birthday"], ["mother's birthday", "family anniversary"], ["mom birthday"]),
    ("e7", "I run 5 km along the river every Saturday morning", ["running", "weekend"], ["exercise routine", "jogging"], ["run"]),
    ("e8", "My shoe size is 270 mm", ["shoes", "size"], ["sneaker size", "footwear size"], ["shoe size"]),
    ("e9", "Guitar lesson with Mr. Park every Wednesday at 8 pm", ["guitar", "lesson"], ["music class", "instrument lesson"], ["guitar"]),
    ("e10", "Parcel tracking number for the novel is 6043-1122-9087", ["parcel", "tracking"], ["delivery number", "package tracking"], ["parcel"]),
]
NOISE_EN = [
    ("Car insurance renews every April", ["car", "insurance"]), ("Gym membership costs 480k a year", ["gym", "payment"]), ("Passport expires March 2029", ["passport"]),
    ("Team lunch on Friday at the noodle place", ["lunch", "team"]), ("Rent is due on the 25th", ["rent"]), ("Cat food brand is Royal Canin indoor", ["cat", "food"]),
    ("Reading club meets the first Sunday", ["reading", "club"]), ("Laptop charger is 65W USB-C", ["laptop", "charger"]), ("Tax filing deadline is end of May", ["tax"]),
    ("Parking spot is B2 C14", ["parking"]), ("Glasses prescription left -3.0 right -2.75", ["glasses"]), ("Coffee beans: Ethiopia Yirgacheffe", ["coffee"]),
    ("Pilates on Tuesday and Thursday at 7 pm", ["pilates", "workout"]), ("Moving date is December 20", ["moving"]), ("Electric car charging card is the government one", ["ev", "charging"]),
    ("Water the plants once a week", ["plants"]), ("Building security phone 02-555-1234", ["security", "phone"]), ("Vacation booked for the first week of August", ["vacation"]),
    ("Company shuttle leaves at 7:40", ["shuttle", "commute"]), ("Cousin's number is 010-2222-3333", ["cousin", "contact"]), ("Dry clean the winter jacket", ["jacket", "laundry"]),
    ("Credit card payment date is the 14th", ["credit card"]), ("Camping reservation number CP-7788", ["camping", "reservation"]), ("Vitamin D one pill every morning", ["vitamin"]),
    ("Slack channel is #kosmos-dev", ["slack"]),
]
# 교차 언어 — (질의, 정답 id)
CROSS = [("notebook password", "p4"), ("allergy", "h2"), ("dentist", "h3"), ("running", "h4"), ("guitar lesson", "h5"), ("mom birthday", "h6"),
         ("subscription", "h7"), ("shoe size", "h8"), ("airline mileage", "h10"), ("parcel tracking", "h11"),
         ("자전거 자물쇠", "e1"), ("치과", "e2"), ("와이파이 비밀번호", "e5"), ("기타 레슨", "e9"), ("택배 번호", "e10")]
# 답 없는 질문 — 비슷한 주제지만 저장 안 된 것
NONE_KO = ["아빠 생일", "안과", "아이폰 비밀번호", "고양이 병원", "치과 예약 시간", "부모님 집 와이파이", "운전면허 번호", "아내 신발 사이즈",
           "피아노 레슨", "자동차 정비 날짜", "넷플릭스 비밀번호", "회사 주소", "토익 점수", "결혼기념일", "수영 강습 요일"]
NONE_EN = ["dad's birthday", "eye doctor", "iphone passcode", "home address", "piano lesson", "car service date"]


def corpus(cases, tag_mode):
    docs = []
    for i, c in enumerate(cases):
        docs.append({"id": c["id"], "content": c["content"], "tags": c[tag_mode], "at": 100 + i})
    for i, (t, tags) in enumerate(NOISE_KO):
        docs.append({"id": f"nk{i}", "content": t, "tags": tags, "at": i})
    for i, (gid, t, tags, _, _) in enumerate(EN_GOLD):
        docs.append({"id": gid, "content": t, "tags": tags, "at": 200 + i})
    for i, (t, tags) in enumerate(NOISE_EN):
        docs.append({"id": f"ne{i}", "content": t, "tags": tags, "at": 60 + i})
    for d in docs:
        d["b"] = bigrams(d["content"] + " " + " ".join(d["tags"]))
    return docs


# ---------------------------------------------------------------- 앱 어휘 경로 (SearchMemoryToolExecutor — 메모 부분)
def lexical(query, docs):
    tokens = list(dict.fromkeys(query.split()))[:4]
    cand = {}
    for t in tokens:
        for d in docs:
            if t.lower() in d["content"].lower() or any(x.lower() == t.lower() for x in d["tags"]):
                cand[d["id"]] = d
    if not cand:
        cand = {d["id"]: d for d in docs if best_overlap(tokens, d["b"]) >= 0.5}
    ranked = sorted(cand.values(), key=lambda d: (-score(tokens, d["b"]), -d["at"]))
    return [d["id"] for d in ranked], ranked


def main():
    cases = load_bench()
    eng = EE.EmbeddingEngine(str(MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    cache = {}

    def emb(text):
        if text not in cache:
            cache[text] = list(eng.compute_embedding(text, opts).embedding)
        return cache[text]

    def dot(a, b):
        return sum(x * y for x, y in zip(a, b))

    questions = []  # (set, query, gold or None)
    for c in cases:
        for q in c["queries"]:
            questions.append((f"bench-{c['kind']}", q, c["id"]))
    for gid, _, _, para, surf in EN_GOLD:
        for q in surf:
            questions.append(("en-surface", q, gid))
        for q in para:
            questions.append(("en-paraphrase", q, gid))
    for q, g in CROSS:
        questions.append(("cross", q, g))
    for q in NONE_KO:
        questions.append(("none-ko", q, None))
    for q in NONE_EN:
        questions.append(("none-en", q, None))

    for tag_mode in ("literal", "enriched"):
        docs = corpus(cases, tag_mode)
        for doc_in in ("T", "N"):
            vec = {}
            for d in docs:
                title = " ".join(d["tags"]) if doc_in == "T" and d["tags"] else "none"
                vec[d["id"]] = emb(f"title: {title} | text: {d['content']}")
            print(f"\n## 태그 {tag_mode} · 문서 입력 {'태그 넣음' if doc_in == 'T' else '태그 뺌'} · 메모 {len(docs)}", flush=True)
            agg = {}
            cos_gold, cos_none = [], []
            for kind, q, gold in questions:
                qv = emb(f"task: search result | query: {q}")
                dense = sorted(docs, key=lambda d: -dot(qv, vec[d["id"]]))
                cos = {d["id"]: dot(qv, vec[d["id"]]) for d in docs}
                lex_ids, lex_docs = lexical(q, docs)
                tokens = list(dict.fromkeys(q.split()))[:4]
                top_sp = max((score(tokens, d["b"]) for d in docs), default=0) or 1.0
                fused = sorted(docs, key=lambda d: -(cos[d["id"]] + 0.05 * score(tokens, d["b"]) / top_sp))
                rer = [d["id"] for d in sorted(lex_docs, key=lambda d: -cos[d["id"]])]
                results = {"L": lex_ids[:3], "D": [d["id"] for d in dense[:3]], "A": rer[:3], "C": [d["id"] for d in fused[:3]]}
                for t in (0.55, 0.60, 0.65, 0.70):
                    results[f"B{t:.2f}"] = lex_ids[:3] if lex_ids else [d["id"] for d in dense[:3] if cos[d["id"]] >= t]
                if gold:
                    cos_gold.append((cos[gold], kind, q))
                else:
                    cos_none.append((cos[dense[0]["id"]], kind, q, dense[0]["content"][:20]))
                for name, ids in results.items():
                    a = agg.setdefault((kind, name), [0, 0, 0])
                    a[0] += 1
                    if gold:
                        a[1] += ids[:1] == [gold]
                        a[2] += gold in ids[:3]
                    else:
                        a[2] += bool(ids)  # 오첨부
            names = ["L", "D", "A", "C", "B0.55", "B0.60", "B0.65", "B0.70"]
            kinds = sorted({k for k, _ in agg})
            print("세트".ljust(22) + "".join(n.ljust(13) for n in names), flush=True)
            for k in kinds:
                row = k.ljust(22)
                for n in names:
                    tot, h1, h3 = agg[(k, n)]
                    row += (f"오첨부{h3}/{tot}" if k.startswith("none") else f"{h1}·{h3}/{tot}").ljust(13)
                print(row, flush=True)
            g = sorted(x[0] for x in cos_gold)
            nn = sorted(x[0] for x in cos_none)
            print(f"정답 코사인 최소 {g[0]:.3f} 하위10% {g[len(g)//10]:.3f} 중앙 {g[len(g)//2]:.3f} | 답 없음 최고 코사인 최소 {nn[0]:.3f} 중앙 {nn[len(nn)//2]:.3f} 최대 {nn[-1]:.3f}", flush=True)
            print("답 없음 상위:", ", ".join(f"{q}→{c}({s:.2f})" for s, _, q, c in sorted(cos_none, reverse=True)[:6]), flush=True)
            print("정답 하위:", ", ".join(f"{q}({s:.2f})" for s, _, q in sorted(cos_gold)[:6]), flush=True)


if __name__ == "__main__":
    main()

"""exp97 — 0.42.3 M2: 대화 요약 + 사실 추출을 한 번의 oneShot 으로 합칠 수 있나.

조건
  SEP : 지금 — 요약(episode_summary_system.txt) 따로 + 추출(extract_system.txt) 따로, 같은 전사 두 번 프리필.
  LAB : 한 번 — 요약 형식 다음에 "프로필:/지식:" 두 줄을 이어 쓰게(라벨).
  JSN : 한 번 — JSON {"docs":[{"title","tags","summary"}], "profile":[...], "knowledge":[...]}.
합성 대화 10개(실제 대화 데이터 아님): 사실 심음 6 · 잡담만 2 · 일정만 1 · 주제 두 개 1.
판정: 요약 형식(제목·태그·요약 다 있음) · 주제 나누기(두 주제 → 2편, 한 주제 → 1편) · 심은 사실 회수 · 잡담/일정 오추출 · 지식 태그 · 걸린 시간.
사용: python exp97_summary_extract_merge.py > exp97_out.txt
"""
import json
import pathlib
import re
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
SUM_SYS = K.fixture("episode_summary_system.txt")
EXT_SYS = K.fixture("extract_system.txt")
EXT_USER = K.fixture("extract_user.txt")

_ext_rules = EXT_SYS.split("\n")
EXT_BODY = "\n".join(l for l in _ext_rules[3:] if not l.startswith("아래 형식만"))  # 고르는 규칙 + 두 라벨 줄

LAB_SYS = SUM_SYS + """

요약 문서를 다 쓴 뒤 '===' 한 줄을 쓰고, 같은 대화에서 앞으로도 기억할 사용자 사실을 아래 규칙으로 이어 쓰세요.
""" + EXT_BODY

JSN_SYS = """[System]
You are a librarian who summarizes conversation logs into searchable index documents and extracts durable user facts.
당신은 대화 기록을 색인용 문서로 요약하고, 앞으로도 유효한 사용자 사실을 골라내는 사서입니다. JSON 하나만 출력하세요(코드 블록·설명 없이).
{"docs":[{"title":"한 줄","tags":["핵심 명사 5~8개 — 상위 개념·동의어 포함(커피→음료, 치과→병원)"],"summary":"3~5문장 해요체, 누가 했는지 밝혀 쓰고 날짜·시각·이름·숫자는 원문 그대로"}],
 "profile":["항목: 값 — 이름·호칭·말투 선호·직업·거주지·가족처럼 항상 관련 있는 사실, 최대 2개"],
 "knowledge":["특정 상황에서만 필요한 사실 — 비밀번호·번호·장소·습관·반복 일정, 끝에 [상위 분류·동의어 태그 2~3개], 최대 3개"]}
대화에 서로 무관한 주제가 2개 이상 섞여 있으면 docs 에 주제마다 하나씩, 하나면 하나만.
사실은 대화에 명시된 것만 — 추측·일반 상식·일회성 잡담·비서의 발언·예약·약속(캘린더 몫)은 넣지 마세요. 없으면 빈 목록."""

# (이름, 기대 문서 수, 심은 사실 낱말들, 오추출이면 안 되는 대화인가, [(u|a, 글)])
CONVS = [
    ("와이파이 비번", 1, ["home2580"], False, [("u", "우리 집 와이파이 비밀번호는 home2580이야"), ("a", "알겠어요. 집 와이파이 비밀번호가 home2580이군요."), ("u", "공유기는 거실 TV 옆에 있어"), ("a", "공유기가 거실 TV 옆에 있군요.")]),
    ("이름·직업", 1, ["진우", "개발"], False, [("u", "내 이름은 진우고 안드로이드 개발자야"), ("a", "반가워요, 진우 님! 안드로이드 개발을 하시는군요."), ("u", "요즘 코틀린 공부 중이야"), ("a", "코틀린 공부 응원할게요.")]),
    ("습관", 1, ["녹차"], False, [("u", "나는 아침엔 커피 대신 녹차를 마셔"), ("a", "아침에 녹차를 드시는군요. 카페인이 덜해서 좋죠."), ("u", "커피는 오후에만 한 잔"), ("a", "오후에 커피 한 잔이시군요.")]),
    ("주차·사물함", 1, ["B2-17", "214"], False, [("u", "회사 주차는 늘 B2-17 자리에 해"), ("a", "회사 주차 자리가 B2-17이군요."), ("u", "사물함 번호는 214번"), ("a", "사물함은 214번이군요.")]),
    ("반복 일정", 1, ["수요일", "요가"], False, [("u", "나 매주 수요일 저녁에 요가 다녀"), ("a", "매주 수요일 저녁 요가를 하시는군요."), ("u", "끝나고 늘 샐러드 먹어"), ("a", "요가 뒤 샐러드, 좋은 습관이에요.")]),
    ("알레르기", 1, ["땅콩"], False, [("u", "나 땅콩 알레르기 있어서 조심해야 해"), ("a", "땅콩 알레르기가 있으시군요. 음식 고를 때 조심할게요."), ("u", "견과류 들어간 과자 추천은 하지 마"), ("a", "알겠어요, 견과류 과자는 빼고 추천할게요.")]),
    ("잡담 날씨", 1, [], True, [("u", "오늘 날씨 진짜 춥다"), ("a", "많이 춥죠. 따뜻하게 입으세요."), ("u", "붕어빵 먹고 싶다"), ("a", "겨울엔 붕어빵이죠!")]),
    ("잡담 영화", 1, [], True, [("u", "인터스텔라 봤어?"), ("a", "저는 볼 수 없지만 줄거리는 알아요. 시간 여행과 블랙홀 이야기죠."), ("u", "결말이 좀 어렵더라"), ("a", "5차원 장면이 특히 어렵게 느껴지죠.")]),
    ("일정만", 1, [], True, [("u", "내일 오후 3시에 치과 예약 잡아줘"), ("a", "내일 오후 3시에 치과 예약을 추가했어요."), ("u", "고마워"), ("a", "천만에요!")]),
    ("두 주제", 2, ["sky1234"], False, [("u", "김치찌개 맛있게 끓이는 법 알려줘"), ("a", "익은 김치와 돼지고기를 먼저 볶은 뒤 물을 부어 끓이면 맛이 깊어져요."), ("u", "아 그리고 회사 와이파이 비번은 sky1234야"), ("a", "회사 와이파이 비밀번호가 sky1234군요.")]),
]


def transcript(conv):
    return "\n".join(("사용자: " if r == "u" else "비서: ") + t for r, t in conv)


def gen(system, user):
    t0 = time.time()
    with K.create_chat_conversation(R.engine(), tools=None, system_message=system) as conv:
        out = R.text_of(conv.send_message(user))
    return out, time.time() - t0


DOC = re.compile(r"제목\s*[:：].+?태그\s*[:：].+?요약\s*[:：].+?(?=\n-{3,}\n|\n===|\Z)", re.S)


def docs_from_labels(text):
    head = text.split("===")[0]
    return [b for b in re.split(r"\n-{3,}\n", head) if all(re.search(f"{k}\\s*[:：]", b) for k in ("제목", "태그", "요약"))]


def facts_from_labels(text):
    tail = text.split("===", 1)[1] if "===" in text else text
    lines = [l for l in tail.splitlines() if re.match(r"\s*[-*•]?\s*(프로필|지식)\s*[:：]", l) or re.match(r"\s*[-*•]\s+", l)]
    body = [re.sub(r"^\s*[-*•]?\s*(프로필|지식)?\s*[:：]?\s*", "", l).strip() for l in lines]
    return [b for b in body if b and not b.startswith("없음")], bool(lines)


def parse_json(text):
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def main():
    score = {c: {"fmt": 0, "split": 0, "recall": 0, "planted": 0, "false": 0, "tagged": 0, "facts": 0, "sec": 0.0} for c in ("SEP", "LAB", "JSN")}
    for name, ndocs, planted, should_empty, conv in CONVS:
        tr = transcript(conv)
        # SEP
        s_out, s_t = gen(SUM_SYS, "다음 대화를 요약하세요.\n\n" + tr)
        e_out, e_t = gen(EXT_SYS, EXT_USER + tr)
        sep_docs = docs_from_labels(s_out)
        sep_facts, sep_ok = facts_from_labels(e_out)
        # LAB
        l_out, l_t = gen(LAB_SYS, "다음 대화를 요약하고 기억할 사실을 골라내세요.\n\n" + tr)
        lab_docs = docs_from_labels(l_out)
        lab_facts, lab_ok = facts_from_labels(l_out)
        # JSN
        j_out, j_t = gen(JSN_SYS, "다음 대화를 처리하세요.\n\n" + tr)
        j = parse_json(j_out)
        jsn_docs = [d for d in (j or {}).get("docs", []) if isinstance(d, dict) and d.get("title") and d.get("tags") and d.get("summary")]
        jsn_facts = [str(x) for x in ((j or {}).get("profile", []) + (j or {}).get("knowledge", []))] if j else []
        for cond, docs, facts, fmt_ok, sec in (("SEP", sep_docs, sep_facts, sep_ok and bool(sep_docs), s_t + e_t),
                                                ("LAB", lab_docs, lab_facts, lab_ok and bool(lab_docs), l_t),
                                                ("JSN", jsn_docs, jsn_facts, j is not None and bool(jsn_docs), j_t)):
            sc = score[cond]
            sc["fmt"] += fmt_ok
            sc["split"] += len(docs) == ndocs
            joined = " ".join(facts)
            sc["planted"] += len(planted)
            sc["recall"] += sum(p in joined for p in planted)
            sc["false"] += should_empty and bool(facts)
            sc["facts"] += len(facts)
            sc["tagged"] += sum(bool(re.search(r"\[[^\]]+\]\s*$", f)) for f in facts if ":" not in f.split("[")[0][:6])
            sc["sec"] += sec
        print(f"\n==== {name}\n-- SEP 요약 ({s_t:.1f}s)\n{s_out}\n-- SEP 추출 ({e_t:.1f}s)\n{e_out}\n-- LAB ({l_t:.1f}s)\n{l_out}\n-- JSN ({j_t:.1f}s)\n{j_out}", flush=True)
    print("\n######## 합계 (10 대화)", flush=True)
    for c, sc in score.items():
        print(f"{c}: 형식 {sc['fmt']}/10 · 주제 나누기 {sc['split']}/10 · 사실 회수 {sc['recall']}/{sc['planted']} · 오추출(잡담·일정 3개 중) {sc['false']} · "
              f"사실 수 {sc['facts']} · 지식 태그 {sc['tagged']} · 시간 {sc['sec']:.0f}s", flush=True)


if __name__ == "__main__":
    main()

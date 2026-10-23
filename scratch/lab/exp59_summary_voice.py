"""exp59 — 0.38.6 M5 모델이 쓰는 글의 말투: 에피소드 요약 · 기억 합치기 (계획서 docs/plans/0.38.6-fixes.md M5).

화면에 "사용자는 …했습니다"(요약)·"…4821이다."(합치기)가 보인다(0.38.5 impeccable P2). 요약은 검색 결과·브리핑 재료로 모델에 다시 들어간다.

part summary — 지시 S0(앱 원문, 픽스처) · S1(해요체 + 주어 "사용자가/비서가") · S2(해요체 + 2인칭 "…하셨어요", 비서는 "비서가").
  합성 대화 8개(단일 주제 6 + 다중 주제 2) + 실제 기록 에피소드(fixtures/kosmos_db, 메시지 ≥4, 최대 12 — 원문은 출력하지 않고 수치만).
  지표: 형식 준수(세 칸), 다중 주제 분리/단일 비분리, 핵심 숫자·이름 보존, 끝맺음(해요/합니다/해라), 주어 표지, 합성 회수(바이그램 top-1).
part merge — 판정 J0(앱 원문, 픽스처) · J1(공통 형식 + 메모 말투). exp42 합성(중복 6묶음·함정 5·무관 10, containment ≥ 0.3 후보).
  게이트(exp42): 오병합 0, 참 중복 ≥ 90%, 숫자 보존 100% + 합친 문장 "…다." 끝맺음 0.
사용: python exp59_summary_voice.py [summary] [merge] [--v=S0,S1,S2]
"""
import itertools
import re
import sqlite3
import sys

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

PARTS = [a for a in sys.argv[1:] if not a.startswith("--")] or ["summary", "merge"]

S0 = K.fixture("episode_summary_system.txt")
J0 = K.fixture("memory_judge_system.txt")
SUM_LINE = "요약: (3~5문장. 날짜·시각·이름·숫자는 원문 그대로 보존)"
assert SUM_LINE in S0, "요약 지시가 바뀌었다 — 픽스처를 다시 내보낼 것"
SUMMARY = {
    "S0": S0,
    "S1": S0.replace(SUM_LINE, "요약: (3~5문장, 해요체(…했어요). \"사용자가 …했어요\", \"비서가 …했어요\"처럼 누가 했는지 밝혀 쓰세요. 날짜·시각·이름·숫자는 원문 그대로 보존)"),
    "S2": S0.replace(SUM_LINE, "요약: (3~5문장, 해요체로 사용자에게 말하듯(…하셨어요). 비서가 한 일은 \"비서가 …했어요\"로 쓰세요. 날짜·시각·이름·숫자는 원문 그대로 보존)"),
}
J1 = """[System]
You are a memory-cleanup assistant that decides whether notes state exactly the same fact.
당신은 기억 정리 도우미예요. 아래 기억들이 완전히 같은 사실을 말하는지 판정하세요.
- 숫자·날짜·시각·사람·대상이 하나라도 다르면 '다름'이에요. 비슷한 주제일 뿐이어도 '다름'이에요.
- 같으면 숫자와 고유명사를 그대로 살려 가장 정확한 한 문장으로 합치세요. 원래 기억처럼 짧은 메모 말투로 쓰고 '…이다'로 끝내지 마세요.
- 출력은 정확히 두 줄이에요.
판정: 같음 또는 다름
합친 문장: 같을 때 한 문장, 다르면 없음"""
JUDGE = {"J0": J0, "J1": J1}
VSEL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--v=")), "S0,S1,S2").split(",")

engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)


def ask(system, user):
    with K.create_chat_conversation(engine, tools=[], system_message=system) as conv:
        res = conv.send_message(user)
    c = res.get("content")
    return (c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))).strip()


# ---------------------------------------------------------------- 요약
def parse(text):
    docs = []
    for block in re.split(r"\n-{3,}\n", text):
        t = re.search(r"제목\s*[:：]\s*(.+)", block)
        g = re.search(r"태그\s*[:：]\s*(.+)", block)
        s = re.search(r"요약\s*[:：]\s*(.+)", block, re.S)
        if t and g and s:
            docs.append((t.group(1).strip(), g.group(1).strip(), s.group(1).strip()))
    return docs


HANGUL = re.compile("[가-힣]")


def endings(text):
    kinds = []
    for p in re.split(r"(?<=[.?!])\s+|\n+", text):
        core = re.sub(r"[\s.?!…~,\"')\]]+$", "", p)
        if not HANGUL.search(core):
            continue
        kinds.append("formal" if re.search(r"(니다|니까)$", core) else "polite" if re.search(r"(요|죠)$", core)
                     else "plain" if re.search(r"(다|음|함|임)$", core) else "other")
    return kinds


def transcript(lines):
    return "\n".join(f"{'사용자' if r == 'u' else '비서'}: {t}" for r, t in lines)


# (대화, 기대 문서 수, 보존해야 할 낱말, 회수 질의)
SYN = [
    ([("u", "내일 오후 3시에 강남역 미소치과 예약 잡아줘"), ("a", "내일 오후 3시 미소치과 예약을 일정에 추가했어요."), ("u", "고마워, 스케일링도 받을 거야"), ("a", "스케일링도 받으시는군요. 잘 다녀오세요.")],
     1, ["3시", "미소치과", "스케일링"], "치과 예약 언제였지"),
    ([("u", "자전거 자물쇠 비밀번호 4821로 바꿨어 기억해줘"), ("a", "자전거 자물쇠 비밀번호 4821을 기억해 둘게요."), ("u", "예전 번호는 1234였어"), ("a", "예전 번호 1234에서 4821로 바뀌었군요.")],
     1, ["4821", "1234"], "자전거 자물쇠 번호"),
    ([("u", "토요일 아침에 한강에서 5km 뛰기로 했어"), ("a", "좋아요! 토요일 아침 한강 5km 러닝이군요."), ("u", "지훈이랑 같이 뛸 거야"), ("a", "지훈 님과 함께라 더 즐겁겠어요.")],
     1, ["5km", "지훈", "한강"], "주말 러닝 누구랑"),
    ([("u", "엄마 생신이 11월 7일이야"), ("a", "11월 7일이 어머니 생신이군요."), ("u", "선물로 캐시미어 스카프 사려고"), ("a", "캐시미어 스카프 좋은 선택이에요.")],
     1, ["11월 7일", "스카프"], "엄마 선물"),
    ([("u", "발표 자료 금요일까지인데 너무 늦어서 걱정이야"), ("a", "걱정되시겠어요. 목차부터 같이 정리해 볼까요?"), ("u", "응, 서론 본론 결론으로 해줘"), ("a", "서론·본론·결론 세 부분으로 정리했어요.")],
     1, ["금요일", "목차"], "발표 자료 마감"),
    ([("u", "수요일 기타 레슨이 저녁 8시로 바뀌었어"), ("a", "수요일 기타 레슨이 저녁 8시로 바뀌었군요."), ("u", "선생님은 박민수 선생님이야"), ("a", "박민수 선생님이시군요.")],
     1, ["8시", "박민수"], "기타 레슨 시간"),
    ([("u", "회사 주차는 지하 2층 C-14야 기억해줘"), ("a", "회사 주차 위치 지하 2층 C-14를 기억해 둘게요."), ("u", "그리고 저녁 메뉴 추천해줘"), ("a", "김치찌개나 된장찌개는 어때요?"), ("u", "김치찌개 좋다, 레시피 알려줘"), ("a", "김치와 돼지고기를 볶은 뒤 물을 붓고 끓이면 돼요.")],
     2, ["C-14", "김치찌개"], "회사 주차 자리"),
    ([("u", "내일 아침 7시에 물 마시라고 알려줘"), ("a", "내일 오전 7시에 물 마시기 알림을 등록했어요."), ("u", "참, 여동생 생일은 5월 3일이야"), ("a", "여동생 생일 5월 3일, 알겠어요.")],
     2, ["7시", "5월 3일"], "여동생 생일"),
]


def bigrams(s):
    c = re.sub(r"[\s\W_]+", "", s)
    return {c[i:i + 2] for i in range(len(c) - 1)}


def score(q, doc):
    x, y = bigrams(q), bigrams(doc)
    return len(x & y) / len(x) if x else 0.0


def real_episodes(limit=12):
    db = sqlite3.connect(str(K.FIXTURES / "kosmos_db"))
    rows = db.execute("select episodeId, role, content from conversation where episodeId is not null and inputType in ('TEXT','VOICE') order by createdAt").fetchall()
    eps = {}
    for eid, role, content in rows:
        eps.setdefault(eid, []).append(("u" if role == "USER" else "a", (content or "")[:400]))
    return [v for v in eps.values() if len(v) >= 4][:limit]


if "summary" in PARTS:
    REAL = real_episodes()
    for v in VSEL:
        system = SUMMARY[v]
        fmt = split_ok = kept = keep_total = 0
        kinds, subj, n_docs = [], 0, 0
        index = []  # (합성 번호, 문서 글)
        for i, (lines, expect, keys, _q) in enumerate(SYN):
            out = ask(system, "다음 대화를 요약하세요.\n\n" + transcript(lines))
            docs = parse(out)
            fmt += bool(docs)
            split_ok += len(docs) == expect
            body = " ".join(" ".join(d) for d in docs)
            kept += sum(k.replace(" ", "") in body.replace(" ", "") for k in keys)
            keep_total += len(keys)
            for d in docs:
                kinds += endings(d[2])
                subj += bool(re.search(r"(사용자|비서|하셨|셨어요|드렸)", d[2]))
                n_docs += 1
                index.append((i, " ".join(d)))
            print(f"[summary {v} syn{i}] 문서 {len(docs)}/{expect} | " + " ‖ ".join(f"{d[0]} / {d[2]}" for d in docs).replace("\n", " ")[:220], flush=True)
        hits = sum(max(index, key=lambda x: score(q, x[1]))[0] == i for i, (_l, _e, _k, q) in enumerate(SYN))
        rfmt, rkinds, rsubj, rdocs = 0, [], 0, 0
        for lines in REAL:
            docs = parse(ask(system, "다음 대화를 요약하세요.\n\n" + transcript(lines)))
            rfmt += bool(docs)
            for d in docs:
                rkinds += endings(d[2])
                rsubj += bool(re.search(r"(사용자|비서|하셨|셨어요|드렸)", d[2]))
                rdocs += 1

        def pct(ks, k):
            return f"{100 * ks.count(k) / max(len(ks), 1):.0f}%"
        print(f"== summary {v}: 합성 형식 {fmt}/{len(SYN)} 문서 수 맞음 {split_ok}/{len(SYN)} 보존 {kept}/{keep_total} 회수 top1 {hits}/{len(SYN)} | "
              f"해요 {pct(kinds, 'polite')} 합니다 {pct(kinds, 'formal')} 해라·명사 {pct(kinds, 'plain')} 주어 {subj}/{n_docs} | "
              f"실제 {len(REAL)}: 형식 {rfmt}/{len(REAL)} 해요 {pct(rkinds, 'polite')} 합니다 {pct(rkinds, 'formal')} 해라·명사 {pct(rkinds, 'plain')} 주어 {rsubj}/{rdocs}", flush=True)

if "merge" in PARTS:
    GROUPS = {
        "G1": ["자전거 자물쇠 비밀번호는 4821", "자전거 자물쇠 번호 4821", "내 자전거 자물쇠 비번은 4821이야"],
        "G2": ["와이파이 비밀번호는 kosmos123", "집 와이파이 비번 kosmos123"],
        "G3": ["땅콩 알레르기가 있다", "사용자는 땅콩 알레르기 있음"],
        "G4": ["아침에 커피 대신 녹차를 마신다", "커피보다 녹차를 좋아함"],
        "G5": ["회사 출근 시간은 오전 9시", "출근은 오전 9시까지"],
        "G6": ["여동생 생일은 5월 3일", "여동생 생일 5월 3일"],
    }
    TRAPS = ["현관 비밀번호는 4821", "자전거 자물쇠 비밀번호는 4812", "남동생 생일은 5월 3일", "회사 퇴근 시간은 오후 6시", "땅콩버터를 좋아한다"]
    OTHERS = ["주말마다 한강에서 러닝을 한다", "강아지 이름은 콩이", "차량 번호는 12가 3456", "좋아하는 영화는 인터스텔라", "치과는 강남역 근처 미소치과",
              "매주 수요일 저녁 기타 레슨", "혈액형은 A형", "회사는 판교에 있다", "고양이 털 알레르기는 없다", "운동화 사이즈 270"]
    NOTES = [(g, t) for g, ts in GROUPS.items() for t in ts] + [("T", t) for t in TRAPS] + [("O", t) for t in OTHERS]

    def containment(a, b):
        x, y = bigrams(a), bigrams(b)
        return len(x & y) / min(len(x), len(y)) if x and y else 0.0

    cands = [p for p in itertools.combinations(range(len(NOTES)), 2) if containment(NOTES[p[0]][1], NOTES[p[1]][1]) >= 0.3]
    truth = lambda i, j: NOTES[i][0] == NOTES[j][0] and NOTES[i][0].startswith("G")  # noqa: E731
    for jv, system in JUDGE.items():
        tp = fp = bad = lost = plain = 0
        for i, j in cands:
            a, b = NOTES[i][1], NOTES[j][1]
            out = ask(system, f"기억들:\n- {a}\n- {b}")
            m = re.search(r"판정\s*[:：]\s*(같음|다름)", out)
            merged = (re.search(r"합친 문장\s*[:：]\s*(.+)", out) or [None, ""])[1].strip()
            verdict = m.group(1) if m else None
            bad += verdict is None
            if verdict == "같음" and truth(i, j):
                tp += 1
                lost += not set(re.findall(r"\d+", a + b)) <= set(re.findall(r"\d+", merged))
                plain += bool(re.search(r"다\.?$", merged))
                print(f"[merge {jv}] {a} | {b} → {merged}", flush=True)
            elif verdict == "같음":
                fp += 1
                print(f"[merge {jv}] 오병합! {a} | {b}", flush=True)
        n_true = sum(truth(*p) for p in cands)
        print(f"== merge {jv}: 후보 {len(cands)} 참중복 {tp}/{n_true} 오병합 {fp} 형식이탈 {bad} 숫자손실 {lost} '…다' 끝 {plain}/{tp}", flush=True)

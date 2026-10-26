"""exp63 — 0.38.8 M5: 주제가 둘 섞인 대화 요약 분리(exp59: 합성 다중 주제 2개 모두 문서 하나 — 문서 수 맞음 6/8).

요약 지시(앱 픽스처)의 분리 문장만 바꾼다.
  P0 현행 "대화에 서로 무관한 주제가 2개 이상 섞여 있으면, 주제마다 …'---'… 주제가 하나면 절대 나누지 마세요."
  P1 P0 + 예시 "(예: 주차 위치를 알려 준 뒤 저녁 메뉴를 물음 → 두 문서)"
  P2 판단 기준을 "앞 이야기와 이어지지 않는 새 부탁·새 정보가 나오면 새 주제"로
지표: 합성 8(단일 6·다중 2) 문서 수 맞음, 다중 분리, 단일 비분리, 핵심 낱말 보존, 실제 기록 12 형식·평균 문서 수.
합성 대화·파서는 exp59 원문에서 그대로 가져온다.
"""
import re
import sqlite3
import sys

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

S0 = K.fixture("episode_summary_system.txt")
SPLIT = next(l for l in S0.splitlines() if "'---'" in l)
VARIANTS = {
    "P0": S0,
    "P1": S0.replace(SPLIT, SPLIT + " (예: 주차 위치를 알려 준 뒤 저녁 메뉴를 물음 → 문서 두 개)"),
    "P2": S0.replace(SPLIT, "앞 이야기와 이어지지 않는 새 부탁이나 새 정보가 나오면 새 주제예요. 주제가 2개 이상이면 주제마다 위 형식을 반복하되 문서 사이를 '---' 한 줄로 구분하세요. 주제가 하나면 절대 나누지 마세요."),
}
engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)


def ask(system, user):
    with K.create_chat_conversation(engine, tools=[], system_message=system) as conv:
        res = conv.send_message(user)
    c = res.get("content")
    return (c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))).strip()


def parse(text):
    docs = []
    for block in re.split(r"\n-{3,}\n", text):
        t = re.search(r"제목\s*[:：]\s*(.+)", block)
        g = re.search(r"태그\s*[:：]\s*(.+)", block)
        s = re.search(r"요약\s*[:：]\s*(.+)", block, re.S)
        if t and g and s:
            docs.append((t.group(1).strip(), g.group(1).strip(), s.group(1).strip()))
    return docs


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


def real_episodes(limit=12):
    db = sqlite3.connect(str(K.FIXTURES / "kosmos_db"))
    rows = db.execute("select episodeId, role, content from conversation where episodeId is not null and inputType in ('TEXT','VOICE') order by createdAt").fetchall()
    eps = {}
    for eid, role, content in rows:
        eps.setdefault(eid, []).append(("u" if role == "USER" else "a", (content or "")[:400]))
    return [v for v in eps.values() if len(v) >= 4][:limit]



REAL = real_episodes()
for v, system in VARIANTS.items():
    right = multi = single = kept = total = 0
    for i, (lines, expect, keys, _q) in enumerate(SYN):
        docs = parse(ask(system, "다음 대화를 요약하세요.\n\n" + transcript(lines)))
        right += len(docs) == expect
        multi += expect == 2 and len(docs) == 2
        single += expect == 1 and len(docs) == 1
        joined = " ".join(" ".join(d) for d in docs).replace(" ", "")
        kept += sum(k.replace(" ", "") in joined for k in keys)
        total += len(keys)
        print(f"[{v} syn{i}] 문서 {len(docs)}/{expect} | " + " ‖ ".join(d[0] for d in docs), flush=True)
    fmt = 0
    counts = []
    for lines in REAL:
        docs = parse(ask(system, "다음 대화를 요약하세요.\n\n" + transcript(lines)))
        fmt += bool(docs)
        counts.append(len(docs))
    print(f"== {v}: 문서 수 맞음 {right}/{len(SYN)} · 다중 분리 {multi}/2 · 단일 비분리 {single}/6 · 보존 {kept}/{total} | 실제 {len(REAL)}: 형식 {fmt}/{len(REAL)} · 평균 문서 {sum(counts) / max(len(counts), 1):.2f}", flush=True)

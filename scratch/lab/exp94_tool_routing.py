"""exp94 — 툴 라우팅 판단 재료 (0.42.0 M2, 앱 코드 변경 없음).

[1] 툴별 앞부분 토큰: 그 툴을 뺀 대화의 앞부분 토큰과의 차이(선언) + 시스템 지시 트리거 줄(engine.tokenize).
[2] 지금 모델의 툴 선택 정확도: 라벨 붙은 문항(툴 6종 + 툴 없음)에서 첫 호출이 정답인가.
[3] 임베딩 분류(EmbeddingGemma 2, 실험실 모델 파일): 질문 ↔ 툴 설명 유사도로 정답 툴이 1위·상위 3 안에 드는 비율, 질의당 시간.
[4] 툴 묶음 변화: 이어지는 대화 4개에서 턴마다 상위 3 묶음이 앞 턴과 달라지는 횟수(= 선언이 바뀌어 앞부분을 다시 프리필하는 횟수).
"""
import pathlib
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402
import litert_lm.embedding_engine as EE  # noqa: E402

K = R.K
TOOLS = R.TOOLS_OFF  # 앱 기본(웹·문서 검색 꺼짐)
NAMES = [t.__name__ for t in TOOLS]
MODEL = pathlib.Path(__file__).resolve().parent.parent / "embeddinggemma-2-740m.litertlm"

CASES = [  # (질문, 정답 툴 또는 None)
    ("내일 오후 3시에 치과 예약 잡아줘", "add_schedule"), ("금요일 저녁 7시에 회식 일정 넣어줘", "add_schedule"), ("다음 주 월요일 10시 팀 회의 추가해줘", "add_schedule"),
    ("오늘 일정 뭐 있어?", "get_schedule"), ("내일 스케줄 알려줘", "get_schedule"), ("이번 주 토요일에 약속 있었나?", "get_schedule"),
    ("내 자전거 비밀번호 4821이야 기억해줘", "add_memory"), ("와이파이 비번 sky7788 메모해줘", "add_memory"), ("우리 강아지 이름은 콩이야 저장해 둬", "add_memory"),
    ("자전거 비밀번호 뭐였지?", "search_memory"), ("내가 알려준 주차 위치 어디였지?", "search_memory"), ("전에 말한 사물함 번호 기억나?", "search_memory"),
    ("30분 뒤에 물 마시라고 알려줘", "add_reminder"), ("저녁 9시에 약 먹으라고 리마인드 해줘", "add_reminder"), ("내일 아침 8시에 출근 준비 알림 해줘", "add_reminder"),
    ("알림 뭐 있어?", "list_reminders"), ("설정해 둔 리마인더 목록 보여줘", "list_reminders"), ("오늘 남은 알림 있어?", "list_reminders"),
    ("고마워", None), ("오늘 기분이 좀 우울해", None), ("김치찌개 끓이는 법 알려줘", None), ("이 문장 영어로 번역해줘: 좋은 아침", None),
    ("점심 메뉴 추천해 줄래?", None), ("너는 누구야?", None),
    # 헷갈리는 것
    ("저녁 약속 언제였지?", "get_schedule"), ("치과 몇 시라고 했지?", "get_schedule"), ("3시에 알려줘", "add_reminder"),
    ("내일 회의 있는 거 기억해 둬", "add_schedule"), ("엄마 생일 언제였더라", "search_memory"), ("내일 일정에 뭐 적어 뒀더라", "get_schedule"),
    ("주차 위치 메모해 둔 거 알려줘", "search_memory"), ("알림 맞춰 둔 거 몇 시였지?", "list_reminders"),
]
SESSIONS = [
    ["오늘 일정 뭐 있어?", "3시 회의 끝나고 뭐 하지", "내일 오후 2시에 치과 예약 잡아줘", "고마워", "30분 뒤에 물 마시라고 알려줘", "알림 뭐 있어?"],
    ["내 자전거 비밀번호 4821이야 기억해줘", "고마워", "주차는 지하 2층 C-14에 했어", "자전거 비밀번호 뭐였지?", "오늘 기분이 좀 우울해", "점심 메뉴 추천해 줄래?"],
    ["김치찌개 끓이는 법 알려줘", "재료는 뭐가 필요해?", "돼지고기 대신 참치 넣어도 돼?", "고마워", "저녁 7시에 장보기 알림 해줘", "내일 일정 알려줘"],
    ["너는 누구야?", "뭘 할 수 있어?", "내일 아침 9시 팀 회의 추가해줘", "회의 몇 시라고 했지?", "와이파이 비번 sky7788 메모해줘", "고마워"],
]


def tool_text(t):
    rule = next((l.strip() for l in R.SYS_OFF.splitlines() if f"`{t.__name__}`" in l), "")
    return f"title: {t.__name__} | text: {(t.__doc__ or '').strip()} {rule}"


def preface_tokens(tools):
    eng = R.engine()
    with K.create_chat_conversation(eng, history=None, tools=tools or None, system_message=R.SYS_OFF) as conv:
        msg = "안녕"
        res = conv.send_message(msg)
        return conv.token_count - len(eng.tokenize(R.text_of(res))) - len(eng.tokenize(msg))


def part1():
    eng = R.engine()
    full = preface_tokens(TOOLS)
    print(f"=== [1] 툴별 앞부분 토큰 (전체 툴 앞부분 ≈ {full}) ===", flush=True)
    for t in TOOLS:
        without = preface_tokens([x for x in TOOLS if x is not t])
        rule = next((l for l in R.SYS_OFF.splitlines() if f"`{t.__name__}`" in l), "")
        print(f"  {t.__name__:16} 선언 ≈ {full - without:4d} · 트리거 줄 {len(eng.tokenize(rule)):3d}", flush=True)


def first_call(q):
    with K.create_chat_conversation(R.engine(), history=None, tools=TOOLS, system_message=R.SYS_OFF) as conv:
        res = conv.send_message(K.with_turn_reminder(q))
        cs = res.get("tool_calls") or []
        return cs[0]["function"]["name"] if cs else None


def part2():
    print("\n=== [2] 지금 모델의 툴 선택 (각 2회) ===", flush=True)
    wrong = 0
    for q, want in CASES:
        for rep in (1, 2):
            got = first_call(q)
            ok = got == want
            wrong += not ok
            if not ok:
                print(f"  틀림 [{q}] 정답={want} 고름={got}", flush=True)
    print(f"  => 틀림 {wrong}/{len(CASES) * 2}", flush=True)


def part3_4():
    ee = EE.EmbeddingEngine(str(MODEL), max_input_length=512)
    opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
    tvec = {t.__name__: list(ee.compute_embedding(tool_text(t), opts).embedding) for t in TOOLS}

    def rank(q):
        qv = list(ee.compute_embedding(f"task: search result | query: {q}", opts).embedding)
        return sorted(NAMES, key=lambda n: -sum(a * b for a, b in zip(qv, tvec[n])))

    print("\n=== [3] 임베딩 분류 (툴이 정답인 문항만) ===", flush=True)
    top1 = top3 = n = 0
    t0 = time.perf_counter()
    for q, want in CASES:
        r = rank(q)
        if want is None:
            continue
        n += 1
        top1 += r[0] == want
        top3 += want in r[:3]
        if want not in r[:3]:
            print(f"  상위3 밖 [{q}] 정답={want} 상위3={r[:3]}", flush=True)
    ms = (time.perf_counter() - t0) * 1000 / len(CASES)
    print(f"  => 1위 {top1}/{n} · 상위3 {top3}/{n} · 질의당 약 {ms:.0f}ms(PC CPU)", flush=True)

    print("\n=== [4] 이어지는 대화에서 상위 3 묶음 변화 ===", flush=True)
    changes = turns = 0
    for s in SESSIONS:
        prev = None
        for q in s:
            cur = frozenset(rank(q)[:3])
            if prev is not None:
                turns += 1
                changes += cur != prev
            prev = cur
    print(f"  => 묶음이 바뀐 턴 {changes}/{turns}", flush=True)


if __name__ == "__main__":
    part1()
    part2()
    part3_4()

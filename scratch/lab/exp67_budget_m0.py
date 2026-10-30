"""exp67 — 0.39.3 M0: 고정 몫은 어디에 쓰이고, 대화 기록 자리는 몇 턴인가 (코드 수정 0).

1 턴당 KV 증가 분해 — 재사용 대화에서 턴마다 token_count 증가 vs tokenize(리마인더) · tokenize(사용자 말) · tokenize(답).
2 선언의 부분별 비용 — 툴 하나를 인자·설명·예시를 바꿔 선언했을 때 첫 턴 KV 차이(빈 대화 기준).
3 몇 턴 만에 정리 기준(2,000·2,500)을 넘는가 — 짧은 일상 대화 · 툴을 쓰는 대화.
4 선택 인자 되묻기 — 기록이 있는 대화에서 "모레 오전 10시에 치과 예약 잡아줘" 류가 등록 대신 묻는 빈도.
"""
import json
import pathlib
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402 — 엔진·픽스처·턴 흉내 재사용
import kosmos_lab as K  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

E = R.engine()
tok = lambda s: len(E.tokenize(s))  # noqa: E731
REMINDER = K.fixture("turn_reminder.txt")


def first_kv(tools, sysm="x"):
    c = K.create_chat_conversation(E, tools=tools, system_message=sysm)
    c.send_message("안녕")
    t = c.token_count
    c.close()
    return t


print("== 1 턴당 KV 증가 분해 (재사용 대화, 툴 선언·지시 = 앱과 같음)", flush=True)
print(f"리마인더 토큰 {tok(REMINDER)}", flush=True)
conv = K.create_chat_conversation(E, tools=R.TOOLS_OFF, system_message=R.SYS_OFF)
prev = None
for u in ["안녕! 한 문장으로 답해 줘.", "점심 메뉴 하나만 추천해 줘.", "주말에 뭐 하면 좋을까? 짧게.", "고마워, 오늘은 여기까지."]:
    res = conv.send_message(K.with_turn_reminder(u))
    kv = conv.token_count
    ans = R.text_of(res)
    if prev is not None:
        print(f"  +{kv - prev:4d} = 리마인더 {tok(REMINDER)} + 사용자 {tok(u)} + 답 {tok(ans)} + 나머지 {kv - prev - tok(REMINDER) - tok(u) - tok(ans)}  ({u})", flush=True)
    else:
        print(f"  첫 턴 KV {kv}", flush=True)
    prev = kv
conv.close()

print("\n== 2 선언 부분별 비용 (첫 턴 KV, 빈 지시)", flush=True)
base = first_kv(None)


def schedule_full(title: str, start_time: str, end_time: str | None, memo: str | None) -> dict:
    """사용자의 캘린더에 일정을 추가한다.

    Args:
        title: 일정 제목. 예: '치과 예약'
        start_time: 시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'
        end_time: 종료 시각. ISO 8601 형식. 모르면 시작 1시간 뒤.
        memo: 메모. 없으면 빈 문자열.
    """


def schedule_no_examples(title: str, start_time: str, end_time: str | None, memo: str | None) -> dict:
    """사용자의 캘린더에 일정을 추가한다.

    Args:
        title: 일정 제목.
        start_time: 시작 시각. ISO 8601 형식.
        end_time: 종료 시각. ISO 8601 형식. 모르면 시작 1시간 뒤.
        memo: 메모. 없으면 빈 문자열.
    """


def schedule_two_params(title: str, start_time: str) -> dict:
    """사용자의 캘린더에 일정을 추가한다.

    Args:
        title: 일정 제목. 예: '치과 예약'
        start_time: 시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'
    """


def schedule_no_desc(title: str, start_time: str, end_time: str | None, memo: str | None) -> dict:
    """사용자의 캘린더에 일정을 추가한다."""


for f in (schedule_full, schedule_no_examples, schedule_two_params, schedule_no_desc):
    print(f"  {f.__name__:22s} {first_kv([f]) - base}", flush=True)
c = K.create_chat_conversation(E, tools=[schedule_full], system_message="x")
pre = c.render_message_to_string(llm.Message.user(llm.Contents([llm.Content.Text("안녕")])))
c.close()
print("  렌더 예:", pre[:400].replace("\n", " "), flush=True)

print("\n== 3 몇 턴 만에 정리 기준을 넘는가 (첫 KV 포함, 재사용 대화)", flush=True)
CHAT = ["안녕! 오늘 기분 어때?", "점심 메뉴 하나만 추천해 줘.", "김치찌개 끓이는 법 간단히 알려줘", "주말에 뭐 하면 좋을까?", "회사 동료 생일 선물 뭐가 좋을까?",
        "요즘 잠이 잘 안 와", "고마워 도움이 됐어", "내일 날씨 좋으면 뭐 할까?", "운동 처음 시작하는데 팁 있어?", "오늘 저녁엔 뭐 먹지?"]
conv = K.create_chat_conversation(E, tools=R.TOOLS_OFF, system_message=R.SYS_OFF)
crossed = {}
for i, u in enumerate(CHAT, 1):
    conv.send_message(K.with_turn_reminder(u))
    kv = conv.token_count
    for th in (2000, 2500, 2800):
        if kv > th and th not in crossed:
            crossed[th] = i
    print(f"  {i:2d}턴 KV {kv}", flush=True)
conv.close()
print("  기준을 넘은 턴:", json.dumps(crossed), flush=True)

print("\n== 4 선택 인자 되묻기 (기록이 있는 대화)", flush=True)
ASKS = ["모레 오전 10시에 치과 예약 잡아줘", "다음 주 화요일 3시에 미용실 예약 잡아줘", "내일 저녁 7시 친구랑 약속 일정 잡아줘", "금요일 오후 2시 병원 예약 추가해줘"]
HISTS = {"새 대화": None, "거절 없는 3턴": R.BEFORE + [R.M("user", "점심 메뉴 추천해 줘"), R.M("model", "따뜻한 김치찌개 어떠세요?")],
         "사실 3쌍": R.fact_history(3), "일정 거절 뒤": R.BEFORE + [R.M("user", "내일 오후 2시에 팀 회의 잡아줘"), R.M("model", "일정을 추가하지 않았어요.")]}
asked = 0
total = 0
for hname, h in HISTS.items():
    for q in ASKS:
        ans, _, _, first = R.turn(h, q)
        ok = first is not None and first["name"] == "add_schedule"
        total += 1
        asked += not ok
        if not ok:
            print(f"  [{hname}] {q} → {str(first)[:40]} | {ans[:50]}", flush=True)
print(f"  등록 대신 다른 답 {asked}/{total}", flush=True)

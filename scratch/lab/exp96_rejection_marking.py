"""exp96 — 0.42.3 M1: 승인 시트 거절을 요약·주간 회고가 어떻게 쓰나(전사 표시 방식 비교).

조건
  now : 지금 — 거절 답이 평범한 비서 줄("비서: 일정을 추가하지 않았어요.")
  A   : 비서 줄 앞 표지 — "비서: (사용자가 승인 창에서 취소함) 일정을 추가하지 않았어요."
  B   : 사용자 줄로 바꿔 쓰기 — "사용자: (승인 창에서 일정 추가를 취소함)" (비서 거절 줄 대신)
대화는 합성 10개(실제 대화 데이터 아님). 요약 지시는 앱이 내보낸 픽스처(episode_summary_system.txt), 회고는 review_system.txt.
판정(자동 + 원문 출력): 거절한 항목을 취소로 씀 / 실패("못", "실패")로 씀 / 저장·추가된 것처럼 씀.
사용: python exp96_rejection_marking.py > exp96_out.txt
"""
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
SUM_SYS = K.fixture("episode_summary_system.txt")
REV_SYS = K.fixture("review_system.txt")

REJECT = {  # 앱 BaseAgent.rejectionReply 원문
    "schedule": "일정을 추가하지 않았어요.",
    "reminder": "알림을 등록하지 않았어요.",
    "memo": "메모에는 남기지 않았어요.",
}
CANCEL_LINE = {
    "schedule": "(승인 창에서 일정 추가를 취소함)",
    "reminder": "(승인 창에서 알림 등록을 취소함)",
    "memo": "(승인 창에서 메모 저장을 취소함)",
}

# (역할, 글) — 역할 "x:종류" 는 거절 답 자리. key = 거절한 항목을 가리키는 낱말(판정용).
CONVS = [
    ("일정 거절", "저녁 약속", [("u", "금요일 저녁 7시 친구들이랑 저녁 약속 잡아줘"), ("x:schedule", ""), ("u", "고마워"), ("a", "네, 또 필요하시면 말씀해 주세요.")]),
    ("메모 거절", "주차", [("u", "주차는 지하 2층 B2-17에 했어, 기억해줘"), ("x:memo", ""), ("u", "응 괜찮아"), ("a", "알겠어요. 필요하시면 언제든 말씀해 주세요.")]),
    ("알림 거절", "약", [("u", "오후 3시에 약 먹으라고 알려줘"), ("x:reminder", ""), ("u", "아 그건 내가 알아서 할게"), ("a", "네, 알겠어요.")]),
    ("거절 뒤 승인", "치과", [("u", "내일 오후 2시에 치과 예약 잡아줘"), ("x:schedule", ""), ("u", "아 아니다 3시로 잡아줘"), ("a", "내일 오후 3시에 치과 예약을 추가했어요.")]),
    ("승인만(대조)", "", [("u", "다음 주 월요일 오전 10시에 팀 회의 잡아줘"), ("a", "다음 주 월요일 오전 10시에 팀 회의를 추가했어요."), ("u", "고마워"), ("a", "천만에요!")]),
    ("일정 승인 + 메모 거절", "와이파이", [("u", "토요일 오후 1시에 부모님 댁 방문 일정 넣어줘"), ("a", "토요일 오후 1시에 부모님 댁 방문을 추가했어요."), ("u", "부모님 댁 와이파이 비번은 home2580이야 저장해줘"), ("x:memo", "")]),
    ("메모 거절 뒤 잡담", "자전거", [("u", "자전거 자물쇠 번호 4821 메모해줘"), ("x:memo", ""), ("u", "그나저나 김치찌개 맛있게 끓이는 법 알려줘"), ("a", "김치는 충분히 익은 것을 쓰고, 돼지고기를 먼저 볶은 뒤 물을 부어 끓이면 맛이 깊어져요.")]),
    ("같은 일정 두 번 거절", "저녁 식사", [("u", "금요일 저녁 7시에 친구들이랑 저녁 식사 일정 추가해줘"), ("x:schedule", ""), ("u", "금요일 저녁 7시 친구들이랑 저녁 식사 일정 추가해줘"), ("x:schedule", "")]),
    ("알림 승인 + 일정 거절", "헬스", [("u", "내일 아침 7시에 일어나라고 알려줘"), ("a", "내일 오전 7시에 알림을 등록했어요."), ("u", "그리고 내일 저녁 8시 헬스 일정도 잡아줘"), ("x:schedule", "")]),
    ("메모 거절 뒤 다시 승인", "택배", [("u", "택배 송장번호 6043-2210 저장해줘"), ("x:memo", ""), ("u", "아니다 저장해줘"), ("a", "택배 송장번호 6043-2210을 메모에 저장했어요.")]),
]


def transcript(conv, cond):
    out = []
    for role, text in conv:
        if role == "u":
            out.append(f"사용자: {text}")
        elif role == "a":
            out.append(f"비서: {text}")
        else:
            kind = role.split(":")[1]
            if cond == "now":
                out.append(f"비서: {REJECT[kind]}")
            elif cond == "A":
                out.append(f"비서: (사용자가 승인 창에서 취소함) {REJECT[kind]}")
            else:
                out.append(f"사용자: {CANCEL_LINE[kind]}")
    return "\n".join(out)


def gen(system, user):
    with K.create_chat_conversation(R.engine(), tools=None, system_message=system) as conv:
        return R.text_of(conv.send_message(user))


FAIL = re.compile(r"못\s*했|못했|못\s*하|하지\s*못|실패|오류|문제가")
CANCEL = re.compile(r"취소|원하지\s*않|거절|그만두|필요\s*없")
DONE = re.compile(r"(추가|저장|등록|메모)(해\s*)?(했|하셨|됐|되었|해 두었|해 뒀|해 드렸)")


def judge(text, key):
    """거절 항목(key)이 든 문장만 본다 — 실패로 씀 / 취소로 씀 / 된 것처럼 씀."""
    if not key:
        return {"fail": False, "cancel": False, "done": False}
    sents = [s for s in re.split(r"(?<=[.!?。])\s+|\n", text) if key in s]
    joined = " ".join(sents)
    return {
        "fail": bool(FAIL.search(joined)),
        "cancel": bool(CANCEL.search(joined)),
        "done": any(DONE.search(s) and not CANCEL.search(s) and not FAIL.search(s) and "않" not in s for s in sents),
    }


def main():
    totals = {}
    for cond in ("now", "A", "B"):
        t = {"sum": {"fail": 0, "cancel": 0, "done": 0}, "rev": {"fail": 0, "cancel": 0, "done": 0}, "n": 0}
        print(f"\n######## 조건 {cond}", flush=True)
        for name, key, conv in CONVS:
            summary = gen(SUM_SYS, "다음 대화를 요약하세요.\n\n" + transcript(conv, cond))
            body = re.search(r"요약\s*[:：]\s*(.+)", summary, re.S)
            body = body.group(1).strip() if body else summary
            title = re.search(r"제목\s*[:：]\s*(.+)", summary)
            title = title.group(1).strip() if title else name
            review = gen(REV_SYS, f"지난 7일 대화 요약:\n- 11월 17일 · {title}: {body}")
            js, jr = judge(body, key), judge(review, key)
            if key:
                t["n"] += 1
                for k in ("fail", "cancel", "done"):
                    t["sum"][k] += js[k]
                    t["rev"][k] += jr[k]
            print(f"\n== [{cond}] {name} (항목 '{key}')\n-- 요약 판정 {js}\n{summary}\n-- 회고 판정 {jr}\n{review}", flush=True)
        totals[cond] = t
    print("\n######## 합계 (거절이 든 대화 수 n)", flush=True)
    for cond, t in totals.items():
        print(f"{cond}: n={t['n']} 요약 실패 {t['sum']['fail']} 취소 {t['sum']['cancel']} 된것처럼 {t['sum']['done']} | "
              f"회고 실패 {t['rev']['fail']} 취소 {t['rev']['cancel']} 된것처럼 {t['rev']['done']}", flush=True)


if __name__ == "__main__":
    main()

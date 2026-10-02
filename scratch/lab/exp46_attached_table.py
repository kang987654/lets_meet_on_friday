"""exp46 — 0.35.0 M0: 채팅 첨부 표(300자 상한)를 어떤 형식으로 넣어야 모델이 잘 읽는가.

## 입력 구조 (앱과 동일)
AssistantOrchestrator 가 사용자 질문을 저장한 뒤 `[Attached Document]\\n\"\"\"\\n첨부된 문서 내용(파일명):\\n<본문>\\n\"\"\"` 를
USER 메시지로 하나 더 저장한다. PromptAssembler 는 히스토리 마지막이 현재 입력과 다르므로 둘 다 히스토리에 두고, 현재 턴은
턴 리마인더 + 질문이다. 본문은 `Constants.MAX_ATTACHED_DOC_CHARS = 300` 자 — 행 단위로 끊는다(행 중간을 자르지 않음).

## 형식
- M 마크다운 표(`| a | b |` + 구분선)
- T 탭 구분(TSV)
- K "열머리: 값" 한 줄에 한 행

## 채점
질문 4종(특정 칸·합계·최댓값 행·조건 개수). 정답은 **그 형식에서 300자 안에 실제로 들어간 행**으로 계산한다(못 본 행은 묻지 않은
것과 같다). 답에서 쉼표·공백을 지운 뒤 정답 문자열이 들어 있으면 정답. 툴을 부르면 오답(문서 질문에 툴은 오답 경로).
형식별 정답 수와 300자에 들어간 평균 행 수를 함께 보고 — 정답 수 우선, 동률이면 행이 많은(짧은) 형식.
"""
import sys

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

CAP = 300

TABLES = {
    "가계부.xlsx": (["날짜", "항목", "금액"], [
        ["10-01", "커피", "4500"], ["10-01", "점심", "9000"], ["10-02", "택시", "12000"], ["10-03", "책", "18000"],
        ["10-03", "커피", "5000"], ["10-04", "마트", "32000"], ["10-05", "영화", "15000"], ["10-05", "커피", "4500"],
        ["10-06", "통신비", "55000"], ["10-07", "점심", "8500"], ["10-08", "커피", "4800"], ["10-09", "택시", "9800"],
    ]),
    "성적.xlsx": (["이름", "국어", "수학", "영어"], [
        ["김민수", "88", "92", "75"], ["이서연", "95", "81", "90"], ["박지훈", "72", "99", "84"], ["최유진", "90", "70", "93"],
        ["정하늘", "65", "88", "79"], ["강도윤", "83", "77", "96"], ["윤서아", "91", "94", "88"], ["임준호", "78", "85", "70"],
        ["한지민", "86", "73", "82"], ["오세훈", "70", "90", "77"],
    ]),
    "재고.xlsx": (["품목", "수량", "단가"], [
        ["볼펜", "120", "500"], ["노트", "45", "2000"], ["지우개", "200", "300"], ["가위", "12", "3500"], ["풀", "60", "800"],
        ["테이프", "30", "1500"], ["형광펜", "75", "900"], ["자", "25", "1200"], ["클립", "500", "20"], ["파일", "40", "2500"],
    ]),
    "일정.xlsx": (["날짜", "시간", "내용"], [
        ["10-01", "09:00", "팀 회의"], ["10-01", "14:00", "치과"], ["10-02", "10:30", "고객 미팅"], ["10-03", "19:00", "저녁 약속"],
        ["10-04", "08:00", "러닝"], ["10-05", "15:00", "회의"], ["10-06", "11:00", "병원"], ["10-07", "09:00", "팀 회의"],
        ["10-08", "18:30", "운동"], ["10-09", "13:00", "점심 약속"],
    ]),
    "연락처.xlsx": (["이름", "부서", "내선"], [
        ["김철수", "개발", "1201"], ["이영희", "디자인", "1302"], ["박민준", "개발", "1203"], ["최수빈", "영업", "1401"],
        ["정다은", "인사", "1501"], ["강현우", "개발", "1205"], ["조예린", "영업", "1402"], ["윤태양", "디자인", "1305"],
        ["장서윤", "개발", "1208"], ["임하준", "영업", "1405"], ["한유나", "인사", "1503"], ["오지호", "개발", "1210"],
    ]),
}


def render(fmt, header, rows):
    if fmt == "M":
        head = "| " + " | ".join(header) + " |\n|" + "---|" * len(header)
        line = lambda r: "| " + " | ".join(r) + " |"
    elif fmt == "T":
        head = "\t".join(header)
        line = lambda r: "\t".join(r)
    else:
        head = None
        line = lambda r: ", ".join(f"{h}: {v}" for h, v in zip(header, r))
    text = head or ""
    used = []
    for r in rows:
        nxt = (text + "\n" if text else "") + line(r)
        if len(nxt) > CAP:
            break
        text = nxt
        used.append(r)
    return text, used


def questions(name, used):
    """(질문, 정답 문자열) — 정답은 used(300자 안에 들어간 행)로 계산."""
    col = lambda i: [r[i] for r in used]
    if name == "가계부.xlsx":
        biggest = max(used, key=lambda r: int(r[2]))
        return [("10월 3일에 산 책은 얼마였어?", "18000"),
                ("첨부한 가계부 지출 합계가 얼마야?", str(sum(int(x) for x in col(2)))),
                ("가장 큰 지출 항목이 뭐야?", biggest[1]),
                ("커피는 몇 번 샀어?", f"{col(1).count('커피')}번")]
    if name == "성적.xlsx":
        top = max(used, key=lambda r: int(r[2]))
        return [("이서연 영어 점수가 몇 점이야?", "90"),
                ("수학 점수가 가장 높은 사람은 누구야?", top[0]),
                ("국어 점수 합계가 얼마야?", str(sum(int(x) for x in col(1)))),
                ("국어 90점 이상인 사람은 몇 명이야?", f"{sum(int(x) >= 90 for x in col(1))}명")]
    if name == "재고.xlsx":
        most = max(used, key=lambda r: int(r[1]))
        return [("가위 단가가 얼마야?", "3500"),
                ("수량이 가장 많은 품목은?", most[0]),
                ("전체 수량 합계는?", str(sum(int(x) for x in col(1)))),
                ("단가가 1000원 이상인 품목은 몇 개야?", f"{sum(int(x) >= 1000 for x in col(2))}개")]
    if name == "일정.xlsx":
        return [("10월 3일 일정이 뭐야?", "저녁 약속"),
                ("치과는 몇 시야?", "14:00"),
                ("팀 회의는 몇 번 있어?", f"{col(2).count('팀 회의')}번"),
                ("10월 1일에 일정이 몇 개야?", f"{col(0).count('10-01')}개")]
    return [("박민준 내선 번호가 뭐야?", "1203"),
            ("최수빈은 무슨 부서야?", "영업"),
            ("개발 부서는 몇 명이야?", f"{col(1).count('개발')}명"),
            ("내선 1302 는 누구야?", "이영희")]


def norm(s):
    return s.replace(",", "").replace(" ", "").replace(" ", "")


def correct(answer, expected):
    a = norm(answer)
    e = norm(expected)
    if e in a:
        return True
    # "3번" 을 "세 번"·"3회"로 답한 경우 — 숫자만 맞으면 정답
    digits = "".join(ch for ch in expected if ch.isdigit())
    if digits and expected[-1] in "번명개":
        return digits + "번" in a or digits + "회" in a or digits + "명" in a or digits + "개" in a or digits + "건" in a
    return False


if __name__ == "__main__":
    engine = K.create_engine()
    SYSTEM = K.fixture("system_instruction.txt")
    summary = {}
    for fmt in "MTK":
        ok = total = 0
        rows_seen = []
        for name, (header, rows) in TABLES.items():
            body, used = render(fmt, header, rows)
            rows_seen.append(len(used))
            doc = f'[Attached Document]\n"""\n첨부된 문서 내용({name}):\n{body}\n"""'
            for q, expected in questions(name, used):
                history = [llm.Message.user(q), llm.Message.user(doc)]
                with K.create_chat_conversation(engine, history=history, system_message=SYSTEM) as conv:
                    res = conv.send_message(K.with_turn_reminder(q))
                calls = res.get("tool_calls") or []
                text = "".join(c.get("text", "") for c in (res.get("content") or []) if isinstance(c, dict)) if not isinstance(res.get("content"), str) else res["content"]
                hit = not calls and correct(text, expected)
                ok += hit
                total += 1
                if not hit:
                    shown = f"툴 {calls[0]['function']['name']}" if calls else text.replace("\n", " ")[:80]
                    print(f"  {fmt} X {name} {q!r} 기대 {expected!r} -> {shown}", flush=True)
        summary[fmt] = (ok, total, sum(rows_seen) / len(rows_seen))
        print(f"== {fmt}: 정답 {ok}/{total}, 300자에 든 평균 행 {summary[fmt][2]:.1f}", flush=True)

    # docx 문단 스모크 3건 — 형식 선택지가 없어 답을 눈으로 본다
    PARA = ("회의록 2026-10-02\n참석: 김민수, 이서연, 박지훈\n안건 1. 4분기 예산 — 마케팅 예산을 10% 늘리기로 했다.\n"
            "안건 2. 신규 채용 — 개발자 2명을 11월까지 뽑는다.\n다음 회의는 10월 16일 오후 2시.")
    for q in ["이 회의록 요약해줘", "다음 회의 언제야?", "채용은 몇 명이야?"]:
        doc = f'[Attached Document]\n"""\n첨부된 문서 내용(회의록.docx):\n{PARA[:CAP]}\n"""'
        with K.create_chat_conversation(engine, history=[llm.Message.user(q), llm.Message.user(doc)], system_message=SYSTEM) as conv:
            res = conv.send_message(K.with_turn_reminder(q))
        calls = res.get("tool_calls") or []
        content = res.get("content")
        text = content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))
        print(f"  문단 {q!r} -> {('툴 ' + calls[0]['function']['name']) if calls else text.replace(chr(10), ' ')[:120]}", flush=True)
    print("끝", summary)

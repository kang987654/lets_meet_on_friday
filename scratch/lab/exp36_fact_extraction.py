"""C′2 M0: 리셋 시점 자동 추출 프롬프트의 형식 준수·회수·오추출 실측.

배경: 에피소드가 요약(SUMMARIZED)될 때 oneShot 1회를 더 돌려 "앞으로도 기억할 사실 0~3개"를
뽑는다 — 프로필 급(항목: 값)은 승인 카드, 지식 급은 source=auto 로 자동 저장. 이 실험은
E4B 가 그 두 라벨 형식을 일관되게 지키는지, 심은 사실을 회수하는지, 잡담에서 "없음"을
지키는지(거짓 양성) 잰다.

입력: ① 실대화 픽스처(kosmos_db, exp33 과 같은 30분 분절 → 9 에피소드, gitignore 영역)
      ② 합성 에피소드 6개(정답 심기 — 프로필 3 / 지식 4 / 잡담·일회성 2)

게이트(계획서 M0): 형식 준수 ≥ 14/15, 심은 사실 회수 ≥ 7/8, 잡담 오추출 ≤ 1건.
실패 → ① 라벨 단일화("사실:") ② 회차 중단·보고.

사용:
  ../.venv/Scripts/python.exe exp36_fact_extraction.py            # 초안 프롬프트
  ../.venv/Scripts/python.exe exp36_fact_extraction.py fixture    # 앱 픽스처(extract_system.txt) 미러 확인
"""
import json
import os
import re
import sqlite3
import sys

import kosmos_lab as K

# [WHY] Windows 콘솔 기본 cp949 — 한글·'—' 출력이 깨지거나 UnicodeEncodeError 로 죽는다.
sys.stdout.reconfigure(encoding="utf-8")

DB = os.path.join(os.path.dirname(__file__), "fixtures", "kosmos_db")
OUT = os.path.join(os.path.dirname(__file__), "exp36_facts.json")  # 실대화 파생 → gitignore
GAP_MS = 30 * 60_000

# --- 프롬프트 (M1 에서 ExtractFactsUseCase.SYSTEM_INSTRUCTION 으로 원문 그대로 옮긴다) ---
# v1(초안) 판정 2026-09-02: 형식 15/15, 회수 6/8, 오추출 2 — 실패. 원인 ① 모델이 프롬프트의
# "규칙:" 라벨을 셋째 줄로 따라 써서("규칙: 없음") 지식 절에 섞임 ② 일회성 일정(치과 예약)을
# 지식으로 뽑음. v2: 규칙을 라벨 없는 문장으로 옮기고 "예약·약속" 제외를 명시.
SYSTEM = """[System]
You extract durable facts about the user from a conversation log.
당신은 대화 기록에서 앞으로도 유효한 사용자 관련 사실을 골라내는 기록 담당자입니다.
대화에 명시된 사실만 고르고, 추측·일반 상식·일회성 잡담·비서의 발언은 제외합니다. 예약·약속처럼 한 번 지나가는 일정은 캘린더가 맡으므로 제외합니다. 날짜·숫자는 원문 그대로, 사실 하나는 한 줄에 하나씩 씁니다.
아래 형식만 출력하세요.
프로필: (이름·호칭·말투 선호·직업·거주지·가족처럼 항상 관련 있는 사실 — "항목: 값" 형태, 최대 2개, 없으면 "없음")
지식: (특정 상황에서만 필요한 사실 — 비밀번호·번호·장소·습관·반복 일정, 최대 3개, 없으면 "없음")"""

USER_PREFIX = "다음 대화에서 기억할 사실을 골라내세요.\n\n"


# --------------------------------------------------------------------------
# 입력 ① 실대화 에피소드 (exp33.segment 재구현 — exp33 은 폐기된 K.Lab API 에 묶여 import 불가)
# --------------------------------------------------------------------------

def real_episodes():
    con = sqlite3.connect(DB)
    try:
        rows = list(con.execute(
            "select role, content, createdAt from conversation order by createdAt, rowid"))
    finally:
        con.close()
    eps, cur, prev = [], [], None
    for role, content, ts in rows:
        if prev is not None and (ts - prev) > GAP_MS and cur:
            eps.append(cur)
            cur = []
        cur.append((role, content))
        prev = ts
    if cur:
        eps.append(cur)
    return eps


# --------------------------------------------------------------------------
# 입력 ② 합성 에피소드 — 정답을 심는다
#   expect_profile: 값에 포함돼야 하는 문자열 (키는 모델 자유)
#   expect_knowledge: 지식 문장에 포함돼야 하는 문자열
#   expect_none: 두 절 모두 "없음" 이어야 한다 (거짓 양성 판정)
# --------------------------------------------------------------------------

SYNTH = [
    dict(name="자기소개",
         turns=[("USER", "안녕, 나는 진우야. 앞으로 진우님이라고 불러줘."),
                ("ASSISTANT", "네 진우님, 반가워요! 무엇을 도와드릴까요?"),
                ("USER", "나 개발자인데 온디바이스 AI 만들고 있어. 말은 편하게 반말로 해줘."),
                ("ASSISTANT", "알겠어, 진우. 온디바이스 AI 개발 멋지다. 궁금한 거 있으면 물어봐.")],
         expect_profile=["진우", "반말"], expect_knowledge=[], expect_none=False),
    dict(name="비밀번호",
         turns=[("USER", "자전거 자물쇠 비밀번호 4936이야. 기억해둬."),
                ("ASSISTANT", "자전거 자물쇠 비밀번호 4936, 기억할게요."),
                ("USER", "그리고 집 와이파이는 kosmos123"),
                ("ASSISTANT", "집 와이파이 비밀번호 kosmos123 도 기억했어요.")],
         expect_profile=[], expect_knowledge=["4936", "kosmos123"], expect_none=False),
    dict(name="습관·장소",
         turns=[("USER", "회사 회식은 매달 마지막 금요일에 삼겹살집에서 해. 강남역 근처 고기굽는집."),
                ("ASSISTANT", "매달 마지막 금요일 강남역 고기굽는집 회식, 알아둘게요."),
                ("USER", "다음 회식 때 말해줘"),
                ("ASSISTANT", "네, 다음 마지막 금요일이 오면 알려드릴게요.")],
         expect_profile=[], expect_knowledge=["금요일", ("고기굽는집", "삼겹살")], expect_none=False),
    dict(name="가족·거주",
         turns=[("USER", "나 부산 살고 있고 어머니 생신이 3월 12일이야."),
                ("ASSISTANT", "부산 거주, 어머니 생신 3월 12일 — 기억할게요."),
                ("USER", "그날 꽃 주문하는 거 잊지 말라고 해줘"),
                ("ASSISTANT", "3월 12일 전에 꽃 주문을 상기시켜 드릴게요.")],
         expect_profile=["부산"], expect_knowledge=["3월 12일"], expect_none=False),
    dict(name="잡담",
         turns=[("USER", "오늘 날씨 어때?"),
                ("ASSISTANT", "죄송해요, 실시간 날씨는 조회할 수 없어요. 날씨 앱을 확인해 주세요."),
                ("USER", "ㅋㅋ 그렇구나. 아무 노래나 추천해줘"),
                ("ASSISTANT", "잔잔한 피아노 곡은 어떠세요? 집중할 때 좋아요.")],
         expect_profile=[], expect_knowledge=[], expect_none=True),
    dict(name="일회성 일정",
         turns=[("USER", "내일 3시에 치과 예약 잡아줘"),
                ("ASSISTANT", "내일 오후 3시 치과 예약을 등록했어요."),
                ("USER", "고마워"),
                ("ASSISTANT", "네, 잊지 않게 알려드릴게요.")],
         expect_profile=[], expect_knowledge=[], expect_none=True),
]


# --------------------------------------------------------------------------
# 공통
# --------------------------------------------------------------------------

def transcript(turns, max_chars=2400):
    """SummarizeEpisodeUseCase.buildTranscript 미러 — 사용자:/비서: 라벨, 앞쪽 보존."""
    lines = [f"{'사용자' if r == 'USER' else '비서'}: {c}" for r, c in turns]
    return "\n".join(lines)[:max_chars]


def parse(text):
    """ExtractFactsUseCase.parse 미러 예정.

    [WHY] v2 실측에서 모델은 ① 항목마다 라벨을 반복하고("지식: A\n지식: B") ② 빈 절의
    "프로필: 없음" 줄을 생략하고 ③ 한 줄에 여러 항목을 쉼표로 이어 쓴다("이름: 진우, 직업: 개발자").
    셋 다 내용은 온전하므로 파서가 수용한다 — 형식 준수 = 라벨 줄이 1개 이상.
    """
    prof_items, know_items = [], []
    labeled = 0
    for ln in text.splitlines():
        m = re.match(r"^\s*[-*•]?\s*(프로필|지식)\s*[:：]\s*(.*)$", ln)
        if not m:
            continue
        labeled += 1
        label, body = m.group(1), m.group(2).strip()
        if not body or body.startswith("없음"):
            continue
        (prof_items if label == "프로필" else know_items).append(body)
    format_ok = labeled >= 1
    prof_kv = []
    for body in prof_items:
        # 쉼표 분리 — 콜론이 없는 조각은 앞 항목의 값에 붙인다(값 안의 쉼표 보존).
        pieces = [x.strip() for x in body.split(",")]
        for piece in pieces:
            if re.search(r"[:：]", piece):
                k, v = re.split(r"[:：]", piece, maxsplit=1)
                if k.strip() and v.strip():
                    prof_kv.append((k.strip(), v.strip()))
            elif prof_kv and piece:
                prof_kv[-1] = (prof_kv[-1][0], prof_kv[-1][1] + ", " + piece)
    return format_ok, prof_kv, know_items


def run(system):
    engine = K.create_engine()
    results = []

    def ask(turns):
        with K.create_chat_conversation(engine, tools=[], system_message=system) as conv:
            res = conv.send_message(USER_PREFIX + transcript(turns))
            # 응답 구조: {"role": "assistant", "content": [{"type": "text", "text": "..."}]}
            parts = res.get("content", []) if isinstance(res, dict) else []
            return "".join(p.get("text", "") for p in parts if p.get("type") == "text") or str(res)

    # ① 실대화 — 육안 판정 + 형식 준수만 집계 (정답 없음)
    for i, ep in enumerate(real_episodes()):
        raw = ask(ep)
        ok, prof, know = parse(raw)
        results.append({"kind": "real", "id": i, "format_ok": ok, "profile": prof, "knowledge": know, "raw": raw})
        print(f"[real {i}] format_ok={ok} 프로필 {len(prof)} 지식 {len(know)} :: "
              f"{' | '.join(f'{k}: {v}' for k, v in prof)[:60]} || {' | '.join(know)[:80]}", flush=True)

    # ② 합성 — 회수·오추출 판정
    recall_hit = recall_total = class_hit = 0
    false_pos = 0
    for s in SYNTH:
        raw = ask(s["turns"])
        ok, prof, know = parse(raw)
        joined_prof = " ".join(f"{k} {v}" for k, v in prof)
        joined_know = " ".join(know)
        joined_all = joined_prof + " " + joined_know

        def found(e, hay):
            alts = e if isinstance(e, tuple) else (e,)
            return any(a in hay for a in alts)
        expected = s["expect_profile"] + s["expect_knowledge"]
        # 회수 = 어느 절에든 있으면 인정(저장은 되므로) / 분류 = 의도한 절에 있는가
        hits = [e for e in expected if found(e, joined_all)]
        classified = [e for e in s["expect_profile"] if found(e, joined_prof)] +                      [e for e in s["expect_knowledge"] if found(e, joined_know)]
        recall_hit += len(hits)
        recall_total += len(expected)
        class_hit += len(classified)
        fp = s["expect_none"] and (prof or know)
        false_pos += bool(fp)
        results.append({"kind": "synth", "id": s["name"], "format_ok": ok, "profile": prof,
                        "knowledge": know, "hits": [str(h) for h in hits], "classified": [str(c) for c in classified], "expected": [str(e) for e in expected], "false_pos": bool(fp), "raw": raw})
        print(f"[synth {s['name']}] format_ok={ok} 회수 {len(hits)}/{len(expected)} 분류 {len(classified)}/{len(expected)}"
              f"{' [오추출!]' if fp else ''} :: {' | '.join(f'{k}: {v}' for k, v in prof)} || {' | '.join(know)}", flush=True)

    n_fmt = sum(1 for r in results if r["format_ok"])
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=1)
    print(f"\n형식 준수 {n_fmt}/{len(results)} (게이트 ≥14/15)  "
          f"회수 {recall_hit}/{recall_total} (게이트 ≥7/8)  분류 {class_hit}/{recall_total} (기록용)  잡담 오추출 {false_pos} (게이트 ≤1)  → {OUT}")
    gate = n_fmt >= 14 and recall_hit >= 7 and false_pos <= 1
    print("판정:", "통과 — 이 문구를 ExtractFactsUseCase 로 옮긴다" if gate else "실패 — 폴백 ① 라벨 단일화 검토")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "fixture":
        # M2 이후: 앱이 내보낸 픽스처를 읽어 미러가 어긋나지 않았는지 확인
        sys_text = K.fixture("extract_system.txt")
        assert sys_text.strip() == SYSTEM.strip(), "픽스처와 실험 프롬프트가 다르다 — 한쪽을 갱신할 것"
        print("픽스처 == 실험 프롬프트 (미러 일치)")
        run(sys_text)
    else:
        run(SYSTEM)

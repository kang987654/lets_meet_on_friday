"""exp71 — 0.39.6 M2: 쓰기 시점 상위 개념·유의어 태그 보강 실측.

1. SummarizeEpisodeUseCase 지시 비교 (S0 현재 vs S1 상위개념 예시):
   - S0: "태그: (쉼표로 구분한 핵심 명사 5~8개 — 검색에 쓰이므로 동의어·구체 명사 포함)"
   - S1: "태그: (쉼표로 구분한 핵심 명사 5~8개 — 본문 명사뿐 아니라 상위 개념·동의어도 함께 포함. 예: 커피→음료, 치과→병원, 강아지→반려동물, 러닝→운동)"
2. ExtractFactsUseCase 지시 비교 (E0 현재 vs E1 문장 끝 [상위 분류·동의어 태그]):
   - E0: "지식: (특정 상황에서만 필요한 사실 — 비밀번호·번호·장소·습관·반복 일정, 최대 3개, 없으면 "없음")"
   - E1: "지식: (특정 상황에서만 필요한 사실 — 비밀번호·번호·장소·습관·반복 일정, 문장 끝에 [상위 분류·동의어 태그 2~3개]를 붙임. 예: 아침에 커피보다 녹차를 마심 [음료, 차], 최대 3개, 없으면 "없음")"
3. exp36 합성 에피소드 6개 무손상 대조 (형식 준수·사실 회수·잡담 오추출 0 확인).
"""
import pathlib
import re
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import exp36_fact_extraction as E36  # noqa: E402
import kosmos_lab as K  # noqa: E402
import run as R  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

eng = R.engine()

EP_CASES = [
    ("커피/녹차", "사용자: 나 아침에는 커피보다 녹차를 더 마셔.\n비서: 아침에는 녹차를 더 즐기시는군요!", {"음료", "차", "선호", "기호"}),
    ("땅콩/새우", "사용자: 나 땅콩이랑 새우 먹으면 두드러기 나.\n비서: 땅콩과 새우는 조심하셔야겠어요.", {"알레르기", "음식", "체질", "식품"}),
    ("미소치과", "사용자: 강남역 미소치과에서 매달 정기 검진 받아.\n비서: 정기적으로 치아 관리를 잘하고 계시네요.", {"병원", "치과", "진료", "의료"}),
    ("한강 러닝", "사용자: 주말 아침마다 한강에서 5km 러닝 뛰고 있어.\n비서: 주말마다 한강 러닝이라니 건강에 정말 좋겠네요!", {"운동", "취미", "달리기", "조깅", "건강"}),
    ("통기타 레슨", "사용자: 매주 수요일 저녁 8시에 박민수 선생님한테 통기타 레슨 받아.\n비서: 통기타 배우시는군요! 수요일 저녁 레슨 기억해 둘게요.", {"악기", "기타", "취미", "학원", "수업", "음악"}),
    ("어머니 생신", "사용자: 우리 어머니 생신은 음력 11월 7일이야.\n비서: 어머니 생신이 음력 11월 7일이시군요.", {"엄마", "생일", "가족", "기념일"}),
    ("넷플릭스", "사용자: 넷플릭스 멤버십은 매달 15일에 신한카드로 자동이체돼.\n비서: 매달 15일 신한카드 자동이체 확인했어요.", {"구독", "정기결제", "결제", "OTT"}),
    ("에어맥스", "사용자: 나이키 에어맥스는 270mm 신어.\n비서: 에어맥스 사이즈 270mm 확인했어요.", {"신발", "운동화", "사이즈", "치수"}),
    ("트렁크 우비", "사용자: 차 트렁크 우산 옆에 비상용 우비 넣어 뒀어.\n비서: 트렁크 우산 옆에 우비를 보관하셨군요.", {"비옷", "차량", "자동차", "보관"}),
    ("스카이패스", "사용자: 대한항공 스카이패스 코드는 9081-2234야.\n비서: 스카이패스 코드 9081-2234 확인했어요.", {"마일리지", "항공사", "항공", "적립"}),
    ("소설책 운송장", "사용자: 집으로 시킨 소설책 운송장 코드가 6043-1122-9087이야.\n비서: 운송장 코드 6043-1122-9087 확인했어요.", {"택배", "배송", "송장", "주문"}),
]

EX_E0 = K.fixture("extract_system.txt")
EX_E1 = EX_E0.replace(
    "지식: (특정 상황에서만 필요한 사실 — 비밀번호·번호·장소·습관·반복 일정, 최대 3개, 없으면 \"없음\")",
    "지식: (특정 상황에서만 필요한 사실 — 비밀번호·번호·장소·습관·반복 일정, 문장 끝에 [상위 분류·동의어 태그 2~3개]를 붙임. 예: 아침에 커피보다 녹차를 마심 [음료, 차], 최대 3개, 없으면 \"없음\")"
)

if "synth" in sys.argv:
    recall_hit = recall_total = fmt_ok = false_pos = 0
    for s in E36.SYNTH:
        with K.create_chat_conversation(eng, tools=[], system_message=EX_E1) as conv:
            raw = R.text_of(conv.send_message(E36.USER_PREFIX + E36.transcript(s["turns"])))
        ok, prof, know = E36.parse(raw)
        # strip trailing [tags] from know items
        clean_know = [re.sub(r"\s*\[[^\]]+\]\s*$", "", k).strip() for k in know]
        fmt_ok += ok
        joined_all = " ".join(f"{k} {v}" for k, v in prof) + " " + " ".join(clean_know)

        def found(e, hay):
            alts = e if isinstance(e, tuple) else (e,)
            return any(a in hay for a in alts)

        expected = s["expect_profile"] + s["expect_knowledge"]
        hits = [e for e in expected if found(e, joined_all)]
        recall_hit += len(hits)
        recall_total += len(expected)
        fp = s["expect_none"] and (prof or clean_know)
        false_pos += bool(fp)
        print(f"  [synth {s['name']}] fmt={ok} 회수={len(hits)}/{len(expected)} fp={bool(fp)} | raw={raw.replace(chr(10), ' / ')}", flush=True)
    print(f"== exp36 SYNTH (E1): 형식 {fmt_ok}/{len(E36.SYNTH)} · 회수 {recall_hit}/{recall_total} · 오추출 {false_pos}", flush=True)

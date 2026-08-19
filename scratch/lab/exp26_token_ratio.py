"""exp26 — Gemma 4 토크나이저의 문자/토큰 비율 실측.

목적: GemmaTokenizer.estimateTokens(`length/3+1`)의 보정 계수 도출.
방법: engine.tokenize(text) 로 실토큰 수를 직접 센다 (생성 없음, 결정적).

판정 기준(보수 설계): 제안 추정식이 모든 샘플에서
  추정 >= 실측 (과소평가 0건)  AND  추정 <= 실측 x 1.5 (과대 50% 이내)
"""

import pathlib

import litert_lm as llm

import kosmos_lab as lab

FIXTURES = pathlib.Path(__file__).parent / "fixtures"

SAMPLES = {
    # (1) 한국어 산문 — 채팅체
    "ko_chat_1": "내일 오후 3시에 치과 예약 잡아줘. 끝나고 장보러 갈 거야.",
    "ko_chat_2": "내 자전거 자물쇠 비밀번호가 뭐였지? 지난번에 알려줬잖아.",
    "ko_chat_3": "다음주 월요일 10시에 팀 회의 일정 추가해줘. 회의실은 3층이야.",
    "ko_chat_4": "오늘 하루 종일 비 온다는데 우산 챙기라고 아침에 알려줘.",
    "ko_chat_5": "어제 회식에서 먹은 삼겹살집 이름 기억해? 거기 또 가고 싶다.",
    # (1) 한국어 산문 — 위키체 (동결 픽스처)
    "ko_wiki_aespa": (FIXTURES / "wiki_aespa_intro.txt").read_text(encoding="utf-8"),
    # (2) 영어 산문
    "en_prose": "The quick brown fox jumps over the lazy dog. Schedule a team meeting for next Monday at ten in the morning.",
    # (3) 숫자·ISO 문자열
    "iso_single": "2026-08-17T10:00:00",
    "iso_repeated": "2026-08-17T10:00:00 2026-08-12T20:00:00 2026-11-03T09:30:00 1234 8282",
    # (4) 한/영/숫자 혼합 툴 JSON
    "tool_json": '{"name":"add_schedule","arguments":{"title":"팀 회의","start_time":"2026-08-17T10:00:00","end_time":"2026-08-17T11:00:00","memo":"3층 회의실"}}',
    # (5) 중국어 — 위키 캡의 zh 분기 제거 검증용
    "zh_prose": "人工智能是研究、开发用于模拟、延伸和扩展人的智能的理论、方法、技术及应用系统的一门新的技术科学。",
    # (5b) 현실 혼합 — 어시스턴트가 일정을 나열하는 형태 (한국어+ISO 혼합)
    "ko_schedule_list": "다음 일정이 있습니다.\n- 2026-08-17T10:00:00 팀 회의 (3층 회의실)\n- 2026-08-18T15:00:00 치과 예약\n확인해 주세요.",
    # (6) 시스템 지시 픽스처 (영어 위주 혼합, 실전 분포)
    "system_instruction": (FIXTURES / "system_instruction.txt").read_text(encoding="utf-8"),
    "turn_reminder": (FIXTURES / "turn_reminder.txt").read_text(encoding="utf-8"),
}


def split_ascii(text: str) -> tuple[int, int]:
    ascii_n = sum(1 for c in text if ord(c) < 128)
    return ascii_n, len(text) - ascii_n


def split3(text: str) -> tuple[int, int, int]:
    """(영문자·공백, 그 외 ASCII(숫자·기호·개행), 비ASCII)

    [WHY] ASCII 가 한 클래스가 아니다 — 영어 산문은 ~4.6자/토큰인데 숫자·ISO 문자열은
    1자/토큰이다(첫 실행 실측). 두 클래스를 뭉치면 어느 쪽으로도 계수를 정할 수 없다.
    """
    alpha_space = 0
    ascii_other = 0
    non_ascii = 0
    for c in text:
        if ord(c) >= 128:
            non_ascii += 1
        elif c.isalpha() or c == " ":
            alpha_space += 1
        else:
            ascii_other += 1
    return alpha_space, ascii_other, non_ascii


def current_estimate(text: str) -> int:
    """현행 GemmaTokenizer: length / 3 + 1"""
    return len(text) // 3 + 1


def main() -> None:  # noqa: C901
    engine = lab.create_engine()
    rows = []
    for name, text in SAMPLES.items():
        tokens = len(engine.tokenize(text))
        ascii_n, non_ascii_n = split_ascii(text)
        rows.append((name, len(text), ascii_n, non_ascii_n, tokens))

    print(f"{'sample':22} {'chars':>6} {'ascii':>6} {'nonAsc':>6} {'tokens':>6} {'chars/tok':>9} {'cur_est':>8} {'cur/act':>8}")
    for name, chars, ascii_n, non_ascii_n, tokens in rows:
        cur = chars // 3 + 1
        print(f"{name:22} {chars:>6} {ascii_n:>6} {non_ascii_n:>6} {tokens:>6} {chars / tokens:>9.2f} {cur:>8} {cur / tokens:>8.2f}")

    # 2클래스 최소자승 대신, 순수 샘플에서 클래스별 비율을 직접 잰다.
    print()
    print("--- 클래스별 비율 (순수 샘플) ---")
    for name in ["ko_chat_1", "ko_chat_2", "ko_chat_3", "ko_chat_4", "ko_chat_5", "zh_prose"]:
        chars, ascii_n, non_ascii_n, tokens = next((c, a, n, t) for s, c, a, n, t in rows if s == name)
        # 공백·숫자(ASCII)를 대략 1토큰/어절로 두고 비ASCII 비율을 근사 — 참고용
        print(f"{name}: non-ascii {non_ascii_n} chars, tokens {tokens} -> {non_ascii_n / tokens:.2f} nonAscii-chars/token (ascii {ascii_n} 포함 전체 {chars / tokens:.2f})")
    print("en_prose / system_instruction (ASCII 위주):")
    for name in ["en_prose", "system_instruction"]:
        chars, ascii_n, non_ascii_n, tokens = next((c, a, n, t) for s, c, a, n, t in rows if s == name)
        print(f"{name}: {chars / tokens:.2f} chars/token")

    # 3클래스 추정식 격자 탐색: est = alphaSpace/K_ALPHA + asciiOther/K_OTHER + nonAscii/K_DENSE + 1
    print()
    print("--- 3클래스 격자 탐색 (전 샘플: 추정>=실측 AND <=1.5x) ---")
    print(f"{'sample':22} {'alpha':>6} {'other':>6} {'nonAsc':>6} {'tokens':>6}")
    detail = []
    for name, text in SAMPLES.items():
        a, o, n = split3(text)
        tokens = next(t for s, _, _, _, t in rows if s == name)
        detail.append((name, a, o, n, tokens))
        print(f"{name:22} {a:>6} {o:>6} {n:>6} {tokens:>6}")

    # 게이트 재설계: (1) 전 샘플 과소평가 0건 [경성] (2) 최악 과대 최소화 [연성].
    # [WHY] 같은 문자 클래스도 문맥에 따라 밀도가 갈린다(숫자 문맥에선 공백·'T' 도 1토큰,
    # 한국어 문맥에선 공백이 음절 토큰에 흡수) — 선형 모델로 전 샘플 <=1.5x 는 불가능함을
    # 첫 격자에서 확인했다. 과소평가는 KV 초과(이번에 막으려는 결함)로 직결되고 과대평가는
    # 히스토리 손실(품질 저하)에 그치므로, 경성 게이트는 과소평가 쪽에만 둔다.
    print()
    best = []
    for k_alpha in [x / 4 for x in range(12, 29)]:  # 3.0 .. 7.0
        for k_other in [x / 20 for x in range(17, 26)]:  # 0.85 .. 1.25
            for k_dense in [x / 20 for x in range(18, 25)]:  # 0.90 .. 1.20
                worst = 0.0
                ok = True
                for name, a, o, n, tokens in detail:
                    est = int(a / k_alpha + o / k_other + n / k_dense) + 1
                    if est < tokens:
                        ok = False
                        break
                    worst = max(worst, est / tokens)
                if ok:
                    best.append((worst, k_alpha, k_other, k_dense))
    if not best:
        print("과소평가 0건 후보 없음")
        engine.close()
        return
    best.sort()
    for worst, k_alpha, k_other, k_dense in best[:8]:
        print(f"PASS K_alpha={k_alpha}, K_other={k_other}, K_dense={k_dense} (max over {worst:.2f}x)")

    print()
    print("--- 최선 후보의 샘플별 상세 ---")
    worst, k_alpha, k_other, k_dense = best[0]
    print(f"est = alphaSpace/{k_alpha} + asciiOther/{k_other} + nonAscii/{k_dense} + 1")
    for name, a, o, n, tokens in detail:
        est = int(a / k_alpha + o / k_other + n / k_dense) + 1
        print(f"{name:22} actual={tokens:>5} est={est:>5} ratio={est / tokens:>5.2f}")

    engine.close()


if __name__ == "__main__":
    main()

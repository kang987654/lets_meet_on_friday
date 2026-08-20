"""실험 1 — 자연어 표현이 툴 호출로 이어지는지 (앱 0.8.6 조건 그대로).

실기기 증상: "add_memory 툴을 사용해서 …" 는 호출됐지만 "기억해줘" 는 호출되지 않았다.
어떤 한국어 표현이 되고 안 되는지를 표로 만들어, 프롬프트 조정의 근거로 쓴다.
"""

import sys

import kosmos_lab as K

K.use_utf8()
BACKEND = sys.argv[1] if len(sys.argv) > 1 else "gpu"

# (기대 툴, 입력) — 기대와 실제를 비교한다. None 은 "툴 없이 답해야 정상".
CASES = [
    # --- AddMemory: 앱에서 실패한 핵심 표현 ---
    ("add_memory", "내 자전거 비밀번호는 1234야, 기억해줘"),
    ("add_memory", "자전거 비밀번호 1234야 기억해줘"),
    ("add_memory", "내 자전거 비밀번호는 1234야"),
    ("add_memory", "나 커피보다 녹차를 더 좋아해. 기억해둬"),
    ("add_memory", "우리집 와이파이 비번 aB3x9k 저장해줘"),
    ("add_memory", "내 동생 생일이 3월 12일이야. 잊지 마"),
    # --- 툴 이름 지목 (앱에서 성공한 대조군) ---
    ("add_memory", "add_memory 툴을 사용해서 내 자전거 비밀번호 1234를 저장해줘"),
    # --- AddSchedule ---
    ("add_schedule", "내일 3시에 치과 예약 잡아줘"),
    ("add_schedule", "내일 오후 3시 치과 예약"),
    ("add_schedule", "모레 저녁 7시에 친구랑 약속 있어. 일정 추가해줘"),
    ("add_schedule", "다음주 월요일 10시 팀 회의 등록해줘"),
    # --- GetSchedule ---
    ("get_schedule", "오늘 일정 뭐 있어?"),
    ("get_schedule", "내일 스케줄 알려줘"),
    # --- SearchWikipedia ---
    ("search_wikipedia", "세종대왕이 누구야? 검색해줘"),
    ("search_wikipedia", "광합성이 뭐야?"),
    # --- 툴을 부르면 안 되는 대조군 ---
    (None, "안녕! 오늘 기분 어때?"),
    (None, "고맙습니다"),
]


def main():
    lab = K.Lab(backend=BACKEND)
    cfg = K.Config(label="app-0.8.6")
    print(f"\n조건: {cfg.describe()}\n")
    print(f"{'기대':<18} {'실제':<18} {'판정':<5} {'초':>5}  입력 / 결과")
    print("-" * 110)

    ok = 0
    fails = []
    for expected, prompt in CASES:
        r = lab.run(prompt, cfg)
        actual = r.called
        exp = expected or "-"
        good = actual == exp
        ok += good
        if not good:
            fails.append((expected, prompt, r))
        detail = prompt
        if r.tool_calls:
            args = r.tool_calls[0].get("arguments")
            detail += f"   → {args}"
        elif r.text:
            detail += f'   → "{r.text[:60].replace(chr(10), " ")}"'
        print(f"{exp:<18} {actual:<18} {'OK' if good else 'FAIL':<5} {r.seconds:5.1f}  {detail}", flush=True)

    print("-" * 110)
    print(f"결과: {ok}/{len(CASES)} 일치")
    if fails:
        print("\n실패 목록:")
        for expected, prompt, r in fails:
            print(f'  [{expected or "-"} 기대, {r.called} 실제] {prompt}')
            if r.text:
                print(f"      모델 답변: {r.text[:150]}")
    lab.close()


if __name__ == "__main__":
    main()

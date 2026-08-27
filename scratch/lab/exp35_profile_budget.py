"""C′1 M0: few-shot 제거 가능성 A/B + 프로필 블록 비용 실측.

배경: 프로필 상시 주입(~100토큰)의 재원이 필요한데 오버헤드 여유가 19토큰뿐이다.
few-shot(104토큰)의 도입 진단(ADR-010)은 ADR-017 이 철회했고(진짜 원인 = 지침 거리,
턴 리마인더가 해결), few-shot 의 예시 숫자가 조회 턴에 새는 실해("8282 에코")도 기록됐다.
게이트: few-shot 無 구성에서 시나리오 11/11 통과 → 제거 확정(성공 경로). 실패 → 폴백
(히스토리 동적 차감, 사용자 승인 2026-08-28).
"""
import json

import kosmos_lab as K

PROFILE_BLOCK = """[User Profile]
- 이름: 진우
- 호칭: 진우님
- 말투: 친근하게, 존댓말
- 직업: 개발자
- 관심사: 온디바이스 AI"""


def insert_profile(si: str) -> str:
    """[System] 블록과 [System Data] 사이 — PromptAssembler.buildProfileBlock 예정 위치."""
    lines = si.splitlines()
    first_data = next(i for i, line in enumerate(lines) if line.startswith("[System Data]"))
    return "\n".join(lines[:first_data] + PROFILE_BLOCK.splitlines() + lines[first_data:])


engine = K.create_engine()
base_si = K.fixture("system_instruction.txt")


def count(few_shot: bool, system=base_si) -> int:
    with K.create_chat_conversation(engine, system_message=system, few_shot=few_shot) as conv:
        conv.send_message("안녕")
        return conv.token_count


def ask(utterance: str, few_shot: bool, system=base_si):
    with K.create_chat_conversation(engine, system_message=system, few_shot=few_shot) as conv:
        return conv.send_message(K.with_turn_reminder(utterance))


# --- 1) 오버헤드 실측 ---
with_fs = count(few_shot=True)
without_fs = count(few_shot=False)
with_profile = count(few_shot=False, system=insert_profile(base_si))
print(f"few-shot 有: {with_fs}  無: {without_fs}  (few-shot 실측 {with_fs - without_fs})")
print(f"프로필 블록 비용: {with_profile - without_fs}  (無 few-shot + 프로필 = {with_profile})")

# --- 2) 게이트: few-shot 無 스모크 11 ---
SCENARIOS = [
    ("내일 3시에 치과 예약해줘", "add_schedule"),
    ("오늘 일정 뭐 있어?", "get_schedule"),
    ("자전거 비밀번호 1234야 기억해줘", "add_memory"),
    ("자전거 비밀번호 뭐였지?", "search_memory"),
    ("에스파가 뭐야? 검색해줘", "search_wikipedia"),
    ("오후 3시에 약 먹으라고 알려줘", "add_reminder"),
    ("내일 아침 9시에 쓰레기 버리라고 리마인드 해줘", "add_reminder"),
    ("3시에 회의 일정 잡아줘", "add_schedule"),
    # 기억 방향 — few-shot 이 맡던 바로 그 자리
    ("내 자물쇠 비밀번호는 4936이야, 기억해줘", "add_memory"),
    ("내 자물쇠 비밀번호 뭐였지?", "search_memory"),
    ("와이파이 비번 kosmos123 저장해줘", "add_memory"),
]

passed = 0
for utterance, expected in SCENARIOS:
    res = ask(utterance, few_shot=False)
    calls = res.get("tool_calls") or []
    got = calls[0]["function"]["name"] if calls else "(없음)"
    args = json.dumps(calls[0]["function"].get("arguments", {}), ensure_ascii=False) if calls else ""
    leaked = "8282" in args  # few-shot 이 없으니 나올 수 없어야 정상
    ok = got == expected and not leaked
    passed += ok
    print(f"{'OK ' if ok else 'FAIL'} 無FS {utterance!r} -> {got} {args[:100]}{' [8282 유출!]' if leaked else ''}")

# --- 3) 대조: few-shot 有에서 8282 에코 관측 (게이트 아님 — ADR-010 실해 기록용) ---
for utterance in ("내 자물쇠 비밀번호 뭐였지?", "내 자물쇠 비밀번호는 4936이야, 기억해줘"):
    res = ask(utterance, few_shot=True)
    calls = res.get("tool_calls") or []
    args = json.dumps(calls[0]["function"].get("arguments", {}), ensure_ascii=False) if calls else "(호출 없음)"
    print(f"관측 有FS {utterance!r} -> {args[:140]}{' [8282 에코]' if '8282' in args else ''}")

# --- 4) 톤 반영 육안 1케이스 ---
res = ask("오늘 하루 어떻게 보낼까?", few_shot=False, system=insert_profile(base_si))
print(f"\n톤 확인(프로필 有): {str(res.get('text') or res)[:300]}")

print(f"\n스모크(無 few-shot) {passed}/{len(SCENARIOS)} -> {'제거 확정(성공 경로)' if passed == len(SCENARIOS) else '폴백 경로(히스토리 동적 차감)'}")

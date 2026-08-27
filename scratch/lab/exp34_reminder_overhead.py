"""add_reminder 선언 + 트리거 규칙 한 줄이 프리필 오버헤드를 얼마나 늘리는지 실측한다 (B1 M0).

게이트: 증가분 <= 71 (PREFILL_OVERHEAD_TOKENS 1400 - 실측 1329) 이면 상수 무변경.
넘으면 기존 선언 다이어트로 상쇄해야 한다 (계획 M0 참조).
"""
import kosmos_lab as K


# --- B1 후보 선언 (KosmosToolDeclarations 에 넣을 문구의 원안) ---
def add_reminder(time: str, content: str) -> dict:
    """지정한 시각에 알림을 울려 사용자에게 상기시킨다. 캘린더 일정 등록이 아니라 알림이 필요할 때 쓴다.

    Args:
        time: 알림 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'
        content: 알림에 표시할 내용
    """
    raise NotImplementedError("툴 실행은 하네스 밖에서 주입한다")


# PromptAssembler.buildFormatBlock 에 넣을 트리거 규칙 원안
TRIGGER_LINE = (
    '- The user asks to be reminded or alerted at a specific time — Korean triggers: '
    '"3시에 알려줘", "리마인드 해줘", "알람 맞춰줘": you MUST call `add_reminder`.'
)


def insert_trigger(si: str) -> str:
    """툴 규칙 블록의 마지막 '- ' 줄 뒤에 삽입 — 실제 buildFormatBlock 순서와 동형."""
    lines = si.splitlines()
    last_rule = max(i for i, line in enumerate(lines) if line.startswith("- "))
    lines.insert(last_rule + 1, TRIGGER_LINE)
    return "\n".join(lines)


engine = K.create_engine()


def count(tools, system):
    with K.create_chat_conversation(engine, tools=tools, system_message=system) as conv:
        conv.send_message("안녕")
        return conv.token_count


base_si = K.fixture("system_instruction.txt")
base = count(K.ALL_TOOLS, base_si)
decl = count(K.ALL_TOOLS + [add_reminder], base_si)
both = count(K.ALL_TOOLS + [add_reminder], insert_trigger(base_si))

print(f"{'조건':40} {'token_count':>12} {'증가분':>8}")
print(f"{'기준 (툴 5종 + 현행 지시)':40} {base:>12} {'-':>8}")
print(f"{'+ add_reminder 선언':40} {decl:>12} {decl - base:>8}")
print(f"{'+ 트리거 규칙 한 줄':40} {both:>12} {both - base:>8}")
print(f"\n게이트: 증가분 {both - base} vs 여유 71 -> {'통과' if both - base <= 71 else '초과 - 다이어트 필요'}")

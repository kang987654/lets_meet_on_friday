"""exp66 — 0.39.2 M0 c: 대화를 답 직후 미리 다시 만들어 두면 다음 턴이 재생성 비용을 안 내는가.

실기기(0.39.0 M1): 재생성 턴은 평소보다 +6~7초. 앱은 다음 사용자 턴에 DB 히스토리를 initialMessages 로 실어 대화를 새로 만든다.
히스토리 프리필이 언제 일어나는지(생성 시점 / 첫 token_count / 첫 send) 모르므로 셋을 가른다. 히스토리는 앱 대화 예산 근처(약 1,000토큰).
  N 지금: 대화 생성 직후 바로 다음 턴 send
  G 생성만 미리: 생성(시간 따로) → 다음 턴 send
  T 생성 + 토큰 수 조회 미리: 생성 → token_count(시간 따로) → 다음 턴 send
  R 대조(재사용): 같은 히스토리를 send 로 쌓아 둔 대화 → 다음 턴 send
"다음 턴" 시간이 R 에 가까우면 그 방식으로 비용을 미리 치를 수 있다. 각 3회.
"""
import sys
import time

import litert_lm as llm
import kosmos_lab as K

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

SYS = K.fixture("system_instruction.txt")
engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU(), max_num_tokens=K.ENGINE_MAX_TOKENS,
                    max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)

PAIRS = [
    ("오늘 저녁 메뉴 추천해 줄래?", "오늘 저녁으로는 따뜻한 김치찌개나 된장찌개를 추천해요. 날씨가 쌀쌀하면 국물 요리가 좋고, 가볍게 드시고 싶으면 비빔밥이나 샐러드도 괜찮아요."),
    ("김치찌개 끓이는 법 간단히 알려줘", "냄비에 돼지고기와 잘 익은 김치를 볶다가 물을 붓고 끓여요. 두부와 대파를 넣고 고춧가루와 국간장으로 간을 맞추면 돼요. 15분 정도 끓이면 맛이 깊어져요."),
    ("내 사물함 번호는 214번이야", "사물함 번호 214번 확인했어요. 메모해 둘까요?"),
    ("아니 됐어", "알겠어요. 필요하시면 언제든 말씀해 주세요."),
    ("주말에 뭐 하면 좋을까?", "날씨가 좋으면 가까운 공원 산책이나 자전거 타기를 추천해요. 실내라면 전시회나 영화도 좋고, 집에서 쉬면서 밀린 책을 읽는 것도 괜찮아요."),
    ("회사 동료 생일 선물 뭐가 좋을까?", "부담 없는 선물로는 좋은 원두커피, 핸드크림, 작은 디퓨저가 무난해요. 동료의 취향을 안다면 관련된 소품을 고르면 더 좋아해요."),
    ("요즘 잠이 잘 안 와", "잠들기 한 시간 전부터 화면을 멀리하고, 카페인은 오후 늦게 피하는 게 도움이 돼요. 매일 같은 시간에 자고 일어나는 습관도 중요해요."),
    ("고마워 도움이 됐어", "도움이 됐다니 기뻐요. 오늘 밤은 푹 주무시길 바라요!"),
]
HISTORY = [m for u, a in PAIRS for m in ({"role": "user", "content": u}, {"role": "model", "content": a})]
NEXT = "내일 아침 운동 뭐 하면 좋을까? 한 문장으로만 답해 줘."


def timed(f):
    t = time.perf_counter()
    r = f()
    return r, time.perf_counter() - t


def build_by_send():
    conv = K.create_chat_conversation(engine, system_message=SYS)
    for u, _ in PAIRS:
        conv.send_message(K.with_turn_reminder(u + " 한 문장으로만 답해 줘."))
    return conv


if __name__ == "__main__":
    for rep in range(3):
        for arm in ("N", "G", "T", "R"):
            if arm == "R":
                conv, t_make = build_by_send(), 0.0
                tokens, t_count = conv.token_count, 0.0
            else:
                conv, t_make = timed(lambda: K.create_chat_conversation(engine, history=HISTORY, system_message=SYS))
                tokens, t_count = timed(lambda: conv.token_count) if arm == "T" else (None, 0.0)
            _, t_turn = timed(lambda: conv.send_message(K.with_turn_reminder(NEXT)))
            conv.close()
            print(f"[{rep}] {arm}: 생성 {t_make:5.2f}s · 토큰 수 조회 {t_count:5.2f}s ({tokens}) · 다음 턴 {t_turn:5.2f}s", flush=True)

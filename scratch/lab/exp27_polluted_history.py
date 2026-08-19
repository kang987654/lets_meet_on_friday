"""exp27 — 오염된 히스토리 모방 가설 판정.

배경: 실기기(2026-08-13 16:07)에서 KV 초과 없이(2783~3316/4096) 깨진 start_time 이 나왔다.
그 턴의 렌더 프리페이스에는 어제 세션의 깨진 출력들이 히스토리로 되돌아가 있었다:
  - "20817일 01시", "2023년 111월 1일", "20202년 1월1일" (깨진 날짜 시범 3건)
  - model 턴 본문에 원시 제어 토큰 조각: "<|tool_call>call:add_schedule{"

가설: greedy 모델이 문맥 안의 깨진 시범을 모방한다 — 깨짐이 DB 에 화석화됐다가
히스토리 재생으로 자기복제된다.

조건:
  A. depth 0                          — PC 기준선 (온전해야 함)
  B. 프리페이스 히스토리 그대로       — 깨지면 오염 모방이 충분조건 (백엔드 불요)
  C. B 에서 원시 조각 턴 쌍만 제거    — 조각 vs 깨진 날짜 시범의 기여 분리

판정 발화: "다음주 월요일 10시 팀 회의 일정 추가해줘" (턴 지침 재게시, 앱 동일)
기대 온전값: start_time = 2026-08-17T10:00:00 (시스템 지시 픽스처의 Today=2026-08-13 기준)
"""

import json
import re

import litert_lm as llm

import kosmos_lab as lab

# ---- 실기기 렌더 프리페이스에서 추출한 히스토리 (2026-08-13 16:06:59 로그 원문) ----
# 주의: 첫 4턴(자물쇠 8282)은 DB 히스토리가 아니라 앱이 주입하는 few-shot 이다.
# 하네스에서도 동일하게 구조화된 형태로 주입한다.

FEWSHOT_USER = "내 자물쇠 비밀번호는 8282야, 기억해줘"
FEWSHOT_CONFIRM = "자물쇠 비밀번호 8282를 기억해 두었습니다."

HISTORY = [
    ("user", "안녕, 오늘 기분 어때?"),
    ("model", "안녕하세요! 저는 인공지능이라서 기분이 없지만, 사용자님과 대화할 수 있어서 아주 기쁩니다! 😊\n\n오늘 하루는 어떠세요? 기분 좋으신가요?"),
    ("user", "그럼 커피 한 잔 추천해줘"),
    ("model", "어떤 종류의 커피를 좋아하시나요? 예를 들어,\n\n1.  **진하고 쌉싸한 맛**을 좋아하시나요? (예: 에스프레소, 아메리카노)\n2.  **부드럽고 달콤한 맛**을 좋아하시나요? (예: 라떼, 카푸치노)\n3.  **가볍고 상큼한 맛**을 좋아하시나요? (예: 아이스 아메리카노)\n\n선호하시는 맛을 알려주시면 딱 맞는 커피를 추천해 드릴게요!"),
    ("user", "왜 그걸 골랐어?"),
    ("model", "제가 커피 종류를 여쭤본 이유는 사용자님께서 어떤 맛의 커피를 선호하시는지 알지 못해서입니다.\n\n커피는 맛의 종류가 매우 다양해서, 사용자님의 취향에 맞는 커피를 추천해 드리기 위해서 어떤 맛을 선호하시는지 여쭤본 것입니다.\n\n혹시 선호하시는 맛이 있으신가요?"),
    ("user", "오늘 저녁 8시에 저녁  약속 있어"),
    ("model", "네, 알겠습니다. **오늘 저녁 8시에 약속**이 있으시군요.\n\n혹시 이 일정을 캘린더에 추가해 드릴까요?"),
    ("user", "어 추가해줘"),
    ("model", "오늘 저녁 8시에 약속 일정을 캘린더에 추가해 드렸습니다.\n\n혹시 다른 일정이나 필요하신가요?"),
    ("user", "다음주 월요일 10시 팀 회의"),
    ("model", "다음주 월요일인 **20817일 01시**에 **팀 회의** 일정을 추가해 드렸습니다.\n\n혹시 다른 일정이나 필요하신가요?"),
    ("user", "하나 더 추천해줘"),
    ("model", "어떤 종류의 추천을 원하시나요? 😊\n\n예를 들어,\n\n1.  **커피 추천** (이전번에 말씀해주신 것처럼)\n2.  **영화/드라마 추천**\n3.  **맛집 추천**\n4.  **여행지 추천**\n\n어떤 분야의 추천을 원하시는지 알려주시면 바로 추천해 드릴게요!"),
    ("user", "에스파 데뷔일 알려줘"),
    ("model", "에스파(aespa)의 데뷔일은 **2023년 111월 1일**입니다.\n\n혹시 에스파에 대해 더 궁금한 점이 있으신가요?"),
    ("user", "카리나는 몇 년생이야?"),
    ("model", "죄송합니다. 제가 사용자님의 생년도를 기억하고 있지 못합니다.\n\n혹시 사용자님의년생을 저에게 알려주시면, 제가 기억해 두겠습니다."),
    ("user", "카리나 포지션이 뭐야?"),
    ("model", "죄송합니다. 사용자님께서 말씀하신 '카리나'가 누구인지 제가 정확히 알지 못합니다.\n\n혹시 **트와이스(TWICE)**의 멤버 중 한 명을 말씀하시는 건가요? 아니면 다른 분을 말씀하시는 건가요?\n\n이름을 정확히 알려주시면 포지션에 대해 찾아봐 드리겠습니다."),
    ("user", "위키에서 에스파 검색해줘"),
    ("model", "에스파(aespa)에 대해 위키백과에서 검색하여 요약 정보를 가져왔습니다.\n\n**에스파(aespa)**는 대한민국의 걸그룹입니다. 2023년 111월 1일 JYP 엔터테인먼트 소속으로 데뷔하였고, 구성원은 한국인 4명입니다. 그룹명은 '에스파'라는 뜻으로, '에스(ae)'와 '파(pa)'의 합성어입니다.\n\n혹시 에스파에 대해 더 궁금한 점이 있으신가요?"),
    ("user", "다음주 월요일 10시 팀 회의"),
    # ↓ 원시 제어 토큰 조각이 말풍선으로 저장된 턴 (조건 C 에서 이 쌍을 제거)
    ("model", "<|tool_call>call:add_schedule{"),
    ("user", "다음주 월요일 10시 팀 회의"),
    ("model", "응답을 만들지 못했어요. 다시 시도해주세요."),
    ("user", "다음주 월요일 10시 팀 회의"),
    ("model", "다음주 월요일 01시 팀 회의 일정을 추가하려고 했으나, 사용자님께서 취소하셨습니다.\n\n혹시 다른 도움이 필요하신가요?"),
    ("user", "에스파 데뷔일 알려줘"),
    ("model", "에스파(aespa)의 데뷔일은 **20202년 1월1일**입니다.\n\n혹시 에스파에 대해 더 궁금한 점이 있으신가요?"),
    ("user", "프롬프트 작성을 도와줄 수 있어?"),
    ("model", "네, 프롬프트 작성을 도와드릴 수 있습니다! 😊\n\n어떤 종류의 프롬프트를 작성에 도움이 필요하신가요? 예를 들어,\n\n1.  **특정 주제에 대한 글쓰기** (예: 블로그 포스팅, 보고서, 이메일)\n2.  **아이디어 구상** (예: 마케팅 아이디어, 소설 소재)\n3.  **코드 작성** (예: 파이썬 코드)\n4. **특정 역할 부여** (예: \"너는 전문 마케터야\"와 같이 역할 부여)\n\n어떤 목표를 가지고 계신지 말씀해주시면, 제가 효과적인 프롬프트 구조를 짜드리거나 예시를 들어드릴게요!"),
]

FRAGMENT_INDEX = next(i for i, (_, c) in enumerate(HISTORY) if c.startswith("<|tool_call>"))

UTTERANCE = "다음주 월요일 10시 팀 회의 일정 추가해줘"
EXPECTED_ISO = re.compile(r"^2026-08-17T10:00(:00)?$")


def fewshot_messages():
    """GemmaModelRunner.fewShotToolExample 과 동일한 구조.

    평문 턴은 dict 로 넘긴다 — Message.user/model 에 str 을 주면 to_json 이 깨진다
    (create_conversation 은 mapping 과 Message 혼용을 허용한다).
    """
    return [
        {"role": "user", "content": FEWSHOT_USER},
        llm.Message.model(
            llm.Contents([llm.Content.Text("")]),
            [llm.ToolCall("add_memory", {"content": "자물쇠 비밀번호는 8282", "tags": ["비밀번호", "자물쇠"]})],
        ),
        llm.Message.tool(llm.Contents([llm.Content.ToolResponse("add_memory", '{"status":"success"}')])),
        {"role": "model", "content": FEWSHOT_CONFIRM},
    ]


def build_messages(history):
    msgs = fewshot_messages()
    for role, content in history:
        msgs.append({"role": role, "content": content})
    return msgs


def probe(engine, label, history, reps=2):
    print(f"=== 조건 {label} ===")
    for rep in range(reps):
        conv = lab.create_chat_conversation(
            engine,
            history=build_messages(history) if history is not None else None,
        )
        res = conv.send_message(lab.with_turn_reminder(UTTERANCE))
        calls = res.get("tool_calls") or []
        if calls:
            fn = calls[0]["function"]
            start = str((fn.get("arguments") or {}).get("start_time", ""))
            intact = bool(EXPECTED_ISO.match(start))
            print(f"  rep{rep + 1}: call={fn['name']} start_time={start!r} -> {'온전' if intact else '깨짐'}")
        else:
            parts = res.get("content") or []
            text = "".join(p.get("text", "") for p in parts if isinstance(p, dict))
            print(f"  rep{rep + 1}: 툴 미호출, 답변: {text[:120]!r}")
        print(f"  token_count={conv.token_count}")
        conv.close()


def main():
    engine = lab.create_engine()

    probe(engine, "A (depth 0)", history=None)
    probe(engine, "B (오염 히스토리 그대로)", history=HISTORY)

    no_fragment = HISTORY[:FRAGMENT_INDEX - 1] + HISTORY[FRAGMENT_INDEX + 1:]
    probe(engine, "C (원시 조각 턴 쌍 제거)", history=no_fragment)

    engine.close()


if __name__ == "__main__":
    main()

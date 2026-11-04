"""exp77c — 깊은 문맥에서 숫자 온전성: PC GPU × FP32(앱 ADR-029) × 추측 디코딩 OFF/ON (LiteRT-LM 0.19.0).

ADR-021: FP16 활성화에서 깊이 약 2,100 토큰부터 숫자가 깨졌고(exp30 1,854 온전 / 2,122 깨짐) PC GPU 에서도 재현됐다.
여기서는 앱 시스템 지시·툴 + 깨끗한 채움 대화로 깊이를 1,500~3,300 으로 늘리며 ① 문자열 따라 쓰기 ② 일정 추가 툴 인자의 시각을 본다.
FP16 + OFF 를 대조군으로 같이 돌려 이 하네스가 깨짐을 잡는지(재현) 확인한다.
사용: PYTHONPATH=<0.19 폴더> python exp77c_deep_digits.py → exp77c_out.txt
"""
import json
import pathlib
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

FILL_Q = [
    "주말에 읽을 만한 책 고르는 요령 알려줘", "집에서 할 수 있는 간단한 스트레칭 알려줘", "회의 전에 준비하면 좋은 것들 정리해줘",
    "장보기 목록을 짜는 방법 알려줘", "겨울철 실내 습도 관리 방법 알려줘", "이메일을 정중하게 거절하는 문장 예시 알려줘",
    "아침 루틴을 만드는 팁 알려줘", "여행 짐 싸는 순서 알려줘", "집중이 안 될 때 해볼 만한 방법 알려줘",
    "화분 물 주기 요령 알려줘", "발표 연습하는 방법 알려줘", "가계부 쓰는 방법 알려줘", "운동화 세탁하는 방법 알려줘",
    "회의록 요약하는 요령 알려줘", "잠이 잘 오게 하는 습관 알려줘", "냉장고 정리 요령 알려줘",
]
FILL_A = ("{q} 요청에 대한 답입니다. 먼저 목적을 분명히 하고, 할 일을 작은 단계로 나눈 뒤, 매일 같은 시간에 조금씩 실천하는 것이 좋습니다. "
          "처음부터 완벽하게 하려 하기보다 지속할 수 있는 수준으로 시작하고, 일주일 뒤에 무엇이 잘 되었는지 돌아보며 조정하세요. "
          "필요하면 메모를 남겨 두고, 어려운 부분은 주변 사람에게 도움을 요청하는 것도 방법입니다. 무리하지 말고 꾸준히 하는 것이 가장 중요합니다.")
COPY = "다음 문자열을 그대로 따라 써줘: 2026-08-17T10:00 / 010-2468-1357 / 2023년 11월 1일 / 8282"
COPY_EXPECT = ["2026-08-17T10:00", "010-2468-1357", "2023년 11월 1일", "8282"]
TOOL = "2026년 8월 17일 오전 10시에 팀 회의 일정 추가해줘"


def text_of(res):
    content = res.get("content")
    return content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))


def history(n):
    h = []
    for i in range(n):
        q = FILL_Q[i % len(FILL_Q)]
        h += [llm.Message.user(q), llm.Message.model(llm.Contents([llm.Content.Text(FILL_A.format(q=q))]))]
    return h


def run(dtype, spec):
    import os
    os.environ["KOSMOS_LAB_SPEC"] = spec
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=dtype)
    rows = []
    for n in [6, 10, 14, 18]:
        for kind, q in [("copy", COPY), ("tool", TOOL)]:
            conv = K.create_chat_conversation(engine, history=history(n))
            res = conv.send_message(K.with_turn_reminder(q))
            depth = conv.token_count
            if kind == "copy":
                text = text_of(res)
                ok = all(e in text for e in COPY_EXPECT)
                shown = text.replace("\n", " ")[:90]
            else:
                calls = res.get("tool_calls") or []
                args = json.dumps([c.get("function", c).get("arguments") for c in calls], ensure_ascii=False)
                ok = "2026-08-17T10:00" in args
                shown = args[:120]
            rows.append((n, kind, depth, ok))
            print(f"{dtype.name:7s} spec={spec or '-':8s} 채움{n:2d} 깊이{depth:5d} {kind:4s} {'온전' if ok else '깨짐/불일치'} | {shown}", flush=True)
            conv.close()
    del engine
    return rows


if __name__ == "__main__":
    for dtype, spec in [(llm.ActivationDataType.FLOAT16, "off"), (llm.ActivationDataType.FLOAT32, "off"),
                        (llm.ActivationDataType.FLOAT32, "on"), (llm.ActivationDataType.FLOAT32, "adaptive")]:
        run(dtype, spec)

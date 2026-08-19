"""스모크: 툴 선언 → 호출 → 응답 주입 → 최종 답변 왕복이 파이썬 API 로 성립하는가.

exp25(grounding A/B)의 성립 조건 선검증이다. 실패하면 exp25 는 레버 B 만 측정하는
설계로 축소한다 (계획 §Phase A-4).
"""

import json
import time

import kosmos_lab as lab

t0 = time.time()
engine = lab.create_engine()
print(f"engine up ({time.time() - t0:.0f}s), max_num_tokens={engine.max_num_tokens}")

conv = lab.create_chat_conversation(engine)
print(f"conversation up, preface token_count={conv.token_count}")

# 1) 위키 툴을 부르는 발화 (앱과 동일하게 턴 지침 재게시)
res = conv.send_message(lab.with_turn_reminder("에스파가 뭐야? 위키에서 검색해줘"))
print("=== turn 1 raw ===")
print(json.dumps(res, ensure_ascii=False, default=str)[:2000])

tool_calls = res.get("tool_calls") or []
if not tool_calls:
    raise SystemExit("SMOKE FAIL: 툴 호출이 없다")

call = tool_calls[0]
print("tool call:", call)

# 2) 툴 응답 주입 (앱의 Message.tool(Content.ToolResponse) 경로와 동일)
result_json = json.dumps(
    {"status": "success", "data": "Title: Aespa\n--- SUMMARY ---\naespa(에스파)는 대한민국의 4인조 다국적 걸그룹이다."},
    ensure_ascii=False,
)
# tool_calls 항목은 OpenAI 스타일 {"type":"function","function":{"name":...,"arguments":{...}}}
name = call["function"]["name"]
res2 = conv.send_message(lab.tool_response_message(name, result_json))
print("=== turn 2 raw ===")
print(json.dumps(res2, ensure_ascii=False, default=str)[:2000])
print(f"token_count after roundtrip={conv.token_count}")
print("SMOKE OK")

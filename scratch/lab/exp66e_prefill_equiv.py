"""exp66e — 0.39.2 M0 c: '모델 메시지 + 출력 상한 1'로 미리 프리필한 대화가 지금 방식과 같은 첫 응답을 내는가 (회귀 세트의 기록·질문 쌍).

A 지금: 전체 기록으로 대화 생성 → 질문 send.  B 미리: 기록[:-1]로 생성 → 마지막 비서 답을 모델 메시지로 send(상한 1) → 질문 send.
첫 응답의 글과 툴 호출(이름·인자)이 같은지 센다(greedy). 기록이 비서 답으로 끝나는 쌍만.
"""
import json
import pathlib
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402
import kosmos_lab as K  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

pair = [R.M("user", R.REFUSAL[0]), R.M("model", R.REFUSAL[1])]
CASES = []
for q in R.DRIFT_ASK[:3]:
    CASES.append(("drift", R.BEFORE + pair[:0] + [R.M("user", "오늘 날씨 좋다"), R.M("model", "정말 좋은 날씨네요! 😊")], q))
for u, _ in R.FACTS[:3]:
    CASES.append(("fact h3", R.fact_history(3), u))
for yes in ("응", "그래"):
    CASES.append(("yes", [R.M("user", "내 사물함 번호는 214번이야"), R.M("model", "사물함 번호 214번 확인했어요. 메모해 둘까요?")], yes))
for fact, q, _ in R.REPEATS[:2]:
    CASES.append(("repeat", [R.M("user", fact), R.M("model", "메모해 둘까요?"), R.M("user", "아니 됐어"), R.M("model", "알겠어요.")], q))
for u, a, q, _ in R.HELPED:
    CASES.append(("helped", R.BEFORE + [R.M("user", u), R.M("model", a)], q))
CASES.append(("schedule", R.BEFORE, "내일 오후 3시에 치과 예약 잡아줘"))
CASES.append(("reminder", R.BEFORE, "오늘 오후 6시에 약 먹으라고 알려줘"))


def first(conv, user):
    r = conv.send_message(K.with_turn_reminder(user))
    calls = [(c["function"]["name"], json.dumps(c["function"].get("arguments"), ensure_ascii=False, sort_keys=True)) for c in (r.get("tool_calls") or [])]
    return R.text_of(r), calls


same = 0
for tag, h, q in CASES:
    a = K.create_chat_conversation(R.engine(), history=h, tools=R.TOOLS_OFF, system_message=R.SYS_OFF)
    ra = first(a, q)
    a.close()
    b = K.create_chat_conversation(R.engine(), history=h[:-1], tools=R.TOOLS_OFF, system_message=R.SYS_OFF)
    b.send_message(llm.Message.model(llm.Contents([llm.Content.Text(h[-1]["content"])])), max_output_tokens=1)
    rb = first(b, q)
    b.close()
    ok = ra == rb
    same += ok
    print(f"{'같음' if ok else '다름'} [{tag}] {q} | A {ra[1] or ra[0][:40]} | B {rb[1] or rb[0][:40]}", flush=True)
print(f"== 같은 첫 응답 {same}/{len(CASES)}", flush=True)

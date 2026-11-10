"""exp86c — 일정 가드 안내 문구 고르기(0.41.11 M1).

시각 없는 말에 add_schedule 이 불린 경우를 모아(앞 기록 0쌍·3쌍 × 사실 문장들), 문구 후보별로 막은 뒤의 답을 본다.
기준: 일정 이야기를 꺼내지 않고 확인 + "메모해 둘까요?" 계열 물음, 거짓 저장·일정 주장 없음.
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402
import exp86_schedule_guard as E  # noqa: E402

K = R.K
CANDIDATES = {
    "G1": E.GUARD,
    "G2": "실행하지 않았습니다. 사용자의 말에 날짜나 시각이 없으니 일정 요청이 아닙니다. 일정 이야기는 꺼내지 말고, 알려 준 내용을 확인한 뒤 \"메모해 둘까요?\"라고 물어보세요. 일정을 잡아 달라는 말이었다면 언제인지만 물어보세요.",
    "G3": "일정으로 추가하지 않았습니다(날짜·시각 없음). 일정을 잡아 달라는 말이면 언제인지 묻고, 아니면 일정은 언급하지 말고 확인한 뒤 \"메모해 둘까요?\"라고 물어보세요.",
}
FACTS = [u for u, _ in R.FACTS] + ["차는 B2 17번에 세웠어", "세차는 어제 했어", "회의실은 5층 C룸으로 바뀌었어"]


def run(history, user, msg):
    hist = R.model_history((history or []) + [R.M("user", user)])[:-1]
    called = None
    with R.open_conversation(hist, R.TOOLS_OFF, R.SYS_OFF) as conv:
        res = conv.send_message(K.with_turn_reminder(user))
        calls = res.get("tool_calls") or []
        if not calls or calls[0]["function"]["name"] != "add_schedule":
            return None
        called = calls[0]["function"].get("arguments")
        if E.TIME_WORDS.search(user):
            return "시각 있음(가드 통과)"
        res = conv.send_message(K.tool_response_message("add_schedule", json.dumps({"status": "error", "message": msg}, ensure_ascii=False)))
        return R.text_of(res), called


if __name__ == "__main__":
    tok = R.engine().tokenize if hasattr(R.engine(), "tokenize") else None
    for k, m in CANDIDATES.items():
        print(f"{k} 토큰 {len(tok(m)) if tok else '?'}: {m}", flush=True)
    print(flush=True)
    for n in (0, 3):
        for u in FACTS:
            first = run(R.fact_history(n), u, CANDIDATES["G1"])
            if first is None:
                continue
            print(f"[h{n}] {u}", flush=True)
            if isinstance(first, str):
                print("    " + first, flush=True)
                continue
            print(f"    호출 인자: {json.dumps(first[1], ensure_ascii=False)}", flush=True)
            for k, m in CANDIDATES.items():
                ans = run(R.fact_history(n), u, m)[0]
                print(f"    {k}: {ans}", flush=True)

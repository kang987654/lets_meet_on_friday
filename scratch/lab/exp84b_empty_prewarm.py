"""exp84b — 0.41.9 M0 보강: 빈 model 메시지로 미리 읽기(EMPTY)가 지금(SYS, 미리 읽기 없음)보다 간격 뒤 새 말에서 나빠지는가.

exp84 에서 EMPTY 는 새 말 4/5(실패 "음…" 1건), 미리 읽기 전후 첫 응답 일치가 조건마다 3~5/9 로 낮았다(0.39.2 exp66e 는 13/13).
MTP(ADAPTIVE) 가 같은 입력에도 답을 흔드는지 먼저 재고(SYS 를 두 번), 걸린 물음이 있는 에피소드 × 애매한 말로 표본을 늘린다.
조건: SYS1·SYS2(같은 조건 두 번 — 흔들림 바닥), EMPTY(앱처럼 [] 로 만들고 빈 model 메시지 상한 1 → 첫 send).
실행: KOSMOS_LAB_SPEC=adaptive python exp84b_empty_prewarm.py > exp84b_out.txt 2> exp84b_err.txt
"""
import importlib.util
import json
import sys
from pathlib import Path

import litert_lm as llm

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
import kosmos_lab as K  # noqa: E402

spec = importlib.util.spec_from_file_location("e84", HERE / "exp84_gap_prewarm.py")
E = importlib.util.module_from_spec(spec)
spec.loader.exec_module(E)

sys.stdout.reconfigure(encoding="utf-8")

EPISODES = {
    "pw": (E.PW, ["비밀번호", "노트북"]),
    "meeting": (E.MEETING, ["회의"]),
    "stew": (E.STEW, ["찌개", "레시피", "김치"]),
    "locker": (E.LOCKER, ["사물함", "214"]),
}
UTTER = ["아아 테스트", "음…", "테스트", "ㅇㅇ", "뭐해?", "hello"]


def first_turn(engine, system, warm, utterance):
    conv = K.create_chat_conversation(engine, history=[], system_message=system)
    if warm:
        conv.send_message(llm.Message.model(llm.Contents([llm.Content.Text("")])), max_output_tokens=1)
    res = conv.send_message(K.with_turn_reminder(utterance))
    out = (E.text_of(res), [(n, json.dumps(a, ensure_ascii=False, sort_keys=True)) for n, a in E.calls_of(res)])
    conv.close()
    return out


def main():
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    score = {"SYS1": 0, "SYS2": 0, "EMPTY": 0}
    same = {"SYS1==SYS2": 0, "SYS1==EMPTY": 0}
    n = 0
    for ep_name, (ep, avoid) in EPISODES.items():
        line = E.summarize(engine, ep)
        print(f"[요약] {ep_name}: {line}", flush=True)
        system = E.SYS + "\n\n" + line
        for utt in UTTER:
            outs = {"SYS1": first_turn(engine, system, False, utt),
                    "SYS2": first_turn(engine, system, False, utt),
                    "EMPTY": first_turn(engine, system, True, utt)}
            n += 1
            same["SYS1==SYS2"] += outs["SYS1"] == outs["SYS2"]
            same["SYS1==EMPTY"] += outs["SYS1"] == outs["EMPTY"]
            for cond, (text, calls) in outs.items():
                joined = text + " " + " ".join(a for _, a in calls)
                hit = [w for w in avoid if w in joined]
                score[cond] += not hit
                print(f"[{cond}] {ep_name} | {utt} | {'통과' if not hit else '실패 ' + str(hit)} | 툴={[c for c, _ in calls] or '-'} | {text.replace(chr(10), ' ')[:120]}", flush=True)
    print("\n## 이어받지 않음", flush=True)
    for cond, ok in score.items():
        print(f"{cond:6s} {ok}/{n}", flush=True)
    print("\n## 첫 응답 일치", flush=True)
    for k, v in same.items():
        print(f"{k:12s} {v}/{n}", flush=True)


if __name__ == "__main__":
    main()

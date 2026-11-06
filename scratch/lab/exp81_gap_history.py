"""exp81 — 0.41.7 M1: 30분 넘게 지난 앞 에피소드를 원문 대신 요약 한 줄 "(과거 대화) 제목: 요약" 으로 실으면 어떻게 되나.

배경: 휴대폰(2026-11-06)에서 69분 뒤 "아아 테스트"에 모델이 앞 물음(노트북 비밀번호 → "메모해 둘까요?")을 이어받아 search_memory 를 다시 불렀다.
`ContextBuilder` 는 세션 단위로 최근 메시지를 실어, 1시간 전 대화가 바로 앞 턴처럼 들어간다.

조건 (같은 합성 대화, GPU FP32 + MTP ADAPTIVE, 앱 프롬프트 픽스처):
- RAW : 지금 — 앞 에피소드 원문을 지난 대화로
- SYS : 원문 대신 요약 한 줄을 시스템 지시 끝에 덧붙임(앱에서는 대화 재사용이 깨지는 자리 — 비교용)
- HIS : 원문 대신 요약 한 줄을 지난 대화의 첫 메시지(model 역할)로
요약은 앱의 요약 프롬프트(fixtures/episode_summary_system.txt + "다음 대화를 요약하세요.\\n\\n" + "사용자:/비서:" 전사)로 이 모델이 만든다.

세트:
- new  : 간격 뒤 새 말 — 앞 주제를 이어받지 않아야(앞 주제 낱말이 툴 인자·답에 없으면 통과, 새 툴 요청은 그 툴을 불러야 통과)
- cont : 간격 뒤 이어 말하기 — 정답 낱말이 답에 있으면 통과
툴은 실행하지 않는다: search_memory 는 new 세트에 "저장된 기억 없음", cont 세트에 그 에피소드 요약 한 줄을 돌려준다(앱의 기억 검색이 요약을 찾는 것과 같은 꼴). 다른 툴은 호출만 기록.
실행: KOSMOS_LAB_SPEC=adaptive python exp81_gap_history.py > exp81_out.txt
"""
import json
import re
import sys
from pathlib import Path

import litert_lm as llm

sys.path.insert(0, str(Path(__file__).parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

U, M = "user", "model"

PW = [(U, "내 노트북 비밀번호 뭐였지?"),
      (M, "죄송해요, 저장된 기억을 확인해 봤는데 노트북 비밀번호에 대한 정보가 없어요. 비밀번호를 알려주시면 메모해 둘까요?")]
STEW = [(U, "김치찌개 끓이는 법 알려줘"),
        (M, "김치찌개는 이렇게 끓여요.\n1. 돼지고기와 신김치를 냄비에 넣고 볶아요.\n2. 물을 붓고 15분 정도 끓여요.\n3. 두부와 대파를 넣고 5분 더 끓이면 완성이에요.\n다른 찌개 레시피도 알려드릴까요?")]
MEETING = [(U, "회의 일정 하나 잡아줘"),
           (M, "좋아요. 어떤 날짜와 몇 시로 잡을까요? 회의 제목도 알려주세요.")]
BREAD = [(U, "사워도우 빵 만드는 과정 단계별로 알려줘"),
         (M, "사워도우는 세 단계로 만들어요.\n1단계 르방 준비: 발효종에 밀가루와 물을 먹여 4~6시간 활성화해요.\n"
             "2단계 오토리즈와 반죽: 밀가루와 물을 먼저 섞어 30분 쉬게 한 뒤(오토리즈) 르방과 소금을 넣고 접기를 반복해요.\n"
             "3단계 발효와 굽기: 냉장고에서 하룻밤 저온 발효한 뒤 250도 오븐에서 40분 구워요.")]
SUSHI = [(U, "어제 강남역 근처 '하루'라는 일식집 갔는데 좋더라"),
         (M, "강남역 '하루'에서 좋은 시간 보내셨군요! 어떤 메뉴가 좋았나요?"),
         (U, "연어덮밥이 맛있었어"),
         (M, "연어덮밥 좋죠! 다음에 가시면 다른 메뉴도 드셔 보세요.")]
BUSAN = [(U, "다음 주에 부산 2박 3일 여행 가는데 첫째 날 일정 짜줘"),
         (M, "첫째 날 일정이에요.\n- 오전: 해운대 해수욕장 산책\n- 점심: 해운대 시장에서 국밥\n- 오후: 동백섬 누리마루 둘러보기\n- 저녁: 광안리 해변에서 광안대교 야경")]
LOCKER = [(U, "내 사물함 번호는 214번이야"),
          (M, "사물함 번호가 214번이군요. 메모해 둘까요?"),
          (U, "아니 괜찮아"),
          (M, "알겠어요. 필요하면 언제든 말씀해 주세요.")]

# (세트, 이름, 앞 에피소드, 간격 뒤 말, 판정)
# new: ("avoid", [앞 주제 낱말]) 또는 ("tool", 불러야 할 툴, 인자 낱말, [앞 주제 낱말])
# cont: ("gold", [정답 낱말 중 하나])
CASES = [
    ("new", "pw-test", PW, "아아 테스트", ("avoid", ["비밀번호", "노트북"])),
    ("new", "pw-um", PW, "음…", ("avoid", ["비밀번호", "노트북"])),
    ("new", "stew-time", STEW, "지금 몇 시야?", ("avoid", ["찌개", "레시피", "김치"])),
    ("new", "meeting-test", MEETING, "테스트", ("avoid", ["회의"])),
    ("new", "pw-dentist", PW, "내일 오후 3시에 치과 예약 넣어줘", ("tool", "add_schedule", "치과", ["비밀번호", "노트북"])),
    ("cont", "bread-step2", BREAD, "아까 그거 두 번째 단계 다시 자세히 설명해줘", ("gold", ["오토리즈"])),
    ("cont", "sushi-name", SUSHI, "아까 말한 그 일식집 이름 뭐였지?", ("gold", ["하루"])),
    ("cont", "busan-day2", BUSAN, "그럼 둘째 날은?", ("gold", ["부산", "감천", "태종대", "자갈치", "남포", "송도", "영도", "해동용궁사", "기장", "흰여울"])),
    ("cont", "locker-num", LOCKER, "내 사물함 번호 몇 번이라고 했지?", ("gold", ["214"])),
]

EMPTY_MEMORY = "저장된 기억이 하나도 없습니다. 사용자가 이 대화에서 이미 말한 내용이 있으면 그것으로 답하고, 없으면 저장된 것이 없다고 답하세요. 추측하지 마세요."
SYS = K.fixture("system_instruction.txt")
SUMMARY_SYS = K.fixture("episode_summary_system.txt")


def msg(role, text):
    return llm.Message.user(text) if role == U else llm.Message.model(llm.Contents([llm.Content.Text(text)]))


def text_of(res):
    c = res.get("content")
    return c if isinstance(c, str) else "".join(x.get("text", "") for x in (c or []) if isinstance(x, dict))


def calls_of(res):
    out = []
    for call in res.get("tool_calls") or []:
        fn = call.get("function", call)
        args = fn.get("arguments")
        if isinstance(args, str):
            try:
                args = json.loads(args)
            except json.JSONDecodeError:
                args = {"raw": args}
        out.append((fn.get("name"), args or {}))
    return out


def summarize(engine, episode):
    transcript = "\n".join(f"{'사용자' if r == U else '비서'}: {t}" for r, t in episode)
    with engine.create_conversation(**K.speculative_kwargs(), system_message=SUMMARY_SYS,
                                    thinking_config=llm.ThinkingConfig(enable_thinking=False),
                                    sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0)) as conv:
        out = text_of(conv.send_message(f"다음 대화를 요약하세요.\n\n{transcript}"))
    title = re.search(r"제목:\s*(.+)", out)
    body = re.search(r"요약:\s*([\s\S]+?)(?:\n---|\Z)", out)
    line = f"(과거 대화) {title.group(1).strip() if title else ''}: {body.group(1).strip().replace(chr(10), ' ') if body else out.strip()}"
    return line


def run(engine, cond, episode, summary_line, utterance, kind):
    if cond == "RAW":
        history, system = [msg(r, t) for r, t in episode], SYS
    elif cond == "SYS":
        history, system = [], SYS + "\n\n" + summary_line
    else:
        history, system = [msg(M, summary_line)], SYS
    trace = []
    with K.create_chat_conversation(engine, history=history, system_message=system) as conv:
        res = conv.send_message(K.with_turn_reminder(utterance))
        for _ in range(2):
            calls = calls_of(res)
            if not calls:
                break
            name, args = calls[0]
            trace.append(f"{name}({json.dumps(args, ensure_ascii=False)})")
            if name == "search_memory":
                body = EMPTY_MEMORY if kind == "new" else f"- {summary_line}\n이 목록에 질문의 답이 없으면, 사용자가 이 대화에서 이미 말한 내용으로 답하세요."
                res = conv.send_message(K.tool_response_message(name, json.dumps({"status": "success", "data": body}, ensure_ascii=False)))
            else:
                break
        return trace, text_of(res)


def judge(rule, trace, answer):
    joined = " ".join(trace) + " " + answer
    if rule[0] == "avoid":
        hit = [w for w in rule[1] if w in joined]
        return not hit, f"앞주제낱말={hit}" if hit else "이어받지않음"
    if rule[0] == "tool":
        _, tool, arg_word, avoid = rule
        called = any(t.startswith(tool) and arg_word in t for t in trace)
        hit = [w for w in avoid if w in joined]
        return called and not hit, f"{tool}={'O' if called else 'X'} 앞주제낱말={hit}"
    found = [w for w in rule[1] if w in answer]
    return bool(found), f"정답낱말={found}"


def main():
    engine = llm.Engine(K.MODEL_PATH, backend=llm.Backend.GPU, max_num_tokens=K.ENGINE_MAX_TOKENS,
                        max_num_images=K.MAX_IMAGES_PER_TURN, activation_data_type=llm.ActivationDataType.FLOAT32)
    summaries = {}
    for _, name, ep, _, _ in CASES:
        key = id(ep)
        if key not in summaries:
            summaries[key] = summarize(engine, ep)
            print(f"[요약] {name}: {summaries[key]}", flush=True)
    score = {}
    for kind, name, ep, utt, rule in CASES:
        for cond in ("RAW", "SYS", "HIS"):
            trace, answer = run(engine, cond, ep, summaries[id(ep)], utt, kind)
            ok, why = judge(rule, trace, answer)
            s = score.setdefault((kind, cond), [0, 0])
            s[0] += ok
            s[1] += 1
            print(f"[{kind}/{cond}] {name} | {'통과' if ok else '실패'} | {why} | 툴={' ; '.join(trace) or '-'} | {answer.replace(chr(10), ' ')[:140]}", flush=True)
    print("\n## 요약", flush=True)
    for (kind, cond), (ok, n) in sorted(score.items()):
        print(f"{kind:5s} {cond}  {ok}/{n}", flush=True)


if __name__ == "__main__":
    main()

"""exp90 — 반복 질문 일반 거절 진단 (0.41.13 M2, 앱 코드 변경 없음).

0.41.3 관찰(CHANGELOG 0.41.3 [Note]):
  같은 질문을 두 번째 하면 앞 질문·답을 모델 기록에서 빼고(RepeatedQuestions.forModel) 대화를 새로 만드는데,
  그 턴에서 모델이 `search_memory` 를 부르지 않고 "저는 AI 라서 개인 정보를 알 수 없습니다"식 일반 거절을
  합니다체로 길게 했다(`toolCalls=[]`).

구성:
  [Part 1] 회상 트리거형 질문 매트릭스 (7문항 × 5시나리오 × 4조건 × 2회 = 280회)
    - 사실 말하기 → 메모 저장 오감(승인 S1 / 거절 S2 / 간격 뒤 빈 기록 S3 / 기능 거절 뒤 S4 / 일상+기능 거절 뒤 S5)
      → 1차 질문(정상 답변 확인) → 같은 질문 2차 반복.
    - 조건 4종:
      * C1_drop_both: 현행 앱 (`RefusalTurns.forModel` -> `RepeatedQuestions.forModel`) — 앞 동일 질문과 그 답을 둘 다 뺌
      * C2_keep_all: 빼지 않음 (`RefusalTurns` 만 유지, `RepeatedQuestions` 미적용)
      * C3_drop_user_only: 앞 동일 질문(user)만 빼고 비서 답(model)은 남김
      * C4_no_refusal_filter: `RefusalTurns` 를 끄고 `RepeatedQuestions` 만 적용

  [Part 2] 회상 트리거 낱말("뭐였지/기억나/내가 알려준") 없는 현재형 개인 질문 + 앞 기록 노출 분리 (7문항 × 3시나리오 × 4조건 × 2회 + 대조 2조건 = 196회)
    - 문항 7개(한국어 현재형 6 + 영어 1), 1차 답은 모두 정상 조회 정답으로 통제한 뒤 같은 질문을 2차 반복:
      * S6_present_empty_prev: 앞 기록 없이 [Q1, A1(정답)] 만 있다가 Q2 반복 (C1 이 빼면 기록 EMPTY)
      * S7_after_ai_privacy_refusal: 앞에 `RefusalTurns` 정규식(`기능이 없|드릴 수 없|도움을 드리기 어려`)에 안 걸리는
        일반 개인정보 거절("저는 AI 비서라서 사용자의 민감한 개인 정보를 알 수 없습니다.")이 있었고, Q1->A1(정답)으로
        회복된 뒤 Q2 반복 (C1 이 Q1·A1 을 빼면 일반 거절이 직전 비서 답 자리로 다시 드러남)
      * S8_after_unfound_memory: 0.41.3 휴대폰 순서(CHANGELOG 0.41.3 137~140줄)처럼 직전에 없는 기억 응답
        ("죄송해요, 아빠 생일에 대한 정보는 저장하고 있지 않아요.", `RefusalTurns` 비대상)이 있고 Q1->A1(정답) 뒤 Q2 반복
    - 추가 런타임·모양 대조 (각 7문항 × 2회):
      * R1_0413_no_conv_thinking: 0.41.3 당시 ConversationPolicy.chatConversationConfig 재현
        (`create_conversation` 에 `thinking_config` 미지정, `send_message` 에만 thinking OFF 전달 — 0.41.9 M2 `727c14d` 이전)
      * R2_orphan_consecutive_user: 앞 턴 취소/누락으로 기록 끝에 `[user]` 만 남아 `[user, user]` 연속이 된 모양
"""
import json
import pathlib
import re
import sys

import litert_lm as llm

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K

# run.py 의 기능 거절 판정식(R.REFUSE) 외에 개인정보·기억 부재형 무호출 일반 거절 판정식
GENERIC_AI_REFUSE = re.compile(
    r"(개인\s*정보|알\s*수\s*없|알지\s*못|확인할\s*수\s*없|접근할\s*수\s*없|가지고\s*있지\s*않|"
    r"저장되어\s*있지\s*않|기억하고\s*있지\s*않|저장하고\s*있지\s*않|"
    r"인공지능|ai\s*(모델|비서|로서|라)|don'?t\s+have\s+access\s+to\s+your\s+personal|cannot\s+know\s+your)",
    re.I,
)
HAPNIDA_STYLE = re.compile(r"(습니다|입니다|없습니다|모릅니다)[.!]?(\s|$)")

# Part 1: 회상 트리거형 7문항 (한국어 6 + 0.41.3 실기기 관찰 영어 교차 1)
ITEMS = [
    ("노트북비번", "내 노트북 비밀번호는 0912야 기억해줘", "내 노트북 비밀번호 뭐였지?", "0912", "- 노트북 비밀번호는 0912 [비밀번호, 노트북]"),
    ("자전거비번", "자전거 자물쇠 비밀번호는 4821이야", "자전거 비밀번호 뭐였지?", "4821", "- 자전거 자물쇠 비밀번호는 4821 [비밀번호, 자전거]"),
    ("사물함", "내 사물함 번호는 214번이야", "사물함 번호 몇 번이었지?", "214", "- 사물함 번호는 214번 [사물함]"),
    ("주차위치", "주차는 지하 2층 C-14에 했어", "주차 어디에 했다고 했지?", "C-14", "- 주차 위치 지하 2층 C-14 [주차]"),
    ("송장번호", "택배 송장번호 6043 1122 9087이야", "택배 송장번호 뭐였더라?", "6043", "- 택배 송장번호 6043 1122 9087 [택배, 송장번호]"),
    ("현관비번", "우리 집 현관 비밀번호는 7394야", "우리 집 현관 비밀번호 뭐야?", "7394", "- 집 현관 비밀번호 7394 [비밀번호, 현관]"),
    ("en_laptop", "내 노트북 비밀번호는 0912야 기억해줘", "What's my laptop password?", "0912", "- 노트북 비밀번호는 0912 [비밀번호, 노트북]"),
]

# Part 2: 회상 트리거("뭐였지", "내가 알려준", "기억나") 없는 현재형 개인 질문 7문항 (1차 정상 답 고정)
PRESENT_ITEMS = [
    ("현_노트북", "내 노트북 비밀번호가 뭐야?", "0912", "- 노트북 비밀번호는 0912 [비밀번호, 노트북]", "노트북 비밀번호는 0912예요."),
    ("현_현관", "우리 집 현관 비밀번호 뭐야?", "7394", "- 집 현관 비밀번호 7394 [비밀번호, 현관]", "우리 집 현관 비밀번호는 7394예요."),
    ("현_사물함", "내 사물함 번호 몇 번이야?", "214", "- 사물함 번호는 214번 [사물함]", "사물함 번호는 214번이에요."),
    ("현_생일", "내 생일이 언제야?", "3월 5일", "- 내 생일은 3월 5일 [생일]", "사용자님의 생일은 3월 5일이에요."),
    ("현_혈액형", "내 혈액형이 뭐야?", "A형", "- 내 혈액형은 A형 [혈액형]", "사용자님의 혈액형은 A형이에요."),
    ("현_커피", "내가 좋아하는 커피가 뭐야?", "라떼", "- 좋아하는 커피는 라떼 [커피, 취향]", "좋아하시는 커피는 라떼예요."),
    ("현_en_laptop", "What's my laptop password?", "0912", "- 노트북 비밀번호는 0912 [비밀번호, 노트북]", "노트북 비밀번호는 0912예요."),
]


def drop_refusal_only(history: list) -> list:
    if not history:
        return history
    last_user = next((m["content"] for m in reversed(history) if m["role"] == "user"), "")
    if R.WHY_REFUSED.search(last_user):
        return history
    drop = set()
    for i, m in enumerate(history):
        if m["role"] == "model" and R.REFUSAL_TURN.search(m["content"]):
            drop.add(i)
            if i > 0 and history[i - 1]["role"] == "user":
                drop.add(i - 1)
    return [m for i, m in enumerate(history) if i not in drop]


def drop_user_only_repeat(history: list) -> list:
    """원인 후보 ③: 앞 동일 질문(user)만 빼고 그 뒤 비서 답(model)은 남김."""
    at = max((i for i, m in enumerate(history) if m["role"] == "user"), default=-1)
    if at < 0:
        return history
    drop = {i for i in range(at) if history[i]["role"] == "user" and R.same_question(history[i]["content"], history[at]["content"])}
    return [m for i, m in enumerate(history) if i not in drop]


def build_hist_for_cond(raw_history: list, user_q: str, cond: str) -> list:
    full = list(raw_history) + [R.M("user", user_q)]
    if cond == "C1_drop_both":
        filtered = R.repeated_drop(drop_refusal_only(full))
    elif cond == "C2_keep_all":
        filtered = drop_refusal_only(full)
    elif cond == "C3_drop_user_only":
        filtered = drop_user_only_repeat(drop_refusal_only(full))
    elif cond == "C4_no_refusal_filter":
        filtered = R.repeated_drop(full)
    else:
        raise ValueError(cond)
    if filtered and filtered[-1]["role"] == "user" and filtered[-1]["content"] == user_q:
        filtered = filtered[:-1]
    return filtered


def open_conv_0413_no_thinking(hist: list, tools: list, sysm: str):
    """0.41.3 당시 chatConversationConfig 재현: create_conversation 에 thinking_config 미지정."""
    return R.engine().create_conversation(
        **K.speculative_kwargs(),
        system_message=sysm,
        messages=list(hist) if hist else None,
        tools=tools,
        automatic_tool_calling=False,
        sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
        constrained_decoding_config=llm.ConstrainedDecodingConfig(enable=bool(tools)),
    )


def run_query_turn(
    model_hist: list,
    raw_history: list,
    user_q: str,
    memo_line: str | None,
    sysm: str = R.SYS_OFF,
    tools: list | None = None,
    conv_thinking_off: bool = True,
):
    """조립된 model_hist 로 새 대화를 열어(앱의 repeatDropped -> invalidateConversation 과 동일) user_q 를 보낸다."""
    use_tools = tools if tools is not None else R.TOOLS_OFF
    calls_made = []
    opener = R.open_conversation(model_hist, use_tools, sysm) if conv_thinking_off else open_conv_0413_no_thinking(model_hist, use_tools, sysm)
    send_kw = {} if conv_thinking_off else {"thinking_config": llm.ThinkingConfig(enable_thinking=False)}
    with opener as conv:
        res = conv.send_message(K.with_turn_reminder(user_q), **send_kw)
        for _ in range(3):
            calls = res.get("tool_calls") or []
            if not calls:
                break
            fn = calls[0]["function"]
            name, args = fn["name"], fn.get("arguments") or {}
            calls_made.append(name)
            if name == "search_memory":
                cur = R.current_lines(raw_history, args.get("keyword"))
                if memo_line:
                    data = cur + f"[저장된 기억 검색 결과]\n{memo_line}{R.HIT_TAIL}"
                else:
                    data = cur + R.SEARCH_EMPTY
                body = {"status": "success", "data": data}
            else:
                body = {"status": "success", "data": "없음"}
            res = conv.send_message(K.tool_response_message(name, json.dumps(body, ensure_ascii=False)), **send_kw)
        ans = R.text_of(res)
        refuse_run = bool(R.REFUSE.search(ans))
        refuse_ai = bool(GENERIC_AI_REFUSE.search(ans))
        return calls_made, ans, refuse_run, refuse_ai


def scenario_histories(fact_u: str, q: str, key: str, memo_line: str):
    """각 시나리오별로 1차 질문까지 마친 시점의 실제 DB 대화 기록(raw_history)과 메모 유무를 만든다."""
    h_s1_before_q1 = [
        R.M("user", fact_u),
        R.M("model", "알려 주셔서 고마워요. 메모해 둘까요?"),
        R.M("user", "응"),
        R.M("model", "메모에 저장했어요."),
    ]
    _, ans_s1_q1, _, _ = run_query_turn(h_s1_before_q1, h_s1_before_q1, q, memo_line)
    h_s1 = h_s1_before_q1 + [R.M("user", q), R.M("model", ans_s1_q1)]

    h_s2_before_q1 = [
        R.M("user", fact_u),
        R.M("model", "알려 주셔서 고마워요. 메모해 둘까요?"),
        R.M("user", "아니"),
        R.M("model", R.MEMO_REJECTED),
    ]
    _, ans_s2_q1, _, _ = run_query_turn(h_s2_before_q1, h_s2_before_q1, q, None)
    h_s2 = h_s2_before_q1 + [R.M("user", q), R.M("model", ans_s2_q1)]

    _, ans_s3_q1, _, _ = run_query_turn([], [], q, memo_line)
    h_s3 = [R.M("user", q), R.M("model", ans_s3_q1)]

    h_s4_before_q1 = [
        R.M("user", "내일 서울 날씨 어때?"),
        R.M("model", "죄송해요, 실시간 날씨를 확인하는 기능은 없어요."),
    ]
    _, ans_s4_q1, _, _ = run_query_turn(drop_refusal_only(h_s4_before_q1), h_s4_before_q1, q, memo_line)
    h_s4 = h_s4_before_q1 + [R.M("user", q), R.M("model", ans_s4_q1)]

    h_s5 = [
        R.M("user", "오늘 저녁 뭐 먹을까?"),
        R.M("model", "따뜻한 김치찌개나 된장찌개 어떠세요?"),
        R.M("user", "알람 앱 좀 바로 열어줘"),
        R.M("model", "앱을 직접 여는 기능은 없어요."),
        R.M("user", q),
        R.M("model", ans_s3_q1),
    ]

    return [
        ("S1_saved_chat", h_s1, memo_line, ans_s1_q1),
        ("S2_rejected_chat", h_s2, None, ans_s2_q1),
        ("S3_saved_empty_hist", h_s3, memo_line, ans_s3_q1),
        ("S4_after_refusal", h_s4, memo_line, ans_s4_q1),
        ("S5_chat_refusal_mix", h_s5, memo_line, ans_s3_q1),
    ]


def record_bucket(summary: dict, key_tuple, calls, has_key: bool, ref_r: bool, ref_ai: bool, ans: str):
    b = summary.setdefault(
        key_tuple,
        {"total": 0, "search": 0, "key_ok": 0, "refuse_run": 0, "refuse_ai": 0, "no_tool_refuse": 0, "hapnida": 0},
    )
    b["total"] += 1
    b["search"] += int("search_memory" in calls)
    b["key_ok"] += int(has_key)
    b["refuse_run"] += int(ref_r)
    b["refuse_ai"] += int(ref_ai)
    b["no_tool_refuse"] += int(not calls and (ref_r or ref_ai))
    b["hapnida"] += int(bool(HAPNIDA_STYLE.search(ans)))


def run_part1():
    conds = ["C1_drop_both", "C2_keep_all", "C3_drop_user_only", "C4_no_refusal_filter"]
    summary = {}

    print("=== [Part 1] 회상 트리거형 반복 질문 재현 및 조건 분리 (7문항 × 5맥락 × 4조건 × 2회 = 280회) ===", flush=True)
    for tag, fact_u, q, key, memo_line in ITEMS:
        scens = scenario_histories(fact_u, q, key, memo_line)
        for s_name, raw_h, m_line, q1_ans in scens:
            q1_ok = key.replace(" ", "") in q1_ans.replace(" ", "")
            print(f"\n--- [{tag} | {s_name}] 1차답(ok={q1_ok}): {q1_ans[:70]!r} ---", flush=True)
            for cond in conds:
                m_hist = build_hist_for_cond(raw_h, q, cond)
                roles = [m["role"][0].upper() for m in m_hist]
                for rep in (1, 2):
                    calls, ans, ref_r, ref_ai = run_query_turn(m_hist, raw_h, q, m_line)
                    has_key = key.replace(" ", "") in ans.replace(" ", "")
                    record_bucket(summary, (s_name, cond), calls, has_key, ref_r, ref_ai, ans)
                    clean = ans.replace("\n", " ")[:80]
                    print(
                        f"  {cond}#r{rep} roles={''.join(roles) or 'EMPTY'} | calls={calls} | key={has_key} "
                        f"| REFUSE={ref_r} AI_REF={ref_ai} | {clean!r}",
                        flush=True,
                    )

    print("\n=== [Part 1 종합 표] 시나리오 × 조건별 집계 (각 칸 n=14 = 7문항 × 2회) ===", flush=True)
    for (s_name, cond), b in summary.items():
        n = b["total"]
        print(
            f"  {s_name:20} | {cond:20} | search_memory={b['search']:2d}/{n} | 정답포함={b['key_ok']:2d}/{n} "
            f"| REFUSE(run.py)={b['refuse_run']:2d}/{n} | 무호출일반거절={b['no_tool_refuse']:2d}/{n}",
            flush=True,
        )


def run_part2():
    conds = ["C1_drop_both", "C2_keep_all", "C3_drop_user_only", "C4_no_refusal_filter"]
    summary = {}
    extra_summary = {}

    prior_ai_refusal = [
        R.M("user", "내 주민등록번호 뭐야?"),
        R.M("model", "저는 AI 비서라서 사용자의 민감한 개인 정보를 알 수 없습니다."),
    ]
    prior_unfound = [
        R.M("user", "우리 아빠 생일 언제였지?"),
        R.M("model", "죄송해요, 아빠 생일에 대한 정보는 저장하고 있지 않아요."),
    ]

    print("\n=== [Part 2] 현재형 개인 질문 + 앞 기록 노출 분리 (7문항 × 3맥락 × 4조건 × 2회 + 런타임/모양 대조 2조건) ===", flush=True)
    for tag, q, key, memo_line, q1_ok_ans in PRESENT_ITEMS:
        scens = [
            ("S6_present_empty_prev", [R.M("user", q), R.M("model", q1_ok_ans)]),
            ("S7_after_ai_refusal", prior_ai_refusal + [R.M("user", q), R.M("model", q1_ok_ans)]),
            ("S8_after_unfound_mem", prior_unfound + [R.M("user", q), R.M("model", q1_ok_ans)]),
        ]
        for s_name, raw_h in scens:
            print(f"\n--- [Part2 {tag} | {s_name}] Q={q!r} | 1차정답={q1_ok_ans!r} ---", flush=True)
            for cond in conds:
                m_hist = build_hist_for_cond(raw_h, q, cond)
                roles = [m["role"][0].upper() for m in m_hist]
                for rep in (1, 2):
                    calls, ans, ref_r, ref_ai = run_query_turn(m_hist, raw_h, q, memo_line)
                    has_key = key.replace(" ", "") in ans.replace(" ", "")
                    record_bucket(summary, (s_name, cond), calls, has_key, ref_r, ref_ai, ans)
                    clean = ans.replace("\n", " ")[:80]
                    print(
                        f"  {cond}#r{rep} roles={''.join(roles) or 'EMPTY':4} | calls={calls} | key={has_key} "
                        f"| REFUSE={ref_r} AI_REF={ref_ai} | {clean!r}",
                        flush=True,
                    )

        # 추가 대조 2조건: 0.41.3 ConversationConfig thinking 미지정 & 연속 user([U]) 모양
        raw_s8 = prior_unfound + [R.M("user", q), R.M("model", q1_ok_ans)]
        m_s8_c1 = build_hist_for_cond(raw_s8, q, "C1_drop_both")
        for r_name, m_h, r_h, think_off in [
            ("R1_0413_no_conv_thinking", m_s8_c1, raw_s8, False),
            ("R2_orphan_consecutive_user", [R.M("user", "우리 아빠 생일 언제였지?")], [R.M("user", "우리 아빠 생일 언제였지?")], True),
        ]:
            roles = [m["role"][0].upper() for m in m_h]
            for rep in (1, 2):
                calls, ans, ref_r, ref_ai = run_query_turn(m_h, r_h, q, memo_line, conv_thinking_off=think_off)
                has_key = key.replace(" ", "") in ans.replace(" ", "")
                record_bucket(extra_summary, r_name, calls, has_key, ref_r, ref_ai, ans)
                clean = ans.replace("\n", " ")[:80]
                print(
                    f"  {r_name}#r{rep} roles={''.join(roles) or 'EMPTY':4} | calls={calls} | key={has_key} "
                    f"| REFUSE={ref_r} AI_REF={ref_ai} | {clean!r}",
                    flush=True,
                )

    print("\n=== [Part 2 종합 표] 현재형 질문 시나리오 × 4조건 집계 (각 칸 n=14 = 7문항 × 2회) ===", flush=True)
    for (s_name, cond), b in summary.items():
        n = b["total"]
        print(
            f"  {s_name:24} | {cond:20} | search_memory={b['search']:2d}/{n} | 정답포함={b['key_ok']:2d}/{n} "
            f"| REFUSE(run.py)={b['refuse_run']:2d}/{n} | 무호출일반거절={b['no_tool_refuse']:2d}/{n} | 합니다체={b['hapnida']:2d}/{n}",
            flush=True,
        )

    print("\n=== [Part 2 런타임·기록모양 추가 대조 표] (각 칸 n=14 = 7문항 × 2회) ===", flush=True)
    for r_name, b in extra_summary.items():
        n = b["total"]
        print(
            f"  {r_name:26} | search_memory={b['search']:2d}/{n} | 정답포함={b['key_ok']:2d}/{n} "
            f"| REFUSE(run.py)={b['refuse_run']:2d}/{n} | 무호출일반거절={b['no_tool_refuse']:2d}/{n} | 합니다체={b['hapnida']:2d}/{n}",
            flush=True,
        )


def main():
    if "--part2-only" not in sys.argv:
        run_part1()
    run_part2()


if __name__ == "__main__":
    main()

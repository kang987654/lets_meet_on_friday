"""exp68c — 0.39.3 M2: 축약(②′③④⑤) 전후의 오버헤드 차이 — token_count(출력 상한 1로 답 길이를 뺀다)와 tokenize 차이 중 큰 값을 MEASURED_OVERHEAD 에서 뺀다."""
import sys
import litert_lm as llm
import kosmos_lab as K
from exp68_trim import OLD_DATES, NEW_DATES, R

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:  # noqa: BLE001
    pass

E = R.engine()
tok = lambda s: len(E.tokenize(s))  # noqa: E731
OLD_DOCS = {
    "add_schedule": ("일정 제목.", "일정 제목. 예: '치과 예약'", "시작 시각. ISO 8601 형식.\n", "시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'\n"),
}
NEW = {f.__name__: f.__doc__ for f in K.ALL_TOOLS}
OLD = dict(NEW)
OLD["add_schedule"] = NEW["add_schedule"].replace("title: 일정 제목.", "title: 일정 제목. 예: '치과 예약'").replace(
    "start_time: 시작 시각. ISO 8601 형식.", "start_time: 시작 시각. ISO 8601 형식. 예: '2026-08-07T15:00:00'")
OLD["get_schedule"] = NEW["get_schedule"].replace("date: 반드시 'today' 또는 'week'. 오늘이 아닌 날(내일·모레·다음주)은 'week'. 다른 값은 쓰지 않는다.",
    "date: 조회 범위. 반드시 'today' 또는 'week' 중 하나만 쓴다. 내일·모레·이번주·다음주처럼 오늘이 아닌 날을 물으면 'week' 를 쓴다. 다른 값은 쓰지 않는다.")
OLD["search_memory"] = NEW["search_memory"].replace("저장해 둔 메모에서 찾는다.", "사용자가 이전에 저장해 둔 기억(메모)에서 찾는다. 추측해서 답하지 말고 이 도구로 확인한다.")
for k in ("add_schedule", "get_schedule", "search_memory"):
    assert OLD[k] != NEW[k], k
SYS_OLD = R.SYS_ON if OLD_DATES in R.SYS_ON else R.SYS_ON.replace(NEW_DATES, OLD_DATES)
SYS_NEW = SYS_OLD.replace(OLD_DATES, NEW_DATES)


def kv(docs, sysm):
    for f in K.ALL_TOOLS:
        f.__doc__ = docs[f.__name__]
    c = K.create_chat_conversation(E, tools=K.ALL_TOOLS, system_message=sysm)
    c.send_message(K.with_turn_reminder("안녕"), max_output_tokens=1)
    t = c.token_count
    c.close()
    return t


a, b = kv(OLD, SYS_OLD), kv(NEW, SYS_NEW)
t = (tok(SYS_OLD) - tok(SYS_NEW)) + sum(tok(OLD[k]) - tok(NEW[k]) for k in OLD)
print(f"token_count {a} → {b} (−{a - b}) · tokenize 차이 −{t}", flush=True)

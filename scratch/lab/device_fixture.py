"""실기기 상태를 실험에 싣는 로더.

2026-08-12 Galaxy S25 Ultra(0.11.1 빌드) 검증에서 두 결함이 나왔는데 둘 다 **문맥이 쌓인
상태에서만** 나타났다. 그 문맥을 손으로 재현할 수 없으니 기기 DB 를 그대로 픽스처로 쓴다.

  fixtures/kosmos_db  — 대화 79개, 감사 46건, 깨진 일정 2건 (integrity_check: ok)

이 파일이 있으면 기기를 다시 꽂을 이유가 없다. 다시 뽑아야 하면 `pull_device_state.sh`.

주의: 픽스처에는 사용자의 실제 대화(비밀번호 포함)가 들어 있다. `scratch/` 는 .gitignore
대상이며 이 디렉터리 밖으로 내보내지 않는다.
"""

import os
import sqlite3

import litert_lm as L

FIXTURE_DIR = os.path.join(os.path.dirname(__file__), "fixtures")
DB_PATH = os.path.join(FIXTURE_DIR, "kosmos_db")


# --------------------------------------------------------------------------
# 앱이 내보낸 시스템 지시 (PromptFixtureExportTest 가 씀)
# --------------------------------------------------------------------------

def system_instruction():
    """실제 `PromptAssembler` 출력. 없으면 None — 호출자가 폴백을 정한다.

    주의: 손으로 베낀 사본을 쓰지 말 것. 실험실 사본이 0.8.6 시절에 멈춰 있던 동안
    `search_memory` 트리거가 빠진 프롬프트로 실험이 돌았다.
    """
    path = os.path.join(FIXTURE_DIR, "system_instruction.txt")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return f.read().rstrip("\n")


def turn_reminder():
    """앱이 **사용자 턴 앞에** 붙이는 툴 지침 한 줄.

    [WHY] 지침이 시스템 턴에만 있으면 히스토리가 쌓일수록 사용자 발화에서 멀어져 툴 호출이
    죽는다(exp18~20). 앱은 이 한 줄을 `currentInput` 앞에 붙이므로, 실험도 같은 모양으로 보내야
    앱과 같은 것을 측정한다. 손으로 베끼지 말 것 — 그 표류가 이 픽스처 체계를 만든 이유다.
    """
    path = os.path.join(FIXTURE_DIR, "turn_reminder.txt")
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return f.read().strip()


def as_app_sends(utterance):
    """앱이 실제로 보내는 사용자 턴 문자열을 만든다 (지침 + 발화)."""
    reminder = turn_reminder()
    return f"{reminder}\n\n{utterance}" if reminder else utterance


def declared_tool_names():
    path = os.path.join(FIXTURE_DIR, "tools.txt")
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [line.strip() for line in f if line.strip()]


# --------------------------------------------------------------------------
# 대화 히스토리
# --------------------------------------------------------------------------

def rows():
    """(role, content) 를 시간순으로 돌려준다."""
    if not os.path.exists(DB_PATH):
        raise FileNotFoundError(
            f"{DB_PATH} 가 없다. pull_device_state.sh 로 기기에서 회수할 것."
        )
    con = sqlite3.connect(DB_PATH)
    try:
        return [
            (role, content)
            for role, content in con.execute(
                "select role, content from conversation order by createdAt, rowid"
            )
        ]
    finally:
        con.close()


# 어시스턴트 답변을 이 문장으로 대체한다 — "모방 가설" 분리용.
#
# 가설: 모델은 히스토리에 있는 **자기 과거 답변의 겉모양**을 베낀다. 위키 검색 결과를 보고한
# 답변이 문맥에 있으면, 툴을 부르는 대신 그 형식(“위키백과에서 검색하여 …”)만 재현한다.
# 툴 호출 자체는 문맥에 남지 않으므로 모방할 대상이 답변 형식뿐이다.
#
# 이 대체가 툴 호출률을 회복시키면 가설이 확정되고, 처방은 프롬프트가 아니라 **히스토리 적재
# 방식**이 된다(툴을 쓴 턴의 어시스턴트 메시지를 요약해 싣기 등).
TERSE_REPLY = "네, 알겠습니다."


# 어시스턴트 답변 문구 → 그 답변이 실제로는 어떤 툴을 썼을 턴인지.
#
# [WHY] `mode="structured"` 가 쓴다. 감사 로그에는 `TOOL_CALL` 이 3건뿐이라(대부분 히스토리가
# 0.9.0 감사 복구 이전이다) 실제 기록으로 재구성할 수가 없다. 그래서 답변 문구로 추론해
# **구조만** 문서 형식으로 바꾼다 — 검증하려는 것이 내용이 아니라 구조의 유무이기 때문이다.
TOOL_REPORT_PATTERNS = [
    ("검색했습니다", "search_wikipedia", {"topic": "트와이스", "lang": "ko"}),
    ("검색하여", "search_wikipedia", {"topic": "에스파", "lang": "ko"}),
    ("기억해 두겠습니다", "add_memory", {"content": "자전거 비밀번호는 1234", "tags": ["비밀번호"]}),
    ("추가해 드렸습니다", "add_schedule", {"title": "팀 회의", "start_time": "2026-08-17T10:00:00"}),
    ("추가해 드렸", "add_schedule", {"title": "저녁 약속", "start_time": "2026-08-12T20:00:00"}),
]

SYNTHETIC_RESULT = '{"status":"success"}'

# `mode="clipped"` 가 어시스턴트 답변을 자르는 길이.
CLIP_CHARS = 60


def _tool_for(text):
    for marker, name, args in TOOL_REPORT_PATTERNS:
        if marker in text:
            return name, args
    return None


def messages(limit=None, mode="verbatim", before=None):
    """히스토리를 LiteRT 메시지 목록으로 만든다.

    Args:
        limit: 마지막 N개만 쓴다. None 이면 전체.
        mode:
            "verbatim"   — 실제 답변 그대로 (지금 앱이 재생하는 모양)
            "terse"      — 어시스턴트 답변을 짧은 문장으로 대체 (모방 대상 제거)
            "structured" — 툴을 썼을 답변에 **툴 호출·응답 구조를 복원** (제안하는 수정)
        before: 이 문자열로 시작하는 **사용자 발화 직전까지** 자른다. 재현할 질문 자체가
            히스토리에 섞이면 모델이 자기 답을 베끼므로 반드시 잘라야 한다.
    """
    data = rows()

    if before is not None:
        cut = next(
            (i for i, (role, content) in enumerate(data)
             if role == "USER" and content.startswith(before)),
            None,
        )
        if cut is None:
            raise ValueError(f"히스토리에 {before!r} 로 시작하는 사용자 발화가 없다")
        data = data[:cut]

    if limit is not None:
        data = data[-limit:] if limit > 0 else []

    out = []
    for role, content in data:
        text = (content or "").strip()
        if role == "USER":
            out.append(L.Message.user(text or " "))
            continue

        if mode == "terse":
            out.append(L.Message.model(L.Contents.of(TERSE_REPLY)))
            continue

        if mode == "useronly":
            # 어시스턴트 턴을 아예 싣지 않는다 — "모방 대상이 어시스턴트 답변인가"의 하한선.
            continue

        if mode == "clipped":
            # 길이만 줄인다. terse 와 달리 **내용은 남는다** — terse 가 이긴 이유가 길이인지
            # 내용(툴 없이 답한 전례)인지 가른다.
            out.append(L.Message.model(L.Contents.of(text[:CLIP_CHARS] or " ")))
            continue

        if mode == "structured":
            tool = _tool_for(text)
            if tool is not None:
                name, args = tool
                # 문서가 요구하는 모양: model(tool_calls) → tool(response) → model(final text).
                # few-shot 시범(`kosmos_lab.few_shot_messages`)과 같은 구조다.
                out.append(L.Message.model(L.Contents.empty(), tool_calls=[L.ToolCall(name, args)]))
                out.append(L.Message.tool(
                    L.Contents.of(L.Content.ToolResponse(name, SYNTHETIC_RESULT))
                ))

        out.append(L.Message.model(L.Contents.of(text or " ")))
    return out


def structured_turn_count(before=None):
    """`structured` 모드에서 툴 구조가 복원되는 어시스턴트 턴 수."""
    data = rows()
    if before is not None:
        cut = next((i for i, (r, c) in enumerate(data)
                    if r == "USER" and c.startswith(before)), len(data))
        data = data[:cut]
    return sum(1 for role, content in data
               if role != "USER" and _tool_for((content or "").strip()) is not None)


def depth_options():
    """실험에서 쓸 히스토리 깊이. 전체 길이를 넘지 않게 잘라 준다."""
    total = len(rows())
    return [d for d in (0, 8, 20, 40, total) if d <= total]


# --------------------------------------------------------------------------
# 재현 대상 — 2026-08-12 실기기에서 실패한 발화 그대로
# --------------------------------------------------------------------------

# #1 툴 호출 실패. 네 발화 모두 `TOOL_CALL` 0건이었고, 마지막은 **검색하지 않고 검색 결과처럼
# 생긴 답변을 위조**했다("위키백과에서 검색하여 요약 정보를 가져왔습니다" + 2020→2023, SM→JYP).
WIKI_PROBES = [
    "에스파 데뷔일 알려줘",
    "카리나는 몇 년생이야?",
    "카리나 포지션이 뭐야?",
    "위키에서 에스파 검색해줘",
]

# 히스토리를 자를 기준 — 위 발화들이 시작되기 직전.
WIKI_HISTORY_CUT = "에스파 데뷔일"

# #3 글자 유실. 툴은 호출됐지만 인자가 깨졌다. 인자는 우리가 조립한 문자열이 아니라
# `message.toolCalls`(네이티브 파싱 구조체)에서 읽으므로, 깨짐은 네이티브 단계에서 생긴다.
#
# (발화, 인자 키, 기대값, 기기에서 실제로 저장된 값)
DIGIT_PROBES = [
    ("다음주 월요일 10시 팀 회의", "start_time", "2026-08-17T10:00", "2026-08-17T01:000"),
    ("오늘 저녁 8시에 저녁 약속 있어", "start_time", "2026-08-12T20:00", "208:000"),
]


def digit_ok(actual, expected):
    """시각 부분이 온전한지 본다.

    날짜와 시·분이 모두 들어 있으면 통과. 초(`:00`)를 붙이는 것은 허용한다 — 기기에서 깨진
    값들은 초가 아니라 **자릿수 자체**가 어긋났다(`10:00` → `01:000`).
    """
    if not actual:
        return False
    date, _, time = expected.partition("T")
    return date in actual and time in actual


if __name__ == "__main__":
    import sys
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

    data = rows()
    print(f"대화 {len(data)}개 (USER {sum(1 for r, _ in data if r == 'USER')})")
    print(f"깊이 옵션: {depth_options()}")
    si = system_instruction()
    print(f"시스템 지시: {'있음 (%d자)' % len(si) if si else '없음 — 테스트를 먼저 돌릴 것'}")
    print(f"선언 툴: {declared_tool_names()}")
    cut = len(messages(before=WIKI_HISTORY_CUT))
    print(f"'{WIKI_HISTORY_CUT}' 직전까지: {cut}개")
    print("\n마지막 6개:")
    for role, content in data[-6:]:
        print(f"  {role:9} {content[:70]!r}")

"""exp79e — 0.41.2 M4: 문서 검색 켜짐에서 다른 툴이 밀리지 않는가 — 회귀 세트 smoke 13 을 켜짐 지시·선언으로 다시 돈다.

켜짐 지시 = 앱 픽스처 `system_instruction_docsearch.txt`(PromptFixtureExportTest) 의 트리거 줄 끝에 앱처럼 등록 문서 이름을 붙인 것.
툴 = kosmos_lab.DOC_TOOLS(기본 7종 + search_documents). 판정식은 regression/run.py set_smoke 그대로.
실행: KOSMOS_LAB_SPEC=adaptive python exp79e_docon_smoke.py
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(Path(__file__).parent / "regression"))
import kosmos_lab as K  # noqa: E402
import run as R  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")

ON = K.fixture("system_instruction_docsearch.txt")
_line = next(l for l in ON.splitlines() if "search_documents" in l)
SYS_DOC = ON.replace(_line, _line + " Registered documents: 민법, 형법, 상법.")

orig_turn = R.turn


def doc_turn(history, user, sysm=None, tools=None, **kw):
    return orig_turn(history, user, sysm=SYS_DOC, tools=K.DOC_TOOLS, **kw)


R.turn = doc_turn
rows = R.set_smoke()
ok = sum(1 for _, good, _ in rows if good)
for tag, good, first in rows:
    print(f"{'OK ' if good else 'NG '} {tag}: {first}", flush=True)
print(f"켜짐 smoke {ok}/{len(rows)} (꺼짐 기준 13/13)", flush=True)

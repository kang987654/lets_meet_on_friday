"""exp104c — 0.42.9: T2 줄이 회귀 세트 clock 5 → 3 · inject 4 → 3 을 깨뜨렸다(줄을 빼면 기준 그대로). 자리·모양을 바꿔 다시 잰다.

각 안마다 픽스처 system_instruction.txt 를 그 안으로 바꿔 쓰고 하위 프로세스로
  regression/run.py --sets=clock,inject,overlap,remind_mem  과  exp104 P0(본 문항 + --held)
를 돌린다(두 스크립트 모두 import 때 픽스처를 읽으므로 하위 프로세스). 끝나면 픽스처를 원래대로 되돌린다.
사용: python exp104c_placement.py > exp104c_out.txt
"""
import pathlib
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")  # 리다이렉트한 stdout 이 cp949 라 '—' 에서 멈췄다
HERE = pathlib.Path(__file__).resolve().parent
FIX = HERE / "fixtures" / "system_instruction.txt"
PY = sys.executable
LINE = "\n- Reminder timed by something the user told you: `search_memory` first."
SEARCH = '- Questions about what the user told you ("뭐였지", "내가 알려준", "기억나"): call `search_memory` with a short noun keyword.'
LIST = '- Listing reminders ("알림 뭐 있어"): call `list_reminders`.'

orig = FIX.read_text(encoding="utf-8")
assert LINE in orig
base = orig.replace(LINE, "")
VARIANTS = {
    "after_list": base.replace(LIST, LIST + LINE),          # 알림 목록 줄 뒤
    "in_search": base.replace(SEARCH, SEARCH.replace("): call", ", or a reminder timed by it): call")),  # 기억 검색 줄에 합침
    "in_search2": base.replace(SEARCH, SEARCH.replace('"기억나")', '"기억나", "병원 예약 하루 전에 알려줘")')),  # 기억 검색 줄 예시로
}
for k, v in VARIANTS.items():
    assert v != base, k


def run(args):
    r = subprocess.run([PY, *args], cwd=HERE, capture_output=True, text=True, encoding="utf-8")
    return r.stdout


try:
    for name, text in VARIANTS.items():
        FIX.write_text(text, encoding="utf-8")
        print(f"\n######## {name}", flush=True)
        out = run(["regression/run.py", "--sets=clock,inject,overlap,remind_mem"])
        print("\n".join(l for l in out.splitlines() if l.startswith(("==", "  [", "기준에서"))), flush=True)
        for extra in ([], ["--held"]):
            o = run(["exp104_memory_to_reminder.py", "P0", *extra])
            print("\n".join(re.sub(r"\| [\d.]+s \|", "|", l)[:170] for l in o.splitlines() if l.strip().startswith("[") and ("M" in l[:12] or "H" in l[:12])), flush=True)
finally:
    FIX.write_text(orig, encoding="utf-8")
    print("\n픽스처 되돌림", flush=True)

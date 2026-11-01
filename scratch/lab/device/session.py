"""실기기 대화 시나리오 — 문장을 차례로 보내고 턴마다 KV·툴 호출·미리 만들기·(임시 로그가 있으면) 시각 표를 찍는다. 0.39.2 M4·0.39.3 M4 에 쓴 것.

사용 (실험실 파이썬, 한글 출력이 깨지지 않게 PYTHONIOENCODING=utf-8):
  PYTHONIOENCODING=utf-8 python scratch/lab/device/session.py [--serial=R3CY10QFYME] [--cold] [--wait=8] [--approve] "문장1" "문장2" ...
  --cold     앱을 강제 종료하고 다시 띄운 뒤 시작(엔진 준비까지 기다리며 걸린 초를 찍는다)
  --wait=N   답이 끝난 뒤 로그를 읽기 전에 기다리는 초 — 답 직후 미리 만들기(0.39.2)는 실기기에서 몇 초 늦게 끝나므로 기본 8
  --approve  승인 시트를 승인한다(기본은 거절 — 데이터를 남기지 않는다)
출력: 턴마다 "KV [...] 호출 [...] 미리준비 True/False | 문장 | 화면 맨 아래 줄" + KOSMOS_M1 시각 표(요청 기준 초).
규칙: 앞 화면이 com.kosmos.app 이 아니면(잠금 화면 포함) turn.py 가 읽지도 누르지도 않고 멈춘다 — 사용자에게 잠금 해제를 부탁한다.
"""
import os
import re
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import adbtool as A  # noqa: E402

TURN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "turn.py")
ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
SERIAL = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--serial=")), os.environ.get("KOSMOS_ADB_SERIAL", ""))
WAIT = float(next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--wait=")), "8"))
ACTION = "approve" if "--approve" in sys.argv else "reject"


def device_now():
    return float(A.adb("shell", "date", "+%s.%N").strip()[:17])


def logs(tags):
    return A.adb("logcat", "-d", "-v", "epoch", "-s", *[f"{t}:*" for t in tags], timeout=30)


def send(text):
    A.adb("logcat", "-c")
    t0 = device_now()
    cmd = [sys.executable, TURN, text, ACTION, "--timeout=300"] + ([f"--serial={SERIAL}"] if SERIAL else [])
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", env={**os.environ, "PYTHONIOENCODING": "utf-8"})
    time.sleep(WAIT)
    log = logs(["GemmaModelRunner", "KOSMOS_M1"])
    kv = re.findall(r"KV usage: 턴 종료 tokens=(\d+)", log)
    calls = [l.split("toolCalls=")[1].split(", enabledTools")[0] for l in log.splitlines() if "turn done:" in l]
    out = " ".join((r.stdout or "").strip().splitlines()[:2])
    print(f"KV {kv} 호출 {calls} 미리준비 {'대화 미리 준비' in log} | {text[:24]} | {out[:80]}", flush=True)
    rows = []
    for line in log.splitlines():
        m = re.match(r"\s*(\d+\.\d+)\s+\d+\s+\d+\s+\w\s+KOSMOS_M1\s*:\s*(.*)", line)
        if m:
            rows.append((float(m.group(1)), m.group(2)))
    base = next((t for t, msg in rows if msg == "request"), t0)
    for t, msg in rows:
        print(f"      {t - base:7.2f}s  {msg}", flush=True)


if "--cold" in sys.argv:
    A.adb("shell", "am", "force-stop", A.PKG)
    time.sleep(2)
    A.adb("logcat", "-c")
    start = time.time()
    A.adb("shell", "monkey", "-p", A.PKG, "-c", "android.intent.category.LAUNCHER", "1")
    while time.time() - start < 180 and "Engine initialized" not in logs(["GemmaModelRunner"]):
        time.sleep(2)
    print(f"엔진 준비까지 약 {time.time() - start:.1f}초(로그 폴링 간격 2초 포함)", flush=True)
    time.sleep(3)
for sentence in ARGS:
    send(sentence)

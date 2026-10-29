"""채팅 한 턴 보내기 (0.39.0 M3) — 입력이 비면 보내고, 승인 시트가 뜨면 처리하고, 답이 끝나면 맨 아래 줄들을 출력한다.

사용: python device/turn.py "문장" [approve|reject|leave] [--serial=<id>] [--timeout=150]
  approve/reject: 승인 시트가 뜨면 바로 누른다(시트는 1분이 지나면 저절로 취소되므로 기다리지 않는다).
  leave: 시트를 그대로 둔다(사람이 보게).
끝 상태 코드: 0 답 받음 · 2 시간 초과 · 3 adb 문제(연결·허용 확인).
"""
import subprocess
import sys
import time

import adbtool as A

IDLE_MARK = "음성 입력"      # 입력바의 마이크 — 답을 만드는 동안에는 "답 멈추기"로 바뀐다
BUSY_MARK = "답 멈추기"
SHEET_MARKS = ("등록하기", "추가하기", "저장하기", "프로필에 저장", "개 저장")


def idle(lines):
    return any(IDLE_MARK in l for l in lines) and not any(BUSY_MARK in l for l in lines)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    text, action = args[0], (args[1] if len(args) > 1 else "leave")
    limit = int(next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--timeout=")), "150"))
    try:
        for _ in range(40):  # 앞 답이 끝날 때까지
            if idle(A.texts()):
                break
            time.sleep(3)
        quoted = text.replace("'", "'\\''")
        A.adb("shell", f"am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '{quoted}' -n {A.PKG}/.MainActivity")
        time.sleep(1.5)
        A.tap_text("Send")
        t0, handled = time.time(), False
        while time.time() - t0 < limit:
            time.sleep(2)
            lines = A.texts()
            if not handled and any(m in l for l in lines for m in SHEET_MARKS):
                print("[승인 시트]", lines[-8:], flush=True)
                if action == "approve":
                    for k in ("등록하기", "추가하기", "저장하기"):
                        if A.tap_text(k):
                            break
                elif action == "reject":
                    A.tap_text("취소")
                handled = True
                continue
            if idle(lines) and time.time() - t0 > 5:
                print(f"[{time.time() - t0:.0f}초]", flush=True)
                print("\n".join(lines[:6]), flush=True)
                return 0
        print(f"[시간 초과 {limit}초]", flush=True)
        return 2
    except A.AdbError as e:
        print(f"[adb] {e}", flush=True)
        return 3


if __name__ == "__main__":
    sys.exit(main())

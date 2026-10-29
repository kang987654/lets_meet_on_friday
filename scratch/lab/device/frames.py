"""시작 화면 → 채팅 전환의 느린 프레임 (0.39.0 M3, 0.38.8~0.38.9 M5 의 측정 도구를 옮김).

앱을 강제 종료 후 실행해 16초 동안 gfxinfo framestats(프레임별 시각)를 모으고, 우리 앱 프로세스 로그(같은 단조 시계)와 맞춰
25ms 넘는 프레임을 단계별로 나눠 출력한다: 지연(입력 처리 전 대기) · 애니(애니메이션·구성) · 레이아웃 · 그리기 · GPU 명령 · 완료 대기.
우리 앱(com.kosmos.app)만 읽는다. **성능은 최적화 빌드로도 재야 한다** — 디버그 빌드는 Compose 가 몇 배 느리다(0.38.9 M5).

사용: python device/frames.py [회수] [--serial=<id>] [--log=<정규식>]   (로그 필터 기본: 엔진·준비 상태)
"""
import re
import sys
import time

import adbtool as A


def run_once(n, log_filter):
    A.adb("shell", "am", "force-stop", A.PKG)
    time.sleep(2)
    A.adb("logcat", "-c")
    A.adb("shell", "monkey", "-p", A.PKG, "-c", "android.intent.category.LAUNCHER", "1")
    frames, t0 = {}, time.time()
    while time.time() - t0 < 16:
        out = A.adb("shell", "dumpsys", "gfxinfo", A.PKG, "framestats", timeout=15)
        sect = out.split("---PROFILEDATA---")
        if len(sect) >= 2:
            lines = [l for l in sect[1].strip().splitlines() if l.strip()]
            if lines:
                header = lines[0].rstrip(",").split(",")
                for l in lines[1:]:
                    v = l.rstrip(",").split(",")
                    if len(v) == len(header) and v[0] == "0":
                        row = dict(zip(header, map(int, v)))
                        frames[row["IntendedVsync"]] = row
        time.sleep(0.7)
    pid = A.adb("shell", "pidof", A.PKG).strip()
    log = A.adb("logcat", "-d", "-v", "monotonic", "--pid", pid, timeout=30) if pid else ""
    rows = sorted(frames.values(), key=lambda r: r["IntendedVsync"])
    if not rows:
        print(f"== run {n}: 프레임 기록 없음", flush=True)
        return
    base = rows[0]["IntendedVsync"] / 1e9
    print(f"== run {n}: 프레임 {len(rows)}장 (첫 프레임 = 0.00초)", flush=True)
    for r in rows:
        dur = (r["FrameCompleted"] - r["IntendedVsync"]) / 1e6
        if dur <= 25:
            continue
        g = lambda a, b: (r.get(b, 0) - r.get(a, 0)) / 1e6  # noqa: E731
        print(f"   느린 프레임 {r['IntendedVsync'] / 1e9 - base:6.2f}초 {dur:5.0f}ms  지연{g('IntendedVsync', 'HandleInputStart'):4.0f} "
              f"애니{g('AnimationStart', 'PerformTraversalsStart'):4.0f} 레이아웃{g('PerformTraversalsStart', 'DrawStart'):4.0f} "
              f"그리기{g('DrawStart', 'SyncQueued'):4.0f} GPU명령{g('IssueDrawCommandsStart', 'SwapBuffers'):4.0f} 완료대기{g('SwapBuffers', 'FrameCompleted'):4.0f}")
    pat = re.compile(log_filter)
    for l in log.splitlines():
        m = re.match(r"\s*(\d+\.\d+)\s+\d+\s+\d+\s+\w\s+(\S+)\s*:\s*(.*)", l)
        if m and pat.search(m.group(2) + " " + m.group(3)) and base - 1 <= float(m.group(1)) <= base + 16:
            print(f"   로그 {float(m.group(1)) - base:6.2f}초  {m.group(2)}: {m.group(3)[:110]}")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    log_filter = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--log=")), r"(?i)Engine initialized|Ready|KOSMOS_")
    try:
        for i in range(int(args[0]) if args else 2):
            run_once(i + 1, log_filter)
        return 0
    except A.AdbError as e:
        print(f"[adb] {e}")
        return 3


if __name__ == "__main__":
    sys.exit(main())

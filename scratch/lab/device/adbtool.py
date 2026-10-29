"""실기기·에뮬레이터 확인 도우미 공통부 (0.39.0 M3).

규칙(사용자 결정): **앞 화면이 우리 앱(com.kosmos.app)일 때만** 화면을 읽고 누른다 — 다른 앱은 읽지 않는다. 캡처·덤프는 PC 에 남기지 않는다.
기기는 `--serial=<id>` 또는 환경 변수 `KOSMOS_ADB_SERIAL`(없으면 adb 기본 기기). 모든 adb 호출은 타임아웃 + 재시도 —
adb 가 멈추면 스크립트가 무한정 붙잡지 않고 실패로 끝난다(0.38.9 M6 에서 adb 가 멈춘 사이 승인 시간이 지나 결과가 뒤집혔다).
"""
import html
import os
import re
import subprocess
import sys
import time

PKG = "com.kosmos.app"
ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe") if os.name == "nt" else "adb"


def serial_args():
    s = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--serial=")), os.environ.get("KOSMOS_ADB_SERIAL"))
    return ["-s", s] if s else []


class AdbError(RuntimeError):
    pass


def adb(*args, timeout=20, retries=2, binary=False):
    """adb 한 번 — 타임아웃이면 재시도, 끝내 실패하면 AdbError."""
    last = None
    for attempt in range(retries + 1):
        try:
            r = subprocess.run([ADB, *serial_args(), *args], capture_output=True, timeout=timeout)
            return r.stdout if binary else r.stdout.decode("utf-8", errors="replace")
        except subprocess.TimeoutExpired as e:
            last = e
            time.sleep(1 + attempt)
    raise AdbError(f"adb {' '.join(args[:3])} 응답 없음 ({timeout}s × {retries + 1}) — USB 연결·디버깅 허용 확인")


def front_app():
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"topResumedActivity=.*?(\S+)/", out) or re.search(r"mResumedActivity.*?(\S+)/", out)
    return m.group(1) if m else "?"


def require_our_app():
    front = front_app()
    if not front.endswith(PKG):
        raise AdbError(f"앞 화면이 우리 앱이 아니다({front}) — 읽지 않는다")


def nodes():
    """화면의 글자·설명 노드 [(text, desc, x, y)] — 우리 앱일 때만. 덤프 파일은 기기에서도 바로 지운다."""
    require_our_app()
    adb("shell", "uiautomator", "dump", "/sdcard/kosmos_ui.xml", timeout=30)
    xml = adb("exec-out", "cat", "/sdcard/kosmos_ui.xml")
    adb("shell", "rm", "-f", "/sdcard/kosmos_ui.xml")
    out = []
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        t, d = html.unescape(m.group(1)), html.unescape(m.group(2))
        x1, y1, x2, y2 = map(int, m.group(3, 4, 5, 6))
        if t or d:
            out.append((t, d, (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def tap_text(key, index=0):
    hits = [n for n in nodes() if key in n[0] or key in n[1]]
    if not hits:
        return False
    _, _, x, y = hits[min(index, len(hits) - 1)]
    adb("shell", "input", "tap", str(x), str(y))
    return True


def texts():
    return [t or d for t, d, _, _ in nodes()]

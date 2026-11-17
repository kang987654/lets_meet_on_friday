"""화면 도우미 (0.39.0 M3) — 우리 앱이 앞에 있을 때만.

사용: python device/ui.py dump | find <글자> | tap <글자> [n] | tapx <글자> [n] | back | front  [--serial=<id>]
"""
import sys

import adbtool as A


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    cmd = args[0]
    try:
        if cmd == "dump":
            for t, d, x, y in A.nodes():
                print(f"{x},{y} | {t} | {d}")
        elif cmd == "find":
            print([n for n in A.nodes() if args[1] in n[0] or args[1] in n[1]])
        elif cmd == "tap":
            print("tap", A.tap_text(args[1], int(args[2]) if len(args) > 2 else 0))
        elif cmd == "tapx":  # 글자가 정확히 같은 노드만(말풍선 속 같은 낱말을 누르지 않게)
            print("tap", A.tap_text(args[1], int(args[2]) if len(args) > 2 else 0, exact=True))
        elif cmd == "back":
            A.require_our_app()
            A.adb("shell", "input", "keyevent", "4")
        elif cmd == "front":
            print(A.front_app())
        return 0
    except A.AdbError as e:
        print(f"[adb] {e}")
        return 3


if __name__ == "__main__":
    sys.exit(main())

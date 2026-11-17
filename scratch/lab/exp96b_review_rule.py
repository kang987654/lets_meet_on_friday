"""exp96b — 0.42.3 M1: (나) 표시로 만든 요약에서 회고가 취소한 일을 "저장해 두었다"고 지어내는가 — 회고 지시에 한 줄 더한 판과 비교.

R0 = 지금 회고 지시(review_system.txt), R1 = 규칙 한 줄 추가.
사용: python exp96b_review_rule.py > exp96b_out.txt
"""
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import exp96_rejection_marking as E  # noqa: E402

RULE = "- 사용자가 승인 창에서 취소한 일은 하지 않은 일이에요. 저장·추가·등록했다거나 기억하고 있다고 쓰지 말고, 취소하셨다고만 쓰세요."
REV0 = E.REV_SYS
lines = REV0.split("\n")
# 마지막 규칙 줄 뒤에 붙인다(형식은 그대로).
idx = max(i for i, l in enumerate(lines) if l.startswith("- "))
REV1 = "\n".join(lines[: idx + 1] + [RULE] + lines[idx + 1:])


def main():
    print("== R1 지시\n" + REV1, flush=True)
    for name, key, conv in E.CONVS:
        summary = E.gen(E.SUM_SYS, "다음 대화를 요약하세요.\n\n" + E.transcript(conv, "B"))
        body = re.search(r"요약\s*[:：]\s*(.+)", summary, re.S)
        body = body.group(1).strip() if body else summary
        title = re.search(r"제목\s*[:：]\s*(.+)", summary)
        title = title.group(1).strip() if title else name
        user = f"지난 7일 대화 요약:\n- 11월 17일 · {title}: {body}"
        r0, r1 = E.gen(REV0, user), E.gen(REV1, user)
        print(f"\n■ {name}\n  요약: {body}\n  R0: {r0}\n  R1: {r1}".replace("\n  ", "\n  "), flush=True)


if __name__ == "__main__":
    main()

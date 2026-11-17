"""exp96c — 0.42.3 M1: (나) 표시에 결과를 박은 판(나2) — "(승인 창에서 메모 저장을 취소함 — 저장되지 않음)". 회고는 지금 지시(R0).
사용: python exp96c_marker_result.py > exp96c_out.txt
"""
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import exp96_rejection_marking as E  # noqa: E402

E.CANCEL_LINE.update({
    "schedule": "(승인 창에서 일정 추가를 취소함 — 일정은 추가되지 않음)",
    "reminder": "(승인 창에서 알림 등록을 취소함 — 알림은 등록되지 않음)",
    "memo": "(승인 창에서 메모 저장을 취소함 — 메모는 저장되지 않음)",
})

for name, key, conv in E.CONVS:
    if not key:
        continue
    summary = E.gen(E.SUM_SYS, "다음 대화를 요약하세요.\n\n" + E.transcript(conv, "B"))
    body = re.search(r"요약\s*[:：]\s*(.+)", summary, re.S)
    body = body.group(1).strip() if body else summary
    title = re.search(r"제목\s*[:：]\s*(.+)", summary)
    title = title.group(1).strip() if title else name
    review = E.gen(E.REV_SYS, f"지난 7일 대화 요약:\n- 11월 17일 · {title}: {body}")
    print(f"\n■ {name}\n  요약: {body}\n  회고: {review}", flush=True)

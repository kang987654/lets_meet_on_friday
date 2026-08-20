"""exp33 — 에피소드 기억(자동 분절 + oneShot 요약 + 어휘 회수)의 실현성 판정.

## 배경 (2026-08-15 설계 논의)

UI 재구성 논의에서 원칙이 잡혔다: **세션은 UX 개념이 아니라 저장 개념이다.** 사용자에게는
하나의 연속 대화만 보이고, 뒤에서 대화가 도메인(주제) 에피소드로 자동 분절되어 각각이
검색 가능한 문서(요약)가 된다 — 과거는 히스토리 윈도우가 아니라 검색으로 돌아온다.

이 설계의 미지수 세 개를 실기기 픽스처(대화 79개)로 잰다:

  ① 분절 — 시간 간격(30분) 경계가 사람 눈에 자연스러운 에피소드를 만드는가
  ② 요약 — E4B 가 oneShot 으로 "제목/태그/요약" 형식을 일관되게 뽑는가
  ③ 회수 — 질문 셋에 대해 [모델 검색어 확장 + 문자 바이그램 매칭(FTS 대용)]이
           정답 에피소드를 찾는가 (recall@1 / @3)

## 판정 기준

- 회수 recall@3 ≥ 85% 면 임베딩 없이 간다 (expand.md C3 게이트 유지)
- 실패 사례는 원인을 분절/요약/검색 중 하나로 귀속시켜 기록한다

## 사용

  python exp33_episodic_memory.py segment              # ① 모델 불필요, 즉시
  python exp33_episodic_memory.py summarize [backend]  # ② 에피소드당 oneShot 1회
  python exp33_episodic_memory.py retrieve [backend]   # ③ 질문당 확장 1회 + 로컬 매칭
"""

import json
import os
import re
import sqlite3
import sys

import kosmos_lab as K

K.use_utf8()

DB = os.path.join(os.path.dirname(__file__), "fixtures", "kosmos_db")
SUMMARIES = os.path.join(os.path.dirname(__file__), "exp33_summaries.json")
GAP_MINUTES = 30

# --------------------------------------------------------------------------
# ① 분절
# --------------------------------------------------------------------------

def load_rows():
    con = sqlite3.connect(DB)
    try:
        return list(con.execute(
            "select role, content, createdAt from conversation order by createdAt, rowid"))
    finally:
        con.close()


def segment():
    """시간 간격 기준 분절. 앱에서는 [간격 + 예산 리셋 경계]가 후보이고, 픽스처에는
    리셋 기록이 없으므로 간격만 쓴다 — 리셋 경계는 간격의 부분집합처럼 동작한다
    (리셋도 결국 대화가 뜸한 지점에서 일어난다)."""
    rows = load_rows()
    episodes, cur = [], []
    prev_ts = None
    for role, content, ts in rows:
        if prev_ts is not None and (ts - prev_ts) > GAP_MINUTES * 60_000 and cur:
            episodes.append(cur)
            cur = []
        cur.append((role, content, ts))
        prev_ts = ts
    if cur:
        episodes.append(cur)
    return episodes


def cmd_segment():
    import datetime
    eps = segment()
    print(f"에피소드 {len(eps)}개 (간격 {GAP_MINUTES}분 기준)\n")
    for i, ep in enumerate(eps):
        t0 = datetime.datetime.fromtimestamp(ep[0][2] / 1000)
        t1 = datetime.datetime.fromtimestamp(ep[-1][2] / 1000)
        users = [c for r, c, _ in ep if r == "USER"]
        first = (users[0] if users else "(사용자 발화 없음)")[:40].replace("\n", " ")
        print(f"[{i}] {t0:%m/%d %H:%M}~{t1:%H:%M}  {len(ep):>2}개  \"{first}\"")
        # 육안 판정용 — 에피소드 안 사용자 발화 전부 (파일로만, 콘솔은 첫 발화만)
    with open("exp33_episodes.txt", "w", encoding="utf-8") as f:
        for i, ep in enumerate(eps):
            f.write(f"===== [{i}] =====\n")
            for r, c, _ in ep:
                f.write(f"{r}: {c[:200]}\n")
    print("\n전체 본문: exp33_episodes.txt (육안 판정용, gitignore 영역)")


# --------------------------------------------------------------------------
# ② 요약
# --------------------------------------------------------------------------

SUMMARIZER_SYSTEM = (
    "당신은 대화 기록을 색인용 문서로 요약하는 사서입니다. 아래 형식만 출력하세요.\n"
    "제목: (한 줄)\n"
    "태그: (쉼표로 구분한 핵심 명사 5~8개 — 검색에 쓰이므로 동의어·구체 명사 포함)\n"
    "요약: (3~5문장. 날짜·시각·이름·숫자는 원문 그대로 보존)"
)

# M0 게이트(2026-08-15): 다중 주제 사후 교정 지시를 얹은 변형. 판정 —
# ① 형식 준수 9/9 유지 ② 단일 주제 에피소드에서 불필요한 분리 없음.
# 실패하면 M1 은 단일 문서로 낙하한다(파서는 List 계약 유지).
SUMMARIZER_SYSTEM_SPLIT = SUMMARIZER_SYSTEM + (
    "\n\n대화에 서로 무관한 주제가 2개 이상 섞여 있으면, 주제마다 위 형식을 반복하되"
    " 문서 사이를 '---' 한 줄로 구분하세요. 주제가 하나면 절대 나누지 마세요."
)


def episode_text(ep, max_chars=1200):
    lines = []
    for r, c, _ in ep:
        tag = "사용자" if r == "USER" else "비서"
        lines.append(f"{tag}: {c}")
    text = "\n".join(lines)
    # [WHY] 요약 입력도 KV 예산 안이어야 한다 — 앱에서는 에피소드가 리셋 직전이므로
    # 어차피 ~1700토큰 이내다. 픽스처의 긴 에피소드만 자른다.
    return text[:max_chars]


def parse_summary(text):
    title = tags = summ = ""
    m = re.search(r"제목\s*[:：]\s*(.+)", text)
    if m: title = m.group(1).strip()
    m = re.search(r"태그\s*[:：]\s*(.+)", text)
    if m: tags = m.group(1).strip()
    m = re.search(r"요약\s*[:：]\s*(.+)", text, re.S)
    if m: summ = m.group(1).strip()
    return title, tags, summ


def cmd_summarize(backend, split=False):
    eps = segment()
    system = SUMMARIZER_SYSTEM_SPLIT if split else SUMMARIZER_SYSTEM
    lab = K.Lab(backend=backend, verbose=False)
    out = []
    try:
        for i, ep in enumerate(eps):
            cfg = K.Config(label=f"ep{i}", tools=[], few_shot=False,
                           system=system, constrained=None,
                           max_output_tokens=400 if split else 300)
            resp = lab.run("다음 대화를 요약하세요.\n\n" + episode_text(ep), cfg)
            text = resp.text
            # 다중 문서: '---' 로 나눠 각각 파싱
            docs = []
            for part in re.split(r"\n-{3,}\n", text):
                title, tags, summ = parse_summary(part)
                if title or tags or summ:
                    docs.append({"title": title, "tags": tags, "summary": summ})
            ok = bool(docs) and all(d["title"] and d["tags"] and d["summary"] for d in docs)
            out.append({"episode": i, "docs": docs, "raw": text, "format_ok": ok,
                        # 하위 호환 (retrieve 가 읽는 필드)
                        "title": docs[0]["title"] if docs else "",
                        "tags": " , ".join(d["tags"] for d in docs),
                        "summary": "\n".join(d["summary"] for d in docs)})
            print(f"[{i}] format_ok={ok} 문서 {len(docs)}개  제목: "
                  f"{docs[0]['title'][:40] if docs else '-'}", flush=True)
    finally:
        lab.close()
    with open(SUMMARIES, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    n_ok = sum(1 for o in out if o["format_ok"])
    print(f"\n형식 준수 {n_ok}/{len(out)} → {SUMMARIES}")


# --------------------------------------------------------------------------
# ③ 회수
# --------------------------------------------------------------------------

# 질문 → 정답 에피소드 번호. cmd_segment 출력(2026-08-15)을 보고 채웠다.
# 정답이 여러 에피소드에 흩어진 경우 전부 정답으로 인정한다 — 앱에서도 어느 것을
# 회수하든 답을 만들 수 있는 경우다. 바꿔 말하기(자물쇠↔비밀번호, 몇 시↔시각)를
# 섞어 어휘 매칭의 한계를 시험한다.
QUESTIONS = [
    ("내 자전거 비밀번호 뭐였지?", [4, 5, 6]),
    ("자전거 자물쇠 번호 기억나?", [4, 5, 6]),          # 바꿔 말하기: 자물쇠
    ("서랍 비밀번호 알려줘", [5]),
    ("치과 예약 언제였지?", [4]),
    ("팀 회의 몇 시로 잡았지?", [8]),
    ("저녁 약속 시간이 언제야?", [8]),
    ("네가 추천해준 커피 뭐였지?", [8]),
    ("커피 말고 하나 더 추천해준 게 있었잖아", [8]),
    ("트와이스 나연에 대해 전에 찾아봤던 내용", [3, 7]),
    ("에스파 데뷔일 전에 물어봤었지?", [8]),
    ("카리나 몇 년생이라고 했지?", [8]),
    ("위키 검색이 안 되던 때 있었잖아", [2, 3, 7]),
    ("네가 검색 안 하고 지어내서 답한 적 있었지?", [3, 7]),  # 환각 지적 대화
    ("음성 메시지 보냈던 대화 찾아줘", [1]),
    ("사진 설명해달라고 했던 거 기억해?", [1]),
    ("자전거 비밀번호를 4321로 바꾼 적 있어?", [4, 5]),
]

EXPANDER_SYSTEM = (
    "당신은 검색어 생성기입니다. 사용자의 질문을 받아, 저장된 메모를 찾기 위한 "
    "검색 키워드를 만드세요. 동의어와 관련 명사를 포함해 키워드 4~8개를 쉼표로만 "
    "구분해 한 줄로 출력하세요. 다른 말은 하지 마세요."
)


def bigrams(s):
    s = re.sub(r"[\s\W_]+", "", s)
    return {s[i:i + 2] for i in range(len(s) - 1)}


def score(query_terms, doc_text):
    """FTS5 바이그램 매칭의 로컬 대용 — 키워드별 바이그램 겹침의 합."""
    doc = bigrams(doc_text)
    total = 0.0
    for t in query_terms:
        b = bigrams(t)
        if not b:
            continue
        total += len(b & doc) / len(b)
    return total


def cmd_retrieve(backend):
    if not QUESTIONS:
        print("[!] QUESTIONS 가 비어 있다 — cmd_segment 출력을 보고 채울 것.")
        return 1
    docs = json.load(open(SUMMARIES, encoding="utf-8"))
    lab = K.Lab(backend=backend, verbose=False)
    hit1 = hit3 = 0
    try:
        for q, gold in QUESTIONS:
            cfg = K.Config(label="expand", tools=[], few_shot=False,
                           system=EXPANDER_SYSTEM, constrained=None,
                           max_output_tokens=60)
            text = lab.run(q, cfg).text
            terms = [t.strip() for t in text.replace("\n", ",").split(",") if t.strip()]
            terms.append(q)  # 원 질문도 매칭에 포함 (앱의 FTS 도 그렇게 한다)
            ranked = sorted(
                docs,
                key=lambda d: -score(terms, f"{d['title']} {d['tags']} {d['summary']}"))
            top = [d["episode"] for d in ranked[:3]]
            ok1 = top[0] in gold
            ok3 = any(t in gold for t in top)
            hit1 += ok1
            hit3 += ok3
            mark = "O" if ok3 else "X"
            print(f"{mark} @1={'O' if ok1 else 'X'} top3={top} gold={gold}  Q: {q[:30]}"
                  f"  확장: {', '.join(terms[:5])[:60]}", flush=True)
    finally:
        lab.close()
    n = len(QUESTIONS)
    print(f"\nrecall@1 = {hit1}/{n}  recall@3 = {hit3}/{n}")
    print("판정: recall@3 >= 85% 면 임베딩 없이 진행 (C3 게이트 유지)")
    return 0


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "segment"
    backend = sys.argv[2] if len(sys.argv) > 2 else "gpu"
    if cmd == "segment":
        cmd_segment()
    elif cmd == "summarize":
        cmd_summarize(backend)
    elif cmd == "summarize-split":
        cmd_summarize(backend, split=True)
    elif cmd == "retrieve":
        raise SystemExit(cmd_retrieve(backend))
    else:
        print("segment | summarize [backend] | retrieve [backend]")

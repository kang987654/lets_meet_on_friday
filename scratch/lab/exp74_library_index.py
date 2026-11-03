"""exp74 — 문서 보관함 색인 실측 (0.41.0 M0, 모델 없음).

자료: scratch/lab/library_data/*.txt (library_fetch.py — 국가법령정보센터 공개 법령 본문, git 제외).
측정:
  ① 조각 규칙 — 조(條) 경계 + 본문 상한 300/500/800자: 조각 수·평균 길이·조가 여러 조각으로 갈린 비율
  ② 색인 — (A) FTS4 바이그램 문자열 + fts4aux 문서빈도 / (B) 바이그램 포스팅 테이블: DB 크기·구축 시간·질의 시간
  ③ 회수율 — 질문 30개(조문 번호 10 · 같은 용어 10 · 바꿔 말한 질문 10) recall@1·@3, 후보 수 20/50/100, 전수 스캔(상한선)과 비교
순위: 후보를 BigramMatcher.score(앱 원문 포팅)로 다시 매긴다 — 머리말 ×1.5 + 본문 + 조문 번호 일치 가산 + 법령명 일치 가산.
실행: %USERPROFILE%\\miniforge3\\envs\\friday\\python.exe -I scratch/lab/exp74_library_index.py > scratch/lab/exp74_out.txt
"""
import os
import re
import sqlite3
import statistics
import sys
import tempfile
import time
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
DATA = Path(__file__).parent / "library_data"

# ---------------------------------------------------------------- BigramMatcher 포팅 (한글·숫자 경로 — 자료에 라틴 단어가 거의 없다)
LATIN = re.compile(r"[a-zA-Z]")


def bigrams(text):
    compact = "".join(ch for ch in text if ch.isalnum())
    if LATIN.search(compact):
        compact = compact.lower()
    return {compact[i:i + 2] for i in range(len(compact) - 1)}


def score(terms, doc_bigrams):
    total = 0.0
    for term in terms:
        b = bigrams(term)
        if b:
            total += sum(1 for x in b if x in doc_bigrams) / len(b)
    return total


# ---------------------------------------------------------------- 조각 나누기
HEADING = re.compile(r"^제\d+(?:편|장|절|관)\s")
ARTICLE = re.compile(r"^(제(\d+)조(?:의(\d+))?)\s*\(([^)]*)\)")
NOTE = re.compile(r"<(?:개정|신설|시행일)[^>]*>|\[(?:본조신설|전문개정|제목개정|종전|본조제목개정|시행일)[^\]]*\]")
LEVEL = {"편": 0, "장": 1, "절": 2, "관": 3}


def clean(line):
    return NOTE.sub("", line).strip()


def chunk_law(name, lines, cap):
    """조 하나 = 조각 하나. 본문이 cap 을 넘으면 줄(항·호) 경계에서 나누고 머리말은 유지한다."""
    path = [None, None, None, None]
    chunks, current = [], None

    def flush():
        if not current:
            return
        title, art_no, body_lines = current
        head = " > ".join([name] + [p for p in path if p] + [title])
        parts, buf = [], ""
        for bl in body_lines:
            if buf and len(buf) + 1 + len(bl) > cap:
                parts.append(buf)
                buf = bl
            else:
                buf = f"{buf}\n{bl}" if buf else bl
        if buf:
            parts.append(buf)
        for i, part in enumerate(parts):
            chunks.append({"law": name, "article": art_no, "path": head + (f" ({i + 1}/{len(parts)})" if len(parts) > 1 else ""),
                           "text": part, "split": len(parts) > 1})

    for raw in lines:
        line = clean(raw)
        if not line:
            continue
        if HEADING.match(line):
            flush(); current = None
            kind = re.match(r"^제\d+(편|장|절|관)", line).group(1)
            lvl = LEVEL[kind]
            path[lvl] = line
            for j in range(lvl + 1, 4):
                path[j] = None
            continue
        m = ARTICLE.match(line)
        if m:
            flush()
            art_no = m.group(2) + (f"의{m.group(3)}" if m.group(3) else "")
            current = (m.group(0), art_no, [line])
            continue
        if current:
            current[2].append(line)
    flush()
    return chunks


def load_laws():
    laws = {}
    for f in sorted(DATA.glob("*.txt")):
        laws[f.stem.replace("_", " ")] = f.read_text(encoding="utf-8").splitlines()
    return laws


# ---------------------------------------------------------------- 질문 (gold = (법령, {조}))
Q_NUMBER = [
    ("민법 제750조", "민법", {"750"}), ("민법 2조 내용", "민법", {"2"}), ("형법 제250조가 뭐야", "형법", {"250"}),
    ("형법 329조", "형법", {"329"}), ("상법 제24조", "상법", {"24"}), ("민사소송법 제1조", "민사소송법", {"1"}),
    ("민법 103조", "민법", {"103"}), ("민법 제1000조 내용 알려줘", "민법", {"1000"}),
    ("공무원 행동강령 제14조", "공무원 행동강령", {"14"}), ("개인정보 보호법 시행령 제35조", "개인정보 보호법 시행령", {"35"}),
]
Q_TERM = [
    ("소멸시효 기간", "민법", {"162", "163", "164"}), ("성년 나이", "민법", {"4"}), ("상속 순위", "민법", {"1000"}),
    ("절도죄 처벌", "형법", {"329"}), ("불법행위 손해배상", "민법", {"750"}), ("정당방위", "형법", {"21", "민법:761"}),
    ("자필증서 유언 방식", "민법", {"1066"}), ("명의대여자 책임", "상법", {"24"}),
    ("공무원 금품 수수 금지", "공무원 행동강령", {"14"}), ("개인정보 영향평가 대상", "개인정보 보호법 시행령", {"35"}),
]
Q_PARA = [
    ("돈을 빌려주고 오래 안 받으면 언제 못 받게 돼?", "민법", {"162", "163", "164"}),
    ("몇 살부터 어른으로 인정돼?", "민법", {"4"}),
    ("부모님이 돌아가시면 누가 먼저 재산을 물려받아?", "민법", {"1000"}),
    ("남의 물건을 몰래 가져가면 무슨 벌을 받아?", "형법", {"329"}),
    ("내 잘못으로 남에게 피해를 주면 물어줘야 해?", "민법", {"750"}),
    ("공격을 막으려다 상대를 다치게 하면 처벌받아?", "형법", {"21", "민법:761"}),
    ("유언장을 직접 손으로 쓰려면 어떻게 해야 해?", "민법", {"1066"}),
    ("사람을 죽이면 어떤 처벌을 받아?", "형법", {"250"}),
    ("공무원이 선물 받아도 돼?", "공무원 행동강령", {"14"}),
    ("소송은 어느 법원에 내야 해?", "민사소송법", {"2", "3"}),
]
QUESTIONS = [("번호", q) for q in Q_NUMBER] + [("용어", q) for q in Q_TERM] + [("바꿔말함", q) for q in Q_PARA]
NUM_Q = re.compile(r"(\d+)\s*조(?:의\s*(\d+))?")


def terms_of(query):
    return [t for t in re.split(r"\s+", query.strip()) if t]


IDF = {}


def score_idf(terms, doc_bigrams):
    """항마다 겹친 바이그램의 idf 합 / 항 바이그램 idf 합 — 흔한 2글자("처벌", "하여")가 드문 2글자("절도")를 이기지 않게."""
    total = 0.0
    for term in terms:
        b = bigrams(term)
        if not b:
            continue
        w = {x: IDF.get(x, 1.0) for x in b}
        total += sum(w[x] for x in b if x in doc_bigrams) / sum(w.values())
    return total


SCORER = {"fn": None}


def rerank(query, laws, cand):
    """cand: [(chunk, path_bigrams, text_bigrams)] → 점수 내림차순 chunk 목록."""
    terms = terms_of(query)
    m = NUM_Q.search(query)
    want = (m.group(1) + (f"의{m.group(2)}" if m.group(2) else "")) if m else None
    named = [law for law in laws if law.replace(" ", "") in query.replace(" ", "")]
    named = [law for law in named if not any(o != law and law in o and o.replace(" ", "") in query.replace(" ", "") for o in named)]
    scored = []
    for c, pb, tb in cand:
        f = SCORER["fn"] or score
        s = 1.5 * f(terms, pb) + f(terms, tb)
        if want and c["article"] == want:
            s += 3.0
        if named and c["law"] in named:
            s += 1.0
        scored.append((s, c))
    scored.sort(key=lambda x: -x[0])
    return [c for _, c in scored]


def hit(ranked, law, gold, k):
    """gold 의 "법령:조" 항목은 다른 법령의 같은 제도(예: 정당방위 — 형법 21조·민법 761조)를 정답으로 인정한다."""
    return any((c["law"] == law and c["article"] in gold) or f"{c['law']}:{c['article']}" in gold for c in ranked[:k])


# ---------------------------------------------------------------- 색인 (A) FTS4 / (B) 포스팅
def build_fts(chunks, path):
    db = sqlite3.connect(path)
    db.execute("CREATE VIRTUAL TABLE chunk_fts USING fts4(bg, tokenize=simple)")
    db.execute("CREATE VIRTUAL TABLE chunk_terms USING fts4aux(chunk_fts)")
    db.executemany("INSERT INTO chunk_fts(docid, bg) VALUES (?, ?)",
                   ((i, " ".join(sorted(bigrams(c["path"] + " " + c["text"])))) for i, c in enumerate(chunks)))
    db.commit()
    return db


def build_post(chunks, path):
    db = sqlite3.connect(path)
    db.execute("CREATE TABLE post(bg TEXT NOT NULL, chunk INTEGER NOT NULL, PRIMARY KEY(bg, chunk)) WITHOUT ROWID")
    db.executemany("INSERT INTO post VALUES (?, ?)",
                   ((b, i) for i, c in enumerate(chunks) for b in bigrams(c["path"] + " " + c["text"])))
    db.execute("CREATE TABLE df AS SELECT bg, COUNT(*) AS n FROM post GROUP BY bg")
    db.execute("CREATE UNIQUE INDEX df_bg ON df(bg)")
    db.commit()
    return db


def idf(n, total):
    import math
    return math.log((total + 1) / (n + 1)) + 1.0


def cand_fts(db, qb, total, limit, max_df):
    """문서빈도(fts4aux)로 흔한 바이그램을 거르고, 남은 바이그램 OR 매치 → matchinfo 로 idf 가중 겹침 상위 limit."""
    if not qb:
        return []
    rows = db.execute(f"SELECT term, documents FROM chunk_terms WHERE col='*' AND term IN ({','.join('?' * len(qb))})", list(qb)).fetchall()
    df = {t: n for t, n in rows}
    keep = [b for b in qb if 0 < df.get(b, 0) <= max_df * total]
    if not keep:
        keep = sorted((b for b in qb if df.get(b)), key=lambda b: df[b])[:3]
    if not keep:
        return []
    w = [idf(df[b], total) for b in keep]
    q = " OR ".join(keep)
    out = []
    for docid, mi in db.execute("SELECT docid, matchinfo(chunk_fts, 'x') FROM chunk_fts WHERE bg MATCH ?", (q,)):
        ints = [int.from_bytes(mi[i:i + 4], "little") for i in range(0, len(mi), 4)]
        s = sum(w[p] for p in range(len(keep)) if ints[3 * p] > 0)
        out.append((s, docid))
    out.sort(key=lambda x: -x[0])
    return [d for _, d in out[:limit]]


def cand_post(db, qb, total, limit, max_df):
    if not qb:
        return []
    qs = ",".join("?" * len(qb))
    df = dict(db.execute(f"SELECT bg, n FROM df WHERE bg IN ({qs})", list(qb)).fetchall())
    keep = [b for b in qb if 0 < df.get(b, 0) <= max_df * total] or sorted((b for b in qb if df.get(b)), key=lambda b: df[b])[:3]
    if not keep:
        return []
    db.execute("CREATE TEMP TABLE IF NOT EXISTS qw(bg TEXT PRIMARY KEY, w REAL)")
    db.execute("DELETE FROM qw")
    db.executemany("INSERT INTO qw VALUES (?, ?)", [(b, idf(df[b], total)) for b in keep])
    rows = db.execute("SELECT p.chunk, SUM(q.w) s FROM post p JOIN qw q ON p.bg = q.bg GROUP BY p.chunk ORDER BY s DESC LIMIT ?", (limit,)).fetchall()
    return [r[0] for r in rows]


def dir_size(path):
    total = os.path.getsize(path)
    for ext in ("-wal", "-journal"):
        if os.path.exists(path + ext):
            total += os.path.getsize(path + ext)
    return total


def main():
    laws = load_laws()
    total_chars = sum(sum(map(len, ls)) for ls in laws.values())
    print(f"자료: {len(laws)}개 법령, 본문 {total_chars:,}자 — " + ", ".join(f"{k} {sum(map(len, v)):,}" for k, v in laws.items()), flush=True)

    # ① 조각 규칙
    print("\n## ① 조각 규칙")
    per_cap = {}
    for cap in (300, 500, 800):
        chunks = [c for name, ls in laws.items() for c in chunk_law(name, ls, cap)]
        articles = {(c["law"], c["article"]) for c in chunks}
        split_articles = {(c["law"], c["article"]) for c in chunks if c["split"]}
        lens = [len(c["text"]) for c in chunks]
        per_cap[cap] = chunks
        print(f"상한 {cap}: 조각 {len(chunks):,} · 조 {len(articles):,} · 갈린 조 {len(split_articles):,} ({100 * len(split_articles) / len(articles):.1f}%) "
              f"· 본문 평균 {statistics.mean(lens):.0f}자 · 중앙 {statistics.median(lens):.0f}자 · 최대 {max(lens)}자", flush=True)

    # ③ 전수 스캔(상한선) 회수율 — 조각 상한별
    print("\n## ③-a 전수 스캔 회수율 (색인 없이 모든 조각 점수 — 상한선)")
    for cap, chunks in per_cap.items():
        pre = [(c, bigrams(c["path"]), bigrams(c["text"])) for c in chunks]
        res = {}
        for kind, (q, law, gold) in QUESTIONS:
            r = rerank(q, laws, pre)
            res.setdefault(kind, []).append((hit(r, law, gold, 1), hit(r, law, gold, 3)))
        print(f"상한 {cap}: " + " · ".join(f"{k} @1 {sum(a for a, _ in v)}/{len(v)} @3 {sum(b for _, b in v)}/{len(v)}" for k, v in res.items()), flush=True)

    # ③-a' IDF 가중 점수 (상한 500, 전수 스캔)
    import math
    chunks500 = per_cap[500]
    df = {}
    for c in chunks500:
        for b in bigrams(c["path"] + " " + c["text"]):
            df[b] = df.get(b, 0) + 1
    IDF.update({b: math.log((len(chunks500) + 1) / (n + 1)) + 1.0 for b, n in df.items()})
    pre500 = [(c, bigrams(c["path"]), bigrams(c["text"])) for c in chunks500]
    for label, fn in (("원래 점수", None), ("IDF 가중", score_idf)):
        SCORER["fn"] = fn
        res = {}
        for kind, (q, law, gold) in QUESTIONS:
            r = rerank(q, laws, pre500)
            res.setdefault(kind, []).append((hit(r, law, gold, 1), hit(r, law, gold, 3)))
        print(f"상한 500 {label}: " + " · ".join(f"{k} @1 {sum(a for a, _ in v)}/{len(v)} @3 {sum(b for _, b in v)}/{len(v)}" for k, v in res.items()), flush=True)
    SCORER["fn"] = score_idf

    # ② 색인 + ③-b 후보 회수율 (상한 500 기준, IDF 가중 재순위)
    cap = 500
    chunks = per_cap[cap]
    pre = [(c, bigrams(c["path"]), bigrams(c["text"])) for c in chunks]
    total = len(chunks)
    tmp = tempfile.mkdtemp(prefix="exp74_")
    print(f"\n## ② 색인 (상한 {cap}, 조각 {total:,})")
    dbs = {}
    for name, build in (("A_FTS4", build_fts), ("B_POST", build_post)):
        p = os.path.join(tmp, name + ".db")
        t0 = time.perf_counter()
        db = build(chunks, p)
        dt = time.perf_counter() - t0
        db.execute("VACUUM")
        print(f"{name}: 구축 {dt:.1f}s · 파일 {dir_size(p) / 1e6:.1f}MB", flush=True)
        dbs[name] = db
    raw_text_mb = sum(len((c['path'] + c['text']).encode('utf-8')) for c in chunks) / 1e6
    print(f"(참고) 조각 글자 자체(머리말 포함, UTF-8): {raw_text_mb:.1f}MB — 앱은 이것도 따로 저장한다", flush=True)

    print("\n## ③-b 색인 후보 → 재순위 회수율 · 질의 시간 (PC)")
    for name, fn in (("A_FTS4", cand_fts), ("B_POST", cand_post)):
        for limit in (20, 50, 100):
            for max_df in (0.05, 0.2):
                res, times = {}, []
                for kind, (q, law, gold) in QUESTIONS:
                    qb = bigrams(q)
                    t0 = time.perf_counter()
                    ids = fn(dbs[name], qb, total, limit, max_df)
                    r = rerank(q, laws, [pre[i] for i in ids])
                    times.append((time.perf_counter() - t0) * 1000)
                    res.setdefault(kind, []).append((hit(r, law, gold, 1), hit(r, law, gold, 3)))
                print(f"{name} 후보 {limit} df≤{max_df}: " + " · ".join(
                    f"{k} @1 {sum(a for a, _ in v)}/{len(v)} @3 {sum(b for _, b in v)}/{len(v)}" for k, v in res.items())
                      + f" · 질의 중앙 {statistics.median(times):.1f}ms 최대 {max(times):.1f}ms", flush=True)

    # 바꿔 말한 질문에서 무엇을 놓쳤는지 (전수 스캔 상한 500)
    print("\n## 바꿔 말한 질문 상위 1개 (전수 스캔, 상한 500)")
    for kind, (q, law, gold) in QUESTIONS:
        if kind != "바꿔말함":
            continue
        top = rerank(q, laws, pre)[0]
        print(f"{'O' if top['law'] == law and top['article'] in gold else 'X'} {q} → {top['path'][:70]}", flush=True)


if __name__ == "__main__":
    main()

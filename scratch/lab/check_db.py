# 백업된 실기기 DB 무결성 확인 (일회성)
import sqlite3

conn = sqlite3.connect("fixtures/kosmos_db")
tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'")]
print("tables:", tables)
for t in tables:
    try:
        n = conn.execute(f"SELECT COUNT(*) FROM [{t}]").fetchone()[0]
        print(f"  {t}: {n} rows")
    except sqlite3.DatabaseError as e:
        print(f"  {t}: ERROR {e}")

# 오염 흔적 확인
for pattern in ["<|tool_call>", "20817", "111월", "20202년"]:
    try:
        n = conn.execute(
            "SELECT COUNT(*) FROM chat_messages WHERE content LIKE ?", (f"%{pattern}%",)
        ).fetchone()[0]
        print(f"오염 '{pattern}': {n}건")
    except sqlite3.DatabaseError as e:
        print(f"오염 검사 실패({pattern}): {e}")
        break
conn.close()

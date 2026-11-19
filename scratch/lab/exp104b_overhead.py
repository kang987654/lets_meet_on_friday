"""exp104b — 0.42.9 T2 줄("Reminder timed by something the user told you: `search_memory` first.")의 비용 실측. AGENTS §2-⑤.

새 픽스처(앱이 내보낸 system_instruction*.txt)에서 그 줄을 빼 옛 지시를 만들고, engine.tokenize 차이와
첫 턴 token_count(출력 상한 1 이 아니라 짧은 "안녕" — 같은 입력이라 차이만 본다) 차이를 잰다.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / "regression"))
import run as R  # noqa: E402

K = R.K
LINE = "\n- Reminder timed by something the user told you: `search_memory` first."

eng = R.engine()


def kv(sysm):
    with K.create_chat_conversation(eng, tools=R.TOOLS_OFF, system_message=sysm) as conv:
        conv.send_message("안녕")
        return conv.token_count


for name in ("system_instruction.txt", "system_instruction_docsearch.txt", "system_instruction_en.txt"):
    new = K.fixture(name)
    old = new.replace(LINE, "")
    t_old, t_new = len(eng.tokenize(old)), len(eng.tokenize(new))
    print(f"{name}: 줄 있음 {LINE in new} · tokenize {t_old} → {t_new} (차이 {t_new - t_old})", flush=True)
    if name == "system_instruction.txt":
        k_old, k_new = kv(old.replace(R._WIKI_LINE, "")), kv(new.replace(R._WIKI_LINE, ""))
        print(f"  첫 턴 token_count(웹 검색 끔) {k_old} → {k_new} (차이 {k_new - k_old})", flush=True)

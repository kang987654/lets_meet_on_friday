"""측정 전용 임시 로그(태그 KOSMOS_M1)를 앱 소스에 붙이고 뗀다 — 0.39.0 M1·0.39.2 M4 에 쓴 것. **붙인 상태로 커밋하지 않는다.**

사용 (저장소 루트에서, 실험실 파이썬):
  python scratch/lab/device/latency_instr.py apply    # 붙임 → ./gradlew :app:assembleDebug → adb install -r
  python scratch/lab/device/latency_instr.py revert   # 원래대로(백업에서 복사) → git status 가 깨끗해야 한다
찍히는 표시: request(요청 받음) · runner enter(러너 진입, oneShot·툴 회신 여부) · locked(엔진 잠금 얻음) · create conversation(대화 새로 만듦)
· conversation ready(대화 준비 끝) · first token(첫 글자). 런너 자체 로그(태그 GemmaModelRunner)의 "KV usage: 턴 종료 tokens=…",
"turn done: … toolCalls=[…]", "대화 미리 준비 …", "Engine initialized …" 와 함께 읽는다(session.py).
앵커가 코드와 어긋나면 assert 로 멈춘다 — 앵커를 고쳐 쓰고, 고친 사실을 계획서에 적는다.
"""
import io
import os
import shutil
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
BACKUP = os.path.join(tempfile.gettempdir(), "kosmos_latency_instr_bak")
L = 'android.util.Log.i("KOSMOS_M1", '
FILES = {
    "app/src/main/java/com/kosmos/app/assistant/orchestrator/AssistantOrchestrator.kt": [
        ("    suspend fun processRequest(request: ChatRequest): AgentResult {\n",
         "    suspend fun processRequest(request: ChatRequest): AgentResult {\n        " + L + '"request")\n'),
    ],
    "app/src/main/java/com/kosmos/app/runtime/gemma/GemmaModelRunner.kt": [
        ("    ): AppResult<ModelTurn> = withContext(llmDispatcher) {\n        if (loadState.value !is ModelLoadState.Ready) {",
         "    ): AppResult<ModelTurn> = withContext(llmDispatcher) {\n        " + L
         + '"runner enter oneShot=${prompt.oneShot} tool=${prompt.toolResponse != null}")\n        if (loadState.value !is ModelLoadState.Ready) {'),
        ("                val currentConversation = getOrCreateConversation(readyEngine, prompt)\n",
         "                " + L + '"locked")\n                val currentConversation = getOrCreateConversation(readyEngine, prompt)\n                '
         + L + '"conversation ready")\n'),
        ("                                if (token.isNotEmpty()) {\n                                    onToken.invoke(token)",
         "                                if (token.isNotEmpty()) {\n                                    if (tokenCount == 0) " + L
         + '"first token")\n                                    onToken.invoke(token)'),
        ("        val newConversation = try {\n            currentEngine.createConversation(chatConversationConfig(prompt))",
         "        " + L + '"create conversation")\n        val newConversation = try {\n            currentEngine.createConversation(chatConversationConfig(prompt))'),
    ],
}


def apply():
    os.makedirs(BACKUP, exist_ok=True)
    for rel, reps in FILES.items():
        p = os.path.join(ROOT, rel)
        shutil.copy(p, os.path.join(BACKUP, os.path.basename(p)))
        s = io.open(p, encoding="utf-8").read()
        for old, new in reps:
            assert s.count(old) == 1, f"앵커가 {s.count(old)}번 나온다 — 코드가 바뀌었다: {rel}: {old[:60]!r}"
            s = s.replace(old, new)
        io.open(p, "w", encoding="utf-8", newline="").write(s)


def revert():
    for rel in FILES:
        p = os.path.join(ROOT, rel)
        shutil.copy(os.path.join(BACKUP, os.path.basename(p)), p)


if __name__ == "__main__":
    {"apply": apply, "revert": revert}[sys.argv[1]]()
    print("ok", sys.argv[1], flush=True)

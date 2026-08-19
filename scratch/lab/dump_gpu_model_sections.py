"""GPU 변형을 GPU 백엔드로 로드해 실제 포함된 섹션(시그니처)을 열거한다."""

import pathlib
import subprocess
import sys

LAB = pathlib.Path(__file__).parent
CHILD = """
import sys
import litert_lm as llm
path = sys.argv[1]
e = llm.Engine(path, backend=llm.Backend.GPU, max_num_tokens=1024)
c = e.create_conversation(system_message="test")
print("RESULT: engine+conversation OK (GPU backend)")
c.close(); e.close()
"""

path = str(LAB.parent / "gemma-4-E4B-it-gpu.litertlm")
proc = subprocess.run(
    [sys.executable, "-c", CHILD, path],
    capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=600,
)
for line in (proc.stderr or "").splitlines():
    if any(k in line for k in ["Section", "signature=", "not found", "Loaded:", "activation"]):
        print(line.strip())
print((proc.stdout or "").strip())

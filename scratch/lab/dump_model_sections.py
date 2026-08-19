"""두 모델 파일의 내부 섹션(컴포넌트) 목록 대조 — 오디오 인코더 존재 여부 확정.

litertlm 파일은 섹션 컨테이너다. 로더가 어떤 섹션을 찾는지/찾았는지를 stderr 로 찍으므로,
엔진을 각 파일로 로드(추론 없음)하며 그 로그를 비교한다. 오디오 백엔드를 CPU 로 설정해
오디오 인코더 요구를 강제한다.
"""

import pathlib
import subprocess
import sys

LAB = pathlib.Path(__file__).parent
CHILD = """
import sys
import litert_lm as llm
path = sys.argv[1]
try:
    e = llm.Engine(path, backend=llm.Backend.CPU, audio_backend=llm.Backend.CPU, max_num_tokens=1024)
    c = e.create_conversation(system_message="test")
    print("RESULT: engine+conversation OK (audio backend CPU)")
    c.close(); e.close()
except Exception as ex:
    print(f"RESULT: FAILED - {type(ex).__name__}: {str(ex)[:200]}")
"""

for name in ["gemma-4-E4B-it.litertlm", "gemma-4-E4B-it-gpu.litertlm"]:
    path = str(LAB.parent / name)
    print(f"\n===== {name} =====")
    proc = subprocess.run(
        [sys.executable, "-c", CHILD, path],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=600,
    )
    for line in (proc.stderr or "").splitlines():
        if any(k in line for k in ["Section", "signature=", "not found", "AUDIO", "Loaded:"]):
            print("  " + line.strip())
    print("  " + (proc.stdout or "").strip())

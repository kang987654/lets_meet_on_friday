"""max_num_tokens 별 프로세스 메모리를 잰다 (exp17 보조).

## 실측 (2026-08-12, cpu backend, gemma-4-E4B-it)

    max_num_tokens=기본(4096)   5,003 MB
    max_num_tokens=8192         9,188 MB   (+4,185MB / +4096토큰 ≈ 토큰당 1MB)

## 왜 중요한가

모델 카드의 컨텍스트 창은 **128,000 토큰**이다. 그것을 KV 로 다 잡으려면 위 비율로 100GB 이상이
필요하다 — 컨텍스트 길이와 KV 할당량은 같은 것이 아니라는 증거다. 런타임은 기본값 4096 으로
바운드를 걸고, gallery 는 이 값을 사용자 설정으로 노출한다(기본 1024).

실기기에서 관측한 앱 PSS 5.5GB 가 여기 기본값 5.0GB 와 같은 자릿수라는 점이 **안드로이드 기본값도
4096 이라는 간접 증거**다(AAR 에서 직접 확인한 것은 아니다).

사용법:  ../.venv/Scripts/python.exe measure_kv_memory.py default
         ../.venv/Scripts/python.exe measure_kv_memory.py 8192
"""
import ctypes, ctypes.wintypes as wt, sys
import kosmos_lab as K

class PMC(ctypes.Structure):
    _fields_ = [("cb", wt.DWORD), ("PageFaultCount", wt.DWORD),
                ("PeakWorkingSetSize", ctypes.c_size_t), ("WorkingSetSize", ctypes.c_size_t),
                ("QuotaPeakPagedPoolUsage", ctypes.c_size_t), ("QuotaPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t), ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                ("PagefileUsage", ctypes.c_size_t), ("PeakPagefileUsage", ctypes.c_size_t)]

psapi = ctypes.WinDLL("psapi")
psapi.GetProcessMemoryInfo.argtypes = [wt.HANDLE, ctypes.POINTER(PMC), wt.DWORD]
psapi.GetProcessMemoryInfo.restype = wt.BOOL
k32 = ctypes.WinDLL("kernel32")
k32.GetCurrentProcess.restype = wt.HANDLE

def mem():
    pmc = PMC(); pmc.cb = ctypes.sizeof(PMC)
    if not psapi.GetProcessMemoryInfo(k32.GetCurrentProcess(), ctypes.byref(pmc), pmc.cb):
        raise OSError(ctypes.get_last_error())
    return pmc.WorkingSetSize / 1048576, pmc.PeakWorkingSetSize / 1048576

cap = None if sys.argv[1] == "default" else int(sys.argv[1])
b_ws, _ = mem()
lab = K.Lab(backend="cpu", verbose=False, max_num_tokens=cap)
lab.run("안녕", K.Config(tools=[], few_shot=False, constrained=None, max_output_tokens=8))
a_ws, a_pk = mem()
print(f"RESULT max_num_tokens={str(cap or 'default'):8} before={b_ws:7.0f}MB after={a_ws:7.0f}MB peak={a_pk:7.0f}MB delta={a_ws-b_ws:7.0f}MB")
lab.close()

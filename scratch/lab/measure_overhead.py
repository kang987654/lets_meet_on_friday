"""시스템 지시 + 툴 선언이 KV 를 얼마나 먹는지 실측한다."""
import device_fixture as F, kosmos_lab as K, litert_lm as L
K.use_utf8()
si = F.system_instruction()
lab = K.Lab(backend="cpu", verbose=False)

def count(tools, few_shot, history=None, label=""):
    cfg = K.Config(tools=tools, few_shot=few_shot, system=si,
                   constrained=bool(tools) or None)
    kw = lab._conv_kwargs(cfg, history)
    with lab.engine.create_conversation(**kw) as conv:
        conv.send_message("안녕")
        return conv.token_count

print(f"{'조건':44} {'token_count':>12}")
print(f"{'시스템 지시만 (툴 0, few-shot X)':44} {count([], False):>12}")
print(f"{'+ 툴 5종 선언':44} {count(K.ALL_TOOLS, False):>12}")
print(f"{'+ few-shot 시범':44} {count(K.ALL_TOOLS, True):>12}")
full = F.messages(before=F.WIKI_HISTORY_CUT)
for d in (8, 20, 40):
    print(f"{'+ 히스토리 %d개' % d:44} {count(K.ALL_TOOLS, True, full[-d:]):>12}")
lab.close()

"""exp77 — LiteRT-LM 0.19.0 확인 (PC CPU). 같은 스크립트를 0.18.0(환경 기본)과 0.19.0(PYTHONPATH 로 앞에 둔 별도 폴더)으로 각각 돌려 비교한다.

① 버전·모델 정보(추측 디코딩=MTP 지원 여부) ② 임베딩 값이 버전 사이에 같은가(문장 5개 벡터 저장 → 비교)
③ 채팅 기본 동작(툴 호출·숫자 그대로 따라 쓰기) ④ 추측 디코딩 OFF/ON/ADAPTIVE 의 생성 속도와 답 일치 ⑤ Conversation.clone() — 복제본에 보낸 턴이 원본 KV 를 건드리지 않는가.
사용: python exp77_litertlm019.py <tag>   (결과 exp77_out_<tag>.txt(JSON) — 실험 출력이라 커밋하지 않는다)
"""
import json
import pathlib
import sys
import time

import litert_lm as llm
import litert_lm.embedding_engine as EE

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import kosmos_lab as K  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
LAB = pathlib.Path(__file__).resolve().parent
TAG = sys.argv[1] if len(sys.argv) > 1 else "run"
OUT = {"tag": TAG, "module": llm.__file__}
EMB_MODEL = LAB.parent / "embeddinggemma-2-740m.litertlm"
SENTENCES = ["임대차 보증금 반환", "몰래 남의 물건을 가져가면 처벌", "제162조 채권의 소멸시효", "내일 오후 3시 치과 예약", "자물쇠 비밀번호는 8282"]


def text_of(res):
    content = res.get("content")
    return content if isinstance(content, str) else "".join(c.get("text", "") for c in (content or []) if isinstance(c, dict))


def section(name):
    print(f"\n== {name}", flush=True)


section("① 버전·모델 정보")
print(llm.__file__, flush=True)
for p in [K.MODEL_PATH, str(EMB_MODEL)]:
    try:
        with llm.ModelInfo(p) as info:
            spec = info.llm.has_speculative_decoding_support() if getattr(info, "llm", None) else None
            print(f"{pathlib.Path(p).name}: speculative={spec}", flush=True)
            OUT[f"spec_{pathlib.Path(p).stem}"] = spec
    except Exception as e:  # noqa: BLE001
        print(f"{pathlib.Path(p).name}: ModelInfo 실패 {e!r}", flush=True)

section("② 임베딩")
emb = EE.EmbeddingEngine(str(EMB_MODEL), max_input_length=512)
opts = EE.EmbeddingOptions(normalize=True, input_overflow_strategy=EE.InputOverflowStrategy.TRUNCATE)
t0 = time.perf_counter()
OUT["emb"] = [list(emb.compute_embedding(f"task: search result | query: {s}", opts).embedding) for s in SENTENCES]
print(f"문장 {len(SENTENCES)}개 {1000 * (time.perf_counter() - t0) / len(SENTENCES):.0f}ms/개 · 차원 {len(OUT['emb'][0])}", flush=True)
del emb

section("③ 채팅 기본")
engine = K.create_engine()
checks = [
    ("tool", "내일 오후 3시에 치과 일정 추가해줘"),
    ("digits", "다음 문자열을 그대로 따라 써줘: 2026-08-17T10:00 / 010-2468-1357 / 8282"),
]
OUT["chat"] = {}
for key, q in checks:
    conv = K.create_chat_conversation(engine)
    res = conv.send_message(K.with_turn_reminder(q))
    calls = [(c.get("function", {}) or {}).get("name") or c.get("name") for c in (res.get("tool_calls") or [])]
    OUT["chat"][key] = {"calls": calls, "text": text_of(res)[:200], "raw_calls": json.dumps(res.get("tool_calls"), ensure_ascii=False)[:300]}
    print(key, calls, text_of(res)[:120].replace("\n", " "), flush=True)
    conv.close()

section("④ 추측 디코딩 속도")
PROMPT = "가을에 하기 좋은 산책 코스를 고르는 기준을 다섯 가지, 각각 두 문장으로 설명해줘."
modes = [None]
if hasattr(llm, "SpeculativeDecodingMode"):
    modes += [llm.SpeculativeDecodingMode.OFF, llm.SpeculativeDecodingMode.ON, llm.SpeculativeDecodingMode.ADAPTIVE]
OUT["spec"] = {}
for mode in modes:
    kwargs = {}
    if mode is not None:
        kwargs["speculative_decoding_config"] = llm.SpeculativeDecodingConfig(mode=mode)
    try:
        conv = engine.create_conversation(
            system_message="너는 한국어 비서다.",
            thinking_config=llm.ThinkingConfig(enable_thinking=False),
            sampler_config=llm.SamplerConfig(top_k=1, top_p=0.95, temperature=1.0),
            **kwargs,
        )
        t0 = time.perf_counter()
        res = conv.send_message(PROMPT)
        dt = time.perf_counter() - t0
        out_tokens = conv.token_count
        text = text_of(res)
        name = "default" if mode is None else mode.name
        OUT["spec"][name] = {"sec": round(dt, 2), "tokens_total": out_tokens, "chars": len(text), "head": text[:80]}
        print(f"{name}: {dt:.1f}s · 대화 토큰 {out_tokens} · 글자 {len(text)} · {text[:50]!r}", flush=True)
        conv.close()
    except Exception as e:  # noqa: BLE001
        print(f"{mode}: 실패 {e!r}", flush=True)
        OUT["spec"][str(mode)] = {"error": repr(e)}

section("⑤ Conversation.clone")
conv = K.create_chat_conversation(engine)
conv.send_message(K.with_turn_reminder("내 자전거 자물쇠 번호는 4729야. 그냥 알고만 있어, 저장은 하지 마."))
base = conv.token_count
if hasattr(conv, "clone"):
    fork = conv.clone()
    fork.send_message("오늘 저녁 메뉴 하나만 추천해줘")
    after_fork = conv.token_count
    res = conv.send_message(K.with_turn_reminder("아까 말한 자전거 자물쇠 번호가 뭐였지?"))
    OUT["clone"] = {"base": base, "fork_tokens": fork.token_count, "orig_after_fork": after_fork, "answer": text_of(res)[:120]}
    print(OUT["clone"], flush=True)
    fork.close()
else:
    OUT["clone"] = None
    print("clone 없음", flush=True)
conv.close()

(LAB / f"exp77_out_{TAG}.txt").write_text(json.dumps(OUT, ensure_ascii=False), encoding="utf-8")
print("저장", f"exp77_out_{TAG}.txt", flush=True)

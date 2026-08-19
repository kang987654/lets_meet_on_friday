# [litert-lm] Digit corruption in GPU backend generation with long context (fp16 activations); deterministic repro on desktop

## Summary

With **Gemma 4 E4B** (`gemma-4-E4B-it.litertlm`) on **litert-lm 0.16.0**, greedy generation on the **GPU backend corrupts digit sequences once the conversation context exceeds ~2K tokens**. The same prompt on the CPU backend is correct at all depths tested. Setting `activation_data_type=FLOAT32` (Python API) fully fixes it — pointing to fp16 activation precision accumulation in the text decoder (loader default: "System's default activation type for Text decoder is fp16").

The repro is **deterministic** (greedy top_k=1; identical output across repeats) and reproduces on desktop (Windows x86_64, WebGPU/NVIDIA) as well as Android (arm64, Adreno / Galaxy S25 Ultra, AAR 0.16.0) — so it is not arch- or device-specific.

## Repro

- litert-lm 0.16.0 (Python, Windows x86_64) / litertlm-android 0.16.0 (Galaxy S25 Ultra)
- Model: `litert-community/gemma-4-E4B-it-litert-lm` → `gemma-4-E4B-it.litertlm` (SHA256 0B2A8980…52E0)
- Engine: `max_num_tokens=4096`; Conversation: greedy (`top_k=1, temperature=1.0, top_p=0.95`), `ThinkingConfig(enable_thinking=False)`, constrained decoding on, 5 function-calling tools declared
- Prompt: Korean personal-assistant system message + N turns of plain chat history + user turn asking to schedule "다음주 월요일 10시 팀 회의" (expected tool call arg `start_time = "2026-08-17T10:00:00"`)

| Backend | Context tokens | `start_time` generated |
|---|---|---|
| CPU | 1,398 / 2,784 | `2026-08-17T10:00:00` (correct, all depths) |
| GPU | 1,398 | `2026-08-17T10:00:00` (correct) |
| GPU | 1,854 | correct |
| GPU | **2,122** | **`20826-08-17T1000:000`** |
| GPU | 2,270–2,784 | corrupted, worsening with depth (`202026-08-17T0000000`, …) |
| GPU + `activation_data_type=FLOAT32` | 2,784 | `2026-08-17T10:00:00` (correct) |

Corruption affects digits in plain prose too (e.g. `2020년 11월` → `20202년 1월1일`), not only tool-call arguments. Onset boundary measured between 1,854 (clean) and 2,122 (corrupted) on desktop GPU.

## Asks

1. **Expose `activationDataType` in the Android AAR** (`EngineConfig`) — the fix exists in the Python API but `litertlm-android` 0.16.0 has no equivalent (verified via javap on `EngineConfig`/`ExperimentalFlags`).
2. The recently published **`gemma-4-E4B-it-gpu.litertlm`** fixes the corruption (verified same repro) but is **text-only**: loader reports `TF_LITE_AUDIO_ENCODER_HW`, `TF_LITE_VISION_ENCODER`, and `TF_LITE_PREFILL_DECODE` not found — so adopting it drops audio, vision, and CPU fallback. Please publish a GPU-safe variant that retains audio + vision encoders.

Workaround we ship meanwhile: cap conversation KV below the measured onset (~1,700 tokens), at the cost of usable history.

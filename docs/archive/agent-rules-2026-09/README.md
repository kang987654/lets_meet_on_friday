# 보관된 에이전트 지침 (2026-09-02 간소화)

이 폴더의 파일은 **더 이상 유효한 지침이 아니다.** 정본은 루트 `AGENTS.md` 하나이고,
모델·런타임 근거 등급은 `.agents/04_MODEL_EVIDENCE.md` 가 맡는다. 이력 보존을 위해 삭제
대신 옮겨 두었다 — 새 세션의 에이전트가 이 파일들을 따르면 **확정된 아키텍처를 되돌린다.**

| 파일 | 보관 이유 |
|---|---|
| `01_TOOLS_AND_GIT.md` | "raw shell 금지"는 특정 에이전트 시대 규칙. 살릴 것(UTF-8 No BOM, `reset --hard` 사전 허가)은 `AGENTS.md` 커밋 규칙으로 흡수 |
| `02_WORKFLOW_AND_AUDIT.md` | 3-Phase 절차는 `AGENTS.md` §2-①이 명시적으로 대체. `docs/agent/task.md` 등 삭제·대조 규칙은 그 파일들이 사라져 사문 |
| `03_ANDROID_TRIGGER.md` | SKILL 읽기 트리거 — SKILL 이 흡수돼 존재 이유 소멸 |
| `SKILL_android-friday.md` | Robolectric·AGP 9·KDoc 수칙이 `AGENTS.md` §2-②·§4 와 중복. 비중복 2줄(AnimatedContent, mock 용 `open suspend fun`)은 §4 로 이관 |
| `SKILL_litertlm-gemma4.md` | **현행 결정과 정면 충돌**: `<tool_call>` 텍스트 파싱(ADR-008 폐기), 윈도우 3,000토큰(현행 1,700 — ADR-021), thinking 여유 2~4천(사고 모드 꺼짐), MTP(미검증), 이미지 토큰 표(측정 없이 정하지 않기로 결정), `filesDir`(실제는 `getExternalFilesDir`). 근거 등급 미표기 — 04 규칙 위반 |
| `ui_improvement_plan_2026-08.md` | 상태 Done(0.5.12 기준), 시안 A′(0.19)로 대체된 완료 계획서 |

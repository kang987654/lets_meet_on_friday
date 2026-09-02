# .agents — 에이전트 진입점

**정본은 루트 `AGENTS.md` 다.** 어떤 에이전트(Claude Code · Antigravity/Gemini · 기타)든 작업 전에
그 파일을 읽는다. 이 폴더에 남은 것은 하나뿐이다:

- `04_MODEL_EVIDENCE.md` — 모델·런타임 주장의 **근거 등급 규칙**(공식 문서 › 우리 실측 › API 계약,
  gallery 는 근거가 아니다). 코드 주석·ADR 이 참조하므로 위치를 유지한다.

예전 규칙 파일(01~03, skills/)은 중복·충돌로 `docs/archive/agent-rules-2026-09/` 로 옮겼다 —
따르지 말 것. 회차 계획서는 `docs/plans/` 에 저장소 파일로 남긴다(계획 세션과 구현 세션이
다른 에이전트일 수 있으므로 — `AGENTS.md` §2-① 참조).

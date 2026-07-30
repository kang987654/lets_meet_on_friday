# Workflow & Audit Rules

ALWAYS: Split work into 3 phases: [Plan/Doc] -> [Execution/Code] -> [QA/Test]. Do not mix these contexts. Once coding is requested, drop planning and execute immediately.

ALWAYS: When asked to audit code or perform broad structural searches, you MUST use subagents (invoke_subagent) in parallel to prevent main context dilution.

CRITICAL: NEVER skip implementation parts and report the task as complete (No Fake Reporting). If something is skipped, log it explicitly in CHANGELOG.md.

CRITICAL: Deep-Dive Verification is REQUIRED. Do not rely on surface-level analysis. Cross-check file existence with `list_dir` or `grep_search` before planning.

ALWAYS: At the end of a session, summarize changes in `docs/CHANGELOG.md` and DELETE temporary agent files (task.md, implementation_plan.md, etc.) inside `docs/agent/`.

ALWAYS: Cross-check the initial implementation_plan.md with actual modified code line-by-line before concluding the session.

ALWAYS: For docs-as-code, do not overwrite original architecture docs. Save versioned updates in `docs/`.

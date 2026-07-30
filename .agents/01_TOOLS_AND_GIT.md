# Tools & Git Rules

CRITICAL: NEVER use raw shell commands (e.g., cat, echo, Get-Content, Set-Content) to view or modify files due to encoding issues and overhead. ALWAYS use the agent's built-in file tools (directory listing, file read/view, content search, file write/edit tools — whatever the current agent provides).

CRITICAL: Before committing with Git across multiple modules, ALWAYS run `git status --porcelain` to check for missed files.

CRITICAL: NEVER perform `git reset --hard` or `git clean -fd` without checking for untracked files and explicitly asking the user for permission.

CRITICAL: When moving files (e.g., `git mv`), check the destination carefully with a directory listing tool to avoid nested `.gitkeep` or folder duplication.

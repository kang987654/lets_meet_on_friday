# Tools & Git Rules

CRITICAL: NEVER use raw shell commands (e.g., cat, echo, Get-Content, Set-Content) to view or modify files due to encoding issues and overhead. ALWAYS use the agent's built-in file tools (directory listing, file read/view, content search, file write/edit tools — whatever the current agent provides).

CRITICAL: This repo contains Korean text in source comments, KDoc, and docs. On Windows, PowerShell writes files as ANSI (Windows-1252) by default, which corrupts them (한글 깨짐). If a shell write is genuinely unavoidable, you MUST force UTF-8 without BOM — e.g. `[System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding $false))`, or pass `-Encoding utf8` to `Out-File`/`Set-Content` and verify the result. Prefer the agent's file write/edit tools, which already emit UTF-8.

CRITICAL: Before committing with Git across multiple modules, ALWAYS run `git status --porcelain` to check for missed files.

CRITICAL: NEVER perform `git reset --hard` or `git clean -fd` without checking for untracked files and explicitly asking the user for permission.

CRITICAL: When moving files (e.g., `git mv`), check the destination carefully with a directory listing tool to avoid nested `.gitkeep` or folder duplication.

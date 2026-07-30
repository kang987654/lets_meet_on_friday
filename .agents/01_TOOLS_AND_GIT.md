# Tools & Git Rules

CRITICAL: NEVER use PowerShell (e.g., cat, echo, Get-Content, Set-Content) to modify or view files due to encoding issues and overhead. ALWAYS use built-in tools (`list_dir`, `view_file`, `grep_search`, `write_to_file`, `replace_file_content`, `multi_replace_file_content`).

CRITICAL: Before committing with Git across multiple modules, ALWAYS run `git status --porcelain` to check for missed files.

CRITICAL: NEVER perform `git reset --hard` or `git clean -fd` without checking for untracked files and explicitly asking the user for permission.

CRITICAL: When moving files (e.g., `git mv`), check the destination carefully with `list_dir` to avoid nested `.gitkeep` or folder duplication.

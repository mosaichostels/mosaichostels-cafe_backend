# Agent context router

Read when: starting any task in this repo (read this first, then only the files your task row lists).
Verified: 2026-10-29 @ 8b57217 (branch `main`; code lives only here, no other worktrees/branches: `git worktree list`, `git branch -a`).

| Task | Read |
| --- | --- |
| Every task | STATUS.md |
| Bug fix | GOTCHAS.md, ARCHITECTURE.md, TESTING.md |
| New feature / endpoint | CONVENTIONS.md, STRUCTURE.md, DECISIONS.md, ARCHITECTURE.md |
| eZee charge posting change | ARCHITECTURE.md (eZee section), DECISIONS.md, GOTCHAS.md, TESTING.md |
| Auth / security change | ARCHITECTURE.md, CONVENTIONS.md, TESTING.md |
| Deploy / config / env | COMMANDS.md, ENVIRONMENT.md, GOTCHAS.md |
| First look at the repo | OVERVIEW.md, STACK.md, STRUCTURE.md |

Coverage: [COVERAGE.md](COVERAGE.md); 2 unknowns remain. Full-read cost: 4,682 estimated tokens. Verified 2026-10-29 @ 8b57217.

| File | One line |
| --- | --- |
| OVERVIEW.md | What the service is, consumers, deploy target |
| STACK.md | Java/Spring/lib versions, Docker images |
| STRUCTURE.md | Package layout, key files, what not to touch |
| COMMANDS.md | Build, test, run, deploy, hook commands |
| ARCHITECTURE.md | Request flows, auth, routes + roles, collections, eZee, schedulers |
| CONVENTIONS.md | Coding rules to follow |
| ENVIRONMENT.md | Env var / property NAMES only |
| TESTING.md | Test layout, tools, how to run |
| DECISIONS.md | Durable design decisions and why |
| GOTCHAS.md | Traps, workspace coupling, hooks |
| STATUS.md | Current state, known gaps, unknowns |

Outside this repo (parent workspace, not a git repo): `../AGENTS.md`, `../docs/backend/AGENTS.md` ("backend AGENTS"), `../.claude/{agents,skills,hooks}`. Sibling repos: `../mosaichostels-cafe_frontend`, `../mosaichostels-cafe_android`.

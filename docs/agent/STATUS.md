# Status

Read when: always, at the start and end of every task (update after changing code).
Verified: 2026-10-23 @ 24bdfbb

- Branch `main` @ 24bdfbb "docs: add agent context (docs/agent) and AGENTS.md"; last code commit 16af0aa "Remove ntfy notifications". Working tree had only these docs and AGENTS.md changed at write time.
- Known gap (backend AGENTS): chargepost idempotency check-then-save is not atomic.
- Not run in this pass: build, tests. Pass state: unknown.
- Unknown: local run instructions, deployed version, where prod secrets are managed, exact env name for `config.jwtSecret`.
- Agent config: consolidation check found nothing to change (see run report); `.claude/settings.json` tracked, `.codex/hooks.json` local.

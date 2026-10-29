Read when: always, at the start and end of every task (update after changing code).
Verified: 2026-10-29 @ 8b57217
Covers: src/main/**, src/test/**, docs/agent/**, AGENTS.md

# Status

- Branch `main` @ 8b57217 "Shorten admin \"post charge\" push notification text"; `origin/main` is the same commit. Working tree was clean before this refresh.
- Known gap (backend AGENTS): chargepost idempotency check-then-save is not atomic.
- Not run in this pass: build, tests. Pass state: unknown.
- Unknown: local run instructions, deployed version, where prod secrets are managed, exact env name for `config.jwtSecret`.
- Agent config: consolidation check found nothing to change (see run report); `.claude/settings.json` tracked, `.codex/hooks.json` local.
- 2026-10-29: `OrderService` admin "post charge" push text is "Ready to post" / "<name> delivered. Post charge?" (8b57217; deployed only through the normal main-branch workflow, deployment state not verified). Tests not run in this refresh.

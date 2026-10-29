Read when: always, at the start and end of every task (update after changing code).
Verified: 2026-10-29 @ 9e39a6f
Covers: src/main/**, src/test/**, docs/agent/**, AGENTS.md

# Status
- Branch `main` @ 9e39a6f (docs-only commit on top of SuperAdmin merge f20c5f6). Docs refresh is uncommitted.
- Tests: `JAVA_HOME=<temurin-17> mvn -B -o test` -> 156 tests, 0 failures, BUILD SUCCESS (2026-10-29). `mvn -B verify` not run.
- Shipped: SUPERADMIN role (hierarchy `ROLE_SUPERADMIN > ROLE_ADMIN`, `/users` + `/audit` SuperAdmin-only, last-SuperAdmin delete guard, `scripts/promote-superadmin.sh`).
- Sweep 2026-10-29 (proven unused, 156 tests green): removed `CategoryService.getAllCategories`, `OrderService.logChargePost`, `CategoryRepository.findAllByOrderByShowOrderDesc`, `OrderStatusRepository.findByValue`, 4 unused imports. Backups: `~/.cache/project-context-backups/*backend*`. Lombok is used only by `ErrorResponse` (needed).
- Known gap (backend AGENTS): chargepost idempotency check-then-save is not atomic.
- Unknown: local run instructions, deployed version, where prod secrets are managed.
- Admin "post charge" push text: "Ready to post" / "<name> delivered. Post charge?" (OrderService); deployment state not verified.

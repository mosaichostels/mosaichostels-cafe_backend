Read when: always, at the start and end of every task (update after changing code).
Verified: 2026-11-10 @ d3b129c
Covers: src/main/**, src/test/**, docs/agent/**, AGENTS.md

# Status
- Branch `feat/pms-p0-foundation` @ a8c6ac6 (not merged to `main`; `main` was 9e39a6f). Docs refreshed 2026-11-10.
- Tests: `.superpowers/run-tests.sh` (Java 17 `mvn -q test`) -> 264 tests, 0 failures (2026-11-10). `mvn -B verify` not run.
- New `pms/` package (staff-hub eZee foundation, spec `2026-11-10-staff-hub-ezee-design.md` in the parent workspace `docs/android/superpowers/specs/`): gateway, rate limiter, cache, write guard, `/pms/status`, `/pms/unconfirmed`. No `pms.writes.*` flag is enabled in prod and there are no hub write endpoints yet (later phases); `application-prod.yml` untouched.
- Hub reads yield `pms.cafe-quiet-ms` (3000) after any café eZee call and the hub budget defaults to 2 per 5 s, so hub reads yield to an in-progress café burst. Hub calls made just before a burst still count against the shared window: this reduces, not eliminates, the chance of exceeding eZee's limit.
- DEPLOY GATE: before enabling any `pms.writes.*` flag, check in Mongo `db.pms_write_claims.getIndexes()` shows the sparse unique index on `openLockKey` and the TTL index on `doneAt`.
- Shipped: SUPERADMIN role (hierarchy `ROLE_SUPERADMIN > ROLE_ADMIN`, `/users` + `/audit` SuperAdmin-only, last-SuperAdmin delete guard, `scripts/promote-superadmin.sh`).
- Sweep 2026-10-29 (proven unused, 156 tests green): removed `CategoryService.getAllCategories`, `OrderService.logChargePost`, `CategoryRepository.findAllByOrderByShowOrderDesc`, `OrderStatusRepository.findByValue`, 4 unused imports. Backups: `~/.cache/project-context-backups/*backend*`. Lombok is used only by `ErrorResponse` (needed).
- Known gap (backend AGENTS): chargepost idempotency check-then-save is not atomic.
- Unknown: local run instructions, deployed version, where prod secrets are managed.
- Admin "post charge" push text: "Ready to post" / "<name> delivered. Post charge?" (OrderService); deployment state not verified.

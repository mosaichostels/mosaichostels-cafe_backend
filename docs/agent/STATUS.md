Read when: always, at the start and end of every task (update after changing code).
Verified: 2026-11-11 @ 2626fed + final review fixes
Covers: src/main/**, src/test/**, docs/agent/**, AGENTS.md

# Status
- Branch `feat/pms-p1-backend` @ 27745ef, stacked on `feat/pms-p0-foundation` (a8c6ac6). Neither is merged to `main` (9e39a6f) and nothing is pushed. Docs refreshed 2026-11-11.
- Tests: `.superpowers/run-tests.sh` (Java 17 `mvn -q test`) -> 396 tests, 0 failures (2026-11-11). `mvn -B verify` not run.
- P0 (`pms/` foundation, spec `2026-11-10-staff-hub-ezee-design.md` in the parent workspace `docs/android/superpowers/specs/`): gateway, rate limiter, cache, write guard, `/pms/status`, `/pms/unconfirmed`. No `pms.writes.*` flag is enabled in prod and there are no hub write endpoints; `application-prod.yml` untouched.
- P1 backend (plan `2026-11-10-staff-hub-p1-read-only-hub.md`, Tasks 0, 1, 2, 3, 5, 6, 8) done: `GET /pms/arrivals|departures|inhouse|bookings/{id}|guests|housekeeping/rooms` (ADMIN, STAFF), `GET /pms/reconcile/charges` (ADMIN), `GET /app/android-version` (any authenticated). Read-only; write features untouched, flags off. Routes, envelopes, cache names: ARCHITECTURE.md; decisions: DECISIONS.md; carried limits: GOTCHAS.md.
- P3 Task 1 (branch `feat/pms-p3-writes`, unpushed): cleaning assignments + departures-derived cleaning to-do (`/pms/cleaning-tasks`, `/pms/cleaning-assignments/{id}`; ADMIN, STAFF). Mongo only, no eZee write, no maintenance tickets (ruling P3-1). Tests: 419, 0 failures.
- Open items: night-audit business date vs calendar day for Retrieve Bills `Date` (CSV has no time; needs a post near midnight to confirm); reconcile FOUND/MISSING tuning against real data; booking detail may open the wrong sub-booking for dorm in-house rows (in-house rows carry the bare reservation number, which eZee rejects for dorm sub-bookings). Verified live 2026-11-11: guest stays CSV, in-house date format, sub-booking id, qty>1 Amount.
- Remaining: Android P1 run (plan Tasks 9-16, android repo); P3-P6 later; plan Task 4 (calendar, availability, catalogue) deferred to P4; Task 7 dropped. P1 is not deployed: merging to `main` deploys.
- Hub reads yield `pms.cafe-quiet-ms` (3000) after any café eZee call and the hub budget defaults to 2 per 5 s, so hub reads yield to an in-progress café burst. Hub calls made just before a burst still count against the shared window: this reduces, not eliminates, the chance of exceeding eZee's limit (watch the gateway's HTTP 429 log line).
- DEPLOY GATE: before enabling any `pms.writes.*` flag, check in Mongo `db.pms_write_claims.getIndexes()` shows the sparse unique index on `openLockKey` and the TTL index on `doneAt`.
- Shipped: SUPERADMIN role (hierarchy `ROLE_SUPERADMIN > ROLE_ADMIN`, `/users` + `/audit` SuperAdmin-only, last-SuperAdmin delete guard, `scripts/promote-superadmin.sh`).
- Sweep 2026-10-29 (proven unused, 156 tests green): removed `CategoryService.getAllCategories`, `OrderService.logChargePost`, `CategoryRepository.findAllByOrderByShowOrderDesc`, `OrderStatusRepository.findByValue`, 4 unused imports. Backups: `~/.cache/project-context-backups/*backend*`. Lombok is used only by `ErrorResponse` (needed).
- Known gap (backend AGENTS): chargepost idempotency check-then-save is not atomic.
- Unknown: local run instructions, deployed version, where prod secrets are managed.
- Admin "post charge" push text: "Ready to post" / "<name> delivered. Post charge?" (OrderService); deployment state not verified.

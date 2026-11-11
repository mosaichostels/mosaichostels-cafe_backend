Read when: debugging, deploying, editing hooks, or before touching prod config.
Verified: 2026-11-11 @ 2626fed + final review fixes
Covers: ../AGENTS.md, ../docs/backend/AGENTS.md, .claude/settings.json, .codex/hooks.json, src/main/java/com/hostel/ordering/service/OrderService.java

# Gotchas

- Workspace coupling: rules, agents, skills and hook scripts live in the parent folder (not a repo, not on GitHub): `../AGENTS.md`, `../docs/backend/AGENTS.md`, `../.claude/{agents,skills,hooks}`.
- `.claude/settings.json` hooks reference `$CLAUDE_PROJECT_DIR/../.claude/hooks/backend-*.sh` in the parent folder: `backend-guard-prod-config.sh` (PreToolUse; blocks edits to `application-prod.yml`, exit 2) and `backend-compile-check.sh` (PostToolUse on `*.java`, runs `mvn -q -o compile`, 120 s timeout; needs `jq` and an offline-ready `~/.m2`). Both exist and are executable. They break if the repo moves away from the workspace.
- `.codex/hooks.json` is gitignored and calls `/Users/naveen/.agents/hooks/project_sync.py pre|post` (absolute; exists). Different from Claude hooks, so kept. `.agents/` does not exist here.
- Push to `main` force-pushes to the live Space after CI passes; a bad merge hits production.
- Space sleeps without inbound traffic; `HeartbeatTask` and Docker HEALTHCHECK do not prevent it, so scheduled jobs (stale charge recovery, audit cleanup) may not fire while asleep.
- Production CORS preflight is answered by the HF proxy; test origins with a real request.
- No base `application.yml`: local run setup unknown; `CORS_ALLOWED_ORIGINS` and `MONGODB_URI` have no prod default, startup fails without them.
- `FirebaseConfig` fails startup without credentials.
- `lazy-initialization: true` in prod: wiring errors can surface at first use, not at boot.
- Rate-limit state is in-memory and per instance; resets on restart.
- Do not rename `ezee` charge-post statuses or `UNCONFIRMED:` prefix: stored on orders and read by clients.
- Run Maven on JDK 17 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`). The default Homebrew `mvn` uses JDK 27, where Lombok 1.18.42 fails with `ClassNotFoundException: com.sun.tools.javac.tree.EndPosTable` during compile.
- eZee returns errors with HTTP 200 (auth/not-enabled codes in the body); `EzeeGateway` maps them (marker `Errors` or `Error`, object or array, as in the café `EzeeClient`), so never judge success by status code alone.
- `PmsCache` keys appear in a DEBUG log: never put guest names, phones or emails in a key.
- Do not enable any `pms.writes.*` flag before the Mongo index check in STATUS.md (DEPLOY GATE).
- Hub reads (P1): a hub read during the quiet period (3 s after any café eZee call) or with the hub budget spent waits up to 2 s, then serves the cached value stale if the key is cached, else 503 (`PmsBusyException`, header `Retry-After: 3`); clients must show a retry state. Android should load each hub screen lazily, never prefetch (`hubPer5s=2`).
- In-house feed (live-verified): `InhouseRoomStatus` `checkinguestlist` also holds "Checked Out" and "Confirmed Reservation" rows; `PmsHousekeepingFeed.isInHouse` drops them from room occupancy. Dates there are `yyyy-MM-dd HH:mm:ss`. `RetrieveListofBills` needs the full SubBookingId (`1234-2` works; bare `1234` for a dorm sub-booking is error 203). Retrieve Bills `Amount` is the line total (qty>1 rows: Amount/qty is a normal unit price in 67 of 68 rows; `Total` equals `Amount`), `Date` is plain `yyyy-MM-dd`.
- Reconcile limits: a reversed charge can still read FOUND (negative rows ignored). A retry on a later day resets `chargePostAt`, so rows from the earlier day are claimed by nobody. IN_PROGRESS orders claim only items already saved; a folio switched mid-post is an edge case. Orders are selected by `createdAt` (not `chargePostAt`), capped at 500 (`truncated`); the `$or` (DELIVERED or `chargePostStatus` set) scans `status_createdAt` keys, fine at café volume. The FINANCE body cap (2 MB) on a wide window gives UNKNOWN. Retrieve Bills `Amount` is taken as the line total: one live café-style row with Qty 3.0000 had Amount == Total (36), pinned by a test; one sample only. With `pms.enabled=false` (or a request type not enabled) reconcile returns the same error as other hub routes (502 kill switch / 501 not enabled), not UNKNOWN. Folio match is exact (trimmed); the night-audit date vs `chargePostAt` day is unverified. A bad-header CSV is cached 60 s. The VOIDED branch is unused (voidPost leaves status QUEUED).

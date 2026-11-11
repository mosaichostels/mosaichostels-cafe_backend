Read when: tracing a request, changing auth, orders, eZee posting, schedulers or data.
Verified: 2026-11-11 @ 2626fed + final review fixes
Covers: src/main/java/com/hostel/ordering/pms/**/*.java, src/main/java/com/hostel/ordering/controller/**/*.java, src/main/java/com/hostel/ordering/service/**/*.java, src/main/java/com/hostel/ordering/config/**/*.java, src/main/java/com/hostel/ordering/model/**/*.java, src/main/java/com/hostel/ordering/repository/**/*.java

# Architecture

Flow: `request -> RateLimitFilter / AuthTokenFilter (SecurityConfig) -> controller (@PreAuthorize) -> service -> repository (Mongo)`. Hot path: `OrderController -> OrderService -> EzeeChargePostService -> EzeeClient`, then `FCMNotificationService`. No SQL.

## Routes and roles (SecurityConfig.java:75-81 + method @PreAuthorize)
| Route | Access |
| --- | --- |
| `/auth/login`, `/auth/refresh`, `/auth/logout` | public (permitAll) |
| `GET /health` | public |
| `POST /orders` | public in HTTP; method rule `permitAll()` (OrderController:32) |
| `GET /config`, `GET /menu-items/**`, `GET /other-essentials/**` | public |
| `GET /orders`, `/orders/{id}`, `PUT /orders/{id}/status` | ADMIN, KITCHEN, STAFF (STAFF only for the rollout window) |
| `DELETE /orders`, `/orders/{id}`; `POST /orders/{id}/chargepost`; `GET /orders/{id}/ezee-candidates` | ADMIN only (OrderController:93,108,175,202) |
| `/notifications/subscribe`, `/unsubscribe` | ADMIN, KITCHEN, STAFF (STAFF only for the rollout window) |
| `POST /users/me/register-fcm` | any authenticated |
| `/users` CRUD, `/audit`, `GET /pms/status` | SUPERADMIN only (Admin gets 403) |
| `/pms/housekeeping/**` (board read and room status write) | ADMIN, HOUSEKEEPING (class-level; SUPERADMIN via hierarchy) |
| `GET /pms/cleaning-tasks?date=` | ADMIN, HOUSEKEEPING (class-level `@PreAuthorize`; SUPERADMIN via hierarchy). Read-only list |
| `GET /pms/reconcile/charges?date=` | ADMIN (shows guest names) |
| `GET /app/android-version` | any authenticated (`anyRequest().authenticated()`; `AppVersionController`) |
| `GET /pms/unconfirmed`, `POST /pms/unconfirmed/{idempotencyKey}/resolve` (body `{outcome: APPLIED\|NOT_APPLIED}`) | ADMIN (`@PreAuthorize`) |
| POST/PUT/DELETE on `/config/categories`, `/config/dormitories`, `/config/statuses`, menu-items and other-essentials writes | ADMIN only |
| everything else | authenticated |

## Auth
- Stateless JWT Bearer, CSRF off, roles SUPERADMIN/ADMIN/KITCHEN/HOUSEKEEPING/STAFF (`UserService.ALLOWED_ROLES`; STAFF is legacy, kept on orders+notifications only); hierarchy bean is only `ROLE_SUPERADMIN > ROLE_ADMIN`; login/refresh return hierarchy-expanded roles (`security/`, `SecurityConfig.roleHierarchy`). `JwtUtils` expiry default 3600000 ms; refresh grace default 365 days.
- Logout sets per-user `User.tokensValidFrom` watermark (model/User.java:34) plus blacklist; refresh does not blacklist the old token (DECISIONS.md).
- `LoginThrottle`: per-account login guessing. `RateLimitFilter` (60s window, in-memory, max 10000 keys): `POST /orders` 30/min, `POST /auth/login` 10/min; key = JWT user else client IP (X-Forwarded-For trusted only if peer is private/loopback).

## Collections (@Document)
`orders` (unique sparse index on idempotency key; compound `status_createdAt`), `users` (unique username), `menu_items`, `other_essentials`, `categories`, `dormitories`, `order_status_config`, `audit_logs`, `idempotency_keys` (TTL 3600 s), `pms_write_claims` (below). The old `cleaning_assignments` collection is no longer read or written by the code; `scripts/migrate-staff-to-kitchen.sh` drops it (`--dry-run` only counts it; back up first).

## Category rename
Items store their category by name: `CategoryService` update (:72-82) `updateMulti`s the new name into `menu_items` or `other_essentials` (by category type), then renumbers `showOrder`. Test: `CategoryRenameCascadeTest`.

## Order create idempotency
`Idempotency-Key` header (OrderController:34): stored on order + `IdempotencyRecord` (TTL). Status updates also cached (`order-status:<id>`, :77). Prices always re-read from DB.

## eZee charge posting (highest risk)
- Admin picks occupant by hand: `GET /orders/{id}/ezee-candidates` then `POST /orders/{id}/chargepost`; no auto matching.
- `OrderRepositoryImpl.claimForChargePost` (:100) atomically claims a DELIVERED order; `OrderService.java:314` calls it.
- `chargePostStatus`: null | IN_PROGRESS | QUEUED (eZee accepted) | FAILED (model/Order.java:85-91). `VOIDED` is read (OrderService:221) but never set (eZee has no void); the Order.java comment saying cancel reverses to VOIDED is stale.
- No reply from eZee -> item recorded as `UNCONFIRMED:<id>` (EzeeChargePostService:37,241,264); retry needs admin to acknowledge after folio check. Charges cannot be voided.
- Stale claims: `OrderService.recoverStaleChargePosts` (:408, every 60 s, claim older than 15 min) marks FAILED with `UNCONFIRMED:interrupted`.
- Posting stops sending after 5 min. `ezee.mock=true` switches `EzeeClient` to `EzeeMockResponses`.
- Deeper detail: backend AGENTS "eZee PMS integration", workspace skill `backend-ezee-integration`.

## Hub eZee calls (`pms/`)
- Flow: `hub service -> PmsCache (single-flight, stale-while-error) -> EzeeGateway (json()/text()) -> PmsRateLimiter -> PmsTransport`. Gateway routes by `EzeeFamily`, throws `PmsException` subclasses before any I/O when `pms.enabled=false`, caps body size, applies one 15 s exchange deadline, maps eZee's HTTP-200 error bodies (auth 611/301/303/602/612/614/121; not enabled 201/302; 210/"no data" = empty), never logs bodies or credentials.
- One shared 5 s window: HUB calls admitted while fewer than `hubPer5s` (2); `acquire(CAFE)` allows up to `hubPer5s+cafeReserve`. `ezee/EzeeClient` calls `recordCafeCall()` before every real request: café calls are counted, never refused or delayed.
- Write guard (`PmsWriteGuard`, collection `pms_write_claims`, `_id` = Idempotency-Key): claim IN_PROGRESS -> DONE, or UNCONFIRMED. Sparse unique `openLockKey` (`feature:targetId`) = one open claim per target; TTL on `doneAt` 24 h; UNCONFIRMED never expires; IN_PROGRESS older than 5 min becomes UNCONFIRMED. Definite failures (Busy/Auth/NotEnabled/FeatureDisabled, and `PmsUpstreamException` with `isSent()==false`: kill switch, unknown request type, request/URI build failure, all before I/O) delete the claim; ambiguous ones (`PmsUpstreamException` sent, others) mark UNCONFIRMED, never auto-retried; admin resolves via `/pms/unconfirmed`. A store or audit failure after the eZee call surfaces as `PmsUnconfirmedException`, never a raw 500.
- `GlobalExceptionHandler.handlePms` -> `{errorCode, message}` with fixed generic text: 502 auth/upstream, 503 busy (`Retry-After: 3`), 501 not-enabled, 403 feature-disabled, 409 unconfirmed/in-progress/target-busy/idempotency-mismatch. `GET /pms/status` reports eZee OK/AUTH/UPSTREAM/DISABLED (a stale probe reports UPSTREAM).

## Hub reads (`pms/`, P1, read-only)
- Envelopes: room board `{items, asOf, stale}` (`asOf` = `Cached.fetchedAtMs()`); reconcile `{items, asOf, stale, truncated}`. Dates are Asia/Kolkata `yyyy-MM-dd`, default today, range over 30 days or `from` after `to` = 400 (`PmsDates`). DTOs carry no phone/email.
- `PmsReads.load` wraps `PmsCache.getOrLoad` (checked exceptions become `PmsUpstreamException`). Cache names (`pms.ttl.<name>`, defaults): `departures` 60 s (key `departures:<date>:<date>`, cleaning to-do only), `bills` 60 s (`bills:<first>:<last>`), `hk` 30 s (`hk:inhouse`).
- `PmsHousekeepingFeed` caches one eZee HOUSEKEEPING `InhouseRoomStatus` call (`{roomlist, checkinguestlist}`); `/pms/housekeeping/rooms` (from `roomlist`, guest joined by room name) and the cleaning to-do (room list) both read it, so one eZee request serves both. `hkstatus` is passed through raw.
- Source: the cleaning to-do's departures = CONNECTIVITY `DepartureList` (one row per `BookingTran`; only `RoomName` and `End` are kept).
- Reconcile (`PmsReconcileService`): orders of one IST day (`OrderRepositoryCustom.findForChargeReconcile`: `createdAt` in the day AND (`status` DELIVERED OR `chargePostStatus` set), oldest first, capped at 500 -> `truncated`) vs FINANCE `RetrieveBills` CSV; each item `{orderId, bookingName, room, amount, ours, theirs}` with `theirs` FOUND/MISSING/UNKNOWN/N/A. See DECISIONS.md for the match policy.
- Hub limiter (P1 Task 0): HUB is refused for `pms.cafe-quiet-ms` (3000) after any café eZee call and `hubPer5s` defaults to 2, so a hub read gets `PmsBusyException` (503) during a café burst; clients must show a retry state.

## Schedulers
`OrderService.recoverStaleChargePosts` (60 s), `AuditCleanupTask` (cron `0 0 0 * * *`, retention `audit.retention-days` default 90), `HeartbeatTask` (6 h log line only; does not keep the Space awake).

## Notifications / startup
`FCMNotificationService`; `FirebaseConfig` throws at startup without credentials. Initial admin created/re-hashed at startup from `config.admin.*`.

## Cleaning workflow (`pms/`, P3, Mongo writes only)
- `PmsHousekeepingWorkflowService`: `GET /pms/cleaning-tasks?date=` (default today IST; invalid or >31 days from today = 400) is read-only: one task per `date|room` (`taskKey`), `turnovers` = departures in that room. Departure room NAME is mapped to `roomId`/`unitId` through the `InhouseRoomStatus` roomlist (trimmed, case-insensitive, letter O never equals 0); blank names never match; a name shared by several rooms (dorm beds) or with no match goes to `unmatched` (`{roomName, count, ambiguous}`, no guest data). Task fields: `taskKey, roomId, unitId, roomName, roomType, departureDate, turnovers, hkStatus`. `hkStatus` is the room's current eZee `hkstatus` label (trimmed, e.g. `Clean`/`Dirty`, `""` when absent) read from the same cached `hk:inhouse` roomlist node the room is matched on, so no extra eZee call; clients compare it case-insensitively, as `PmsRoomStatusWriteService.norm` does. A feed outage serves the stale cache (or fails as before), never a different shape. Envelope `{items, unmatched, asOf, stale}`. Nothing is stored; the assign/update endpoints, assignments, idempotency keys and `CLEANING_*` audit actions were removed.

## Room status write (`pms/`, P3, first eZee write, ships OFF)
- `POST /pms/housekeeping/rooms/{roomId}/status` `{unitId, status: CLEAN|DIRTY, remarks?<=200, no control chars}` + required `Idempotency-Key` (ADMIN, HOUSEKEEPING; SuperAdmin via hierarchy) -> `PmsWriteResult<RoomStatusWriteResult>` `{value:{roomId,unitId,status,hkStatus,confirmed}, confirmed}`. `PmsRoomStatusWriteService` order: validate (400) -> `PmsFlags.requireWrite("room-status")` and canary (403, zero eZee calls) -> fresh `InhouseRoomStatus` read (cache `hk:inhouse` evicted first) -> room+unit must match (else 409 "no longer matches eZee") -> same status = confirmed no-op, no eZee write -> `PmsWriteGuard.execute` (feature `room-status`, target = roomId, hash of room/unit/current status/target/remarks, audit `PMS_ROOM_STATUS` with ids, statuses and remark LENGTH only) -> ONE `UpdateRoomStatus` POST (HOUSEKEEPING family, `service.hkupdatestatus`) -> read-back (evict, reload, same room/unit, trimmed case-insensitive `hkstatus` == `Clean`/`Dirty`). Mismatch or failed read-back = `confirmed=false` and the claim stays UNCONFIRMED (`/pms/unconfirmed`); a send timeout or error reply = 409 unconfirmed; never auto-retried. Blocked rooms may be marked (doc silent; the block is untouched).
- Request body (eZee doc "Update Room Status", never run live): `{authcode, hotel_code, updatehkdata:[{hotel_code, roomid, unitid, hkstatus:"Clean"|"Dirty", hkremarks}]}`; success reply `{"status":"0","message":"Success"}`; error reply `{"errorcode":"1",...}` is NOT recognised by the gateway, so the service accepts only `status=="0"` and treats anything else as an upstream error (unconfirmed).
- Rollout (owner step, nothing is enabled by this change): (1) check the DEPLOY GATE indexes in STATUS.md; (2) set `SPRING_APPLICATION_JSON` with `pms.writes.room-status=true` and `pms.canary.room-status=["<one test room id>"]`; (3) mark that one room Dirty then Clean from the board, watch the response `confirmed`, the eZee UI and a `/pms/housekeeping/rooms` read; (4) if `confirmed=false` or 409, stop, resolve in `/pms/unconfirmed`, and fix the body (per-row `hotel_code`, status value "0" vs "1" are unproven) before widening the canary.
- Review fixes (P3-9): pre-check, write and read-back use `gateway.json(..., PmsPriority.HUB, Duration 7 s)` (limiter wait only; window and defaults unchanged) because 3 hub calls in ~1 s exceed 2 per 5 s; both reads go straight to eZee (no cache, no shared in-flight load) and evict `hk:inhouse` before and after. The service fails closed on an empty canary (the guard itself still treats empty as any). Reply check: only `errorcode` or a non-empty textual `warning` throws; any other reply (even status other than "0") is judged by the read-back. Non-blank remarks skip the no-op shortcut. A same-key retry after an applied-but-UNCONFIRMED write can return a confirmed no-op while the claim stays listed in `/pms/unconfirmed`.
- Lock and canary are per ROOM (`room-status:<roomId>`): dorm units sharing a roomid share the lock; revisit as `roomId:unitId` once dorm ids are seen live. Blocked rooms may be marked (owner decision pending).
- Must prove live before enabling: eZee accepts the body (per-row `hotel_code`, casing) and replies status "0"; the read-back sees the new hkstatus without lag under real limits; `hkremarks` lands as sent and empty does not clear unexpectedly; roomid/unitid from the feed are what UpdateRoomStatus accepts, incl. dorm units; behaviour on blocked and occupied rooms; `pms_write_claims` indexes exist in prod and a deliberately failed write appears in `/pms/unconfirmed` and resolves.

## Role rollout (owner-run, role-based tabs)
1. Push the backend first: it accepts old and new roles (`ROLE_STAFF` still allowed on orders and notifications for the window).
2. Run `MONGODB_URI=... scripts/migrate-staff-to-kitchen.sh --dry-run`, compare its "matched" count with a manual `db.users.countDocuments({roles: {$in: ["ROLE_STAFF","STAFF"]}})` on the same database, then run it without `--dry-run` (this also drops the unused `cleaning_assignments` collection). Sessions survive (roles load from the DB per request).
3. Push the web admin. 4. Hand out the Android APK (1.7.0, versionCode 15). 5. Set the Space env value `APP_ANDROID_MIN_VERSION_CODE` (`app.android.min-version-code`) to 15 so 1.6.0 installs get the required-update prompt (1.6.0 joins the `new_orders` topic for any role, so a Housekeeping login on it would ring with guest names). 6. Create housekeeping accounts (ROLE_HOUSEKEEPING). 7. A follow-up change drops `ROLE_STAFF` from the allowed roles and rules.
- Until step 2 is done, an old `ROLE_STAFF` account loses every `/pms/**` endpoint (403) and Android treats it as no access.
- Reverse: edit the script's `rewrite()` to map `ROLE_KITCHEN` to `ROLE_STAFF` and re-run; it also flips accounts that were KITCHEN before the migration, so dry-run first. There is no separate rollback script.
- The script has never been run on a real mongod (no mongod available when written); only its rewrite function was tested with node. Treat the first dry-run as the test.

## Release note: dropped routes (2026-11-11, drop-features)
- The front desk routes (`/pms/arrivals`, `/pms/departures`, `/pms/inhouse`, `/pms/bookings/{id}`, `/pms/guests`) and the cleaning write routes (`POST /pms/cleaning-tasks/{taskKey}/assign`, `PUT /pms/cleaning-assignments/{id}`) are gone. An unmapped path falls into `GlobalExceptionHandler` and returns HTTP 500 (with an ERROR stack trace in the log), not 404.
- Hand out the Android 1.8.0 (versionCode 16) APK before or together with this backend deploy. An older 1.7.0 app shows the front desk error state with a retry that never succeeds, its cleaning "Take it" fails with a server-error toast, and a cleaning action already in its offline queue replays into a 500, is kept for retry and can delay queued order updates behind it for up to 30 minutes.
- On the Space set `APP_ANDROID_MIN_VERSION_CODE=16`, plus `APP_ANDROID_LATEST_VERSION_CODE` and `APP_ANDROID_APK_URL` (see ENVIRONMENT.md). The old app's update dialog has never been checked on a device.

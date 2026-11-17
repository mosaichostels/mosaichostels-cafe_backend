Read when: tracing a request, changing auth, orders, eZee posting, schedulers or data.
Verified: 2026-11-17 on `main`
Covers: src/main/java/com/hostel/ordering/pms/**/*.java, src/main/java/com/hostel/ordering/controller/**/*.java, src/main/java/com/hostel/ordering/service/**/*.java, src/main/java/com/hostel/ordering/config/**/*.java, src/main/java/com/hostel/ordering/model/**/*.java, src/main/java/com/hostel/ordering/repository/**/*.java

# Architecture

Flow: `request -> RateLimitFilter / AuthTokenFilter (SecurityConfig) -> controller (@PreAuthorize) -> service -> repository (Mongo)`. Hot path: `OrderController -> OrderService -> EzeeChargePostService -> EzeeClient`, then `FCMNotificationService`, which resolves registered device tokens from users' current roles. No SQL.

## Routes and roles (SecurityConfig.java:75-81 + method @PreAuthorize)
| Route | Access |
| --- | --- |
| `/auth/login`, `/auth/refresh`, `/auth/logout` | public (permitAll) |
| `GET /health` | public |
| `POST /orders` | public in HTTP; method rule `permitAll()` (OrderController:32) |
| `GET /config`, `GET /menu-items/**`, `GET /other-essentials/**` | public |
| `GET /orders`, `/orders/{id}`, `PUT /orders/{id}/status` | ADMIN, KITCHEN, STAFF (STAFF only for the rollout window) |
| `DELETE /orders`, `/orders/{id}`; `POST /orders/{id}/chargepost`; `GET /orders/{id}/ezee-candidates` | ADMIN only (OrderController:93,108,175,202) |
| `POST /users/me/register-fcm` | any authenticated |
| `/users` CRUD, `/audit`, `GET /pms/status` | SUPERADMIN only (Admin gets 403) |
| `GET /pms/housekeeping/rooms` | ADMIN, HOUSEKEEPING (class-level; SUPERADMIN via hierarchy) |
| `GET /pms/cleaning-tasks?date=` | ADMIN, HOUSEKEEPING (class-level `@PreAuthorize`; SUPERADMIN via hierarchy). Read-only list |
| `GET /cleaning-list`, `POST /cleaning-list/{id}/done` | ADMIN, HOUSEKEEPING (Superadmin via hierarchy); Kitchen 403 |
| `POST /cleaning-list`, `DELETE /cleaning-list/{id}` | ADMIN only (Housekeeping and Kitchen 403) |
| `GET /app/android-version` | any authenticated (`anyRequest().authenticated()`; `AppVersionController`) |
| `GET /pms/unconfirmed`, `POST /pms/unconfirmed/{idempotencyKey}/resolve` (body `{outcome: APPLIED\|NOT_APPLIED}`) | ADMIN (`@PreAuthorize`) |
| POST/PUT/DELETE on `/config/categories`, `/config/dormitories`, `/config/statuses`, menu-items and other-essentials writes | ADMIN only |
| everything else | authenticated |

## Auth
- Stateless JWT Bearer, CSRF off, roles SUPERADMIN/ADMIN/KITCHEN/HOUSEKEEPING/STAFF (`UserService.ALLOWED_ROLES`; STAFF is legacy, kept on orders+notifications only); hierarchy bean is only `ROLE_SUPERADMIN > ROLE_ADMIN`; login/refresh return hierarchy-expanded roles (`security/`, `SecurityConfig.roleHierarchy`). `JwtUtils` expiry default 3600000 ms; refresh grace default 365 days.
- Logout sets per-user `User.tokensValidFrom` watermark (model/User.java:34) plus blacklist; refresh does not blacklist the old token (DECISIONS.md).
- `LoginThrottle`: per-account login guessing. `RateLimitFilter` (60s window, in-memory, max 10000 keys): `POST /orders` 30/min, `POST /auth/login` 10/min; key = JWT user else client IP (X-Forwarded-For trusted only if peer is private/loopback).

## Collections (@Document)
`orders` (unique sparse index on idempotency key; compound `status_createdAt`), `users` (unique username), `menu_items`, `other_essentials`, `categories`, `dormitories`, `order_status_config`, `audit_logs`, `idempotency_keys` (TTL 3600 s), `pms_write_claims` (below), `cleaning_list` (one document per bed, unique compound index `room_unit_unique` on `roomId`+`unitId`). The old `cleaning_assignments` collection is no longer read or written by the code; `scripts/migrate-staff-to-kitchen.sh` drops it (`--dry-run` only counts it; back up first).

## Category rename
Items store their category by name: `CategoryService` update (:72-82) `updateMulti`s the new name into `menu_items` or `other_essentials` (by category type), then renumbers `showOrder`. Test: `CategoryRenameCascadeTest`.

## Order idempotency and concurrency
`Idempotency-Key` on create is stored on the order plus `IdempotencyRecord` (TTL). Prices are always re-read from DB. Status updates send `{status, expectedVersion}`; `OrderRepositoryImpl.updateStatusIfVersion` atomically matches `_id` and `version`, increments `version`, and a stale write returns 409 instead of overwriting newer state.

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
- The rooms envelope is `{items, asOf, stale}` (`asOf` = `Cached.fetchedAtMs()`). DTOs carry no phone/email.
- `PmsReads.load` wraps `PmsCache.getOrLoad` (checked exceptions become `PmsUpstreamException`). Active cache names are `departures` 60 s (cleaning to-do) and `hk` 30 s (`hk:inhouse`).
- `PmsHousekeepingFeed` caches one eZee HOUSEKEEPING `InhouseRoomStatus` call (`{roomlist, checkinguestlist}`); `/pms/housekeeping/rooms` (from `roomlist`, guest joined by room name) and the cleaning to-do (room list) both read it, so one eZee request serves both. `hkstatus` is passed through raw.
- Source: the cleaning to-do's departures = CONNECTIVITY `DepartureList` (one row per `BookingTran`; only `RoomName` and `End` are kept).
- Hub limiter (P1 Task 0): HUB is refused for `pms.cafe-quiet-ms` (3000) after any café eZee call and `hubPer5s` defaults to 2, so a hub read gets `PmsBusyException` (503) during a café burst; clients must show a retry state.

## Schedulers
`OrderService.recoverStaleChargePosts` (60 s), `AuditCleanupTask` (cron `0 0 0 * * *`, retention `audit.retention-days` default 90), `HeartbeatTask` (6 h log line only; does not keep the Space awake).

## Notifications / startup
`FCMNotificationService` sends to deduplicated registered tokens selected from users' current roles; invalid tokens are removed. NEW_ORDER/CANCELLED target ADMIN, SUPERADMIN, KITCHEN and legacy STAFF, except essentials-only orders omit KITCHEN. Cleaning/schedule notifications use their own role sets. `FirebaseConfig` throws at startup without credentials. Initial admin is created/re-hashed at startup from `config.admin.*`.

## Cleaning workflow (`pms/`, P3, Mongo writes only)
- `PmsHousekeepingWorkflowService`: `GET /pms/cleaning-tasks?date=` (default today IST; invalid or >31 days from today = 400) is read-only: one task per `date|room` (`taskKey`), `turnovers` = departures in that room. Departure room NAME is mapped to `roomId`/`unitId` through the `InhouseRoomStatus` roomlist (trimmed, case-insensitive, letter O never equals 0); blank names never match; a name shared by several rooms (dorm beds) or with no match goes to `unmatched` (`{roomName, count, ambiguous}`, no guest data). Task fields: `taskKey, roomId, unitId, roomName, roomType, departureDate, turnovers, hkStatus`. `hkStatus` is the room's current eZee `hkstatus` label (trimmed, e.g. `Clean`/`Dirty`, `""` when absent) read from the same cached `hk:inhouse` roomlist node the room is matched on, so no extra eZee call; clients compare it case-insensitively, as `PmsRoomStatusWriteService.norm` does. A feed outage serves the stale cache (or fails as before), never a different shape. Envelope `{items, unmatched, asOf, stale}`. Nothing is stored; the assign/update endpoints, assignments, idempotency keys and `CLEANING_*` audit actions were removed.

## Internal room status write (`pms/`, ships OFF)
- No public room-status write route exists. `PmsRoomStatusWriteService` is called only after Cleaning List Done and retains its feature flag, canary, write guard and read-back confirmation. The live eZee write path remains unverified.
- The write guard locks per room. A Cleaning List Done call removes the bed first and reports the eZee outcome separately, so the list cannot get stuck on an uncertain external write.

## Role rollout (owner-run, role-based tabs)
1. Push the backend first: it accepts old and new roles (`ROLE_STAFF` still allowed on orders and notifications for the window).
2. Run `MONGODB_URI=... scripts/migrate-staff-to-kitchen.sh --dry-run`, compare its "matched" count with a manual `db.users.countDocuments({roles: {$in: ["ROLE_STAFF","STAFF"]}})` on the same database, then run it without `--dry-run` (this also drops the unused `cleaning_assignments` collection). Sessions survive (roles load from the DB per request).
3. Push the web admin. 4. Hand out the current Android APK. 5. Set the Space minimum Android version code so older topic-based builds cannot remain active. 6. Create housekeeping accounts (ROLE_HOUSEKEEPING). 7. A follow-up change drops `ROLE_STAFF` from the allowed roles and rules.
- Until step 2 is done, an old `ROLE_STAFF` account loses every `/pms/**` endpoint (403) and Android treats it as no access.
- Reverse: edit the script's `rewrite()` to map `ROLE_KITCHEN` to `ROLE_STAFF` and re-run; it also flips accounts that were KITCHEN before the migration, so dry-run first. There is no separate rollback script.
- The script has never been run on a real mongod (no mongod available when written); only its rewrite function was tested with node. Treat the first dry-run as the test.

## Release note: dropped routes
- The front desk, reconciliation, public room-write and old cleaning assignment routes are gone. Unmapped paths return HTTP 404.
- Hand out the Android 1.8.0 (versionCode 16) APK before or together with this backend deploy. An older 1.7.0 app shows the front desk error state with a retry that never succeeds, its cleaning "Take it" fails with a server-error toast, and a cleaning action already in its offline queue replays into a 500, is kept for retry and can delay queued order updates behind it for up to 30 minutes.
- On the Space set `APP_ANDROID_MIN_VERSION_CODE=16`, plus `APP_ANDROID_LATEST_VERSION_CODE` and `APP_ANDROID_APK_URL` (see ENVIRONMENT.md). The old app's update dialog has never been checked on a device.

## Cleaning list (Android 1.9.0 / code 19, branch `feat/cleaning-list`, unpushed)
- Classes: `CleaningListController`, `CleaningListService`, `CleaningNotifier`, `CleaningListEntry`, `CleaningListRepository`. Admin submits 1-200 beds (`roomId`/`unitId` `[0-9]{1,20}`, `roomName` non-blank, <=80, no control chars); a bed already listed is skipped, never duplicated.
- Done order (fixed): (1) atomically remove the document, (2) one eZee write attempt via `PmsRoomStatusWriteService.setStatus(..., CLEAN, "Cleaned by <username>", key "cleaning-done-<entryId>")`, (3) push admins. The bed leaves the list even if eZee is off, refuses or throws. A second Done on the same bed returns `alreadyDone`, with no eZee write and no push.
- eZee outcome in the response and push: `CONFIRMED`, `NOT_CONFIRMED` (answered, read-back differed), `OFF` (`PmsFeatureDisabledException` / `PmsNotEnabledException`; today's state, flags `pms.writes.room-status` and `pms.canary.room-status` stay off), `FAILED` (any other exception). The eZee write guard locks the whole room, not the bed, so two beds in the same dorm marked Done at once can make the second report FAILED, and a NOT_CONFIRMED write keeps the room locked until an admin resolves it; the bed still leaves the list and the admin push says to set it Clean in eZee.
- Push types: `CLEANING_LIST` to every Housekeeping user on submit, only when at least one bed was added; `CLEANING_DONE` to every Admin and Superadmin, one per bed. `CLEANING_DONE` is also sent to every Admin and Superadmin (title `Task done`, body `<label or taskKey> done by <user>`, ` (<date>)` appended when the date is not today IST) when housekeeping newly ticks a schedule task; an already-ticked tick sends nothing. The old `DELIVERED` "Ready to post" push was removed (2026-11-13); moving an order to DELIVERED sends no push. Payloads carry room names and the cleaner's login only. Housekeeping phones now register FCM tokens (they still subscribe to no topic). Audit: `CLEANING_LIST_SUBMITTED`, `CLEANING_BED_DONE` (not logged when already done).
- Release: deploy the backend first (harmless: new endpoints, pushes nobody receives yet), then install APK 1.9.0 on every Housekeeping, Admin and Superadmin phone. Old apps ignore the new pushes and have no screen for the list. `GET /pms/cleaning-tasks` is still served for old apps.

## Housekeeping schedule (Android 1.10.0 / code 20, branch `feat/schedule`, unpushed)
The schedule itself (which task falls on which date) lives only in the Android app (`HkSchedule`); the backend stores no schedule and changing it needs an app update. The backend stores only which days the admin released and which task slots were ticked.
- Collections: `schedule_days` (one document per released date, unique index on `date`), `schedule_ticks` (one per date + `taskKey` + `slot`, unique compound index `date_task_slot_unique`; fields `doneBy`, `doneAt`, `confirmedBy`, `confirmedAt`), `schedule_reminders` (one per date, unique index on `date`). The unique indexes are created only where `spring.data.mongodb.auto-index-creation` is true, which is `application-prod.yml` only; check `getIndexes()` on the live DB after the first deploy.
- Routes (`ScheduleController`, `/schedule`): `GET /schedule/days?from=&to=` (ADMIN, HOUSEKEEPING; `from`/`to` are `yyyy-MM-dd`, at most 32 days inclusive, i.e. `LOOKBACK_DAYS + 1`; returns one entry per submitted day with its ticks); `POST /schedule/days/{date}/ticks` body `{taskKey, slot, label?}` (`label` optional display text for the push: trimmed; null, blank, over 80 chars or with control characters falls back to `taskKey`, never an error) (ADMIN, HOUSEKEEPING; response `alreadyTicked`, `doneBy`; a second tick of the same slot creates no new record and returns the first person's name); `POST /schedule/days/{date}/ticks/{taskKey}/{slot}/confirm` (ADMIN, Superadmin via hierarchy; any date in the same 31-day window as tick, so late ticks can be signed off; 404 if the slot was never ticked; audit `SCHEDULE_TASK_CONFIRMED`). Kitchen gets 403 on all.
- `POST /cleaning-list` (ADMIN) now also takes optional `day` (`yyyy-MM-dd`) and `taskCount` (whole number) and returns `daySubmitted`. Submitting with `day` marks that date released in `schedule_days`; beds may be empty when a day is submitted.
- Tick window and carry-over: a tick is accepted only for a submitted day from 31 days ago (`ScheduleService.LOOKBACK_DAYS = 31`) up to today, never a future date. Carry-over is computed by the app from `/schedule/days`: every unticked slot of a submitted day (daily tasks included) stays on housekeeping's list on later days until ticked, looking back 31 days; older leftovers drop off. A never-submitted day is not carried over. A leftover ticked today is stored against its original date with today's `doneAt`.
- Push types: `CLEANING_LIST` to Housekeeping, combined wording `Today's cleaning: N bed(s), M task(s)`; `SCHEDULE_REMINDER` (title `Cleaning schedule`, body `Today's schedule has not been submitted.`) to Admin and Superadmin only. No guest data.
- Reminder job (`ScheduleReminderTask`): cron `0 */10 9-11 * * *`, zone `Asia/Kolkata`. It claims one reminder per date, sends to Admin/SuperAdmin devices, and removes the inserted claim if no device accepted the push so a later run can retry. It only runs while the Space is awake.

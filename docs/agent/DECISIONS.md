Read when: about to change auth, idempotency, eZee posting, rate limits or notifications.
Verified: 2026-11-11 @ 2626fed + final review fixes. Source: backend AGENTS (`../docs/backend/AGENTS.md`) plus code comments where cited.
Covers: -

# Decisions

| Decision | Why / where |
| --- | --- |
| Manual admin-mapped charge posting; no name/room auto-match | auto matching is spoofable (ARCHITECTURE.md eZee) |
| Charges cannot be voided; unknown outcomes kept as `UNCONFIRMED:` | avoid double-charging guests |
| Idempotency and logout state persisted in Mongo | Space sleeps and redeploys, memory is lost |
| Refresh does not blacklist the old token | a lost response would kill the session |
| Refresh grace 365 days | staff devices stay signed in (JwtUtils:29-32) |
| Per-IP limits 30 orders/min, 10 logins/min | hostel shares one IP; per-account guessing handled by LoginThrottle (RateLimitFilter:66-71) |
| No wildcard CORS in prod | credentials allowed (application-prod.yml:42) |
| `auto-index-creation: true` | declared indexes were inert before; only `_id` existed (yml:21) |
| App-version-check feature removed | the old one; do not resurrect without explicit requirement. P1 added a minimal `GET /app/android-version` (`{latest, min, url}`, unset = 0/0/"", no yml edit) at the owner's request |
| ntfy notifications removed | commit 16af0aa |
| Agents/skills/rules moved to shared workspace folder | commit e95ae6b |
| Café eZee calls are counted by `PmsRateLimiter`, never refused or delayed | charge posting must gain no new failure mode; hub calls yield instead |
| One open write claim per `feature:targetId` via sparse unique `openLockKey` | atomic in Mongo, no check-then-save race (unlike the old chargepost gap) |
| Ambiguous hub write outcome = UNCONFIRMED, never auto-retried; admin resolves | eZee writes cannot be undone; same reasoning as `UNCONFIRMED:` charges |
| `pms.*` defaults live in code; `application-prod.yml` not edited | file is guarded; scalars (`pms.enabled`, `pms.hub-per5s`, ...) via env vars; per-feature `pms.writes.*`/`pms.canary.*` via one `SPRING_APPLICATION_JSON` secret (env names cannot express hyphenated keys), an approved yml block, or P6 overrides |
| P1 hub is read-only; write features stay off | no `pms.writes.*` flag enabled, no hub write endpoints; the order ack/ETA/settlement task was dropped and calendar/availability/catalogue deferred to P4 |
| Hub reads never log values and never put guest names, phones or emails in cache keys | keys are logged at DEBUG; keys hold only ids and dates; list DTOs carry no phone/email, only `BookingDetail` does |
| In-house list is built from HOUSEKEEPING `InhouseRoomStatus` JSON, not POS2PMS `roomlist` XML | no XML/XXE surface, names only, and the same cached call (`PmsHousekeepingFeed`, `hk:inhouse`) feeds the rooms board; in-house `bookingId` = `reservationno` |
| Stale-while-error is accepted for booking contact details | a cached `BookingDetail` (phone/email) stays in the in-memory `PmsCache` past its TTL and is served `stale=true` while eZee is down; process-local, bounded by `MAX_KEYS`, never in Mongo or logs |
| Reconciliation never reports a false FOUND (it may say UNKNOWN or MISSING) | FOUND needs a Retrieve Bills row with the order's folio, the IST day of `chargePostAt`, and Amount equal to the order total or a confirmed item subtotal; header must have `Folio No, Date, Type, Particular, Amount`, else UNKNOWN |
| Reconcile consumes rows: each order claims an amount with a weight | one row cannot prove two orders; claims come from every listed order with a folio and `chargePostAt` whatever its status (FAILED/UNCONFIRMED/VOIDED can leave real rows), only QUEUED/CHECKED are looked up; fewer rows than total claims = UNKNOWN |
| Reconcile counts only café charge rows: Type "Extra Charges" and Particular "Food Charge" or "Essential" (case-insensitive) | from a read-only live Retrieve Bills probe 2026-11-11; Date is ISO yyyy-MM-dd; negative (reversal) rows are ignored; a differently named charge shows MISSING (safe direction). A manual eZee posting with Particular "Food Charge" is indistinguishable from a café row. |
| Money in hub DTOs is `BigDecimal`; an unparsable folio amount is null | eZee amounts are decimal strings; never double |

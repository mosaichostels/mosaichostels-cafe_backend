# Architecture

Read when: tracing a request, changing auth, orders, eZee posting, schedulers or data.
Verified: 2026-10-23 @ 24bdfbb

Flow: `request -> RateLimitFilter / AuthTokenFilter (SecurityConfig) -> controller (@PreAuthorize) -> service -> repository (Mongo)`. Hot path: `OrderController -> OrderService -> EzeeChargePostService -> EzeeClient`, then `FCMNotificationService`. No SQL.

## Routes and roles (SecurityConfig.java:67-73 + method @PreAuthorize)
| Route | Access |
| --- | --- |
| `/auth/login`, `/auth/refresh`, `/auth/logout` | public (permitAll) |
| `GET /health` | public |
| `POST /orders` | public in HTTP; method rule `hasAnyRole('ADMIN','STAFF') or isAnonymous()` (OrderController:32) |
| `GET /config`, `GET /menu-items/**`, `GET /other-essentials/**` | public |
| `GET /orders`, `/orders/{id}`, `PUT /orders/{id}/status` | ADMIN, STAFF |
| `DELETE /orders`, `/orders/{id}`; `POST /orders/{id}/chargepost`; `GET /orders/{id}/ezee-candidates` | ADMIN only (OrderController:93,108,175,202) |
| `/notifications/subscribe`, `/unsubscribe` | ADMIN, STAFF |
| `POST /users/me/register-fcm` | any authenticated |
| `/users` CRUD, `/audit` (GET, DELETE), POST/PUT/DELETE on `/config/categories`, `/config/dormitories`, `/config/statuses`, menu-items and other-essentials writes | ADMIN only |
| everything else | authenticated |

## Auth
- Stateless JWT Bearer, CSRF off, roles ADMIN/STAFF (`security/`). `JwtUtils` expiry default 3600000 ms; refresh grace default 365 days.
- Logout sets per-user `User.tokensValidFrom` watermark (model/User.java:34) plus blacklist; refresh does not blacklist the old token (DECISIONS.md).
- `LoginThrottle`: per-account login guessing. `RateLimitFilter` (60s window, in-memory, max 10000 keys): `POST /orders` 30/min, `POST /auth/login` 10/min; key = JWT user else client IP (X-Forwarded-For trusted only if peer is private/loopback).

## Collections (@Document)
`orders` (unique sparse index on idempotency key; compound `status_createdAt`), `users` (unique username), `menu_items`, `other_essentials`, `categories`, `dormitories`, `order_status_config`, `audit_logs`, `idempotency_keys` (TTL 3600 s).

## Order create idempotency
`Idempotency-Key` header (OrderController:34): stored on order + `IdempotencyRecord` (TTL). Status updates also cached (`order-status:<id>`, :77). Prices always re-read from DB.

## eZee charge posting (highest risk)
- Admin picks occupant by hand: `GET /orders/{id}/ezee-candidates` then `POST /orders/{id}/chargepost`; no auto matching.
- `OrderRepositoryImpl.claimForChargePost` (:100) atomically claims a DELIVERED order; `OrderService.java:309` calls it.
- `chargePostStatus`: null | IN_PROGRESS | QUEUED (eZee accepted) | FAILED (model/Order.java:87-91).
- No reply from eZee -> item recorded as `UNCONFIRMED:<id>` (EzeeChargePostService:37,241,264); retry needs admin to acknowledge after folio check. Charges cannot be voided.
- Stale claims: `OrderService.recoverStaleChargePosts` (:402, every 60 s, claim older than 15 min) marks FAILED with `UNCONFIRMED:interrupted`.
- Posting stops sending after 5 min. `ezee.mock=true` switches `EzeeClient` to `EzeeMockResponses`.
- Deeper detail: backend AGENTS "eZee PMS integration", workspace skill `backend-ezee-integration`.

## Schedulers
`OrderService.recoverStaleChargePosts` (60 s), `AuditCleanupTask` (cron `0 0 0 * * *`, retention `audit.retention-days` default 90), `HeartbeatTask` (6 h log line only; does not keep the Space awake).

## Notifications / startup
`FCMNotificationService`; `FirebaseConfig` throws at startup without credentials. Initial admin created/re-hashed at startup from `config.admin.*`.

# Architecture

Layers: controller -> service -> repository (Spring Data MongoDB, `MongoTemplate` for custom queries, e.g. `OrderRepositoryImpl`). No SQL. Most changes ripple through `OrderService` -> `EzeeChargePostService` -> FCM notification (backend AGENTS).

Auth: JWT Bearer via `security/SecurityConfig` + `AuthTokenFilter`; roles ADMIN/STAFF; `cors()` enabled. Logout raises a persisted `tokensValidFrom` watermark. Public: `/auth/**`, `/health`, `POST /orders`, `GET /config`, `GET /menu-items/**`, `GET /other-essentials/**`. `/chargepost` and `/ezee-candidates` are ADMIN only. Rate limiting in `RateLimitFilter`, login throttling in `LoginThrottle`.

eZee charge posting (highest risk): admin picks the occupant by hand (no auto matching); `claimForChargePost` atomically claims a DELIVERED order; charges cannot be voided; unconfirmed outcomes recorded as `UNCONFIRMED:<id>`; stale claims recovered by a scheduled job (15 min). `chargePostStatus`: null | IN_PROGRESS | QUEUED | FAILED. Full detail: backend AGENTS "eZee PMS integration" and the workspace skill `backend-ezee-integration`.

Order creation idempotency: `Idempotency-Key` stored on the order (unique sparse index) and in `IdempotencyRecord` (TTL). Prices are always re-read from the DB.

Notifications: `FCMNotificationService`; `FirebaseConfig` fails app start without credentials.

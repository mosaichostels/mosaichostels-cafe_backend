Read when: about to change auth, idempotency, eZee posting, rate limits or notifications.
Verified: 2026-10-29 @ 9e39a6f. Source: backend AGENTS (`../docs/backend/AGENTS.md`) plus code comments where cited.
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
| App-version-check feature removed | do not resurrect without explicit requirement |
| ntfy notifications removed | commit 16af0aa |
| Agents/skills/rules moved to shared workspace folder | commit e95ae6b |

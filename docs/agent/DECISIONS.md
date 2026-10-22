# Decisions

From backend AGENTS (workspace `../docs/backend/AGENTS.md`):
- Manual admin-mapped charge posting; automatic name/room matching rejected as spoofable.
- Idempotency and logout state persisted in Mongo (Space sleeps and redeploys).
- Refresh does not blacklist the old token (lost response would kill the session).
- Per-IP rate limits are 30 orders/min and 10 logins/min (hostel shares one IP).
- App-version-check feature removed; do not resurrect without explicit requirement.
- ntfy notifications removed (git log 16af0aa).

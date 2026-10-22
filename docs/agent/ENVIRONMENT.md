# Environment

Names only, from `application-prod.yml` and `@Value`/`getenv` in `src/main`. Values never read.
- `PORT` (default 7860), `MONGODB_URI`, `CORS_ALLOWED_ORIGINS` (required in prod, no default)
- `EZEE_ENDPOINT`, `EZEE_KIOSK_ENDPOINT`, `EZEE_AUTH_CODE`, `EZEE_HOTEL_CODE`, `EZEE_FOOD_CHARGE_ID`, `EZEE_ESSENTIAL_CHARGE_ID`
- `FIREBASE_SERVICE_ACCOUNT_JSON` (app will not start without it)
- Properties without a mapped env var in the yml: `config.admin.username`, `config.admin.password` (backend AGENTS names `CONFIG_ADMIN_PASSWORD`), `config.jwtSecret`, `config.jwtExpirationMs`, `config.jwtRefreshGraceMs`, `audit.retention-days`, `ezee.mock`. Exact env names for JWT secret: unknown.
- CI secret: `HF_TOKEN`.

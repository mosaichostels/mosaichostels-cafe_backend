# Environment

Read when: configuring, deploying, or debugging startup. Names only; no values read.
Verified: 2026-10-23 @ 24bdfbb (source: application-prod.yml and `@Value`/`getenv` in src/main)

| Name | Where used | Notes |
| --- | --- | --- |
| `PORT` | application-prod.yml:2 | default 7860 |
| `MONGODB_URI` | yml:16 | required, no default |
| `CORS_ALLOWED_ORIGINS` | yml:44 | required in prod, comma-separated, no wildcard |
| `EZEE_ENDPOINT`, `EZEE_KIOSK_ENDPOINT` | yml:50-51 | defaults to eZee live URLs |
| `EZEE_AUTH_CODE`, `EZEE_HOTEL_CODE` | yml:52-53 | default empty |
| `EZEE_FOOD_CHARGE_ID`, `EZEE_ESSENTIAL_CHARGE_ID` | yml:54-55 | default empty |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | FirebaseConfig:38 | falls back to classpath `firebase-service-account.json` (not in repo); no credentials -> startup fails |
| `HF_TOKEN` | GitHub Actions secret | deploy only |

Properties with no env mapping in the yml (set elsewhere or defaults):
| Property | Default / note |
| --- | --- |
| `config.admin.username` | default `admin` |
| `config.admin.password` | default none; backend AGENTS names `CONFIG_ADMIN_PASSWORD` |
| `config.jwtSecret` | default empty; exact env name unknown |
| `config.jwtExpirationMs` | 3600000 |
| `config.jwtRefreshGraceMs` | 31536000000 |
| `audit.retention-days` | 90 |
| `ezee.mock` | `false` in prod yml |
| `cors.allowed-origins` | code default `http://localhost:3000` (WebConfig:10) |

Where secrets are stored (HF Space settings, GitHub secrets): unknown. Local .env: none tracked.

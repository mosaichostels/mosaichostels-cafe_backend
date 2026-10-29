Read when: configuring, deploying, or debugging startup. Names only; no values read.
Verified: 2026-10-29 @ 9e39a6f (source: application-prod.yml and `@Value`/`getenv` in src/main)
Covers: src/main/resources/application-prod.yml, src/main/java/**/*.java, Dockerfile

# Environment

| Name | Where used | Notes |
| --- | --- | --- |
| `PORT` | application-prod.yml:2 | default 7860 |
| `MONGODB_URI` | yml:18 | required, no default |
| `CORS_ALLOWED_ORIGINS` | yml:43 | required in prod, comma-separated, no wildcard |
| `EZEE_ENDPOINT`, `EZEE_KIOSK_ENDPOINT` | yml:47-48 | defaults to eZee live URLs |
| `EZEE_AUTH_CODE`, `EZEE_HOTEL_CODE` | yml:49-50 | default empty |
| `EZEE_FOOD_CHARGE_ID`, `EZEE_ESSENTIAL_CHARGE_ID` | yml:51-52 | default empty |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | FirebaseConfig:38 | falls back to classpath `firebase-service-account.json` (not in repo); no credentials -> startup fails |
| `HF_TOKEN` | GitHub Actions secret | deploy only |

Properties with no env mapping in the yml (set elsewhere or defaults):
| Property | Default / note |
| --- | --- |
| `config.admin.username` | default `admin` |
| `config.admin.password` | default none (HostelOrderingApplication:19); env `CONFIG_ADMIN_PASSWORD` (HostelOrderingApplication:31, backend AGENTS) |
| `config.jwtSecret` | default empty, startup fails if under 32 bytes; env `CONFIG_JWTSECRET` (JwtUtils:39) |
| `config.jwtExpirationMs` | 3600000 |
| `config.jwtRefreshGraceMs` | 31536000000 |
| `audit.retention-days` | 90 |
| `ezee.mock` | `false` in prod yml |
| `cors.allowed-origins` | code default `http://localhost:3000` (WebConfig:10) |

Where secrets are stored (HF Space settings, GitHub secrets): unknown. Local .env: none tracked.
